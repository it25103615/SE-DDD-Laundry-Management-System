package _6.Y2.S1.MTR._6.LaundryLink.dto.support;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public final class SupportRequests {
    private SupportRequests() {}
    public record CaseInput(
        @NotBlank @Pattern(regexp = "Complaint|Feedback|Question") String type,
        @NotBlank @Size(max = 100) String subject,
        @NotBlank @Size(max = 500) String message,
        @Positive Integer orderId,
        @Min(1) @Max(5) Integer rating,
        @PositiveOrZero Integer version) {}
    public record CaseUpdate(
        @NotBlank @Pattern(regexp = "New|Assigned|In Review|Resolved|Closed|Reopened") String status,
        @NotBlank @Pattern(regexp = "Low|Normal|High") String priority,
        @Positive Integer assigneeId,
        @Size(max = 500) String note,
        @NotNull @PositiveOrZero Integer version) {}
    public record MessageInput(@NotBlank @Size(max = 250) String message) {}
    public record ServiceInput(
        @NotBlank @Size(max = 30) String name,
        @NotBlank @Size(max = 250) String description,
        @NotNull @Min(1) @Max(720) Integer turnaroundHours,
        @NotNull Boolean active,
        @PositiveOrZero Integer version) {}
    public record PriceInput(
        @NotNull @Positive Integer itemId,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal price) {}
    public record StaffInput(
        @NotBlank @Size(max = 50) String firstName,
        @NotBlank @Size(max = 50) String lastName,
        @NotBlank @Email @Size(max = 100) String email,
        @NotBlank @Pattern(regexp = "[0-9]{10}") String phone,
        @NotBlank @Pattern(regexp = "RIDER|STAFF|CSM|MANAGER") String role,
        @Size(min = 8, max = 72) String password,
        @NotNull Boolean active,
        @PositiveOrZero Integer version) {}
}
