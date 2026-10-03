package _6.Y2.S1.MTR._6.LaundryLink.billing;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class BillingService {
    private static final BigDecimal BULK_DISCOUNT_THRESHOLD = BigDecimal.valueOf(5000);
    private static final BigDecimal BULK_DISCOUNT_RATE = BigDecimal.valueOf(0.10);

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

        BigDecimal automaticBulkDiscount = calculateAutomaticBulkDiscount(subtotal);
        BigDecimal amountAfterBulkDiscount = subtotal.subtract(automaticBulkDiscount);

        BigDecimal promotionDiscount = billingRepository.findAppliedDiscountAmount(orderID);
        if (promotionDiscount.compareTo(BigDecimal.ZERO) < 0) {
            promotionDiscount = BigDecimal.ZERO;
        }

        if (promotionDiscount.compareTo(amountAfterBulkDiscount) > 0) {
            promotionDiscount = amountAfterBulkDiscount;
        }

        BigDecimal discountAmount = automaticBulkDiscount.add(promotionDiscount);
        BigDecimal finalPayableAmount = amountAfterBulkDiscount.subtract(promotionDiscount);
        if (finalPayableAmount.compareTo(BigDecimal.ZERO) < 0) {
            finalPayableAmount = BigDecimal.ZERO;
        }

        return new BillingDetails(
                orderID,
                userID,
                lines,
                subtotal,
                automaticBulkDiscount,
                promotionDiscount,
                discountAmount,
                finalPayableAmount
        );
    }

    BigDecimal calculateAutomaticBulkDiscount(BigDecimal subtotal) {
        if (subtotal.compareTo(BULK_DISCOUNT_THRESHOLD) < 0) {
            return BigDecimal.ZERO;
        }
        return subtotal.multiply(BULK_DISCOUNT_RATE).setScale(2, RoundingMode.HALF_UP);
    }
}
