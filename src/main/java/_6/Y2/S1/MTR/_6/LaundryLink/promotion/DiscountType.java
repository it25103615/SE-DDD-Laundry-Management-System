/* DiscountType
 * STRATEGY COMPOSITION: each enum constant holds a DiscountStrategy. strategy() exposes the chosen calculation. This is a has-a relationship, not discount classes inheriting from the enum.
 */
package _6.Y2.S1.MTR._6.LaundryLink.promotion;

public enum DiscountType {
  PERCENTAGE(new PercentageDiscountStrategy()),
  FIXED_AMOUNT(new FixedAmountDiscountStrategy());

  // The calculation that belongs to this discount type (Strategy pattern).
  // OOP — Has-a relationship / Strategy: this value holds a calculation strategy; it does not
  // inherit from that strategy.
  private final DiscountStrategy strategy;

  DiscountType(DiscountStrategy strategy) {
    this.strategy = strategy;
  }

  /** The strategy PromotionService uses to calculate a discount of this type. */
  public DiscountStrategy strategy() {
    return strategy;
  }
}
