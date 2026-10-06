package _6.Y2.S1.MTR._6.LaundryLink.payment;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class PaymentManagementJdbcRepository implements PaymentManagementRepository {
    private final JdbcTemplate jdbcTemplate;

    public PaymentManagementJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<String> findUserType(Integer userID) {
        try {
            String type = jdbcTemplate.queryForObject(
                    "SELECT type FROM users WHERE userID = ?",
                    String.class,
                    userID
            );
            return Optional.ofNullable(type);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<PaymentOrderStatus> findOrderStatus(Integer orderID) {
        try {
            PaymentOrderStatus status = jdbcTemplate.queryForObject(
                    """
                            SELECT s.statusID, s.statusLabel
                            FROM orders o
                            JOIN status s ON s.statusID = o.statusID
                            WHERE o.orderID = ?
                            """,
                    (rs, rowNum) -> new PaymentOrderStatus(
                            rs.getInt("statusID"),
                            rs.getString("statusLabel")
                    ),
                    orderID
            );
            return Optional.ofNullable(status);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<PaymentManagementOrderSummary> findOrderSummary(Integer orderID) {
        try {
            PaymentManagementOrderSummary summary = jdbcTemplate.queryForObject(
                    """
                            SELECT o.orderID,
                                   o.userID AS customerID,
                                   CONCAT_WS(' ', u.firstName, u.middleName, u.lastName) AS customerName,
                                   s.statusLabel AS orderStatus,
                                   MAX(d.pickup_scheduled) AS orderDate
                            FROM orders o
                            JOIN users u ON u.userID = o.userID
                            JOIN status s ON s.statusID = o.statusID
                            LEFT JOIN delivery d ON d.orderID = o.orderID
                            WHERE o.orderID = ?
                              AND EXISTS (SELECT 1 FROM orderLines ol WHERE ol.orderID = o.orderID)
                            GROUP BY o.orderID, o.userID, u.firstName, u.middleName, u.lastName, s.statusLabel
                            """,
                    (rs, rowNum) -> new PaymentManagementOrderSummary(
                            rs.getInt("orderID"),
                            rs.getInt("customerID"),
                            rs.getString("customerName"),
                            rs.getString("orderStatus"),
                            toLocalDateTime(rs.getTimestamp("orderDate"))
                    ),
                    orderID
            );
            return Optional.ofNullable(summary);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public List<PaymentManagementOrderSummary> findBillableOrderSummaries() {
        return jdbcTemplate.query(
                """
                        SELECT o.orderID,
                               o.userID AS customerID,
                               CONCAT_WS(' ', u.firstName, u.middleName, u.lastName) AS customerName,
                               s.statusLabel AS orderStatus,
                               MAX(d.pickup_scheduled) AS orderDate
                        FROM orders o
                        JOIN users u ON u.userID = o.userID
                        JOIN status s ON s.statusID = o.statusID
                        LEFT JOIN delivery d ON d.orderID = o.orderID
                        WHERE EXISTS (SELECT 1 FROM orderLines ol WHERE ol.orderID = o.orderID)
                        GROUP BY o.orderID, o.userID, u.firstName, u.middleName, u.lastName, s.statusLabel
                        ORDER BY COALESCE(MAX(d.pickup_scheduled), CONVERT(DATETIME2, '1900-01-01')) DESC,
                                 o.orderID DESC
                        """,
                (rs, rowNum) -> new PaymentManagementOrderSummary(
                        rs.getInt("orderID"),
                        rs.getInt("customerID"),
                        rs.getString("customerName"),
                        rs.getString("orderStatus"),
                        toLocalDateTime(rs.getTimestamp("orderDate"))
                )
        );
    }

    @Override
    public void updateOrderStatus(Integer orderID, Integer statusID) {
        jdbcTemplate.update(
                "UPDATE orders SET statusID = ? WHERE orderID = ?",
                statusID,
                orderID
        );
    }

    @Override
    public boolean wasPaymentVerified(Integer orderID) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM logs l
                        JOIN status s ON s.statusID = l.status_after
                        WHERE l.orderID = ? AND s.statusLabel = 'Payment Verified'
                        """,
                Integer.class,
                orderID
        );
        return count != null && count > 0;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
