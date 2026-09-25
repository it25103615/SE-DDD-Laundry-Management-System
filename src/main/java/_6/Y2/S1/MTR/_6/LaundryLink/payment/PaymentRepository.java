package _6.Y2.S1.MTR._6.LaundryLink.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Integer> {
    List<Payment> findByOrderID(Integer orderID);

    boolean existsByOrderID(Integer orderID);
}
