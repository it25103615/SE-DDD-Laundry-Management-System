package _6.Y2.S1.MTR._6.LaundryLink.repository.support;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Parameterized database access shared by support workflows and their reports. */
@Repository
public class SupportRepository {
    private final JdbcTemplate db;
    public SupportRepository(JdbcTemplate db) { this.db = db; }
    public List<Map<String, Object>> query(String sql, Object... args) { return db.queryForList(sql, args); }
    public int update(String sql, Object... args) { return db.update(sql, args); }
    public int count(String sql, Object... args) { return Objects.requireNonNull(db.queryForObject(sql, Integer.class, args)); }
    public int insert(String sql, Object... args) { return Objects.requireNonNull(db.queryForObject(sql, Integer.class, args)); }
    public void audit(Integer caseId, int actorId, String action, String details) {
        update("INSERT INTO support_activity(feedbackID, actorID, action, details) VALUES (?, ?, ?, ?)", caseId, actorId, action, details);
    }
    public static final String CASE_SELECT = """
        SELECT f.feedbackID AS id, f.userID AS customerId, f.orderID AS orderId,
               f.feedback AS message, f.caseType AS type, f.subject, f.rating,
               f.caseStatus AS status, f.priority, f.assigneeID AS assigneeId,
               f.createdAt, f.updatedAt, f.version,
               CONCAT(u.firstName, ' ', u.lastName) AS customer,
               CONCAT(a.firstName, ' ', a.lastName) AS assignee
        FROM feedback f JOIN users u ON u.userID=f.userID
        LEFT JOIN users a ON a.userID=f.assigneeID
        WHERE f.deleted=0
        """;
}
