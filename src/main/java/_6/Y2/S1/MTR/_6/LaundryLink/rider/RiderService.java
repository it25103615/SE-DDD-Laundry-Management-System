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

// WHY: Only a signed-in RIDER account may use the rider module. Every public method starts by
//      resolving the current rider (currentRider()/getCurrentRiderId()), so a request from
//      anyone else is rejected before any task is read or changed: 401 when nobody is signed
//      in, 403 when the signed-in account is not a rider. RiderController only calls this
//      service, so the same rule covers every /api/rider endpoint.
@Service
public class RiderService {

    private final RiderRepository repository;
    private final UserRepository users;

    public RiderService(RiderRepository repository, UserRepository users) {
        this.repository = repository;
        this.users = users;
    }

    /** The signed-in rider's user ID (see currentRider() for the checks). */
    public Integer getCurrentRiderId() {
        return currentRider().getUserID();
    }

    // WHY: Replaces the fixed test rider ID. The rider is whoever is signed in.
    // HOW: Spring Security stores the login email as the authentication name; the account is
    //      looked up with UserRepository.findByEmailIgnoreCase and must have type RIDER.
    //      An unauthenticated request carries an AnonymousAuthenticationToken (which reports
    //      isAuthenticated() == true), so it is checked for explicitly.
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

    public List<RiderTaskDTO> getAvailableTasks() {
        getCurrentRiderId(); // the open task pool is for riders only
        return repository.findAvailableTasks().stream().map(this::mapRow).toList();
    }

    public List<RiderTaskDTO> getMyWork() {
        return repository.findMyWork(getCurrentRiderId()).stream().map(this::mapRow).toList();
    }

    // WHY: Returns the six database-backed dashboard metrics.
    // HOW: Rider ID is needed only for completed and remaining work.
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

    // WHY: Backs GET /me (profile badge and greeting).
    // HOW: Uses the signed-in rider's account directly, so no second users query is needed.
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

    private int setDeliveryEnRoute(Integer deliverId, Integer riderId) {
        // Uses a direct status update because the rider ID has already been assigned.
        return repository.updateDeliveryToEnRoute(deliverId, riderId);
    }

    @Transactional
    public void pickedUp(Integer deliverId) {
        Integer riderId = getCurrentRiderId();
        requireCurrentTask(deliverId, "pickup", 4);
        if (repository.pickupPickedUp(deliverId, riderId) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pickup is no longer available for this action.");
        }
    }

    @Transactional
    public void pickupDelivered(Integer deliverId) {
        Integer riderId = getCurrentRiderId();
        requireCurrentTask(deliverId, "pickup", 6);
        if (repository.pickupDeliveredToShop(deliverId, riderId) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pickup is no longer available for this action.");
        }
    }

    @Transactional
    public void pickupFailed(Integer deliverId, FailureRequest request) {
        Integer riderId = getCurrentRiderId();
        requireCurrentTask(deliverId, "pickup", 4);
        if (repository.pickupFailed(deliverId, riderId, request.note().trim()) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pickup failure could not be recorded.");
        }
    }

    @Transactional
    public void deliveryDelivered(Integer deliverId) {
        Integer riderId = getCurrentRiderId();
        requireCurrentTask(deliverId, "delivery", 13);
        if (repository.deliveryDelivered(deliverId, riderId) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Delivery is no longer available for this action.");
        }
    }

    @Transactional
    public void deliveryFailed(Integer deliverId, FailureRequest request) {
        Integer riderId = getCurrentRiderId();
        requireCurrentTask(deliverId, "delivery", 13);
        if (repository.deliveryFailed(deliverId, riderId, request.note().trim()) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Delivery failure could not be recorded.");
        }
    }

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

    private Object[] findTask(Integer deliverId) {
        return repository.findTaskById(deliverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Delivery assignment not found."));
    }

    private void requireCurrentTask(Integer deliverId, String type, int statusId) {
        Object[] task = findTask(deliverId);
        if (!type.equals(task[2].toString()) || ((Number) task[3]).intValue() != statusId) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invalid action for the current task status.");
        }
        // Assignment ownership is checked by the update query itself.
    }

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

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        if (value instanceof java.sql.Date date) return date.toLocalDate().atStartOfDay();
        if (value instanceof LocalDateTime dateTime) return dateTime;
        return LocalDateTime.parse(value.toString().replace(' ', 'T'));
    }



}
