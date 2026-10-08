package _6.Y2.S1.MTR._6.LaundryLink.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentReceiptResponse {
    private final Integer paymentID;
    private final Integer orderID;
    private final BigDecimal subtotal;
    private final BigDecimal automaticBulkDiscount;
    private final BigDecimal promotionDiscount;
    private final BigDecimal discountAmount;
    private final BigDecimal totalDiscount;
    private final BigDecimal finalPayableAmount;
    private final BigDecimal amountPaid;
    private final PaymentStatus paymentStatus;
    private final PaymentMethod paymentMethod;
    private final String transactionReference;
    private final LocalDateTime processedAt;
    private final String orderStatus;
    private final BigDecimal refundAmount;
    private final RefundStatus refundStatus;
    private final LocalDateTime refundRequestedAt;
    private final LocalDateTime refundProcessedAt;
    private final LocalDateTime refundedAt;

    public PaymentReceiptResponse(
            Integer paymentID,
            Integer orderID,
            BigDecimal subtotal,
            BigDecimal automaticBulkDiscount,
            BigDecimal promotionDiscount,
            BigDecimal discountAmount,
            BigDecimal totalDiscount,
            BigDecimal finalPayableAmount,
            BigDecimal amountPaid,
            PaymentStatus paymentStatus,
            PaymentMethod paymentMethod,
            String transactionReference,
            LocalDateTime processedAt,
            String orderStatus,
            BigDecimal refundAmount,
            RefundStatus refundStatus,
            LocalDateTime refundRequestedAt,
            LocalDateTime refundProcessedAt,
            LocalDateTime refundedAt
    ) {
        this.paymentID = paymentID;
        this.orderID = orderID;
        this.subtotal = subtotal;
        this.automaticBulkDiscount = automaticBulkDiscount;
        this.promotionDiscount = promotionDiscount;
        this.discountAmount = discountAmount;
        this.totalDiscount = totalDiscount;
        this.finalPayableAmount = finalPayableAmount;
        this.amountPaid = amountPaid;
        this.paymentStatus = paymentStatus;
        this.paymentMethod = paymentMethod;
        this.transactionReference = transactionReference;
        this.processedAt = processedAt;
        this.orderStatus = orderStatus;
        this.refundAmount = refundAmount;
        this.refundStatus = refundStatus;
        this.refundRequestedAt = refundRequestedAt;
        this.refundProcessedAt = refundProcessedAt;
        this.refundedAt = refundedAt;
    }

    public Integer getPaymentID() {
        return paymentID;
    }

    public Integer getOrderID() {
        return orderID;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getAutomaticBulkDiscount() {
        return automaticBulkDiscount;
    }

    public BigDecimal getPromotionDiscount() {
        return promotionDiscount;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public BigDecimal getTotalDiscount() {
        return totalDiscount;
    }

    public BigDecimal getFinalPayableAmount() {
        return finalPayableAmount;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    public RefundStatus getRefundStatus() {
        return refundStatus;
    }

    public LocalDateTime getRefundRequestedAt() {
        return refundRequestedAt;
    }

    public LocalDateTime getRefundProcessedAt() {
        return refundProcessedAt;
    }

    public LocalDateTime getRefundedAt() {
        return refundedAt;
    }
}
