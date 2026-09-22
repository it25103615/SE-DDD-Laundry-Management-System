package _6.Y2.S1.MTR._6.LaundryLink.billing;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class BillingService {
    private final BillingRepository billingRepository;

    public BillingService(BillingRepository billingRepository) {
        this.billingRepository = billingRepository;
    }

    public BillingDetails getBillingDetails(Integer orderID) {
        Integer userID = billingRepository.findOrderUserID(orderID).orElseThrow();
        List<BillingLine> lines = billingRepository.findOrderLines(orderID);

        if (lines.isEmpty()) {
            throw new NoSuchElementException("No order lines found for order " + orderID);
        }

        BigDecimal subtotal = lines.stream()
                .map(BillingLine::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal discountAmount = billingRepository.findAppliedDiscountAmount(orderID);
        if (discountAmount.compareTo(BigDecimal.ZERO) < 0) {
            discountAmount = BigDecimal.ZERO;
        }

        if (discountAmount.compareTo(subtotal) > 0) {
            discountAmount = subtotal;
        }

        BigDecimal finalPayableAmount = subtotal.subtract(discountAmount);

        return new BillingDetails(orderID, userID, lines, subtotal, discountAmount, finalPayableAmount);
    }
}
