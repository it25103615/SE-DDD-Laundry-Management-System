package _6.Y2.S1.MTR._6.LaundryLink.billing;

import _6.Y2.S1.MTR._6.LaundryLink.payment.PaymentAccessService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/billing")
public class BillingController {
    private final BillingService billingService;
    private final PaymentAccessService paymentAccessService;

    public BillingController(BillingService billingService, PaymentAccessService paymentAccessService) {
        this.billingService = billingService;
        this.paymentAccessService = paymentAccessService;
    }

    @GetMapping("/orders/{orderID}")
    public ResponseEntity<BillingDetails> getBillingDetails(
            @PathVariable Integer orderID,
            Principal principal
    ) {
        try {
            Integer userID = paymentAccessService.resolveUserID(principal);
            BillingDetails billingDetails = billingService.getBillingDetails(orderID);
            verifyCanViewBilling(userID, billingDetails);
            return ResponseEntity.ok(billingDetails);
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).build();
        }
    }

    @GetMapping("/orders/{orderID}/invoice")
    public ResponseEntity<BillingDetails> getInvoice(
            @PathVariable Integer orderID,
            Principal principal
    ) {
        return getBillingDetails(orderID, principal);
    }

    private void verifyCanViewBilling(Integer userID, BillingDetails billingDetails) {
        try {
            paymentAccessService.requireManagementUser(userID);
            return;
        } catch (AccessDeniedException ignored) {
            // Non-management users must be customers viewing their own order.
        }

        paymentAccessService.requireCustomer(userID);
        paymentAccessService.verifyOrderBelongsToCustomer(billingDetails, userID);
    }
}
