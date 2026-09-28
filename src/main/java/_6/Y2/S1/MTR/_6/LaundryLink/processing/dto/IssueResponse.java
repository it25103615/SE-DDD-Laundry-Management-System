package _6.Y2.S1.MTR._6.LaundryLink.processing.dto;

import java.time.LocalDateTime;

/**
 * One issue report as listed on Issue Reports and the order page. Each issue is a support case
 * (a feedback row): {@code caseID} is its feedbackID, {@code subject} names the item (or
 * "Whole order"), and {@code caseStatus} is its status in the CSM queue (New, In Review, Resolved...).
 */
public record IssueResponse(
        int caseID,
        int orderID,
        String subject,
        String issueType,
        String description,
        String reportedBy,
        LocalDateTime createdAt,
        String caseStatus) {
}
