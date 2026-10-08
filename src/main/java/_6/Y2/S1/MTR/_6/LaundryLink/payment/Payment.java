package _6.Y2.S1.MTR._6.LaundryLink.payment;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer paymentID;

    @Column(columnDefinition = "decimal(10,2)", nullable = false)
    private Double amount;

    private Integer orderID;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PaymentMethod paymentMethod;

    @Column(length = 50)
    private String transactionReference;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(nullable = false)
    private LocalDateTime processedAt;

    public Payment(Double amount, Integer orderID) {
        this.amount = amount;
        this.orderID = orderID;
        this.paymentStatus = PaymentStatus.PENDING;
        this.processedAt = LocalDateTime.now();
    }

    public Payment(Double amount, Integer orderID, PaymentMethod paymentMethod, String transactionReference) {
        this(amount, orderID);
        this.paymentMethod = paymentMethod;
        this.transactionReference = transactionReference;
    }

    @PrePersist
    public void ensureRecordedPaymentFields() {
        if (paymentStatus == null) {
            paymentStatus = PaymentStatus.PENDING;
        }
        if (processedAt == null) {
            processedAt = LocalDateTime.now();
        }
        if (transactionReference == null || transactionReference.isBlank()) {
            transactionReference = generateTransactionReference(orderID);
        }
    }

    private String generateTransactionReference(Integer orderID) {
        String orderPart = orderID == null ? "ORDER" : orderID.toString();
        String uniquePart = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        return "LLPAY-" + orderPart + "-" + uniquePart;
    }
}
