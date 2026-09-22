package _6.Y2.S1.MTR._6.LaundryLink.orders;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Integer> {
    List<Order> findByUserID(Integer userID);

    Optional<Order> findByOrderIDAndUserID(Integer orderID, Integer userID);

    @Query(value = """
            SELECT o.orderID AS orderID,
                   o.userID AS userID,
                   u.firstName AS firstName,
                   u.middleName AS middleName,
                   u.lastName AS lastName,
                   u.email AS email,
                   u.phoneNumber AS phoneNumber,
                   o.statusID AS statusID,
                   s.statusLabel AS statusLabel,
                   COALESCE(SUM(ol.linePrice), 0) AS orderTotal
            FROM orders o
            JOIN users u ON u.userID = o.userID
            JOIN status s ON s.statusID = o.statusID
            LEFT JOIN orderLines ol ON ol.orderID = o.orderID
            WHERE (:search IS NULL
                   OR CAST(o.orderID AS VARCHAR(20)) LIKE CONCAT('%', :search, '%')
                   OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(u.middleName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR u.phoneNumber LIKE CONCAT('%', :search, '%'))
              AND (:statusID IS NULL OR o.statusID = :statusID)
            GROUP BY o.orderID, o.userID, u.firstName, u.middleName, u.lastName,
                     u.email, u.phoneNumber, o.statusID, s.statusLabel
            ORDER BY o.orderID DESC
            """, nativeQuery = true)
    List<ManagerOrderSummaryProjection> searchManagementOrders(
            @Param("search") String search,
            @Param("statusID") Integer statusID);

    @Query(value = "SELECT COUNT(*) FROM users WHERE userID = :userID AND type = 'CUSTOMER'", nativeQuery = true)
    int countCustomersByUserID(@Param("userID") Integer userID);
}
