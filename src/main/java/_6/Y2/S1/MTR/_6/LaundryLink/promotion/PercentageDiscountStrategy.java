package _6.Y2.S1.MTR._6.LaundryLink.promotion;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Concrete strategy: the discount value is a percentage of the subtotal (10 means 10% off). */
public class PercentageDiscountStrategy implements DiscountStrategy {
    @Override
    public BigDecimal calculate(BigDecimal subtotal, BigDecimal discountValue) {
        // Rounded to 2 decimal places, half up, the same as the original calculation.
        return subtotal
                .multiply(discountValue)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
