/* UserResponse
 * DTO: carries request/response data across a layer/API boundary. Validation annotations constrain input; this object is not automatically a database table.
 *
 */
package _6.Y2.S1.MTR._6.LaundryLink.account.dto;

import _6.Y2.S1.MTR._6.LaundryLink.user.*;

public record UserResponse(
    Integer userID,
    String firstName,
    String middleName,
    String lastName,
    String email,
    String phoneNumber,
    UserRole type) {
  public static UserResponse from(User user) {
    return new UserResponse(
        user.getUserID(),
        user.getFirstName(),
        user.getMiddleName(),
        user.getLastName(),
        user.getEmail(),
        user.getPhoneNumber(),
        user.getType());
  }
}
