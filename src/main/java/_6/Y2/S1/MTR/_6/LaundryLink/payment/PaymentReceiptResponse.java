package _6.Y2.S1.MTR._6.LaundryLink.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentReceiptResponse {
    private final Integer paymentID;
    private final Integer orderID;
    private final BigDecimal subtotal;
    private final BigDecimal discountAmount;
    private final BigDecimal finalPayableAmount;
    private final BigDecimal amountPaid;
    private final PaymentStatus paymentStatus;
    private final PaymentMethod paymentMethod;
    private final String transactionReference;
    private final LocalDateTime processedAt;
    private final String orderStatus;

    public PaymentReceiptResponse(
            Integer paymentID,
            Integer orderID,
            BigDecimal subtotal,
            BigDecimal discountAmount,
            BigDecimal finalPayableAmount,
            BigDecimal amountPaid,
            PaymentStatus paymentStatus,
            PaymentMethod paymentMethod,
            String transactionReference,
            LocalDateTime processedAt,
            String orderStatus
    ) {
        this.paymentID = paymentID;
        this.orderID = orderID;
        this.subtotal = subtotal;
        this.discountAmount = discountAmount;
        this.finalPayableAmount = finalPayableAmount;
        this.amountPaid = amountPaid;
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

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
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
}
