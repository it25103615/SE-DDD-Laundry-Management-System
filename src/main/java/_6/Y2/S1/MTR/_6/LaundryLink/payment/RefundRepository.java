package _6.Y2.S1.MTR._6.LaundryLink.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Integer> {
    Optional<Refund> findByPaymentID(Integer paymentID);

    boolean existsByPaymentID(Integer paymentID);
}
