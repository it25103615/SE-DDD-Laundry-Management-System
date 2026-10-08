package _6.Y2.S1.MTR._6.LaundryLink.payment;

import java.math.BigDecimal;

public class PaymentAmountResponse {
    private final Integer orderID;
    private final BigDecimal payableAmount;
    private final BigDecimal paidAmount;
    private final BigDecimal outstandingAmount;

    public PaymentAmountResponse(Integer orderID, BigDecimal payableAmount, BigDecimal paidAmount, BigDecimal outstandingAmount) {
        this.orderID = orderID;
        this.payableAmount = payableAmount;
        this.paidAmount = paidAmount;
        this.outstandingAmount = outstandingAmount;
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
}
