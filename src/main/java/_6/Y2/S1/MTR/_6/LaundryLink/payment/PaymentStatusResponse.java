package _6.Y2.S1.MTR._6.LaundryLink.payment;

import java.math.BigDecimal;

public class PaymentStatusResponse {
    private final Integer orderID;
    private final BigDecimal payableAmount;
    private final BigDecimal paidAmount;
    private final BigDecimal outstandingAmount;
    private final PaymentStatus status;

    public PaymentStatusResponse(
            Integer orderID,
            BigDecimal payableAmount,
            BigDecimal paidAmount,
            BigDecimal outstandingAmount,
            PaymentStatus status
    ) {
        this.orderID = orderID;
        this.payableAmount = payableAmount;
        this.paidAmount = paidAmount;
        this.outstandingAmount = outstandingAmount;
        this.status = status;
    }

    public Integer getOrderID() {
        return orderID;
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

    public PaymentStatus getStatus() {
        return status;
    }
}
