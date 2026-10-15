/* SupportService
 * MAJOR COMPONENT: customer requests, case scoping, assignment, conversation, one-time feedback and customer satisfaction. create/edit/delete/handle/message/satisfaction write SQL through SupportRepository. @Transactional makes each operation atomic. Status changes are in handle() and satisfaction(); feedback is not a status workflow.
 */
package _6.Y2.S1.MTR._6.LaundryLink.support;

import static _6.Y2.S1.MTR._6.LaundryLink.support.dto.SupportRequests.*;
import static org.springframework.http.HttpStatus.*;

import _6.Y2.S1.MTR._6.LaundryLink.notification.NotificationService;
import _6.Y2.S1.MTR._6.LaundryLink.processing.ProcessingIssueRepository;
import _6.Y2.S1.MTR._6.LaundryLink.support.SupportAccess.Actor;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
// Run database reads in a read-only transaction.
@Transactional(readOnly = true)
public class SupportService {
  private final SupportRepository repo;
  private final SupportAccess access;
  private final NotificationService notifications;

  public SupportService(
      SupportRepository repo, SupportAccess access, NotificationService notifications) {
    this.repo = repo;
    this.access = access;
    this.notifications = notifications;
  }

  // READ: scoped/paginated case queue; excludes Feedback unless explicitly requested. Customer sees
  // own records; assigned staff sees scoped records; CSM oversees queue.

  // OOP — Method overloading: the two cases methods accept different parameter lists.
  // One uses the default topic; the other supports a topic filter.
  public List<Map<String, Object>> cases(
      Actor actor,
      String search,
      String status,
      String type,
      String priority,
      Integer assigneeId,
      int page) {
    return cases(actor, search, status, type, priority, assigneeId, page, null);
  }

  // READ: scoped/paginated case queue; excludes Feedback unless explicitly requested. Customer sees
  // own records; assigned staff sees scoped records; CSM oversees queue.
  // OOP — Method overloading: the two cases methods accept different parameter lists.
  // One uses the default topic; the other supports a topic filter.
  public List<Map<String, Object>> cases(
      Actor actor,
      String search,
      String status,
      String type,
      String priority,
      Integer assigneeId,
      int page,
      String topic) {
    if ("Feedback".equals(type) && actor.staff() && !actor.coordinator())
      throw new ResponseStatusException(FORBIDDEN, "Only customer service manages feedback.");
    if (page < 0) throw new ResponseStatusException(BAD_REQUEST, "Invalid page.");
    String sql = SupportRepository.CASE_SELECT;
    if (!"Feedback".equals(type)) sql += " AND f.caseType<>'Feedback'";
    List<Object> args = new ArrayList<>();
    if ("CUSTOMER".equals(actor.role())) {
      sql += " AND f.userID=?";
      args.add(actor.id());
    } else if (!actor.seesAllCases()) {
      sql += " AND f.assigneeID=?";
      args.add(actor.id());
    }
    if (search != null && !search.isBlank()) {
      if (search.length() > 100)
        throw new ResponseStatusException(BAD_REQUEST, "Search must be at most 100 characters.");
      sql +=
          " AND (f.subject LIKE ? OR f.feedback LIKE ? OR CONCAT(u.firstName, ' ', u.lastName) LIKE"
              + " ?)";
      String term = "%" + search.replace("[", "[[]").replace("%", "[%]").replace("_", "[_]") + "%";
      args.addAll(List.of(term, term, term));
    }
    if ("pending".equalsIgnoreCase(status))
      sql += " AND f.caseStatus IN ('New','Assigned','Reopened')";
    else if ("resolved".equalsIgnoreCase(status))
      sql += " AND f.caseStatus IN ('Resolved','Closed')";
    else if (status != null && !status.isBlank()) {
      sql += " AND f.caseStatus=?";
      args.add(status);
    }
    if ("Complaint".equals(type))
      sql +=
          " AND f.caseType IN ('Complaint','Damaged item','Existing stain','Missing item','Item"
              + " count mismatch')";
    else if (type != null && !type.isBlank()) {
      sql += " AND f.caseType=?";
      args.add(type);
    }
    if (topic != null && !topic.isBlank()) {
      sql += " AND f.topic=?";
      args.add(topic);
    }
    if (priority != null && !priority.isBlank()) {
      sql += " AND f.priority=?";
      args.add(priority);
    }
    if (assigneeId != null && actor.seesAllCases()) {
      sql += " AND f.assigneeID=?";
      args.add(assigneeId);
    }
    sql += " ORDER BY f.updatedAt DESC, f.feedbackID DESC OFFSET ? ROWS FETCH NEXT 25 ROWS ONLY";
    args.add((long) page * 25);
    return repo.query(sql, args.toArray());
  }

  // READ: aggregate case counts using role scope; Feedback excluded from the complaint/enquiry
  // status totals.
  public Map<String, Object> summary(Actor actor) {
    String scope = " AND caseType<>'Feedback'";
    List<Object> args = new ArrayList<>();
    if ("CUSTOMER".equals(actor.role())) {
      scope += " AND userID=?";
      args.add(actor.id());
    } else if (!actor.seesAllCases()) {
      scope += " AND assigneeID=?";
      args.add(actor.id());
    }
    return repo.query(
            """
            SELECT COUNT(*) AS total,
              COALESCE(SUM(CASE WHEN caseStatus IN ('New','Assigned','Reopened') THEN 1 ELSE 0 END),0) AS pending,
              COALESCE(SUM(CASE WHEN caseStatus='In Review' THEN 1 ELSE 0 END),0) AS inProgress,
              COALESCE(SUM(CASE WHEN caseStatus IN ('Resolved','Closed') THEN 1 ELSE 0 END),0) AS resolved,
              COALESCE(SUM(CASE WHEN priority='High' AND caseStatus NOT IN ('Resolved','Closed') THEN 1 ELSE 0 END),0) AS highPriority
            FROM feedback WHERE deleted=0
            """
                + scope,
            args.toArray())
        .getFirst();
  }

  // OWNERSHIP GUARD: retrieve active case and check actor visibility before any read/write workflow
  // continues.
  public Map<String, Object> one(Actor actor, int id) {
    var rows = repo.query(SupportRepository.CASE_SELECT + " AND f.feedbackID=?", id);
    if (rows.isEmpty()) throw new ResponseStatusException(NOT_FOUND, "Case not found.");
    var item = rows.getFirst();
    if (!canView(actor, item))
      throw new ResponseStatusException(NOT_FOUND, "Case not found or not assigned to you.");
    return item;
  }

  // READ: case + ordered messages; history is exposed only to CSM. This is the payload rendered by
  // the browser detail panel.
  public Map<String, Object> detail(Actor actor, int id) {
    var item = new LinkedHashMap<>(one(actor, id));
    if (Set.of("CSM", "CUSTOMER_SERVICE_MANAGER").contains(actor.role()))
      item.put(
          "history",
          repo.query(
              """
              SELECT a.activityID AS id, a.action, a.details, a.createdAt, CONCAT(u.firstName, ' ', u.lastName) AS actor
              FROM support_activity a JOIN users u ON u.userID=a.actorID
              WHERE a.feedbackID=? ORDER BY a.createdAt, a.activityID
              """,
              id));
    item.put(
        "messages",
        repo.query(
            """
            SELECT c.chatID AS id, c.message, c.sentAt, c.userID AS authorId,
                   UPPER(u.type) AS authorRole, CONCAT(u.firstName, ' ', u.lastName) AS author
            FROM chat c JOIN users u ON u.userID=c.userID WHERE c.feedbackID=? ORDER BY c.sentAt, c.chatID
            """,
            id));
    return item;
  }

  private void validateCase(Actor actor, CaseInput input) {
    if (!"CUSTOMER".equals(actor.role()))
      throw new ResponseStatusException(FORBIDDEN, "Use a customer account to submit feedback.");
    if ("Feedback".equals(input.type()) && input.rating() == null)
      throw new ResponseStatusException(BAD_REQUEST, "Choose a rating from 1 to 5 for feedback.");
    if (!"Feedback".equals(input.type()) && input.rating() != null)
      throw new ResponseStatusException(BAD_REQUEST, "Ratings apply only to feedback.");
    // Enforce this in the service too: callers outside HTTP validation must select an order.
    if (input.orderId() == null || input.orderId() <= 0)
      throw new ResponseStatusException(BAD_REQUEST, "Select a related order.");
    if (input.orderId() != null
        && repo.count(
                "SELECT COUNT(*) FROM orders WHERE orderID=? AND userID=?",
                input.orderId(),
                actor.id())
            != 1)
      throw new ResponseStatusException(BAD_REQUEST, "Select an order belonging to your account.");
  }

  /** Read-only order view scoped to a visible case, including assigned staff and riders. */
  public Map<String, Object> relatedOrder(Actor actor, int id) {
    // Use the case visibility check first; accepting an arbitrary order ID here would bypass it.
    var item = one(actor, id);
    if (!(item.get("orderId") instanceof Number orderId))
      throw new ResponseStatusException(NOT_FOUND, "This older case has no related order.");
    var orders = repo.query(
        """
        SELECT o.orderID AS id, CONCAT(u.firstName, ' ', u.lastName) AS customer,
               s.statusLabel AS status,
               (SELECT MIN(logDate) FROM logs WHERE orderID=o.orderID) AS placedOn,
               COALESCE((SELECT SUM(linePrice) FROM orderLines WHERE orderID=o.orderID),0) AS itemTotal,
               COALESCE((SELECT SUM(amount) FROM payments WHERE orderID=o.orderID),0) AS recordedPayments
        FROM orders o JOIN users u ON u.userID=o.userID
        LEFT JOIN status s ON s.statusID=o.statusID
        WHERE o.orderID=? AND o.userID=?
        """, orderId.intValue(), number(item, "customerId"));
    if (orders.isEmpty())
      throw new ResponseStatusException(NOT_FOUND, "Related order not found for this customer.");
    var result = new LinkedHashMap<>(orders.getFirst());
    result.put("lines", repo.query(
        """
        SELECT i.itemName AS item, s.serviceName AS service, l.quantity, l.linePrice
        FROM orderLines l JOIN items i ON i.itemID=l.itemID
        JOIN services s ON s.serviceID=l.serviceID
        WHERE l.orderID=? ORDER BY l.orderLineID
        """, orderId.intValue()));
    result.put("history", repo.query(
        """
        SELECT l.logDate, l.logTime, b.statusLabel AS beforeStatus, a.statusLabel AS afterStatus
        FROM logs l LEFT JOIN status b ON b.statusID=l.status_before
        LEFT JOIN status a ON a.statusID=l.status_after
        WHERE l.orderID=?
        ORDER BY CASE WHEN l.status_before IS NULL THEN 0 ELSE 1 END,l.logDate,l.logTime,l.logID
        """, orderId.intValue()));
    return result;
  }

  // Commit these database changes together, or roll them back if the operation fails.
  @Transactional
  // CREATE: validate ownership/input; insert feedback row, audit creation and notify CSM. Feedback
  // and support cases share storage but have different journeys.
  public Map<String, Object> create(Actor actor, CaseInput input) {
    validateCase(actor, input);
    int id =
        repo.insert(
            """
            SET NOCOUNT ON;
            DECLARE @created TABLE (id INT);
            INSERT INTO feedback(feedback, userID, orderID, caseType, subject, rating, topic)
            OUTPUT INSERTED.feedbackID INTO @created VALUES (?, ?, ?, ?, ?, ?, ?);
            SELECT id FROM @created;
            """,
            input.message().trim(),
            actor.id(),
            input.orderId(),
            input.type(),
            input.subject().trim(),
            input.rating(),
            input.topic() == null ? "General" : input.topic());
    // Record who performed this action in the audit history.
    repo.audit(id, actor.id(), "Created", input.type() + " submitted");
    // Create a notification for the affected user or role.
    notifications.notifyRoles(
        Set.of("CSM", "CUSTOMER_SERVICE_MANAGER"),
        "SUPPORT",
        "Feedback".equals(input.type()) ? "New customer feedback" : "New support case",
        input.type() + " #" + id + ": " + input.subject().trim(),
        "/html/support/"
            + ("Feedback".equals(input.type()) ? "feedback" : "cases")
            + ".html?caseId="
            + id,
        "SUPPORT",
        id,
        actor.id());
    // Return the updated request details so the browser can refresh its view.
    return detail(actor, id);
  }

  // Commit these database changes together, or roll them back if the operation fails.
  @Transactional
  // UPDATE: customer-owned new request only; version predicate prevents lost updates. Submitted
  // feedback cannot be edited.
  public Map<String, Object> edit(Actor actor, int id, CaseInput input) {
    var item = one(actor, id);
    if ("Feedback".equals(item.get("type")))
      throw new ResponseStatusException(CONFLICT, "Submitted feedback cannot be edited.");
    submittedByCustomer(item, "edited");
    validateCase(actor, input);
    if (!SupportCaseWorkflow.forStatus((String) item.get("status")).canEditOrDelete())
      throw new ResponseStatusException(CONFLICT, "Only new cases can be edited.");
    // Require the database write to affect the expected row; reject stale updates.
    changed(
        repo.update(
            """
            UPDATE feedback SET feedback=?, orderID=?, caseType=?, subject=?, rating=?, topic=?, updatedAt=SYSDATETIME(), version=version+1
            WHERE feedbackID=? AND deleted=0 AND caseStatus='New' AND version=?
            """,
            input.message().trim(),
            input.orderId(),
            input.type(),
            input.subject().trim(),
            input.rating(),
            input.topic() == null ? "General" : input.topic(),
            id,
            input.version()));
    // Record who performed this action in the audit history.
    repo.audit(id, actor.id(), "Edited", "Customer updated the case details");
    // Return the updated request details so the browser can refresh its view.
    return detail(actor, id);
  }

  // Commit these database changes together, or roll them back if the operation fails.
  @Transactional
  // DELETE: soft deletion preserves the database history; customers may remove eligible new support
  // requests only.
  public void delete(Actor actor, int id, int version) {
    var item = one(actor, id);
    if (!"CUSTOMER".equals(actor.role()) || number(item, "customerId") != actor.id())
      throw new ResponseStatusException(
          FORBIDDEN, "Only the customer who submitted this case can delete it.");
    submittedByCustomer(item, "deleted");
    if (!SupportCaseWorkflow.forStatus((String) item.get("status")).canEditOrDelete())
      throw new ResponseStatusException(
          CONFLICT, "Only new cases can be deleted; handled cases retain their history.");
    // Require the database write to affect the expected row; reject stale updates.
    changed(
        repo.update(
            "UPDATE feedback SET deleted=1, version=version+1, updatedAt=SYSDATETIME() WHERE"
                + " feedbackID=? AND version=? AND deleted=0 AND caseStatus='New'",
            id,
            version));
    // Record who performed this action in the audit history.
    repo.audit(id, actor.id(), "Deleted", "Case removed from active views; history retained");
  }

  static boolean allowed(String before, String after) {
    // Delegate to the current State object while preserving this helper for existing callers/tests.
    return SupportCaseWorkflow.forStatus(before).canTransitionTo(after);
  }

  // Commit these database changes together, or roll them back if the operation fails.
  @Transactional
  // STATUS / ASSIGNMENT WRITE: staff role and assignee checks, permitted transition, required
  // resolution note, optimistic version; updates caseStatus/priority/assigneeID and writes
  // audit/notification.
  public Map<String, Object> handle(Actor actor, int id, CaseUpdate input) {
    if (!actor.staff()) throw new ResponseStatusException(FORBIDDEN, "Staff access required.");
    var item = one(actor, id);
    if ("Feedback".equals(item.get("type")))
      throw new ResponseStatusException(
          BAD_REQUEST,
          "Feedback only needs a reply; it cannot be assigned or resolved as a support case.");
    if (!actor.coordinator() && !Objects.equals(item.get("assigneeId"), input.assigneeId()))
      throw new ResponseStatusException(
          FORBIDDEN, "Only a support coordinator can reassign a case.");
    String note = input.note() == null ? "" : input.note().trim();
    // The destination state owns its requirements; validation order and messages stay the same.
    var targetState = SupportCaseWorkflow.forStatus(input.status());
    if (targetState.requiresResolutionNote() && note.isBlank())
      throw new ResponseStatusException(
          BAD_REQUEST, "Add a resolution note before resolving or closing a case.");
    if (!allowed((String) item.get("status"), input.status()))
      throw new ResponseStatusException(
          CONFLICT,
          "Invalid case transition. Assign or review before resolving; resolve before closing.");
    if (targetState.requiresAssignee() && input.assigneeId() == null)
      throw new ResponseStatusException(
          BAD_REQUEST, "Assign a staff member before progressing the case.");
    if (input.assigneeId() != null
        && repo.count(
                "SELECT COUNT(*) FROM users WHERE userID=? AND active=1 AND UPPER(type) IN"
                    + " ('ADMIN','MANAGER','OWNER','CSM','CUSTOMER_SERVICE_MANAGER','STAFF','RIDER')",
                input.assigneeId())
            != 1)
      throw new ResponseStatusException(BAD_REQUEST, "Assignee must be an active staff member.");
    // Require the database write to affect the expected row; reject stale updates.
    changed(
        repo.update(
            """
            UPDATE feedback SET caseStatus=?, priority=?, assigneeID=?, version=version+1, updatedAt=SYSDATETIME()
            WHERE feedbackID=? AND version=? AND deleted=0
            """,
            input.status(),
            input.priority(),
            input.assigneeId(),
            id,
            input.version()));
    String assignee =
        input.assigneeId() == null
            ? "Unassigned"
            : repo
                .query(
                    "SELECT CONCAT(firstName, ' ', lastName) AS name FROM users WHERE userID=?",
                    input.assigneeId())
                .stream()
                .findFirst()
                .map(row -> String.valueOf(row.get("name")))
                .orElse("Staff member");
    // Record who performed this action in the audit history.
    repo.audit(
        id,
        actor.id(),
        "Case updated",
        "Status: "
            + item.get("status")
            + " → "
            + input.status()
            + "\nPriority: "
            + input.priority()
            + "\nAssigned to: "
            + assignee
            + (note.isBlank() ? "" : "\nNote: " + note));
    // Create a notification for the affected user or role.
    notifications.notifyUser(
        number(item, "customerId"),
        "SUPPORT",
        "Support case updated",
        "Case #" + id + " is now " + input.status() + ".",
        "/html/customer/feedback.html?caseId=" + id,
        "SUPPORT",
        id);
    Object previousAssignee = item.get("assigneeId");
    if (input.assigneeId() != null
        && (!(previousAssignee instanceof Number)
            || ((Number) previousAssignee).intValue() != input.assigneeId()))
      // Create a notification for the affected user or role.
      notifications.notifyUser(
          input.assigneeId(),
          "ASSIGNMENT",
          "Support case assigned",
          "Case #" + id + " was assigned to you.",
          "/html/support/cases.html?caseId=" + id,
          "SUPPORT",
          id);
    // Return the updated request details so the browser can refresh its view.
    return detail(actor, id);
  }

  // Commit these database changes together, or roll them back if the operation fails.
  @Transactional
  // CHAT WRITE: insert chat row and increment case version. Closed support conversations reject
  // messages. Feedback allows one CSM response and no customer follow-up.
  public Map<String, Object> message(Actor actor, int id, MessageInput input) {
    var item = one(actor, id);
    if ("Feedback".equals(item.get("type")) && actor.staff() && !actor.coordinator())
      throw new ResponseStatusException(FORBIDDEN, "Only customer service replies to feedback.");
    if ("Feedback".equals(item.get("type")) && "CUSTOMER".equals(actor.role()))
      throw new ResponseStatusException(
          FORBIDDEN,
          "Feedback is a one-time submission. You can read the customer service reply here.");
    if ("Feedback".equals(item.get("type"))
        && repo.count(
                "SELECT COUNT(*) FROM chat c JOIN users u ON u.userID=c.userID WHERE c.feedbackID=?"
                    + " AND UPPER(u.type)<>'CUSTOMER'",
                id)
            > 0)
      throw new ResponseStatusException(
          CONFLICT, "Customer service has already replied to this feedback.");
    // Feedback keeps its separate one-time reply rules; support cases consult their State.
    if (!SupportCaseWorkflow.forStatus((String) item.get("status")).canMessage()
        && !"Feedback".equals(item.get("type")))
      throw new ResponseStatusException(CONFLICT, "Reopen the case before adding a message.");
    // Updating the case first serializes messages against closure/deletion.
    changed(
        repo.update(
            "UPDATE feedback SET updatedAt=SYSDATETIME(), version=version+1 WHERE feedbackID=? AND"
                + " deleted=0 AND version=? AND (caseStatus<>'Closed' OR caseType='Feedback')",
            id,
            item.get("version")));
    repo.update(
        "INSERT INTO chat(message,userID,feedbackID) VALUES (?,?,?)",
        input.message().trim(),
        actor.id(),
        id);
    // Record who performed this action in the audit history.
    repo.audit(id, actor.id(), "Message added", "Communication recorded");
    if ("CUSTOMER".equals(actor.role())) {
      Object assignee = item.get("assigneeId");
      if (assignee instanceof Number)
        // Create a notification for the affected user or role.
        notifications.notifyUser(
            ((Number) assignee).intValue(),
            "SUPPORT",
            "Customer replied",
            "The customer replied to case #" + id + ".",
            "/html/support/cases.html?caseId=" + id,
            "SUPPORT",
            id);
      else
        // Create a notification for the affected user or role.
        notifications.notifyRoles(
            Set.of("CSM", "CUSTOMER_SERVICE_MANAGER", "OWNER", "ADMIN"),
            "SUPPORT",
            "Customer replied",
            "A customer replied to unassigned case #" + id + ".",
            "/html/support/cases.html?caseId=" + id,
            "SUPPORT",
            id,
            actor.id());
    } else
      // Create a notification for the affected user or role.
      notifications.notifyUser(
          number(item, "customerId"),
          "SUPPORT",
          "Support replied",
          "A support reply was added to case #" + id + ".",
          "/html/customer/feedback.html?caseId=" + id,
          "SUPPORT",
          id);
    // Return the updated request details so the browser can refresh its view.
    return detail(actor, id);
  }

  // Commit these database changes together, or roll them back if the operation fails.
  @Transactional
  // CUSTOMER STATUS WRITE: owner customer + enquiry/complaint + latest staff reply required. Yes ->
  // Closed; No -> In Review or Reopened. Writes customer confirmation to chat/audit and sends
  // notifications.
  public Map<String, Object> satisfaction(Actor actor, int id, SatisfactionInput input) {
    if (!"CUSTOMER".equals(actor.role()))
      throw new ResponseStatusException(FORBIDDEN, "Only the customer can confirm satisfaction.");
    var item = one(actor, id);
    if (!Set.of("Question", "Complaint").contains(String.valueOf(item.get("type")))
        || !SupportCaseWorkflow.forStatus((String) item.get("status")).canConfirmSatisfaction())
      throw new ResponseStatusException(
          CONFLICT, "This request is not awaiting customer confirmation.");
    var latest =
        repo.query(
            "SELECT TOP 1 UPPER(u.type) AS role FROM chat c JOIN users u ON u.userID=c.userID WHERE"
                + " c.feedbackID=? ORDER BY c.chatID DESC",
            id);
    if (latest.isEmpty() || "CUSTOMER".equals(latest.getFirst().get("role")))
      throw new ResponseStatusException(
          CONFLICT, "Wait for a support reply before confirming satisfaction.");
    // The current State chooses the customer-confirmation outcome after the reply checks.
    String status =
        SupportCaseWorkflow.forStatus((String) item.get("status"))
            .satisfactionStatus(input.satisfied());
    // Require the database write to affect the expected row; reject stale updates.
    changed(
        repo.update(
            "UPDATE feedback SET caseStatus=?,version=version+1,updatedAt=SYSDATETIME() WHERE"
                + " feedbackID=? AND version=? AND deleted=0 AND caseStatus<>'Closed'",
            status,
            id,
            input.version()));
    String message =
        input.satisfied()
            ? "I am satisfied with the response. Please close my request."
            : "I am not satisfied yet and still need help.";
    repo.update(
        "INSERT INTO chat(message,userID,feedbackID) VALUES (?,?,?)", message, actor.id(), id);
    // Record who performed this action in the audit history.
    repo.audit(id, actor.id(), "Case updated", message);
    // Create a notification for the affected user or role.
    notifications.notifyRoles(
        Set.of("CSM", "CUSTOMER_SERVICE_MANAGER"),
        "SUPPORT",
        input.satisfied() ? "Customer closed a case" : "Customer needs more help",
        "Case #" + id + ": " + message,
        "/html/support/cases.html?caseId=" + id,
        "SUPPORT",
        id,
        null);
    Object assignee = item.get("assigneeId");
    if (assignee instanceof Number)
      // Create a notification for the affected user or role.
      notifications.notifyUser(
          ((Number) assignee).intValue(),
          "SUPPORT",
          "Customer confirmation",
          message,
          "/html/support/cases.html?caseId=" + id,
          "SUPPORT",
          id);
    // Return the updated request details so the browser can refresh its view.
    return detail(actor, id);
  }

  // READ: customer order options and eligible staff list for dropdowns. CSM gets assignment
  // choices; other staff do not get reassignment privileges.
  public Map<String, Object> options(Actor actor) {
    var orders =
        repo.query(
            "SELECT orderID AS id FROM orders WHERE userID=? ORDER BY orderID DESC", actor.id());
    var staff =
        actor.staff()
            ? repo.query(
                "SELECT userID AS id, CONCAT(firstName, ' ', lastName) AS name, UPPER(type) AS role"
                    + " FROM users WHERE active=1 AND UPPER(type) IN"
                    + " ('ADMIN','MANAGER','OWNER','CSM','CUSTOMER_SERVICE_MANAGER','STAFF','RIDER')"
                    + (actor.coordinator() ? "" : " AND userID=" + actor.id())
                    + " ORDER BY type, firstName")
            : List.of();
    return Map.of("actor", actor, "orders", orders, "staff", staff);
  }

  static boolean canView(Actor actor, Map<String, Object> item) {
    if (actor.seesAllCases()) return true;
    if ("CUSTOMER".equals(actor.role())) return number(item, "customerId") == actor.id();
    Object assignee = item.get("assigneeId");
    return actor.staff()
        && assignee instanceof Number
        && ((Number) assignee).intValue() == actor.id();
  }

  /**
   * Laundry processing issues ("Damaged item", "Missing item", ...) are cases raised by staff and
   * filed under the order's customer. The customer can view and reply to them, but not edit or
   * delete them.
   */
  static void submittedByCustomer(Map<String, Object> item, String action) {
    if (ProcessingIssueRepository.ISSUE_TYPES.contains(String.valueOf(item.get("type"))))
      throw new ResponseStatusException(
          FORBIDDEN, "This case was raised by staff and cannot be " + action + ".");
  }

  static int number(Map<String, Object> row, String key) {
    return ((Number) row.get(key)).intValue();
  }

  static void changed(int count) {
    if (count != 1)
      throw new ResponseStatusException(
          CONFLICT, "This record changed. Refresh it before trying again.");
  }
}
