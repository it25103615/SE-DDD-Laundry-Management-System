package _6.Y2.S1.MTR._6.LaundryLink.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentConfirmationResponse {
    private final Integer paymentID;
    private final Integer orderID;
    private final BigDecimal amount;
    private final PaymentMethod paymentMethod;
    private final String transactionReference;
    private final PaymentStatus status;
    private final LocalDateTime processedAt;
    private final String message;

    public PaymentConfirmationResponse(
            Integer paymentID,
            Integer orderID,
            BigDecimal amount,
            PaymentMethod paymentMethod,
            String transactionReference,
            PaymentStatus status,
            LocalDateTime processedAt,
            String message
    ) {
        this.paymentID = paymentID;
        this.orderID = orderID;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.transactionReference = transactionReference;
        this.status = status;
        this.processedAt = processedAt;
        this.message = message;
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

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public String getMessage() {
        return message;
    }
}
