package _6.Y2.S1.MTR._6.LaundryLink.promotion;

import java.math.BigDecimal;

/**
 * Strategy interface (Strategy pattern): one way of turning a promotion's discount value into a
 * discount amount. Each discount type has its own implementation, so PromotionService does not
 * need an if/else to choose between them.
 */
public interface DiscountStrategy {
    /**
     * @param subtotal      the amount the promotion applies to
     * @param discountValue the value stored on the promotion (a percentage or a fixed amount)
     * @return the discount before it is capped at the subtotal
     */
    BigDecimal calculate(BigDecimal subtotal, BigDecimal discountValue);
}
