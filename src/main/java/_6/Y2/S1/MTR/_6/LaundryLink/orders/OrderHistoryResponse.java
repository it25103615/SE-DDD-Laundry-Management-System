/* OrderHistoryResponse
 * DTO: carries request/response data across a layer/API boundary. Validation annotations constrain input; this object is not automatically a database table.
 */
package _6.Y2.S1.MTR._6.LaundryLink.orders;

import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OrderHistoryResponse {
  private Integer logID;
  private Integer statusBeforeID;
  private String statusBeforeLabel;
  private Integer statusAfterID;
  private String statusAfterLabel;
  private LocalDate logDate;
  private LocalTime logTime;
}
