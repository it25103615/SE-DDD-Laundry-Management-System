package _6.Y2.S1.MTR._6.LaundryLink.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentHistoryResponse {
    private final Integer paymentID;
    private final Integer orderID;
    private final BigDecimal amount;
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

    public PaymentHistoryResponse(
            Integer paymentID,
            Integer orderID,
            BigDecimal amount,
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
        this.amount = amount;
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

    public BigDecimal getAmount() {
        return amount;
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
