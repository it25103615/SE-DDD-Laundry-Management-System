package _6.Y2.S1.MTR._6.LaundryLink.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentResponse {
    private final Integer paymentID;
    private final BigDecimal amount;
    private final Integer orderID;
    private final PaymentMethod paymentMethod;
    private final String transactionReference;
    private final PaymentStatus paymentStatus;
    private final LocalDateTime processedAt;

    public PaymentResponse(
            Integer paymentID,
            BigDecimal amount,
            Integer orderID,
            PaymentMethod paymentMethod,
            String transactionReference,
            PaymentStatus paymentStatus,
            LocalDateTime processedAt
    ) {
        this.paymentID = paymentID;
        this.amount = amount;
        this.orderID = orderID;
        this.paymentMethod = paymentMethod;
        this.transactionReference = transactionReference;
        this.paymentStatus = paymentStatus;
        this.processedAt = processedAt;
    }

    public Integer getPaymentID() {
        return paymentID;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Integer getOrderID() {
        return orderID;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }
}
