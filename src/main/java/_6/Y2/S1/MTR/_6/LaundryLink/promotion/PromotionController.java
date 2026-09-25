package _6.Y2.S1.MTR._6.LaundryLink.promotion;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/promotions")
public class PromotionController {
    private final PromotionService promotionService;

    public PromotionController(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @PostMapping
    public ResponseEntity<Promotion> createPromotion(@RequestBody PromotionRequest request) {
        try {
            return ResponseEntity.status(201).body(promotionService.createPromotion(request));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{promotionID}")
    public ResponseEntity<Promotion> updatePromotion(
            @PathVariable Integer promotionID,
            @RequestBody PromotionRequest request
    ) {
        try {
            return ResponseEntity.ok(promotionService.updatePromotion(promotionID, request));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PatchMapping("/{promotionID}/active")
    public ResponseEntity<Promotion> setPromotionActive(
            @PathVariable Integer promotionID,
            @RequestParam boolean active
    ) {
        try {
            return ResponseEntity.ok(promotionService.setPromotionActive(promotionID, active));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{promotionID}")
    public ResponseEntity<Promotion> getPromotion(@PathVariable Integer promotionID) {
        try {
            return ResponseEntity.ok(promotionService.getPromotion(promotionID));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<Promotion>> listPromotions(
            @RequestParam(defaultValue = "false") boolean availableOnly
    ) {
        if (availableOnly) {
            return ResponseEntity.ok(promotionService.listAvailablePromotions());
        }
        return ResponseEntity.ok(promotionService.listPromotions());
    }

    @GetMapping("/available")
    public ResponseEntity<List<Promotion>> listAvailablePromotions() {
        return ResponseEntity.ok(promotionService.listAvailablePromotions());
    }

    @GetMapping("/{promotionCode}/orders/{orderID}/validate")
    public ResponseEntity<PromotionValidationResponse> validatePromotion(
            @PathVariable String promotionCode,
            @PathVariable Integer orderID
    ) {
        try {
            return ResponseEntity.ok(promotionService.validatePromotion(promotionCode, orderID));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{promotionCode}/orders/{orderID}/apply")
    public ResponseEntity<PromotionApplicationResponse> applyPromotion(
            @PathVariable String promotionCode,
            @PathVariable Integer orderID
    ) {
        try {
            return ResponseEntity.ok(promotionService.applyPromotion(promotionCode, orderID));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }
}
