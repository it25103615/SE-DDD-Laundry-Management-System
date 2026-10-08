package _6.Y2.S1.MTR._6.LaundryLink.promotion;

public enum DiscountType {
    PERCENTAGE(new PercentageDiscountStrategy()),
    FIXED_AMOUNT(new FixedAmountDiscountStrategy());

    // The calculation that belongs to this discount type (Strategy pattern).
    private final DiscountStrategy strategy;

    DiscountType(DiscountStrategy strategy) {
        this.strategy = strategy;
    }

    /** The strategy PromotionService uses to calculate a discount of this type. */
    public DiscountStrategy strategy() {
        return strategy;
    }
}
