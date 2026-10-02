package _6.Y2.S1.MTR._6.LaundryLink.account.dto;

import jakarta.validation.constraints.*;
public record PasswordChangeRequest(@NotBlank String currentPassword,
                                    @NotBlank @Size(min = 8, max = 100) String newPassword,
                                    @NotBlank String confirmPassword) { }
