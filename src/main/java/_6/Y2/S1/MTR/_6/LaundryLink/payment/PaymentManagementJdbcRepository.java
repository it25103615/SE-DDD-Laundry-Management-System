package _6.Y2.S1.MTR._6.LaundryLink.payment;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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
    public void updateOrderStatus(Integer orderID, Integer statusID) {
        jdbcTemplate.update(
                "UPDATE orders SET statusID = ? WHERE orderID = ?",
                statusID,
                orderID
        );
    }
}
