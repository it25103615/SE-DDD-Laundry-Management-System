package _6.Y2.S1.MTR._6.LaundryLink.billing;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/billing")
public class BillingController {
    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @GetMapping("/orders/{orderID}")
    public ResponseEntity<BillingDetails> getBillingDetails(@PathVariable Integer orderID) {
        try {
            return ResponseEntity.ok(billingService.getBillingDetails(orderID));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/orders/{orderID}/invoice")
    public ResponseEntity<BillingDetails> getInvoice(@PathVariable Integer orderID) {
        return getBillingDetails(orderID);
    }
}
