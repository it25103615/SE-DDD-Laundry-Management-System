/* PromotionValidationResponse
 * DTO: carries request/response data across a layer/API boundary. Validation annotations constrain input; this object is not automatically a database table.
 */
package _6.Y2.S1.MTR._6.LaundryLink.promotion;

import java.math.BigDecimal;

public class PromotionValidationResponse {
  private final boolean valid;
  private final String message;
  private final BigDecimal discountAmount;
  private final BigDecimal finalPayableAmount;

  public PromotionValidationResponse(
      boolean valid, String message, BigDecimal discountAmount, BigDecimal finalPayableAmount) {
    this.valid = valid;
    this.message = message;
    this.discountAmount = discountAmount;
    this.finalPayableAmount = finalPayableAmount;
  }

  public boolean isValid() {
    return valid;
  }

  public String getMessage() {
    return message;
  }

  public BigDecimal getDiscountAmount() {
    return discountAmount;
  }

  public BigDecimal getFinalPayableAmount() {
    return finalPayableAmount;
  }
}
