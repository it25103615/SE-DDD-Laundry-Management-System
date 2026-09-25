package _6.Y2.S1.MTR._6.LaundryLink.orderlines;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderLineRepository extends JpaRepository<OrderLine, Integer> {
    List<OrderLine> findByOrderOrderID(Integer orderID);
}
