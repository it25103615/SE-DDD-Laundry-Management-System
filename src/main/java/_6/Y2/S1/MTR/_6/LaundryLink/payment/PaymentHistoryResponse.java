package _6.Y2.S1.MTR._6.LaundryLink.payment;

import java.math.BigDecimal;

public class PaymentHistoryResponse {
    private final Integer paymentID;
    private final Integer orderID;
    private final BigDecimal amount;
    private final PaymentStatus paymentStatus;
    private final String orderStatus;

    public PaymentHistoryResponse(
            Integer paymentID,
            Integer orderID,
            BigDecimal amount,
            PaymentStatus paymentStatus,
            String orderStatus
    ) {
        this.paymentID = paymentID;
        this.orderID = orderID;
        this.amount = amount;
        this.paymentStatus = paymentStatus;
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

    public String getOrderStatus() {
        return orderStatus;
    }
}
