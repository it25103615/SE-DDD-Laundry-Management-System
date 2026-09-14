package _6.Y2.S1.MTR._6.LaundryLink.payment;

import java.math.BigDecimal;

public class PaymentHistoryResponse {
    private final Integer paymentID;
    private final Integer orderID;
    private final BigDecimal amount;

    public PaymentHistoryResponse(Integer paymentID, Integer orderID, BigDecimal amount) {
        this.paymentID = paymentID;
        this.orderID = orderID;
        this.amount = amount;
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
}
