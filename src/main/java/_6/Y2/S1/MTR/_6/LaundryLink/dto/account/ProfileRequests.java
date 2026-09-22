package _6.Y2.S1.MTR._6.LaundryLink.dto.account;

import jakarta.validation.constraints.*;

public final class ProfileRequests {
    private ProfileRequests() {}
    public record Details(
            @NotBlank @Size(min=2,max=100) String fullName,
            @NotBlank @Email @Size(max=100) String email,
            @NotBlank @Pattern(regexp="[0-9]{10}",message="Phone must contain exactly 10 digits") String phone) {}
    public record Password(
            @NotBlank String currentPassword,
            @NotBlank @Pattern(regexp="^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S{8,72}$",
                    message="New password needs 8–72 characters, uppercase, lowercase, a number, and a special character, with no spaces") String newPassword,
            @NotBlank String confirmPassword) {}
}
