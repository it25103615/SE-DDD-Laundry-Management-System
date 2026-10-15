/* BillingJdbcRepository
 * REPOSITORY: persistence boundary. JPA repository interfaces are implemented by Spring; JDBC implementations execute explicit SQL.
 * ABSTRACTION / POLYMORPHISM: implements the declared contract. @Override identifies the contract methods supplied by this type.
 */
package _6.Y2.S1.MTR._6.LaundryLink.billing;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BillingJdbcRepository implements BillingRepository {
  private final JdbcTemplate jdbcTemplate;

  public BillingJdbcRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public Optional<Integer> findOrderUserID(Integer orderID) {
    try {
      Integer userID =
          jdbcTemplate.queryForObject(
              "SELECT userID FROM orders WHERE orderID = ?", Integer.class, orderID);
      return Optional.ofNullable(userID);
    } catch (EmptyResultDataAccessException ex) {
      return Optional.empty();
    }
  }

  @Override
  public List<BillingLine> findOrderLines(Integer orderID) {
    return jdbcTemplate.query(
        """
        SELECT orderLineID, itemID, serviceID, quantity, linePrice
        FROM orderLines
        WHERE orderID = ?
        ORDER BY orderLineID
        """,
        (rs, rowNum) ->
            new BillingLine(
                rs.getInt("orderLineID"),
                rs.getInt("itemID"),
                rs.getInt("serviceID"),
                rs.getInt("quantity"),
                BigDecimal.valueOf(rs.getDouble("linePrice"))),
        orderID);
  }

  @Override
  public BigDecimal findAppliedDiscountAmount(Integer orderID) {
    try {
      BigDecimal discountAmount =
          jdbcTemplate.queryForObject(
              "SELECT discountAmount FROM orderPromotions WHERE orderID = ?",
              BigDecimal.class,
              orderID);
      return discountAmount == null ? BigDecimal.ZERO : discountAmount;
    } catch (EmptyResultDataAccessException ex) {
      return BigDecimal.ZERO;
    } catch (DataAccessException ex) {
      return BigDecimal.ZERO;
    }
  }
}
