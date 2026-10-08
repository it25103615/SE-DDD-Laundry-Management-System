package _6.Y2.S1.MTR._6.LaundryLink.promotion;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Promotion {
    private Integer promotionID;
    private String promotionCode;
    private String promotionName;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal minimumOrderAmount;
    private LocalDate validFrom;
    private LocalDate validTo;
    private boolean active;

    public Promotion() {
    }

    public Promotion(
            Integer promotionID,
            String promotionCode,
            String promotionName,
            DiscountType discountType,
            BigDecimal discountValue,
            BigDecimal minimumOrderAmount,
            LocalDate validFrom,
            LocalDate validTo,
            boolean active
    ) {
        this.promotionID = promotionID;
        this.promotionCode = promotionCode;
        this.promotionName = promotionName;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.minimumOrderAmount = minimumOrderAmount;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.active = active;
    }

    public Integer getPromotionID() {
        return promotionID;
    }

    public void setPromotionID(Integer promotionID) {
        this.promotionID = promotionID;
    }

    public String getPromotionCode() {
        return promotionCode;
    }

    public void setPromotionCode(String promotionCode) {
        this.promotionCode = promotionCode;
    }

    public String getPromotionName() {
        return promotionName;
    }

    public void setPromotionName(String promotionName) {
        this.promotionName = promotionName;
    }

    public DiscountType getDiscountType() {
        return discountType;
    }

    public void setDiscountType(DiscountType discountType) {
        this.discountType = discountType;
    }

    public BigDecimal getDiscountValue() {
        return discountValue;
    }

    public void setDiscountValue(BigDecimal discountValue) {
        this.discountValue = discountValue;
    }

    public BigDecimal getMinimumOrderAmount() {
        return minimumOrderAmount;
    }

    public void setMinimumOrderAmount(BigDecimal minimumOrderAmount) {
        this.minimumOrderAmount = minimumOrderAmount;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDate validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public void setValidTo(LocalDate validTo) {
        this.validTo = validTo;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
