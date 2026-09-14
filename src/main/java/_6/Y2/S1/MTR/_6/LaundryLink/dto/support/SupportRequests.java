package _6.Y2.S1.MTR._6.LaundryLink.dto.support;

import jakarta.validation.constraints.*;

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
        @NotBlank @Size(max = 500) String note,
        @NotNull @PositiveOrZero Integer version) {}
    public record MessageInput(@NotBlank @Size(max = 250) String message) {}
    public record SettingInput(
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9_.-]{2,59}") String key,
        @NotBlank @Size(max = 250) String value,
        @NotBlank @Size(max = 250) String description,
        @PositiveOrZero Integer version) {}
}
