package _6.Y2.S1.MTR._6.LaundryLink.service.support;

import _6.Y2.S1.MTR._6.LaundryLink.repository.support.SupportRepository;
import _6.Y2.S1.MTR._6.LaundryLink.security.support.SupportAccess;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;
import static _6.Y2.S1.MTR._6.LaundryLink.dto.support.SupportRequests.*;
import _6.Y2.S1.MTR._6.LaundryLink.security.support.SupportAccess.Actor;

@Service
@Transactional(readOnly = true)
public class SupportService {
    private final SupportRepository repo;
    private final SupportAccess access;
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
        "New", Set.of("Assigned", "In Review"),
        "Assigned", Set.of("In Review"),
        "In Review", Set.of("Resolved"),
        "Resolved", Set.of("Closed", "Reopened"),
        "Closed", Set.of("Reopened"),
        "Reopened", Set.of("Assigned", "In Review"));
    public SupportService(SupportRepository repo, SupportAccess access) { this.repo = repo; this.access = access; }

    public List<Map<String, Object>> cases(Actor actor, String search, String status, String type, int page) {
        if (page < 0) throw new ResponseStatusException(BAD_REQUEST, "Invalid page.");
        String sql = SupportRepository.CASE_SELECT;
        List<Object> args = new ArrayList<>();
        if (!actor.staff()) { sql += " AND f.userID=?"; args.add(actor.id()); }
        if (search != null && !search.isBlank()) {
            if (search.length() > 100) throw new ResponseStatusException(BAD_REQUEST, "Search must be at most 100 characters.");
            sql += " AND (f.subject LIKE ? OR f.feedback LIKE ? OR CONCAT(u.firstName, ' ', u.lastName) LIKE ?)";
            String term = "%" + search.replace("[", "[[]").replace("%", "[%]").replace("_", "[_]") + "%";
            args.addAll(List.of(term, term, term));
        }
        if (status != null && !status.isBlank()) { sql += " AND f.caseStatus=?"; args.add(status); }
        if (type != null && !type.isBlank()) { sql += " AND f.caseType=?"; args.add(type); }
        sql += " ORDER BY f.updatedAt DESC, f.feedbackID DESC OFFSET ? ROWS FETCH NEXT 25 ROWS ONLY";
        args.add((long) page * 25);
        return repo.query(sql, args.toArray());
    }
    public Map<String, Object> one(Actor actor, int id) {
        var rows = repo.query(SupportRepository.CASE_SELECT + " AND f.feedbackID=?", id);
        if (rows.isEmpty()) throw new ResponseStatusException(NOT_FOUND, "Case not found.");
        var item = rows.getFirst();
        if (!actor.staff() && number(item, "customerId") != actor.id()) throw new ResponseStatusException(NOT_FOUND, "Case not found.");
        return item;
    }
    public Map<String, Object> detail(Actor actor, int id) {
        var item = new LinkedHashMap<>(one(actor, id));
        item.put("history", repo.query("""
            SELECT a.activityID AS id, a.action, a.details, a.createdAt, CONCAT(u.firstName, ' ', u.lastName) AS actor
            FROM support_activity a JOIN users u ON u.userID=a.actorID
            WHERE a.feedbackID=? ORDER BY a.createdAt, a.activityID
            """, id));
        item.put("messages", repo.query("""
            SELECT c.chatID AS id, c.message, c.sentAt, CONCAT(u.firstName, ' ', u.lastName) AS author
            FROM chat c JOIN users u ON u.userID=c.userID WHERE c.feedbackID=? ORDER BY c.sentAt, c.chatID
            """, id));
        return item;
    }
    private void validateCase(Actor actor, CaseInput input) {
        if (!"CUSTOMER".equals(actor.role())) throw new ResponseStatusException(FORBIDDEN, "Use a customer account to submit feedback.");
        if ("Feedback".equals(input.type()) && input.rating() == null) throw new ResponseStatusException(BAD_REQUEST, "Choose a rating from 1 to 5 for feedback.");
        if (!"Feedback".equals(input.type()) && input.rating() != null) throw new ResponseStatusException(BAD_REQUEST, "Ratings apply only to feedback.");
        if (input.orderId() != null && repo.count("SELECT COUNT(*) FROM orders WHERE orderID=? AND userID=?", input.orderId(), actor.id()) != 1)
            throw new ResponseStatusException(BAD_REQUEST, "Select an order belonging to your account.");
    }
    @Transactional
    public Map<String, Object> create(Actor actor, CaseInput input) {
        validateCase(actor, input);
        int id = repo.insert("""
            INSERT INTO feedback(feedback, userID, orderID, caseType, subject, rating)
            OUTPUT INSERTED.feedbackID VALUES (?, ?, ?, ?, ?, ?)
            """, input.message().trim(), actor.id(), input.orderId(), input.type(), input.subject().trim(), input.rating());
        repo.audit(id, actor.id(), "Created", input.type() + " submitted");
        return detail(actor, id);
    }
    @Transactional
    public Map<String, Object> edit(Actor actor, int id, CaseInput input) {
        var item = one(actor, id);
        validateCase(actor, input);
        if (!"New".equals(item.get("status"))) throw new ResponseStatusException(CONFLICT, "Only new cases can be edited.");
        changed(repo.update("""
            UPDATE feedback SET feedback=?, orderID=?, caseType=?, subject=?, rating=?, updatedAt=SYSDATETIME(), version=version+1
            WHERE feedbackID=? AND deleted=0 AND caseStatus='New' AND version=?
            """, input.message().trim(), input.orderId(), input.type(), input.subject().trim(), input.rating(), id, input.version()));
        repo.audit(id, actor.id(), "Edited", "Customer updated the case details");
        return detail(actor, id);
    }
    @Transactional
    public void delete(Actor actor, int id, int version) {
        var item = one(actor, id);
        if (!"New".equals(item.get("status"))) throw new ResponseStatusException(CONFLICT, "Only new cases can be deleted; handled cases retain their history.");
        changed(repo.update("UPDATE feedback SET deleted=1, version=version+1, updatedAt=SYSDATETIME() WHERE feedbackID=? AND version=? AND deleted=0 AND caseStatus='New'", id, version));
        repo.audit(id, actor.id(), "Deleted", "Case removed from active views; history retained");
    }
    static boolean allowed(String before, String after) { return before.equals(after) || TRANSITIONS.getOrDefault(before, Set.of()).contains(after); }
    @Transactional
    public Map<String, Object> handle(Actor actor, int id, CaseUpdate input) {
        access.staff(actor);
        var item = one(actor, id);
        if (!allowed((String) item.get("status"), input.status())) throw new ResponseStatusException(CONFLICT, "Invalid case transition. Assign or review before resolving; resolve before closing.");
        if (!"New".equals(input.status()) && input.assigneeId() == null) throw new ResponseStatusException(BAD_REQUEST, "Assign a staff member before progressing the case.");
        if (input.assigneeId() != null && repo.count("SELECT COUNT(*) FROM users WHERE userID=? AND UPPER(type) IN ('ADMIN','MANAGER','OWNER','CSM','CUSTOMER_SERVICE_MANAGER')", input.assigneeId()) != 1)
            throw new ResponseStatusException(BAD_REQUEST, "Assignee must be support staff or a manager.");
        changed(repo.update("""
            UPDATE feedback SET caseStatus=?, priority=?, assigneeID=?, version=version+1, updatedAt=SYSDATETIME()
            WHERE feedbackID=? AND version=? AND deleted=0
            """, input.status(), input.priority(), input.assigneeId(), id, input.version()));
        repo.audit(id, actor.id(), "Case updated", item.get("status") + " -> " + input.status() + "; " + input.priority() + "; assignee " + input.assigneeId() + ". " + input.note().trim());
        return detail(actor, id);
    }
    @Transactional
    public Map<String, Object> message(Actor actor, int id, MessageInput input) {
        var item = one(actor, id);
        if ("Closed".equals(item.get("status"))) throw new ResponseStatusException(CONFLICT, "Reopen the case before adding a message.");
        // Updating the case first serializes messages against closure/deletion.
        changed(repo.update("UPDATE feedback SET updatedAt=SYSDATETIME(), version=version+1 WHERE feedbackID=? AND deleted=0 AND version=? AND caseStatus<>'Closed'", id, item.get("version")));
        repo.update("INSERT INTO chat(message,userID,feedbackID) VALUES (?,?,?)", input.message().trim(), actor.id(), id);
        repo.audit(id, actor.id(), "Message added", "Communication recorded");
        return detail(actor, id);
    }
    public Map<String, Object> options(Actor actor) {
        var orders = repo.query("SELECT orderID AS id FROM orders WHERE userID=? ORDER BY orderID DESC", actor.id());
        var staff = actor.staff() ? repo.query("SELECT userID AS id, CONCAT(firstName, ' ', lastName) AS name FROM users WHERE UPPER(type) IN ('ADMIN','MANAGER','OWNER','CSM','CUSTOMER_SERVICE_MANAGER') ORDER BY firstName") : List.of();
        return Map.of("actor", actor, "orders", orders, "staff", staff);
    }
    static int number(Map<String, Object> row, String key) { return ((Number) row.get(key)).intValue(); }
    static void changed(int count) { if (count != 1) throw new ResponseStatusException(CONFLICT, "This record changed. Refresh it before trying again."); }
}
