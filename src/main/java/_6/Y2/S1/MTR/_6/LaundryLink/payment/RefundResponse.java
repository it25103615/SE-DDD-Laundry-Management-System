package _6.Y2.S1.MTR._6.LaundryLink.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class RefundResponse {
    private final Integer refundID;
    private final Integer paymentID;
    private final Integer orderID;
    private final BigDecimal refundAmount;
    private final RefundStatus refundStatus;
    private final LocalDateTime requestedAt;
    private final LocalDateTime processedAt;
    private final LocalDateTime refundedAt;
    private final Integer requestedBy;
    private final Integer processedBy;
    private final String message;

    public RefundResponse(
            Integer refundID,
            Integer paymentID,
            Integer orderID,
            BigDecimal refundAmount,
            RefundStatus refundStatus,
            LocalDateTime requestedAt,
            LocalDateTime processedAt,
            LocalDateTime refundedAt,
            Integer requestedBy,
            Integer processedBy,
            String message
    ) {
        this.refundID = refundID;
        this.paymentID = paymentID;
        this.orderID = orderID;
        this.refundAmount = refundAmount;
        this.refundStatus = refundStatus;
        this.requestedAt = requestedAt;
        this.processedAt = processedAt;
        this.refundedAt = refundedAt;
        this.requestedBy = requestedBy;
        this.processedBy = processedBy;
        this.message = message;
    }

    public Integer getRefundID() {
        return refundID;
    }

    public Integer getPaymentID() {
        return paymentID;
    }

    public Integer getOrderID() {
        return orderID;
    }

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    public RefundStatus getRefundStatus() {
        return refundStatus;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public LocalDateTime getRefundedAt() {
        return refundedAt;
    }

    public Integer getRequestedBy() {
        return requestedBy;
    }

    public Integer getProcessedBy() {
        return processedBy;
    }

    public String getMessage() {
        return message;
    }
}
