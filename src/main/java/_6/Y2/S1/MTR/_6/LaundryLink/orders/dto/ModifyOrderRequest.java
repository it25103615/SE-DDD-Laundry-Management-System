/* ModifyOrderRequest
 * DTO: carries request/response data across a layer/API boundary. Validation annotations constrain input; this object is not automatically a database table.
 */
package _6.Y2.S1.MTR._6.LaundryLink.orders.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ModifyOrderRequest {
  @NotEmpty @Valid private List<CreateOrderLineRequest> orderLines;

  // Customer updates include these fields; management's line-only update ignores them.
  private LocalDateTime pickupScheduled;
  private Integer addressID;
  private String instructions;
  private List<String> preferences;
}
