package _6.Y2.S1.MTR._6.LaundryLink.log;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface LogRepository extends JpaRepository<Log, Integer> {
    List<Log> findByOrderID(Integer orderID);

    // Every log of the orders placed by one customer. Order Management owns the orders
    // entity, so its table is read directly here instead of depending on that module.
    @Query(value = """
            SELECT l.* FROM logs l
            JOIN orders o ON o.orderID = l.orderID
            WHERE o.userID = :userID
            ORDER BY l.logID
            """, nativeQuery = true)
    List<Log> findByCustomer(@Param("userID") Integer userID);

    // 1 when the order exists and was placed by this customer, otherwise 0. Used to stop a
    // customer reading the logs of someone else's order.
    @Query(value = "SELECT COUNT(*) FROM orders WHERE orderID = :orderID AND userID = :userID", nativeQuery = true)
    int countOrdersOwnedByCustomer(@Param("orderID") Integer orderID, @Param("userID") Integer userID);
}
