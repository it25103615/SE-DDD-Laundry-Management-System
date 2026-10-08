package _6.Y2.S1.MTR._6.LaundryLink.payment;

import jakarta.validation.constraints.NotNull;

public class PaymentVerificationRequest {
    @NotNull
    private Boolean approved;

    public Boolean getApproved() {
        return approved;
    }

    public void setApproved(Boolean approved) {
        this.approved = approved;
    }
}
