package _6.Y2.S1.MTR._6.LaundryLink.promotion;

import _6.Y2.S1.MTR._6.LaundryLink.payment.PaymentAccessService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/promotions")
public class PromotionController {
    private final PromotionService promotionService;
    private final PaymentAccessService paymentAccessService;

    public PromotionController(PromotionService promotionService, PaymentAccessService paymentAccessService) {
        this.promotionService = promotionService;
        this.paymentAccessService = paymentAccessService;
    }

    @PostMapping
    public ResponseEntity<Promotion> createPromotion(
            Principal principal,
            @RequestBody PromotionRequest request
    ) {
        try {
            paymentAccessService.requireManagementUser(paymentAccessService.resolveUserID(principal));
            return ResponseEntity.status(201).body(promotionService.createPromotion(request));
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{promotionID}")
    public ResponseEntity<Promotion> updatePromotion(
            @PathVariable Integer promotionID,
            Principal principal,
            @RequestBody PromotionRequest request
    ) {
        try {
            paymentAccessService.requireManagementUser(paymentAccessService.resolveUserID(principal));
            return ResponseEntity.ok(promotionService.updatePromotion(promotionID, request));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PatchMapping("/{promotionID}/active")
    public ResponseEntity<Promotion> setPromotionActive(
            @PathVariable Integer promotionID,
            Principal principal,
            @RequestParam boolean active
    ) {
        try {
            paymentAccessService.requireManagementUser(paymentAccessService.resolveUserID(principal));
            return ResponseEntity.ok(promotionService.setPromotionActive(promotionID, active));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).build();
        }
    }

    @GetMapping("/{promotionID}")
    public ResponseEntity<Promotion> getPromotion(
            @PathVariable Integer promotionID,
            Principal principal
    ) {
        try {
            paymentAccessService.requireManagementUser(paymentAccessService.resolveUserID(principal));
            return ResponseEntity.ok(promotionService.getPromotion(promotionID));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).build();
        }
    }

    @GetMapping
    public ResponseEntity<List<Promotion>> listPromotions(
            Principal principal,
            @RequestParam(defaultValue = "false") boolean availableOnly
    ) {
        if (availableOnly) {
            return ResponseEntity.ok(promotionService.listAvailablePromotions());
        }
        try {
            paymentAccessService.requireManagementUser(paymentAccessService.resolveUserID(principal));
            return ResponseEntity.ok(promotionService.listPromotions());
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).build();
        }
    }

    @GetMapping("/available")
    public ResponseEntity<List<Promotion>> listAvailablePromotions() {
        return ResponseEntity.ok(promotionService.listAvailablePromotions());
    }

    @GetMapping("/{promotionCode}/orders/{orderID}/validate")
    public ResponseEntity<PromotionValidationResponse> validatePromotion(
            @PathVariable String promotionCode,
            @PathVariable Integer orderID,
            Principal principal
    ) {
        try {
            Integer customerID = paymentAccessService.resolveCustomerID(principal);
            return ResponseEntity.ok(promotionService.validatePromotionForCustomer(promotionCode, orderID, customerID));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{promotionCode}/orders/{orderID}/apply")
    public ResponseEntity<PromotionApplicationResponse> applyPromotion(
            @PathVariable String promotionCode,
            @PathVariable Integer orderID,
            Principal principal
    ) {
        try {
            Integer customerID = paymentAccessService.resolveCustomerID(principal);
            return ResponseEntity.ok(promotionService.applyPromotionForCustomer(promotionCode, orderID, customerID));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }
}
