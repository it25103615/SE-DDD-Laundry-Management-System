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

    public PaymentHistoryResponse(
            Integer paymentID,
            Integer orderID,
            BigDecimal amount,
            PaymentStatus paymentStatus,
            PaymentMethod paymentMethod,
            String transactionReference,
            LocalDateTime processedAt,
            String orderStatus
    ) {
        this.paymentID = paymentID;
        this.orderID = orderID;
        this.amount = amount;
        this.paymentStatus = paymentStatus;
        this.paymentMethod = paymentMethod;
        this.transactionReference = transactionReference;
        this.processedAt = processedAt;
        this.orderStatus = orderStatus;
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
}
