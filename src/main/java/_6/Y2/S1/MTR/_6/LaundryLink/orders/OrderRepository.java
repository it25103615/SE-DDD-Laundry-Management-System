package _6.Y2.S1.MTR._6.LaundryLink.orders;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Integer> {
    List<Order> findByUserID(Integer userID);

    Optional<Order> findByOrderIDAndUserID(Integer orderID, Integer userID);

    // Check and change the status in one statement so a concurrent payment cannot be cancelled.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE orders SET statusID = :cancelledStatusID
            WHERE orderID = :orderID AND userID = :userID AND statusID = :unconfirmedStatusID
            """, nativeQuery = true)
    int cancelUnconfirmedOrder(
            @Param("orderID") Integer orderID,
            @Param("userID") Integer userID,
            @Param("unconfirmedStatusID") Integer unconfirmedStatusID,
            @Param("cancelledStatusID") Integer cancelledStatusID);

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

    // 1 when the address exists and belongs to this customer, otherwise 0. Used to stop an
    // order being placed against someone else's address.
    @Query(value = "SELECT COUNT(*) FROM addresses WHERE addressID = :addressID AND userID = :userID", nativeQuery = true)
    int countAddressesOwnedByCustomer(@Param("addressID") Integer addressID, @Param("userID") Integer userID);

    // Every order needs a matching delivery row so the Rider module can assign pickup and
    // delivery riders to it later. Only the order, customer, requested pickup time and
    // address are known when the order is placed; the rider columns, pickup_actual and
    // delivery_time stay NULL until the rider workflow fills them in.
    //
    // addressID is the saved address the customer chose. When none was chosen (NULL), the
    // customer's default address is stored instead, so the row records where the rider is
    // actually sent; it only stays NULL when the customer has no default address.
    // The CAST tells SQL Server the parameter is a number even when NULL is passed.
    @Modifying
    @Query(value = """
            INSERT INTO delivery(orderID, userID, pickup_scheduled, addressID)
            VALUES (:orderID, :userID, :pickupScheduled,
                    COALESCE(CAST(:addressID AS INT),
                             (SELECT TOP 1 a.addressID FROM addresses a
                               WHERE a.userID = :userID AND a.isDefault = 1
                               ORDER BY a.addressID DESC)))
            """, nativeQuery = true)
    void createDelivery(
            @Param("orderID") Integer orderID,
            @Param("userID") Integer userID,
            @Param("pickupScheduled") LocalDateTime pickupScheduled,
            @Param("addressID") Integer addressID);
}
