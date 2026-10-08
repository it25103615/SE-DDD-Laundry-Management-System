package _6.Y2.S1.MTR._6.LaundryLink.payment;

import jakarta.validation.constraints.Size;

public class RefundRequest {
    @Size(max = 255)
    private String refundReason;

    @Size(max = 255)
    private String reason;

    public String getRefundReason() {
        return refundReason;
    }

    public void setRefundReason(String refundReason) {
        this.refundReason = refundReason;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String resolvedReason() {
        return refundReason != null ? refundReason : reason;
    }
}
