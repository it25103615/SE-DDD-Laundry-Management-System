package _6.Y2.S1.MTR._6.LaundryLink.payment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public class PaymentRequest {
    @NotNull
    private PaymentMethod paymentMethod;

    @NotNull
    @Positive
    private BigDecimal amount;

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
