package _6.Y2.S1.MTR._6.LaundryLink.processing;

import _6.Y2.S1.MTR._6.LaundryLink.common.ApiException;
import _6.Y2.S1.MTR._6.LaundryLink.processing.dto.ProcessingOrderDetail.HistoryEntry;
import _6.Y2.S1.MTR._6.LaundryLink.processing.dto.ProcessingOrderSummary;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * All SQL for orders moving through processing: reading orders and their lines, saving received
 * counts and quality checks, and changing status through {@code dbo.sp_UpdateProcessingStatus}.
 *
 * <p>Uses JdbcTemplate because the tables involved belong
 * to several modules and the status change is a stored procedure call anyway.
 */
@Repository
public class ProcessingRepository {

    /**
     * Header data for one order: its status, customer and delivery instruction, plus what the
     * customer asked for when placing the order ({@code instructions} is their note,
     * {@code preferences} the ticked options as stored, e.g. "fragrance-free,hangers").
     */
    public record OrderHeader(
            int orderID,
            int statusID,
            String statusLabel,
            int customerID,
            String customerName,
            String deliveryInstruction,
            String instructions,
            String preferences) {
    }

    /** One order line joined with its item, service and (once received) counted quantity. */
    public record LineRow(
            int orderLineID,
            String itemName,
            int serviceID,
            String serviceName,
            int quantity,
            Integer receivedQuantity,
            String itemCondition) {
    }

    /** The newest quality check of an order. */
    public record CheckRow(
            int checkID,
            String result,
            Integer reworkStatusID,
            String reworkStatusLabel,
            boolean packed,
            String notes,
            String checkedBy,
            LocalDateTime checkedAt) {
    }

    // THROW numbers raised by dbo.sp_UpdateProcessingStatus (see initialize_database.sql).
    private static final int ORDER_NOT_FOUND = 51110;
    private static final int STATUS_NOT_FOUND = 51111;
    private static final int STATUS_UNCHANGED = 51112;

    private final JdbcTemplate db;

    public ProcessingRepository(JdbcTemplate db) {
        this.db = db;
    }

    /** Looks up the signed-in user by login email; inactive accounts are treated as missing. */
    public Optional<StaffMember> findActiveUserByEmail(String email) {
        return db.query("""
                SELECT userID, CONCAT(firstName, ' ', lastName) AS name, UPPER(type) AS role
                FROM users WHERE email = ? AND active = 1
                """,
                (rs, row) -> new StaffMember(rs.getInt("userID"), rs.getString("name"), rs.getString("role")),
                email).stream().findFirst();
    }

    /** statusID -> label for every status, so the page can show readable stage names. */
    public Map<Integer, String> findStatusLabels() {
        Map<Integer, String> labels = new HashMap<>();
        db.query("SELECT statusID, statusLabel FROM status",
                (ResultSet rs) -> { labels.put(rs.getInt("statusID"), rs.getString("statusLabel")); });
        return labels;
    }

    /**
     * Orders currently in processing (statuses 7-11, 19 and 21), optionally narrowed to one status.
     * Also works out each order's route, item count, open issues and when its status last changed.
     */
    public List<ProcessingOrderSummary> findOrdersInProcessing(Integer statusFilter) {
        // The route is picked in Java by ProcessingTransitions.routeFor, the same rule the order
        // page uses, so the rule is written in one place only. It needs each order's service names.
        Map<Integer, List<String>> services = findServiceNamesOfOrdersInProcessing();
        return db.query("""
                SELECT o.orderID,
                       CONCAT(u.firstName, ' ', u.lastName) AS customerName,
                       o.statusID,
                       s.statusLabel,
                       (SELECT COUNT(*) FROM orderLines l WHERE l.orderID = o.orderID) AS lineCount,
                       (SELECT COALESCE(SUM(l.quantity), 0) FROM orderLines l WHERE l.orderID = o.orderID) AS itemCount,
                       -- Issue cases (support cases with an issue type) not yet Resolved or Closed.
                       (SELECT COUNT(*) FROM feedback f
                         WHERE f.orderID = o.orderID AND f.deleted = 0
                           AND f.caseType IN (%s)
                           AND f.caseStatus NOT IN ('Resolved', 'Closed')) AS openIssues,
                       -- logs stores date and time separately; combine them into one timestamp.
                       (SELECT MAX(DATEADD(SECOND, DATEDIFF(SECOND, CAST('00:00:00' AS TIME), lg.logTime),
                                           CAST(lg.logDate AS DATETIME2)))
                          FROM logs lg WHERE lg.orderID = o.orderID) AS lastUpdated
                FROM orders o
                JOIN users u ON u.userID = o.userID
                JOIN status s ON s.statusID = o.statusID
                WHERE o.statusID IN (7, 8, 9, 10, 11, 19, 21)
                  AND (? IS NULL OR o.statusID = ?)
                ORDER BY o.orderID
                """.formatted(ProcessingIssueRepository.ISSUE_TYPES_SQL),  // %s: the issue case types
                (rs, row) -> new ProcessingOrderSummary(
                        rs.getInt("orderID"),
                        rs.getString("customerName"),
                        rs.getInt("statusID"),
                        rs.getString("statusLabel"),
                        // An order with no lines has no entry; routeFor then gives the default route.
                        ProcessingTransitions.routeFor(services.getOrDefault(rs.getInt("orderID"), List.of())).name(),
                        rs.getInt("lineCount"),
                        rs.getInt("itemCount"),
                        rs.getInt("openIssues"),
                        toDateTime(rs.getTimestamp("lastUpdated"))),
                statusFilter, statusFilter);
    }

    /** orderID -> the service names on its lines, for every order currently in processing. */
    private Map<Integer, List<String>> findServiceNamesOfOrdersInProcessing() {
        Map<Integer, List<String>> services = new HashMap<>();
        db.query("""
                SELECT ol.orderID, sv.serviceName
                FROM orderLines ol
                JOIN services sv ON sv.serviceID = ol.serviceID
                JOIN orders o ON o.orderID = ol.orderID
                WHERE o.statusID IN (7, 8, 9, 10, 11, 19, 21)
                """,
                (ResultSet rs) -> {
                    services.computeIfAbsent(rs.getInt("orderID"), id -> new ArrayList<>()).add(rs.getString("serviceName"));
                });
        return services;
    }

    /**
     * One order with its customer, the delivery instruction from the customer's default address,
     * and the note and preferences saved on the order itself.
     */
    public Optional<OrderHeader> findOrder(int orderID) {
        return db.query("""
                SELECT o.orderID, o.statusID, s.statusLabel, o.userID,
                       o.instructions, o.preferences,
                       CONCAT(u.firstName, ' ', u.lastName) AS customerName,
                       (SELECT TOP 1 a.DeliveryInstructions FROM addresses a
                         WHERE a.userID = o.userID AND a.isDefault = 1
                         ORDER BY a.addressID DESC) AS deliveryInstruction
                FROM orders o
                JOIN users u ON u.userID = o.userID
                JOIN status s ON s.statusID = o.statusID
                WHERE o.orderID = ?
                """,
                (rs, row) -> new OrderHeader(
                        rs.getInt("orderID"),
                        rs.getInt("statusID"),
                        rs.getString("statusLabel"),
                        rs.getInt("userID"),
                        rs.getString("customerName"),
                        rs.getString("deliveryInstruction"),
                        rs.getString("instructions"),
                        rs.getString("preferences")),
                orderID).stream().findFirst();
    }

    /** The order's lines, each with the quantity received so far (null until the order is received). */
    public List<LineRow> findLines(int orderID) {
        return db.query("""
                SELECT ol.orderLineID, i.itemName, ol.serviceID, sv.serviceName, ol.quantity,
                       r.receivedQuantity, r.itemCondition
                FROM orderLines ol
                JOIN items i ON i.itemID = ol.itemID
                JOIN services sv ON sv.serviceID = ol.serviceID
                LEFT JOIN receivedItems r ON r.orderLineID = ol.orderLineID
                WHERE ol.orderID = ?
                ORDER BY ol.orderLineID
                """,
                (rs, row) -> new LineRow(
                        rs.getInt("orderLineID"),
                        rs.getString("itemName"),
                        rs.getInt("serviceID"),
                        rs.getString("serviceName"),
                        rs.getInt("quantity"),
                        (Integer) rs.getObject("receivedQuantity"),
                        rs.getString("itemCondition")),
                orderID);
    }

    /**
     * The order's status history, oldest first. Sorted by logID (the order rows were written in)
     * rather than by date/time, so the sequence stays right even if the stored times come from
     * different clocks or time zones.
     */
    public List<HistoryEntry> findHistory(int orderID) {
        return db.query("""
                SELECT l.logID, b.statusLabel AS fromStatus, a.statusLabel AS toStatus, l.logDate, l.logTime
                FROM logs l
                LEFT JOIN status b ON b.statusID = l.status_before
                LEFT JOIN status a ON a.statusID = l.status_after
                WHERE l.orderID = ?
                ORDER BY l.logID
                """,
                (rs, row) -> {
                    LocalDate date = rs.getObject("logDate", LocalDate.class);
                    LocalTime time = rs.getObject("logTime", LocalTime.class);
                    LocalDateTime changedAt = date == null ? null : date.atTime(time == null ? LocalTime.MIDNIGHT : time);
                    return new HistoryEntry(rs.getInt("logID"), rs.getString("fromStatus"), rs.getString("toStatus"), changedAt);
                },
                orderID);
    }

    /** The order's newest quality check, if any (the newest row is the current one). */
    public Optional<CheckRow> findLatestQualityCheck(int orderID) {
        return db.query("""
                SELECT TOP 1 q.checkID, q.result, q.reworkStatusID, s.statusLabel AS reworkStatusLabel,
                       q.packed, q.notes, CONCAT(u.firstName, ' ', u.lastName) AS checkedBy, q.checkedAt
                FROM qualityChecks q
                LEFT JOIN status s ON s.statusID = q.reworkStatusID
                JOIN users u ON u.userID = q.checkedBy
                WHERE q.orderID = ?
                ORDER BY q.checkedAt DESC, q.checkID DESC
                """,
                (rs, row) -> new CheckRow(
                        rs.getInt("checkID"),
                        rs.getString("result"),
                        (Integer) rs.getObject("reworkStatusID"),
                        rs.getString("reworkStatusLabel"),
                        rs.getBoolean("packed"),
                        rs.getString("notes"),
                        rs.getString("checkedBy"),
                        toDateTime(rs.getTimestamp("checkedAt"))),
                orderID).stream().findFirst();
    }

    /** Stores (or, on a re-count, replaces) what was received for one order line. */
    public void saveReceivedItem(int orderLineID, int receivedQuantity, String itemCondition, int receivedBy) {
        db.update("""
                MERGE receivedItems AS target
                USING (SELECT ? AS orderLineID, ? AS receivedQuantity, ? AS itemCondition, ? AS receivedBy) AS source
                ON target.orderLineID = source.orderLineID
                WHEN MATCHED THEN UPDATE SET receivedQuantity = source.receivedQuantity,
                                             itemCondition = source.itemCondition,
                                             receivedBy = source.receivedBy,
                                             receivedAt = SYSDATETIME()
                WHEN NOT MATCHED THEN INSERT (orderLineID, receivedQuantity, itemCondition, receivedBy)
                                      VALUES (source.orderLineID, source.receivedQuantity, source.itemCondition, source.receivedBy);
                """,
                orderLineID, receivedQuantity, itemCondition, receivedBy);
    }

    /**
     * Changes the order's status through dbo.sp_UpdateProcessingStatus, which updates the order in
     * one transaction. That update fires the dbo.trg_order_status_log trigger, which writes the
     * log row (status before/after, date, time) in the same transaction. The procedure
     * returns one row, so it is called as a query. Its THROW numbers are turned into API errors.
     */
    public void updateStatus(int orderID, int newStatusID) {
        try {
            db.queryForMap("EXEC dbo.sp_UpdateProcessingStatus @OrderID = ?, @NewStatusID = ?", orderID, newStatusID);
        } catch (DataAccessException exception) {
            Integer code = procedureErrorCode(exception);
            if (code != null && code == ORDER_NOT_FOUND) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Order #" + orderID + " was not found.");
            }
            if (code != null && (code == STATUS_NOT_FOUND || code == STATUS_UNCHANGED)) {
                throw new ApiException(HttpStatus.CONFLICT, rootMessage(exception));
            }
            throw exception;
        }
    }

    /** Saves a quality check and returns its new ID. */
    public int insertQualityCheck(int orderID, String result, Integer reworkStatusID, boolean packed, String notes, int checkedBy) {
        return db.queryForObject("""
                INSERT INTO qualityChecks (orderID, result, reworkStatusID, packed, notes, checkedBy)
                OUTPUT INSERTED.checkID
                VALUES (?, ?, ?, ?, ?, ?)
                """, Integer.class, orderID, result, reworkStatusID, packed, notes, checkedBy);
    }

    /** Marks the order's newest quality check as packed, if that check passed. Returns rows changed. */
    public int markLatestPassedCheckPacked(int orderID) {
        return db.update("""
                UPDATE qualityChecks SET packed = 1
                WHERE checkID = (SELECT TOP 1 checkID FROM qualityChecks
                                  WHERE orderID = ? ORDER BY checkedAt DESC, checkID DESC)
                  AND result = 'Passed'
                """, orderID);
    }

    /** Finds the SQL Server error number (50000+ = raised by our own THROW) inside a Spring exception. */
    private static Integer procedureErrorCode(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getErrorCode() >= 50000) {
                return sql.getErrorCode();
            }
        }
        return null;
    }

    /** The message written by the procedure's THROW, without Spring's SQL prefix. */
    private static String rootMessage(Throwable exception) {
        Throwable root = exception;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage();
    }

    private static LocalDateTime toDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
