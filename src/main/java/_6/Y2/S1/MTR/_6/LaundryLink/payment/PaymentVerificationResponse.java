package _6.Y2.S1.MTR._6.LaundryLink.payment;

import java.time.LocalDate;
import java.time.LocalTime;

public class PaymentVerificationResponse {
    private final Integer paymentID;
    private final Integer orderID;
    private final String previousOrderStatus;
    private final String updatedOrderStatus;
    private final Integer verifiedByUserID;
    private final LocalDate verificationDate;
    private final LocalTime verificationTime;
    private final String message;

    public PaymentVerificationResponse(
            Integer paymentID,
            Integer orderID,
            String previousOrderStatus,
            String updatedOrderStatus,
            Integer verifiedByUserID,
            LocalDate verificationDate,
            LocalTime verificationTime,
            String message
    ) {
        this.paymentID = paymentID;
        this.orderID = orderID;
        this.previousOrderStatus = previousOrderStatus;
        this.updatedOrderStatus = updatedOrderStatus;
        this.verifiedByUserID = verifiedByUserID;
        this.verificationDate = verificationDate;
        this.verificationTime = verificationTime;
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

    public Integer getVerifiedByUserID() {
        return verifiedByUserID;
    }

    public LocalDate getVerificationDate() {
        return verificationDate;
    }

    public LocalTime getVerificationTime() {
        return verificationTime;
    }

    public String getMessage() {
        return message;
    }
}
