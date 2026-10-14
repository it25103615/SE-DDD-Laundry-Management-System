package _6.Y2.S1.MTR._6.LaundryLink.account.dto;

import jakarta.validation.constraints.*;

public final class PasswordRecoveryRequests {
    private PasswordRecoveryRequests() {}
    public record Forgot(@NotBlank @Email @Size(max=100) String email) {}
    public record Reset(
            @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") String token,
            @NotBlank @Pattern(regexp="^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S{8,72}$",
                    message="Password needs 8–72 characters, uppercase, lowercase, a number and a special character, without spaces") String password,
            @NotBlank String confirmPassword) {
        @AssertTrue(message="Passwords do not match")
        public boolean isConfirmed() { return password != null && password.equals(confirmPassword); }
    }
}
