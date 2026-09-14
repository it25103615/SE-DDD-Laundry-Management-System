package _6.Y2.S1.MTR._6.LaundryLink.billing;

import java.util.List;
import java.util.Optional;

public interface BillingRepository {
    Optional<Integer> findOrderUserID(Integer orderID);

    List<BillingLine> findOrderLines(Integer orderID);
}
