/* CreateOrderResponse
 * DTO: carries request/response data across a layer/API boundary. Validation annotations constrain input; this object is not automatically a database table.
 */
package _6.Y2.S1.MTR._6.LaundryLink.orders.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CreateOrderResponse {
  private Integer orderID;
  private Integer userID;
  private Integer statusID;
  private String statusLabel;
  private List<CreatedOrderLineResponse> orderLines;
}
