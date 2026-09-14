package _6.Y2.S1.MTR._6.LaundryLink.dto.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistrationRequest(
        @NotBlank @Size(max = 100) String fullName,
        @NotBlank @Email @Size(max = 100) String email,
        @NotBlank @Pattern(regexp = "[0-9]{10}", message = "must contain exactly 10 digits") String phone,
        @NotBlank @Size(min = 8, max = 72) String password,
        @Size(max = 100) String address
) {}
