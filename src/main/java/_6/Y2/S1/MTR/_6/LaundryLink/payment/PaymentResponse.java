package _6.Y2.S1.MTR._6.LaundryLink.payment;

import java.math.BigDecimal;

public class PaymentResponse {
    private final Integer paymentID;
    private final BigDecimal amount;
    private final Integer orderID;

    public PaymentResponse(Integer paymentID, BigDecimal amount, Integer orderID) {
        this.paymentID = paymentID;
        this.amount = amount;
        this.orderID = orderID;
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
}
