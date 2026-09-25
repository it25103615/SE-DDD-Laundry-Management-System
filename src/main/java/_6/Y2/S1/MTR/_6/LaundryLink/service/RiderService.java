package _6.Y2.S1.MTR._6.LaundryLink.service;

import _6.Y2.S1.MTR._6.LaundryLink.dto.FailureRequest;
import _6.Y2.S1.MTR._6.LaundryLink.dto.RiderTaskDTO;
import _6.Y2.S1.MTR._6.LaundryLink.repository.RiderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

// WHY: Sits between the controller and the repository — translates raw
//      Object[] rows into RiderTaskDTOs, enforces status/ownership rules
//      before allowing a state change, and is the one place that knows
//      "who is the current rider" (see getCurrentRiderId()).
// HOW: Each public method maps 1:1 to a RiderController endpoint. Every
//      write method is @Transactional so a multi-step change (e.g. accept:
//      claim the task, then advance its status) either fully commits or
//      fully rolls back.
@Service
public class RiderService {

    private final RiderRepository repository;

    /* Temporary until the team's login/authentication function is connected. */
    private final Integer testRiderId;

    public RiderService(RiderRepository repository,
                        @Value("${rider.test-id:7}") Integer testRiderId) {
        this.repository = repository;
        this.testRiderId = testRiderId;
    }

    public Integer getCurrentRiderId() {
        return testRiderId;
    }
    /* there are two methods that does the same function
       one of them is chosen and replaced with the current getCurrentRiderID

    public Integer getCurrentRiderId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // Check if an authenticated user session exists in Spring Security
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            String email = auth.getName(); // Spring Security stores the email here
            return repository.findUserIdByEmail(email)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Rider account not found for user: " + email
                    ));
        }

        // Fallback for unauthenticated local testing
        return testRiderId;
    }

    public Integer getCurrentRiderId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();

    if (auth == null || !auth.isAuthenticated()
            || "anonymousUser".equals(auth.getPrincipal())) {
        throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Rider authentication required"
        );
    }

    String email = auth.getName();

    return repository.findUserIdByEmail(email)
            .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Rider account not found for user: " + email
            ));
    }*/

    // WHY/HOW: Backs GET /tasks — fetches the unassigned pool and maps each
//          raw row to a RiderTaskDTO via mapRow().
    public List<RiderTaskDTO> getAvailableTasks() {
        return repository.findAvailableTasks().stream().map(this::mapRow).toList();
    }

    // WHY/HOW: Backs GET /my-work — same mapping as getAvailableTasks(), but
//          scoped to the current rider's own active assignments.
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

    // WHY: Backs GET /me — populates the profile badge and greeting.
// HOW: Computes initials from first/last name here rather than storing
//      them, so a name change never requires a data migration.
    public java.util.Map<String, Object> getCurrentRider() {
        Object[] rider = repository.findRider(getCurrentRiderId());
        if (rider == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Test rider was not found.");
        }
        String firstName = rider[1] == null ? "" : rider[1].toString();
        String lastName = rider[2] == null ? "" : rider[2].toString();
        String initials = ((firstName.isEmpty() ? "" : firstName.substring(0, 1))
                + (lastName.isEmpty() ? "" : lastName.substring(0, 1))).toUpperCase();
        return java.util.Map.of(
                "userID", ((Number) rider[0]).intValue(),
                "firstName", firstName,
                "lastName", lastName,
                "initials", initials
        );
    }

    // WHY: Backs PUT /tasks/{id}/accept — one rider action that needs two
//      database changes to succeed together (claim the task, then move
//      its status forward), hence @Transactional.
// HOW: Looks up the task to find its type, calls the matching claim method
//      (acceptPickup/acceptDelivery), then advances its status. Either
//      step failing (return value != 1) throws a 409 Conflict — most
//      often because another rider claimed it first.
    @Transactional
    public void accept(Integer deliverId) {
        Object[] task = findTask(deliverId);
        String type = task[2].toString();
        int updated = "pickup".equals(type)
                ? repository.acceptPickup(deliverId, getCurrentRiderId())
                : repository.acceptDelivery(deliverId, getCurrentRiderId());
        if (updated != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This assignment is no longer available.");
        }
        int next = "pickup".equals(type)
                ? repository.movePickupToEnRoute(deliverId, getCurrentRiderId())
                : setDeliveryEnRoute(deliverId);
        if (next != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The assignment could not be moved to en route.");
        }
    }

    // WHY: Split out from accept() only because a delivery's "claim" and
//      "advance status" are actually the same repository call
//      (updateDeliveryToEnRoute already sets the rider column and status
//      together), unlike pickup which needs two separate calls.
    private int setDeliveryEnRoute(Integer deliverId) {
        // Uses a direct status update because the rider ID has already been assigned.
        return repository.updateDeliveryToEnRoute(deliverId, getCurrentRiderId());
    }

    // WHY/HOW: Backs PUT /tasks/{id}/picked-up. requireCurrentTask() guards
//          that this task is actually a pickup at statusID 4 for this
//          rider before attempting the change, so a stale/duplicate
//          button click gets a clear 409 instead of a confusing DB error.
    @Transactional
    public void pickedUp(Integer deliverId) {
        requireCurrentTask(deliverId, "pickup", 4);
        if (repository.pickupPickedUp(deliverId, getCurrentRiderId()) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pickup is no longer available for this action.");
        }
    }

    // WHY/HOW: Backs PUT /tasks/{id}/pickup-delivered — same guard pattern as
//          pickedUp(), at statusID 6.
    @Transactional
    public void pickupDelivered(Integer deliverId) {
        requireCurrentTask(deliverId, "pickup", 6);
        if (repository.pickupDeliveredToShop(deliverId, getCurrentRiderId()) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pickup is no longer available for this action.");
        }
    }

    // WHY/HOW: Backs PUT /tasks/{id}/pickup-failed. The note is trimmed here
//          (not just validated) so stored notes never have stray leading/
//          trailing whitespace regardless of what the client sends.
    @Transactional
    public void pickupFailed(Integer deliverId, FailureRequest request) {
        requireCurrentTask(deliverId, "pickup", 4);
        if (repository.pickupFailed(deliverId, getCurrentRiderId(), request.note().trim()) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pickup failure could not be recorded.");
        }
    }

    // WHY/HOW: Backs PUT /tasks/{id}/delivery-delivered — same guard pattern,
//          at statusID 13.
    @Transactional
    public void deliveryDelivered(Integer deliverId) {
        requireCurrentTask(deliverId, "delivery", 13);
        if (repository.deliveryDelivered(deliverId, getCurrentRiderId()) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Delivery is no longer available for this action.");
        }
    }

    // WHY/HOW: Backs PUT /tasks/{id}/delivery-failed — mirrors pickupFailed().
    @Transactional
    public void deliveryFailed(Integer deliverId, FailureRequest request) {
        requireCurrentTask(deliverId, "delivery", 13);
        if (repository.deliveryFailed(deliverId, getCurrentRiderId(), request.note().trim()) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Delivery failure could not be recorded.");
        }
    }

    // WHY: Backs PUT /tasks/{id}/cancel — the only action whose valid
//      statusID depends on which type the task is, so it can't reuse
//      requireCurrentTask() as-is; it checks both branches itself.
// HOW: pickup can only be cancelled at statusID 4, delivery only at 13 —
//      anything else is rejected before even attempting the DB call.
    @Transactional
    public void cancel(Integer deliverId) {
        Object[] task = findTask(deliverId);
        String type = task[2].toString();
        int statusId = ((Number) task[3]).intValue();
        int updated;
        if ("pickup".equals(type) && statusId == 4) {
            updated = repository.cancelPickup(deliverId, getCurrentRiderId());
        } else if ("delivery".equals(type) && statusId == 13) {
            updated = repository.cancelDelivery(deliverId, getCurrentRiderId());
        } else {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This assignment cannot be cancelled at its current status.");
        }
        if (updated != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Assignment could not be cancelled.");
        }
    }

    // WHY/HOW: Shared lookup used by every action method — turns "task not
//          found" into a clean 404 instead of a null-pointer risk later.
    private Object[] findTask(Integer deliverId) {
        return repository.findTaskById(deliverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Delivery assignment not found."));
    }

    // WHY: Centralizes the "is this task the right type, at the right status,
//      for this action?" check so each action method doesn't repeat it.
// HOW: Ownership itself isn't checked here — it's enforced by the WHERE
//      clause inside the repository UPDATE, and a mismatch there just
//      results in 0 rows affected -> a 409 from the caller.
    private void requireCurrentTask(Integer deliverId, String type, int statusId) {
        Object[] task = findTask(deliverId);
        if (!type.equals(task[2].toString()) || ((Number) task[3]).intValue() != statusId) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invalid action for the current task status.");
        }
        // Assignment ownership is checked by the update query itself.
    }

    // WHY: Converts a raw Object[] row from a native query into a typed,
//      null-safe RiderTaskDTO.
// HOW: Positional — row[0], row[1]... must match the exact column order
//      of whichever SELECT produced this row (findAvailableTasks() /
//      findMyWork()). Changing a SELECT's column order without updating
//      this mapping will silently misalign fields.
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

    // WHY: JDBC can hand back a Timestamp, a plain Date, or already a
//      LocalDateTime depending on the driver/column type — this
//      normalizes all of them to one type for the DTO.
    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        if (value instanceof java.sql.Date date) return date.toLocalDate().atStartOfDay();
        if (value instanceof LocalDateTime dateTime) return dateTime;
        return LocalDateTime.parse(value.toString().replace(' ', 'T'));
    }



}
