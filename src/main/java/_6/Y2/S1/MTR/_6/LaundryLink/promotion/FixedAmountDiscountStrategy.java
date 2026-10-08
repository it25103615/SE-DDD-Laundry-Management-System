package _6.Y2.S1.MTR._6.LaundryLink.promotion;

import java.math.BigDecimal;

/** Concrete strategy: the discount value is the amount taken off, whatever the subtotal is. */
public class FixedAmountDiscountStrategy implements DiscountStrategy {
    @Override
    public BigDecimal calculate(BigDecimal subtotal, BigDecimal discountValue) {
        // PromotionService caps the result, so a fixed amount larger than the subtotal is safe here.
        return discountValue;
    }
}
