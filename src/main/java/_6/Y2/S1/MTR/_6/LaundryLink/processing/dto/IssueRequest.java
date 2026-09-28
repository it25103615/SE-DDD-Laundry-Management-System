package _6.Y2.S1.MTR._6.LaundryLink.processing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /api/processing/issues: a damaged, stained or missing item reported by staff.
 * {@code orderLineID} is optional so an issue can be about the whole order.
 * The allowed issue types match the drop-down on issue_reports.html and
 * ProcessingIssueRepository.ISSUE_TYPES; the type is saved as the support case's caseType.
 */
public record IssueRequest(
        @NotNull(message = "is required")
        Integer orderID,

        Integer orderLineID,

        @NotBlank(message = "is required")
        @Pattern(regexp = "Damaged item|Existing stain|Missing item|Item count mismatch",
                message = "must be Damaged item, Existing stain, Missing item or Item count mismatch")
        String issueType,

        @NotBlank(message = "is required")
        @Size(max = 250, message = "must be at most 250 characters")
        String description) {
}
