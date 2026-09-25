package _6.Y2.S1.MTR._6.LaundryLink.payment;

import java.util.Optional;

public interface PaymentManagementRepository {
    Optional<String> findUserType(Integer userID);

    Optional<PaymentOrderStatus> findOrderStatus(Integer orderID);

    void updateOrderStatus(Integer orderID, Integer statusID);
}
