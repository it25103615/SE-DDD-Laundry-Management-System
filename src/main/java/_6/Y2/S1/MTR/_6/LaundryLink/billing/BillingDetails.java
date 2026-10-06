package _6.Y2.S1.MTR._6.LaundryLink.billing;

import java.math.BigDecimal;
import java.util.List;

public class BillingDetails {
    private final Integer orderID;
    private final Integer userID;
    private final List<BillingLine> lines;
    private final BigDecimal subtotal;
    private final BigDecimal automaticBulkDiscount;
    private final BigDecimal promotionDiscount;
    private final BigDecimal discountAmount;
    private final BigDecimal finalPayableAmount;

    public BillingDetails(
            Integer orderID,
            Integer userID,
            List<BillingLine> lines,
            BigDecimal subtotal,
            BigDecimal discountAmount,
            BigDecimal finalPayableAmount
    ) {
        this(orderID, userID, lines, subtotal, BigDecimal.ZERO, discountAmount, discountAmount, finalPayableAmount);
    }

    public BillingDetails(
            Integer orderID,
            Integer userID,
            List<BillingLine> lines,
            BigDecimal subtotal,
            BigDecimal automaticBulkDiscount,
            BigDecimal promotionDiscount,
            BigDecimal discountAmount,
            BigDecimal finalPayableAmount
    ) {
        this.orderID = orderID;
        this.userID = userID;
        this.lines = lines;
        this.subtotal = subtotal;
        this.automaticBulkDiscount = automaticBulkDiscount;
        this.promotionDiscount = promotionDiscount;
        this.discountAmount = discountAmount;
        this.finalPayableAmount = finalPayableAmount;
    }

    public Integer getOrderID() {
        return orderID;
    }

    public Integer getUserID() {
        return userID;
    }

    public List<BillingLine> getLines() {
        return lines;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getAutomaticBulkDiscount() {
        return automaticBulkDiscount;
    }

    public BigDecimal getAmountAfterBulkDiscount() {
        return subtotal.subtract(automaticBulkDiscount);
    }

    public BigDecimal getPromotionDiscount() {
        return promotionDiscount;
    }

    public BigDecimal getTotalDiscount() {
        return discountAmount;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public BigDecimal getFinalPayableAmount() {
        return finalPayableAmount;
    }
}
