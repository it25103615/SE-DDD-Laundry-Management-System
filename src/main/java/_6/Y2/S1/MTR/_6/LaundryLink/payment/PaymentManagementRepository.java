package _6.Y2.S1.MTR._6.LaundryLink.payment;

import java.util.Optional;

public interface PaymentManagementRepository {
    Optional<String> findUserType(Integer userID);

    Optional<PaymentOrderStatus> findOrderStatus(Integer orderID);

    void updateOrderStatus(Integer orderID, Integer statusID);

    /**
     * True when the order's status log shows it reached "Payment Verified". An approved order is
     * moved straight on to "Awaiting Pickup", so its current status alone no longer shows that
     * its payment was verified.
     */
    boolean wasPaymentVerified(Integer orderID);
}
