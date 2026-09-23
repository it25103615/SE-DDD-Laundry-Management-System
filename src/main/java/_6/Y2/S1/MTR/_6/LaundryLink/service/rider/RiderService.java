package _6.Y2.S1.MTR._6.LaundryLink.service.rider;

import _6.Y2.S1.MTR._6.LaundryLink.dto.rider.FailureRequest;
import _6.Y2.S1.MTR._6.LaundryLink.dto.rider.RiderTaskDTO;
import _6.Y2.S1.MTR._6.LaundryLink.repository.rider.RiderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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

    public List<RiderTaskDTO> getAvailableTasks() {
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

    private int setDeliveryEnRoute(Integer deliverId) {
        // Uses a direct status update because the rider ID has already been assigned.
        return repository.updateDeliveryToEnRoute(deliverId, getCurrentRiderId());
    }

    @Transactional
    public void pickedUp(Integer deliverId) {
        requireCurrentTask(deliverId, "pickup", 4);
        if (repository.pickupPickedUp(deliverId, getCurrentRiderId()) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pickup is no longer available for this action.");
        }
    }

    @Transactional
    public void pickupDelivered(Integer deliverId) {
        requireCurrentTask(deliverId, "pickup", 6);
        if (repository.pickupDeliveredToShop(deliverId, getCurrentRiderId()) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pickup is no longer available for this action.");
        }
    }

    @Transactional
    public void pickupFailed(Integer deliverId, FailureRequest request) {
        requireCurrentTask(deliverId, "pickup", 4);
        if (repository.pickupFailed(deliverId, getCurrentRiderId(), request.note().trim()) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pickup failure could not be recorded.");
        }
    }

    @Transactional
    public void deliveryDelivered(Integer deliverId) {
        requireCurrentTask(deliverId, "delivery", 13);
        if (repository.deliveryDelivered(deliverId, getCurrentRiderId()) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Delivery is no longer available for this action.");
        }
    }

    @Transactional
    public void deliveryFailed(Integer deliverId, FailureRequest request) {
        requireCurrentTask(deliverId, "delivery", 13);
        if (repository.deliveryFailed(deliverId, getCurrentRiderId(), request.note().trim()) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Delivery failure could not be recorded.");
        }
    }

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
