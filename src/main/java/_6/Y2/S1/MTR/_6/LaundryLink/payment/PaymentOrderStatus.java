package _6.Y2.S1.MTR._6.LaundryLink.payment;

public class PaymentOrderStatus {
    private final Integer statusID;
    private final String statusLabel;

    public PaymentOrderStatus(Integer statusID, String statusLabel) {
        this.statusID = statusID;
        this.statusLabel = statusLabel;
    }

    public Integer getStatusID() {
        return statusID;
    }

    public String getStatusLabel() {
        return statusLabel;
    }
}
