package _6.Y2.S1.MTR._6.LaundryLink.payment;

public class PaymentVerificationResponse {
    private final Integer paymentID;
    private final Integer orderID;
    private final String previousOrderStatus;
    private final String updatedOrderStatus;
    private final String message;

    public PaymentVerificationResponse(
            Integer paymentID,
            Integer orderID,
            String previousOrderStatus,
            String updatedOrderStatus,
            String message
    ) {
        this.paymentID = paymentID;
        this.orderID = orderID;
        this.previousOrderStatus = previousOrderStatus;
        this.updatedOrderStatus = updatedOrderStatus;
        this.message = message;
    }

    public Integer getPaymentID() {
        return paymentID;
    }

    public Integer getOrderID() {
        return orderID;
    }

    public String getPreviousOrderStatus() {
        return previousOrderStatus;
    }

    public String getUpdatedOrderStatus() {
        return updatedOrderStatus;
    }

    public String getMessage() {
        return message;
    }
}
