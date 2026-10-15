/* PasswordRecoveryRequests
 * DTO: carries request/response data across a layer/API boundary. Validation annotations constrain input; this object is not automatically a database table.
 */
package _6.Y2.S1.MTR._6.LaundryLink.account.dto;

import jakarta.validation.constraints.*;

public final class PasswordRecoveryRequests {
  private PasswordRecoveryRequests() {}

  public record Forgot(@NotBlank @Email @Size(max = 100) String email) {}

  public record Verify(
      @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String challenge,
      @NotBlank @Pattern(regexp = "[0-9]{6}", message = "Enter the six-digit email code") String code) {}

  public record Reset(
      @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token,
      @NotBlank
          @Pattern(
              regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S{8,72}$",
              message =
                  "Password needs 8–72 characters, uppercase, lowercase, a number and a special"
                      + " character, without spaces")
          String password,
      @NotBlank String confirmPassword) {
    @AssertTrue(message = "Passwords do not match")
    public boolean isConfirmed() {
      return password != null && password.equals(confirmPassword);
    }
  }
}
