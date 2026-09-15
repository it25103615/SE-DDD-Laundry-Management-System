package _6.Y2.S1.MTR._6.LaundryLink.dto.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;

public record RegistrationRequest(
        @NotBlank(message = "Full name is required") @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters") String fullName,
        @NotBlank(message = "Email is required") @Email(message = "Enter a valid email address") @Size(max = 100, message = "Email must be at most 100 characters") String email,
        @NotBlank(message = "Phone number is required") @Pattern(regexp = "[0-9]{10}", message = "Phone number must contain exactly 10 digits") String phone,
        @NotBlank(message = "Password is required")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S{8,72}$",
                message = "Password must contain uppercase, lowercase, number and special character") String password,
        @NotBlank(message = "Confirm your password") String confirmPassword,
        @Size(max = 100, message = "Address must be at most 100 characters") String address
) {
    @AssertTrue(message = "Passwords do not match")
    public boolean isPasswordConfirmed() {
        return password != null && password.equals(confirmPassword);
    }
}
