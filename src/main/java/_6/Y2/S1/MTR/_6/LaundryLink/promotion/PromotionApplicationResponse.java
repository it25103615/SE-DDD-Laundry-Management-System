package _6.Y2.S1.MTR._6.LaundryLink.promotion;

import java.math.BigDecimal;

public class PromotionApplicationResponse {
    private final Integer orderID;
    private final Integer promotionID;
    private final String promotionCode;
    private final BigDecimal discountAmount;
    private final BigDecimal finalPayableAmount;
    private final String message;

    public PromotionApplicationResponse(
            Integer orderID,
            Integer promotionID,
            String promotionCode,
            BigDecimal discountAmount,
            BigDecimal finalPayableAmount,
            String message
    ) {
        this.orderID = orderID;
        this.promotionID = promotionID;
        this.promotionCode = promotionCode;
        this.discountAmount = discountAmount;
        this.finalPayableAmount = finalPayableAmount;
        this.message = message;
    }

    public Integer getOrderID() {
        return orderID;
    }

    public Integer getPromotionID() {
        return promotionID;
    }

    public String getPromotionCode() {
        return promotionCode;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public BigDecimal getFinalPayableAmount() {
        return finalPayableAmount;
    }

    public String getMessage() {
        return message;
    }
}
