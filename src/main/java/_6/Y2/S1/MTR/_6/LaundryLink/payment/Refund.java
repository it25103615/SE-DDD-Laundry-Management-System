package _6.Y2.S1.MTR._6.LaundryLink.payment;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "refunds")
@Getter
@Setter
@NoArgsConstructor
public class Refund {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer refundID;

    @Column(nullable = false, unique = true)
    private Integer paymentID;

    @Column(columnDefinition = "decimal(10,2)", nullable = false)
    private BigDecimal refundAmount;

    @Column(length = 255, nullable = false)
    private String refundReason;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private RefundStatus refundStatus = RefundStatus.REQUESTED;

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    private LocalDateTime processedAt;

    private LocalDateTime refundedAt;

    private Integer requestedBy;

    private Integer processedBy;

    public Refund(Integer paymentID, BigDecimal refundAmount, String refundReason, Integer requestedBy) {
        this.paymentID = paymentID;
        this.refundAmount = refundAmount;
        this.refundReason = refundReason;
        this.requestedBy = requestedBy;
        this.refundStatus = RefundStatus.REQUESTED;
        this.requestedAt = LocalDateTime.now();
    }

    @PrePersist
    public void ensureRecordedRefundFields() {
        if (refundStatus == null) {
            refundStatus = RefundStatus.REQUESTED;
        }
        if (requestedAt == null) {
            requestedAt = LocalDateTime.now();
        }
    }
}
