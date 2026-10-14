/* OrderDetailResponse
 * DTO: carries request/response data across a layer/API boundary. Validation annotations constrain input; this object is not automatically a database table.
 */
package _6.Y2.S1.MTR._6.LaundryLink.orders;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OrderDetailResponse {
  private Integer orderID;
  private Integer userID;
  private Integer statusID;
  private String statusLabel;
  private List<OrderLineDetailResponse> orderLines;
  private Double orderTotal;
  private List<OrderHistoryResponse> history;
  // The customer's note for the laundry team; null when none was written.
  private String instructions;
  // The ticked preferences as display labels (e.g. "Fragrance-free detergent"); empty when none.
  private List<String> preferences;
  private boolean customerCanModify;
  private LocalDateTime pickupScheduled;
  private Integer pickupAddressID;
  private List<String> preferenceCodes;
}
