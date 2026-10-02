package _6.Y2.S1.MTR._6.LaundryLink.rider;

import _6.Y2.S1.MTR._6.LaundryLink.rider.dto.FailureRequest;
import _6.Y2.S1.MTR._6.LaundryLink.rider.dto.RiderTaskDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST endpoints under {@code /api/rider}. Contains no logic of its own: every method
 * delegates to {@link RiderService}, which only lets a signed-in RIDER account through
 * (401 when signed out, 403 for any other account type).
 */
@RestController
@RequestMapping("/api/rider")
public class RiderController {

    private final RiderService riderService;

    public RiderController(RiderService riderService) {
        this.riderService = riderService;
    }

    /**
     * Open task pool (pickups and deliveries without a rider).
     *
     * @return available tasks
     */
    @GetMapping("/tasks")
    public List<RiderTaskDTO> getAvailableTasks() {
        return riderService.getAvailableTasks();
    }

    /**
     * This rider's active assignments.
     *
     * @return the rider's tasks
     */
    @GetMapping("/my-work")
    public List<RiderTaskDTO> getMyWork() {
        return riderService.getMyWork();
    }

    /**
     * Six dashboard counts (available, completed, remaining; pickup and delivery).
     *
     * @return map of counts
     */
    @GetMapping("/summary")
    public Map<String, Object> getDashboardSummary() {
        return riderService.getDashboardSummary();
    }

    /**
     * Signed-in rider's name and initials for the profile badge.
     *
     * @return map with userID, firstName, lastName, initials
     */
    @GetMapping("/me")
    public Map<String, Object> getCurrentRider() {
        return riderService.getCurrentRider();
    }

    /**
     * Claims a task and starts it (accept + en route).
     *
     * @param deliverId the task ID
     * @return 204 No Content
     */
    @PutMapping("/tasks/{deliverId}/accept")
    public ResponseEntity<Void> accept(@PathVariable Integer deliverId) {
        riderService.accept(deliverId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Rider collected the laundry (4 to 6).
     *
     * @param deliverId the pickup task ID
     * @return 204 No Content
     */
    @PutMapping("/tasks/{deliverId}/picked-up")
    public ResponseEntity<Void> pickedUp(@PathVariable Integer deliverId) {
        riderService.pickedUp(deliverId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Rider handed the laundry to the shop (6 to 7).
     *
     * @param deliverId the pickup task ID
     * @return 204 No Content
     */
    @PutMapping("/tasks/{deliverId}/pickup-delivered")
    public ResponseEntity<Void> pickupDelivered(@PathVariable Integer deliverId) {
        riderService.pickupDelivered(deliverId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Pickup failed. Requires a note (max 250 characters); the task returns to the pool.
     *
     * @param deliverId the pickup task ID
     * @param request   body with the failure note
     * @return 204 No Content
     */
    @PutMapping("/tasks/{deliverId}/pickup-failed")
    public ResponseEntity<Void> pickupFailed(@PathVariable Integer deliverId,
                                             @Valid @RequestBody FailureRequest request) {
        riderService.pickupFailed(deliverId, request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Delivery completed (13 to 15).
     *
     * @param deliverId the delivery task ID
     * @return 204 No Content
     */
    @PutMapping("/tasks/{deliverId}/delivery-delivered")
    public ResponseEntity<Void> deliveryDelivered(@PathVariable Integer deliverId) {
        riderService.deliveryDelivered(deliverId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Delivery failed. Requires a note; the task returns to the pool.
     *
     * @param deliverId the delivery task ID
     * @param request   body with the failure note
     * @return 204 No Content
     */
    @PutMapping("/tasks/{deliverId}/delivery-failed")
    public ResponseEntity<Void> deliveryFailed(@PathVariable Integer deliverId,
                                               @Valid @RequestBody FailureRequest request) {
        riderService.deliveryFailed(deliverId, request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Rider releases an accepted task (only at status 4 or 13).
     *
     * @param deliverId the task ID
     * @return 204 No Content
     */
    @PutMapping("/tasks/{deliverId}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable Integer deliverId) {
        riderService.cancel(deliverId);
        return ResponseEntity.noContent().build();
    }
}
