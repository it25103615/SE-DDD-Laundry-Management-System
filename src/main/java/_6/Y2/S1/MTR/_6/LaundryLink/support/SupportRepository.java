/* SupportRepository
 * REPOSITORY PATTERN / DATA ACCESS: private JdbcTemplate is injected. query reads; update executes INSERT/UPDATE/DELETE; insert returns an inserted ID; audit writes support_activity. SQL values use placeholders and arguments. CASE_SELECT joins case/customer/assignee data. This is JDBC, not a JPA entity repository.
 */
package _6.Y2.S1.MTR._6.LaundryLink.support;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Parameterized database access shared by support workflows and their reports. */
@Repository
public class SupportRepository {
  private final JdbcTemplate db;

  public SupportRepository(JdbcTemplate db) {
    this.db = db;
  }

  public List<Map<String, Object>> query(String sql, Object... args) {
    return db.queryForList(sql, args);
  }

  // OOP — Abstraction / Repository: expose a database operation while wrapping the JdbcTemplate
  // execution details.
  public int update(String sql, Object... args) {
    return db.update(sql, args);
  }

  // OOP — Abstraction / Repository: expose a database operation while wrapping the JdbcTemplate
  // execution details.
  public int count(String sql, Object... args) {
    return Objects.requireNonNull(db.queryForObject(sql, Integer.class, args));
  }

  // OOP — Abstraction / Repository: expose a database operation while wrapping the JdbcTemplate
  // execution details.
  public int insert(String sql, Object... args) {
    return Objects.requireNonNull(db.queryForObject(sql, Integer.class, args));
  }

  // OOP — Abstraction / Repository: expose a database operation while wrapping the JdbcTemplate
  // execution details.
  public void audit(Integer caseId, int actorId, String action, String details) {
    update(
        "INSERT INTO support_activity(feedbackID, actorID, action, details) VALUES (?, ?, ?, ?)",
        caseId,
        actorId,
        action,
        details);
  }

  public static final String CASE_SELECT =
      """
      SELECT f.feedbackID AS id, f.userID AS customerId, f.orderID AS orderId,
             f.feedback AS message, f.caseType AS type, f.subject, f.rating, f.topic,
             f.caseStatus AS status, f.priority, f.assigneeID AS assigneeId,
             f.createdAt, f.updatedAt, f.version,
             CONCAT(u.firstName, ' ', u.lastName) AS customer,
             CASE WHEN reporter.actorID IS NULL THEN CONCAT(u.firstName, ' ', u.lastName)
                  WHEN r.userID IS NULL THEN 'Staff'
                  ELSE CONCAT(r.firstName, ' ', r.lastName) END AS initialAuthor,
             CASE WHEN reporter.actorID IS NULL THEN 'CUSTOMER'
                  ELSE COALESCE(UPPER(r.type), 'STAFF') END AS initialAuthorRole,
             CONCAT(a.firstName, ' ', a.lastName) AS assignee
      FROM feedback f JOIN users u ON u.userID=f.userID
      LEFT JOIN users a ON a.userID=f.assigneeID
      OUTER APPLY (SELECT TOP (1) activity.actorID FROM support_activity activity
                   WHERE activity.feedbackID=f.feedbackID
                     AND activity.action='Processing issue reported'
                   ORDER BY activity.activityID) reporter
      LEFT JOIN users r ON r.userID=reporter.actorID
      WHERE f.deleted=0
      """;
}
