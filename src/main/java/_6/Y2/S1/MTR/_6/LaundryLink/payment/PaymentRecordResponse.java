package _6.Y2.S1.MTR._6.LaundryLink.payment;

import java.math.BigDecimal;

public class PaymentRecordResponse {
    private final Integer paymentID;
    private final Integer orderID;
    private final Integer customerID;
    private final BigDecimal amount;
    private final BigDecimal payableAmount;
    private final BigDecimal paidAmount;
    private final BigDecimal outstandingAmount;
    private final PaymentStatus paymentStatus;
    private final String orderStatus;

    public PaymentRecordResponse(
            Integer paymentID,
            Integer orderID,
            Integer customerID,
            BigDecimal amount,
            BigDecimal payableAmount,
            BigDecimal paidAmount,
            BigDecimal outstandingAmount,
            PaymentStatus paymentStatus,
            String orderStatus
    ) {
        this.paymentID = paymentID;
        this.orderID = orderID;
        this.customerID = customerID;
        this.amount = amount;
        this.payableAmount = payableAmount;
        this.paidAmount = paidAmount;
        this.outstandingAmount = outstandingAmount;
        this.paymentStatus = paymentStatus;
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

    public String getOrderStatus() {
        return orderStatus;
    }
}
