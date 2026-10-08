package _6.Y2.S1.MTR._6.LaundryLink.rider;

import _6.Y2.S1.MTR._6.LaundryLink.rider.dto.FailureRequest;
import _6.Y2.S1.MTR._6.LaundryLink.rider.dto.RiderTaskDTO;
import _6.Y2.S1.MTR._6.LaundryLink.user.User;
import _6.Y2.S1.MTR._6.LaundryLink.user.UserRepository;
import _6.Y2.S1.MTR._6.LaundryLink.user.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Business rules for the rider module.
 *
 * <p>Only a signed-in RIDER account may use it. Every public method starts by resolving the
 * current rider ({@link #currentRider()} / {@link #getCurrentRiderId()}), so a request from
 * anyone else is rejected before any task is read or changed: 401 when nobody is signed in,
 * 403 when the signed-in account is not a rider. {@code RiderController} only calls this
 * service, so the same rule covers every {@code /api/rider} endpoint.
 *
 * <p>Every write method is {@code @Transactional}, so multi-step updates commit or roll back
 * together.
 */
@Service
public class RiderService {

    private final RiderRepository repository;
    private final UserRepository users;

    public RiderService(RiderRepository repository, UserRepository users) {
        this.repository = repository;
        this.users = users;
    }

    /**
     * Returns the signed-in rider's user ID. The single entry point for the rider check, so
     * every public method starts with it.
     *
     * @return the rider's {@code userID}
     * @throws ResponseStatusException see {@link #currentRider()}
     */
    public Integer getCurrentRiderId() {
        return currentRider().getUserID();
    }

    /**
     * Resolves the signed-in user and confirms they are a RIDER.
     *
     * <p>Spring Security stores the login email as the authentication name; the account is
     * found with {@code UserRepository.findByEmailIgnoreCase}. An anonymous request carries an
     * {@code AnonymousAuthenticationToken} (which still reports {@code isAuthenticated() == true}),
     * so it is checked explicitly.
     *
     * @return the rider's {@link User}
     * @throws ResponseStatusException 401 if not signed in, 404 if no account exists for the
     *                                 email, 403 if the account is not a RIDER
     */
    private User currentRider() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Rider authentication required");
        }

        String email = auth.getName();
        User user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Rider account not found for user: " + email
                ));

        if (user.getType() != UserRole.RIDER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only rider accounts can use rider tasks.");
        }
        return user;
    }

    /**
     * Returns the open pool of pickups and deliveries. Any rider may see all of it.
     *
     * @return available tasks, oldest first
     */
    public List<RiderTaskDTO> getAvailableTasks() {
        getCurrentRiderId(); // the open task pool is for riders only
        return repository.findAvailableTasks().stream().map(this::mapRow).toList();
    }

    /**
     * Returns only this rider's active assignments (see {@code RiderRepository.findMyWork}).
     *
     * @return the rider's active tasks
     */
    public List<RiderTaskDTO> getMyWork() {
        return repository.findMyWork(getCurrentRiderId()).stream().map(this::mapRow).toList();
    }

    /**
     * Builds the six database-backed dashboard metrics: available, completed and remaining
     * work for pickup and delivery. The rider ID is needed only for completed and remaining.
     *
     * @return map with keys {@code availablePickup, availableDelivery, completedPickup,
     *         completedDelivery, remainingPickup, remainingDelivery}
     */
    public Map<String, Object> getDashboardSummary() {
        Integer riderId = getCurrentRiderId();

        return Map.of(
                "availablePickup", repository.countAvailablePickup(),
                "availableDelivery", repository.countAvailableDelivery(),
                "completedPickup", repository.countCompletedPickup(riderId),
                "completedDelivery", repository.countCompletedDelivery(riderId),
                "remainingPickup", repository.countRemainingPickup(riderId),
                "remainingDelivery", repository.countRemainingDelivery(riderId)
        );
    }

    /**
     * Backs {@code GET /me} (profile badge and greeting). Uses the signed-in rider's account
     * directly, so no second users query is needed. Initials are the first letters of the
     * first and last name, upper-cased.
     *
     * @return map with {@code userID, firstName, lastName, initials}
     */
    public java.util.Map<String, Object> getCurrentRider() {
        User rider = currentRider();
        String firstName = rider.getFirstName() == null ? "" : rider.getFirstName();
        String lastName = rider.getLastName() == null ? "" : rider.getLastName();
        String initials = ((firstName.isEmpty() ? "" : firstName.substring(0, 1))
                + (lastName.isEmpty() ? "" : lastName.substring(0, 1))).toUpperCase();
        return java.util.Map.of(
                "userID", rider.getUserID(),
                "firstName", firstName,
                "lastName", lastName,
                "initials", initials
        );
    }

    /**
     * A rider takes a task from the pool.
     *
     * <p>Looks up the task, claims it, then moves it to "en route". Both steps are in one
     * transaction, so a rider is never left assigned to a task that is still at "awaiting".
     *
     * <p><b>Note:</b> {@code findTask()} can return a {@code NULL} type for statuses outside the
     * rider flow (for example 1, 2, 8); {@code task[2].toString()} would then fail with a 500
     * instead of a 409.
     *
     * @param deliverId the task to accept
     * @throws ResponseStatusException 404 if not found; 409 if someone else claimed it first
     *                                 or it could not be moved to en route
     */
    @Transactional
    public void accept(Integer deliverId) {
        Integer riderId = getCurrentRiderId();
        Object[] task = findTask(deliverId);
        String type = task[2].toString();
        int updated = "pickup".equals(type)
                ? repository.acceptPickup(deliverId, riderId)
                : repository.acceptDelivery(deliverId, riderId);
        if (updated != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This assignment is no longer available.");
        }
        int next = "pickup".equals(type)
                ? repository.movePickupToEnRoute(deliverId, riderId)
                : setDeliveryEnRoute(deliverId, riderId);
        if (next != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The assignment could not be moved to en route.");
        }
    }

    /**
     * Thin wrapper so {@link #accept} reads the same for pickups and deliveries. The rider ID
     * has already been assigned, so this is a direct status update.
     */
    private int setDeliveryEnRoute(Integer deliverId, Integer riderId) {
        // Uses a direct status update because the rider ID has already been assigned.
        return repository.updateDeliveryToEnRoute(deliverId, riderId);
    }

    /**
     * Rider collected the laundry (pickup at status 4 to 6).
     * {@link #requireCurrentTask} checks type and status first; the repository update then
     * checks ownership.
     *
     * @param deliverId the pickup task
     * @throws ResponseStatusException 409 if the task is not a pickup at status 4 or the
     *                                 update affected no row
     */
    @Transactional
    public void pickedUp(Integer deliverId) {
        Integer riderId = getCurrentRiderId();
        requireCurrentTask(deliverId, "pickup", 4);
        if (repository.pickupPickedUp(deliverId, riderId) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pickup is no longer available for this action.");
        }
    }

    /**
     * Rider handed the laundry to the shop (pickup at status 6 to 7, In Shop).
     *
     * @param deliverId the pickup task
     * @throws ResponseStatusException 409 if the task is not a pickup at status 6 or the
     *                                 update affected no row
     */
    @Transactional
    public void pickupDelivered(Integer deliverId) {
        Integer riderId = getCurrentRiderId();
        requireCurrentTask(deliverId, "pickup", 6);
        if (repository.pickupDeliveredToShop(deliverId, riderId) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pickup is no longer available for this action.");
        }
    }

    /**
     * Pickup attempt failed (status 4). The trimmed note is stored and the task returns to
     * the open pool.
     *
     * @param deliverId the pickup task
     * @param request   the failure note
     * @throws ResponseStatusException 409 if the task is not a pickup at status 4 or the
     *                                 update affected no row
     */
    @Transactional
    public void pickupFailed(Integer deliverId, FailureRequest request) {
        Integer riderId = getCurrentRiderId();
        requireCurrentTask(deliverId, "pickup", 4);
        if (repository.pickupFailed(deliverId, riderId, request.note().trim()) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pickup failure could not be recorded.");
        }
    }

    /**
     * Rider delivered to the customer (delivery at status 13 to 15, Completed).
     *
     * @param deliverId the delivery task
     * @throws ResponseStatusException 409 if the task is not a delivery at status 13 or the
     *                                 update affected no row
     */
    @Transactional
    public void deliveryDelivered(Integer deliverId) {
        Integer riderId = getCurrentRiderId();
        requireCurrentTask(deliverId, "delivery", 13);
        if (repository.deliveryDelivered(deliverId, riderId) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Delivery is no longer available for this action.");
        }
    }

    /**
     * Delivery attempt failed (status 13). The trimmed note is stored and the task returns
     * to the open pool.
     *
     * @param deliverId the delivery task
     * @param request   the failure note
     * @throws ResponseStatusException 409 if the task is not a delivery at status 13 or the
     *                                 update affected no row
     */
    @Transactional
    public void deliveryFailed(Integer deliverId, FailureRequest request) {
        Integer riderId = getCurrentRiderId();
        requireCurrentTask(deliverId, "delivery", 13);
        if (repository.deliveryFailed(deliverId, riderId, request.note().trim()) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Delivery failure could not be recorded.");
        }
    }

    /**
     * The rider releases a task they accepted but have not completed.
     *
     * <p>Allowed only for a pickup at status 4 or a delivery at status 13. Status 6 cannot be
     * cancelled because the rider already holds the items.
     *
     * @param deliverId the task to release
     * @throws ResponseStatusException 409 if the status does not allow cancelling or the
     *                                 update affected no row
     */
    @Transactional
    public void cancel(Integer deliverId) {
        Integer riderId = getCurrentRiderId();
        Object[] task = findTask(deliverId);
        String type = task[2].toString();
        int statusId = ((Number) task[3]).intValue();
        int updated;
        if ("pickup".equals(type) && statusId == 4) {
            updated = repository.cancelPickup(deliverId, riderId);
        } else if ("delivery".equals(type) && statusId == 13) {
            updated = repository.cancelDelivery(deliverId, riderId);
        } else {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This assignment cannot be cancelled at its current status.");
        }
        if (updated != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Assignment could not be cancelled.");
        }
    }

    /**
     * Loads the task row.
     *
     * @param deliverId the task ID
     * @return the raw row (see {@code RiderRepository.findTaskById})
     * @throws ResponseStatusException 404 if it does not exist
     */
    private Object[] findTask(Integer deliverId) {
        return repository.findTaskById(deliverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Delivery assignment not found."));
    }

    /**
     * Guards an action by checking the task's type and status match what the action expects.
     * Ownership is not checked here; the repository's UPDATE does it.
     *
     * @param deliverId the task ID
     * @param type      expected type ({@code "pickup"} or {@code "delivery"})
     * @param statusId  expected status ID
     * @throws ResponseStatusException 404 if not found; 409 if type or status differ
     */
    private void requireCurrentTask(Integer deliverId, String type, int statusId) {
        Object[] task = findTask(deliverId);
        if (!type.equals(task[2].toString()) || ((Number) task[3]).intValue() != statusId) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invalid action for the current task status.");
        }
        // Assignment ownership is checked by the update query itself.
    }

    /**
     * Converts a native-query row into a DTO. The column order must match the SELECT in
     * {@code RiderRepository}. Null text fields become {@code ""}.
     *
     * @param row one result row
     * @return the DTO
     */
    private RiderTaskDTO mapRow(Object[] row) {
        return new RiderTaskDTO(
                ((Number) row[0]).intValue(),
                row[1] == null ? null : ((Number) row[1]).intValue(),
                row[2] == null ? null : row[2].toString(),
                row[3] == null ? null : ((Number) row[3]).intValue(),
                row[4] == null ? null : row[4].toString(),
                row[5] == null ? "" : row[5].toString().trim(),
                row[6] == null ? "" : row[6].toString().trim(),
                row[7] == null ? "" : row[7].toString().trim(),
                toLocalDateTime(row[8]),
                toLocalDateTime(row[9])
        );
    }

    /**
     * Normalises the different date/time types a native query may return.
     *
     * @param value a {@code Timestamp}, {@code java.sql.Date}, {@code LocalDateTime}, or
     *              date string; may be null
     * @return the value as {@code LocalDateTime}, or null
     */
    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        if (value instanceof java.sql.Date date) return date.toLocalDate().atStartOfDay();
        if (value instanceof LocalDateTime dateTime) return dateTime;
        return LocalDateTime.parse(value.toString().replace(' ', 'T'));
    }



}
