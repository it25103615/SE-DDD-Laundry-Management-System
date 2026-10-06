package _6.Y2.S1.MTR._6.LaundryLink.promotion;

import java.math.BigDecimal;
import java.time.LocalDate;

public class AvailablePromotionResponse {
    private final String promotionCode;
    private final String promotionName;
    private final DiscountType discountType;
    private final BigDecimal discountValue;
    private final BigDecimal minimumOrderAmount;
    private final LocalDate validTo;

    public AvailablePromotionResponse(Promotion promotion) {
        this.promotionCode = promotion.getPromotionCode();
        this.promotionName = promotion.getPromotionName();
        this.discountType = promotion.getDiscountType();
        this.discountValue = promotion.getDiscountValue();
        this.minimumOrderAmount = promotion.getMinimumOrderAmount();
        this.validTo = promotion.getValidTo();
    }

    public String getPromotionCode() {
        return promotionCode;
    }

    public String getPromotionName() {
        return promotionName;
    }

    public DiscountType getDiscountType() {
        return discountType;
    }

    public BigDecimal getDiscountValue() {
        return discountValue;
    }

    public BigDecimal getMinimumOrderAmount() {
        return minimumOrderAmount;
    }

    public LocalDate getValidTo() {
        return validTo;
    }
}
