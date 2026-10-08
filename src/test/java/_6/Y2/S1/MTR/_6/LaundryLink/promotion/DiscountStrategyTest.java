package _6.Y2.S1.MTR._6.LaundryLink.promotion;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class DiscountStrategyTest {
    @Test
    void percentageStrategyTakesShareOfSubtotal() {
        BigDecimal discount = new PercentageDiscountStrategy()
                .calculate(new BigDecimal("1000.00"), new BigDecimal("10"));

        assertEquals(new BigDecimal("100.00"), discount);
    }

    @Test
    void percentageStrategyRoundsHalfUpToTwoDecimals() {
        BigDecimal discount = new PercentageDiscountStrategy()
                .calculate(new BigDecimal("333.33"), new BigDecimal("15"));

        assertEquals(new BigDecimal("50.00"), discount);
    }

    @Test
    void fixedAmountStrategyReturnsTheDiscountValue() {
        BigDecimal discount = new FixedAmountDiscountStrategy()
                .calculate(new BigDecimal("1000.00"), new BigDecimal("200"));

        assertEquals(new BigDecimal("200"), discount);
    }

    @Test
    void eachDiscountTypeSuppliesItsOwnStrategy() {
        assertInstanceOf(PercentageDiscountStrategy.class, DiscountType.PERCENTAGE.strategy());
        assertInstanceOf(FixedAmountDiscountStrategy.class, DiscountType.FIXED_AMOUNT.strategy());
    }
}
