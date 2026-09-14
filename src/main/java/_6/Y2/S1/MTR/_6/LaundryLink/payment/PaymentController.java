package _6.Y2.S1.MTR._6.LaundryLink.payment;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentService paymentService;
    private final PaymentAccessService paymentAccessService;

    public PaymentController(PaymentService paymentService, PaymentAccessService paymentAccessService) {
        this.paymentService = paymentService;
        this.paymentAccessService = paymentAccessService;
    }

    @GetMapping("/orders/{orderID}/amount")
    public ResponseEntity<PaymentAmountResponse> getAmountDue(
            @PathVariable Integer orderID,
            @RequestHeader(value = "X-User-ID", required = false) Integer headerUserID,
            Principal principal
    ) {
        try {
            Integer customerID = paymentAccessService.resolveCustomerID(principal, headerUserID);
            return ResponseEntity.ok(paymentService.getAmountDue(orderID, customerID));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).build();
        }
    }

    @PostMapping("/orders/{orderID}")
    public ResponseEntity<PaymentConfirmationResponse> submitPayment(
            @PathVariable Integer orderID,
            @RequestHeader(value = "X-User-ID", required = false) Integer headerUserID,
            Principal principal,
            @Valid @RequestBody PaymentRequest request
    ) {
        try {
            Integer customerID = paymentAccessService.resolveCustomerID(principal, headerUserID);
            return ResponseEntity.ok(paymentService.submitPayment(orderID, customerID, request));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(409).build();
        }
    }

    @GetMapping("/orders/{orderID}/status")
    public ResponseEntity<PaymentStatusResponse> getPaymentStatus(
            @PathVariable Integer orderID,
            @RequestHeader(value = "X-User-ID", required = false) Integer headerUserID,
            Principal principal
    ) {
        try {
            Integer customerID = paymentAccessService.resolveCustomerID(principal, headerUserID);
            return ResponseEntity.ok(paymentService.getPaymentStatus(orderID, customerID));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).build();
        }
    }

    @GetMapping("/staff/orders/{orderID}/status")
    public ResponseEntity<PaymentStatusResponse> getPaymentStatusForStaff(
            @PathVariable Integer orderID,
            @RequestHeader(value = "X-User-ID", required = false) Integer headerUserID,
            Principal principal
    ) {
        try {
            Integer userID = paymentAccessService.resolveUserID(principal, headerUserID);
            return ResponseEntity.ok(paymentService.getPaymentStatusForStaff(orderID, userID));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).build();
        }
    }

    @GetMapping("/history")
    public ResponseEntity<List<PaymentHistoryResponse>> getPaymentHistory(
            @RequestHeader(value = "X-User-ID", required = false) Integer headerUserID,
            Principal principal
    ) {
        try {
            Integer customerID = paymentAccessService.resolveCustomerID(principal, headerUserID);
            return ResponseEntity.ok(paymentService.getPaymentHistory(customerID));
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).build();
        }
    }

    @GetMapping("/management")
    public ResponseEntity<List<PaymentRecordResponse>> getPaymentRecords(
            @RequestHeader(value = "X-User-ID", required = false) Integer headerUserID,
            @RequestParam(required = false) Integer orderID,
            @RequestParam(required = false) Integer customerID,
            @RequestParam(required = false) PaymentStatus status,
            Principal principal
    ) {
        try {
            Integer userID = paymentAccessService.resolveUserID(principal, headerUserID);
            return ResponseEntity.ok(paymentService.getPaymentRecords(userID, orderID, customerID, status));
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).build();
        }
    }

    @PostMapping("/management/{paymentID}/verify")
    public ResponseEntity<PaymentVerificationResponse> verifyPayment(
            @PathVariable Integer paymentID,
            @RequestHeader(value = "X-User-ID", required = false) Integer headerUserID,
            Principal principal,
            @Valid @RequestBody PaymentVerificationRequest request
    ) {
        try {
            Integer userID = paymentAccessService.resolveUserID(principal, headerUserID);
            return ResponseEntity.ok(paymentService.verifyPayment(userID, paymentID, request));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).build();
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(409).build();
        }
    }
}
