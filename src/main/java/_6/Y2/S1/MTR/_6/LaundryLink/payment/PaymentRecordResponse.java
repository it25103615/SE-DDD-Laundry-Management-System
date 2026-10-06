package _6.Y2.S1.MTR._6.LaundryLink.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentRecordResponse {
    private final Integer paymentID;
    private final Integer orderID;
    private final Integer customerID;
    private final String customerName;
    private final BigDecimal amount;
    private final BigDecimal payableAmount;
    private final BigDecimal paidAmount;
    private final BigDecimal outstandingAmount;
    private final PaymentStatus paymentStatus;
    private final PaymentMethod paymentMethod;
    private final String transactionReference;
    private final LocalDateTime processedAt;
    private final LocalDateTime recordDate;
    private final String orderStatus;

    public PaymentRecordResponse(
            Integer paymentID,
            Integer orderID,
            Integer customerID,
            String customerName,
            BigDecimal amount,
            BigDecimal payableAmount,
            BigDecimal paidAmount,
            BigDecimal outstandingAmount,
            PaymentStatus paymentStatus,
            PaymentMethod paymentMethod,
            String transactionReference,
            LocalDateTime processedAt,
            LocalDateTime recordDate,
            String orderStatus
    ) {
        this.paymentID = paymentID;
        this.orderID = orderID;
        this.customerID = customerID;
        this.customerName = customerName;
        this.amount = amount;
        this.payableAmount = payableAmount;
        this.paidAmount = paidAmount;
        this.outstandingAmount = outstandingAmount;
        this.paymentStatus = paymentStatus;
        this.paymentMethod = paymentMethod;
        this.transactionReference = transactionReference;
        this.processedAt = processedAt;
        this.recordDate = recordDate;
        this.orderStatus = orderStatus;
    }

    public Integer getPaymentID() {
        return paymentID;
    }

    public Integer getOrderID() {
        return orderID;
    }

    public Integer getCustomerID() {
        return customerID;
    }

    public String getCustomerName() {
        return customerName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getPayableAmount() {
        return payableAmount;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public BigDecimal getOutstandingAmount() {
        return outstandingAmount;
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

    public LocalDateTime getRecordDate() {
        return recordDate;
    }

    public String getOrderStatus() {
        return orderStatus;
    }
}
