/* BillingRepository
 * REPOSITORY: persistence boundary. JPA repository interfaces are implemented by Spring; JDBC implementations execute explicit SQL.
 *
 */
package _6.Y2.S1.MTR._6.LaundryLink.billing;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

// OOP — Abstraction: this interface describes operations that implementing classes must provide.
public interface BillingRepository {
  Optional<Integer> findOrderUserID(Integer orderID);

  List<BillingLine> findOrderLines(Integer orderID);

  BigDecimal findAppliedDiscountAmount(Integer orderID);
}
