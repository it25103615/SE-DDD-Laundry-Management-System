package _6.Y2.S1.MTR._6.LaundryLink.billing;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface BillingRepository {
    Optional<Integer> findOrderUserID(Integer orderID);

    List<BillingLine> findOrderLines(Integer orderID);

    BigDecimal findAppliedDiscountAmount(Integer orderID);
}
