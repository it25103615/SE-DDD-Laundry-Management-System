package _6.Y2.S1.MTR._6.LaundryLink.processing;

import _6.Y2.S1.MTR._6.LaundryLink.processing.dto.IssueResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * SQL for staff issue reports. An issue is stored only as a support case (a row in
 * feedback), so customer service work it in their queue like any other case:
 * <ul>
 *   <li>{@code caseType} holds the issue type (Damaged item, Existing stain, ...);</li>
 *   <li>{@code subject} names the item it is about, or "Whole order";</li>
 *   <li>{@code feedback} holds the staff member's description;</li>
 *   <li>{@code userID} is the order's customer, so they can see and reply to the case;</li>
 *   <li>the staff member who reported it is the actor of the case's
 *       "Processing issue reported" entry in support_activity.</li>
 * </ul>
 */
@Repository
public class ProcessingIssueRepository {

    /** The issue types staff can report; stored in feedback.caseType (at most 20 characters). */
    public static final List<String> ISSUE_TYPES = List.of("Damaged item", "Existing stain", "Missing item", "Item count mismatch");

    /** The support_activity action written when staff report an issue; used to find the reporter. */
    public static final String REPORTED_ACTION = "Processing issue reported";

    /** feedback.subject is NVARCHAR(100). */
    private static final int MAX_SUBJECT_LENGTH = 100;

    /**
     * The issue types as a SQL list ('Damaged item', 'Existing stain', ...) for "caseType IN (...)".
     * Built from the constant above, never from user input, so it is safe to put in the SQL text.
     */
    public static final String ISSUE_TYPES_SQL = String.join(", ", ISSUE_TYPES.stream().map(type -> "'" + type + "'").toList());

    // Shared SELECT for listing issues: the case itself, plus the staff member from the first
    // "Processing issue reported" activity entry (OUTER APPLY picks just that one row).
    private static final String ISSUE_SELECT = """
            SELECT f.feedbackID, f.orderID, f.subject, f.caseType, f.feedback, f.createdAt, f.caseStatus,
                   CASE WHEN u.userID IS NULL THEN NULL ELSE CONCAT(u.firstName, ' ', u.lastName) END AS reportedBy
            FROM feedback f
            OUTER APPLY (SELECT TOP (1) a.actorID FROM support_activity a
                         WHERE a.feedbackID = f.feedbackID AND a.action = ?
                         ORDER BY a.activityID) reporter
            LEFT JOIN users u ON u.userID = reporter.actorID
            WHERE f.deleted = 0 AND f.caseType IN (""" + ISSUE_TYPES_SQL + ")";

    private final JdbcTemplate db;

    public ProcessingIssueRepository(JdbcTemplate db) {
        this.db = db;
    }

    public boolean orderExists(int orderID) {
        Integer count = db.queryForObject("SELECT COUNT(*) FROM orders WHERE orderID = ?", Integer.class, orderID);
        return count != null && count > 0;
    }

    /** The item name on an order line, or empty when the line is not part of that order. */
    public Optional<String> findLineItemName(int orderLineID, int orderID) {
        return db.queryForList("""
                SELECT it.itemName FROM orderLines ol JOIN items it ON it.itemID = ol.itemID
                WHERE ol.orderLineID = ? AND ol.orderID = ?
                """, String.class, orderLineID, orderID).stream().findFirst();
    }

    /**
     * Opens a High-priority case for the issue, filed under the order's customer.
     * Returns the new feedbackID. feedback has no triggers, so OUTPUT is allowed here.
     */
    public int insertIssueCase(int orderID, String issueType, String subject, String description) {
        String trimmedSubject = subject.length() > MAX_SUBJECT_LENGTH ? subject.substring(0, MAX_SUBJECT_LENGTH) : subject;
        return db.queryForObject("""
                INSERT INTO feedback (feedback, userID, orderID, caseType, subject, priority)
                OUTPUT INSERTED.feedbackID
                SELECT ?, o.userID, o.orderID, ?, ?, 'High'
                FROM orders o WHERE o.orderID = ?
                """, Integer.class, description, issueType, trimmedSubject, orderID);
    }

    /** Adds an entry to the support case's activity history, as the support module does. */
    public void audit(int feedbackID, int actorID, String action, String details) {
        db.update("INSERT INTO support_activity (feedbackID, actorID, action, details) VALUES (?, ?, ?, ?)",
                feedbackID, actorID, action, details);
    }

    /** Issue reports, newest first; all of them, or only one order when orderID is given. */
    public List<IssueResponse> findIssues(Integer orderID) {
        return db.query(ISSUE_SELECT + " AND (? IS NULL OR f.orderID = ?) ORDER BY f.feedbackID DESC",
                ProcessingIssueRepository::toIssue, args(orderID, orderID));
    }

    public Optional<IssueResponse> findIssue(int feedbackID) {
        return db.query(ISSUE_SELECT + " AND f.feedbackID = ?", ProcessingIssueRepository::toIssue, args(feedbackID))
                .stream().findFirst();
    }

    /** Parameters for ISSUE_SELECT (the reporter's activity action), then the extra ones. */
    private static Object[] args(Object... extra) {
        List<Object> all = new ArrayList<>();
        all.add(REPORTED_ACTION);
        all.addAll(Arrays.asList(extra));
        return all.toArray();
    }

    private static IssueResponse toIssue(ResultSet rs, int row) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("createdAt");
        return new IssueResponse(
                rs.getInt("feedbackID"),
                rs.getInt("orderID"),
                rs.getString("subject"),
                rs.getString("caseType"),
                rs.getString("feedback"),
                rs.getString("reportedBy"),
                createdAt == null ? null : createdAt.toLocalDateTime(),
                rs.getString("caseStatus"));
    }
}
