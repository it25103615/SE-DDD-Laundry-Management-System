package _6.Y2.S1.MTR._6.LaundryLink.rider.controller;

import _6.Y2.S1.MTR._6.LaundryLink.rider.dto.FailureRequest;
import _6.Y2.S1.MTR._6.LaundryLink.rider.dto.RiderTaskDTO;
import _6.Y2.S1.MTR._6.LaundryLink.rider.service.RiderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// WHY: Thin HTTP layer for the rider frontend (dashboard.html,
//      task_list.html) — every method is a direct pass-through to
//      RiderService, with no business logic of its own.
// HOW: GET endpoints return data; PUT endpoints perform one state change
//      each and return 204 No Content on success (errors are thrown as
//      ResponseStatusException by the service layer and handled by
//      Spring's default exception handling).
@RestController
@RequestMapping("/api/rider")
public class RiderController {

    private final RiderService riderService;

    public RiderController(RiderService riderService) {
        this.riderService = riderService;
    }

    @GetMapping("/tasks")
    public List<RiderTaskDTO> getAvailableTasks() {
        return riderService.getAvailableTasks();
    }

    @GetMapping("/my-work")
    public List<RiderTaskDTO> getMyWork() {
        return riderService.getMyWork();
    }

    @GetMapping("/summary")
    public Map<String, Object> getDashboardSummary() {
        return riderService.getDashboardSummary();
    }

    @GetMapping("/me")
    public Map<String, Object> getCurrentRider() {
        return riderService.getCurrentRider();
    }

    @PutMapping("/tasks/{deliverId}/accept")
    public ResponseEntity<Void> accept(@PathVariable Integer deliverId) {
        riderService.accept(deliverId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/tasks/{deliverId}/picked-up")
    public ResponseEntity<Void> pickedUp(@PathVariable Integer deliverId) {
        riderService.pickedUp(deliverId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/tasks/{deliverId}/pickup-delivered")
    public ResponseEntity<Void> pickupDelivered(@PathVariable Integer deliverId) {
        riderService.pickupDelivered(deliverId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/tasks/{deliverId}/pickup-failed")
    public ResponseEntity<Void> pickupFailed(@PathVariable Integer deliverId,
                                             @Valid @RequestBody FailureRequest request) {
        riderService.pickupFailed(deliverId, request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/tasks/{deliverId}/delivery-delivered")
    public ResponseEntity<Void> deliveryDelivered(@PathVariable Integer deliverId) {
        riderService.deliveryDelivered(deliverId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/tasks/{deliverId}/delivery-failed")
    public ResponseEntity<Void> deliveryFailed(@PathVariable Integer deliverId,
                                               @Valid @RequestBody FailureRequest request) {
        riderService.deliveryFailed(deliverId, request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/tasks/{deliverId}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable Integer deliverId) {
        riderService.cancel(deliverId);
        return ResponseEntity.noContent().build();
    }
}
