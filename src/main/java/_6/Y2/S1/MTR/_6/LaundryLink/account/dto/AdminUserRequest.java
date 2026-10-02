package _6.Y2.S1.MTR._6.LaundryLink.account.dto;

import _6.Y2.S1.MTR._6.LaundryLink.user.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminUserRequest(@NotBlank @Size(max = 50) String firstName, @Size(max = 50) String middleName,
                               @NotBlank @Size(max = 50) String lastName,
                               @NotBlank @Email @Size(max = 100) String email, @NotBlank @Size(max = 20) String phoneNumber,
                               @NotNull UserRole type,
                               @Size(min = 8, max = 100) String password) { }
