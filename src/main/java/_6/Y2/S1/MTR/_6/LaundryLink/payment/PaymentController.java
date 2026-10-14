package _6.Y2.S1.MTR._6.LaundryLink.payment;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
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

    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            Principal principal,
            @Valid @RequestBody PaymentCrudRequest request
    ) {
        try {
            Integer userID = paymentAccessService.resolveUserID(principal);
            return ResponseEntity.status(201).body(paymentService.createPayment(userID, request));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{paymentID}")
    public ResponseEntity<PaymentResponse> getPayment(
            @PathVariable Integer paymentID,
            Principal principal
    ) {
        try {
            Integer userID = paymentAccessService.resolveUserID(principal);
            return ResponseEntity.ok(paymentService.getPayment(userID, paymentID));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).build();
        }
    }

    @GetMapping("/{paymentID}/receipt")
    public ResponseEntity<PaymentReceiptResponse> getReceipt(
            @PathVariable Integer paymentID,
            @RequestParam(required = false) Integer orderID,
            Principal principal
    ) {
        try {
            Integer customerID = paymentAccessService.resolveCustomerID(principal);
            return ResponseEntity.ok(paymentService.getCustomerReceipt(customerID, paymentID, orderID));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping(value = "/{paymentID}/receipt.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadReceiptPdf(
            @PathVariable Integer paymentID,
            @RequestParam(required = false) Integer orderID,
            Principal principal
    ) {
        try {
            Integer customerID = paymentAccessService.resolveCustomerID(principal);
            byte[] pdf = paymentService.getCustomerReceiptPdf(customerID, paymentID, orderID);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"laundrylink-receipt-" + paymentID + ".pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{paymentID}/refund-request")
    public ResponseEntity<?> requestRefund(
            @PathVariable Integer paymentID,
            Principal principal,
            @Valid @RequestBody RefundRequest request
    ) {
        try {
            Integer customerID = paymentAccessService.resolveCustomerID(principal);
            return ResponseEntity.ok(paymentService.requestRefund(customerID, paymentID, request));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(409).body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<List<PaymentResponse>> getPayments(
            Principal principal
    ) {
        try {
            Integer userID = paymentAccessService.resolveUserID(principal);
            return ResponseEntity.ok(paymentService.getPayments(userID));
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).build();
        }
    }

    @GetMapping("/orders/{orderID}")
    public ResponseEntity<List<PaymentResponse>> getPaymentsByOrder(
            @PathVariable Integer orderID,
            Principal principal
    ) {
        try {
            Integer userID = paymentAccessService.resolveUserID(principal);
            return ResponseEntity.ok(paymentService.getPaymentsByOrder(userID, orderID));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).build();
        }
    }

    @PutMapping("/{paymentID}")
    public ResponseEntity<PaymentResponse> updatePayment(
            @PathVariable Integer paymentID,
            Principal principal,
            @Valid @RequestBody PaymentCrudRequest request
    ) {
        try {
            Integer userID = paymentAccessService.resolveUserID(principal);
            return ResponseEntity.ok(paymentService.updatePayment(userID, paymentID, request));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{paymentID}")
    public ResponseEntity<Void> deletePayment(
            @PathVariable Integer paymentID,
            Principal principal
    ) {
        try {
            Integer userID = paymentAccessService.resolveUserID(principal);
            paymentService.deletePayment(userID, paymentID);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).build();
        }
    }

    @GetMapping("/orders/{orderID}/amount")
    public ResponseEntity<PaymentAmountResponse> getAmountDue(
            @PathVariable Integer orderID,
            Principal principal
    ) {
        try {
            Integer customerID = paymentAccessService.resolveCustomerID(principal);
            return ResponseEntity.ok(paymentService.getAmountDue(orderID, customerID));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).build();
        }
    }

    @PostMapping("/orders/{orderID}")
    public ResponseEntity<?> submitPayment(
            @PathVariable Integer orderID,
            Principal principal,
            @Valid @RequestBody PaymentRequest request
    ) {
        try {
            Integer customerID = paymentAccessService.resolveCustomerID(principal);
            return ResponseEntity.ok(paymentService.submitPayment(orderID, customerID, request));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(409).body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/orders/{orderID}/status")
    public ResponseEntity<PaymentStatusResponse> getPaymentStatus(
            @PathVariable Integer orderID,
            Principal principal
    ) {
        try {
            Integer customerID = paymentAccessService.resolveCustomerID(principal);
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
            Principal principal
    ) {
        try {
            Integer userID = paymentAccessService.resolveUserID(principal);
            return ResponseEntity.ok(paymentService.getPaymentStatusForStaff(orderID, userID));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).build();
        }
    }

    @GetMapping("/history")
    public ResponseEntity<List<PaymentHistoryResponse>> getPaymentHistory(
            Principal principal
    ) {
        try {
            Integer customerID = paymentAccessService.resolveCustomerID(principal);
            return ResponseEntity.ok(paymentService.getPaymentHistory(customerID));
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).build();
        }
    }

    @GetMapping("/management")
    public ResponseEntity<List<PaymentRecordResponse>> getPaymentRecords(
            @RequestParam(required = false) Integer orderID,
            @RequestParam(required = false) Integer customerID,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) PaymentMethod method,
            @RequestParam(required = false) String search,
            Principal principal
    ) {
        try {
            Integer userID = paymentAccessService.resolveUserID(principal);
            return ResponseEntity.ok(paymentService.getPaymentRecords(userID, orderID, customerID, status, method, search));
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).build();
        }
    }

    @GetMapping("/management/{paymentID}")
    public ResponseEntity<PaymentRecordResponse> getPaymentRecord(
            @PathVariable Integer paymentID,
            Principal principal
    ) {
        try {
            Integer userID = paymentAccessService.resolveUserID(principal);
            return ResponseEntity.ok(paymentService.getPaymentRecord(userID, paymentID));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).build();
        }
    }

    @PostMapping("/management/{paymentID}/verify")
    public ResponseEntity<?> verifyPayment(
            @PathVariable Integer paymentID,
            Principal principal,
            @Valid @RequestBody PaymentVerificationRequest request
    ) {
        try {
            Integer userID = paymentAccessService.resolveUserID(principal);
            return ResponseEntity.ok(paymentService.verifyPayment(userID, paymentID, request));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).build();
        } catch (IllegalStateException ex) {
            // The message says why, e.g. that the order was cancelled.
            return ResponseEntity.status(409).body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/management/{paymentID}/approve")
    public ResponseEntity<?> approvePayment(
            @PathVariable Integer paymentID,
            Principal principal
    ) {
        try {
            Integer userID = paymentAccessService.resolveUserID(principal);
            return ResponseEntity.ok(paymentService.approvePayment(userID, paymentID));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).build();
        } catch (IllegalStateException ex) {
            // The message says why, e.g. that the order was cancelled.
            return ResponseEntity.status(409).body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/management/{paymentID}/reject")
    public ResponseEntity<?> rejectPayment(
            @PathVariable Integer paymentID,
            Principal principal
    ) {
        try {
            Integer userID = paymentAccessService.resolveUserID(principal);
            return ResponseEntity.ok(paymentService.rejectPayment(userID, paymentID));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).build();
        } catch (IllegalStateException ex) {
            // The message says why, e.g. that the order was cancelled.
            return ResponseEntity.status(409).body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/management/{paymentID}/refund/approve")
    public ResponseEntity<?> approveRefund(
            @PathVariable Integer paymentID,
            Principal principal
    ) {
        try {
            Integer userID = paymentAccessService.resolveUserID(principal);
            return ResponseEntity.ok(paymentService.approveRefund(userID, paymentID));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).build();
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(409).body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/management/{paymentID}/refund/reject")
    public ResponseEntity<?> rejectRefund(
            @PathVariable Integer paymentID,
            Principal principal
    ) {
        try {
            Integer userID = paymentAccessService.resolveUserID(principal);
            return ResponseEntity.ok(paymentService.rejectRefund(userID, paymentID));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).build();
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(409).body(Map.of("message", ex.getMessage()));
        }
    }
}
