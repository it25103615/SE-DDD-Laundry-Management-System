package _6.Y2.S1.MTR._6.LaundryLink.payment;

public enum PaymentStatus {
    UNPAID,
    PENDING,
    PAID,
    VERIFIED,
    REJECTED,
    // Read-only legacy value: existing payment records must remain readable.
    REFUNDED
}
