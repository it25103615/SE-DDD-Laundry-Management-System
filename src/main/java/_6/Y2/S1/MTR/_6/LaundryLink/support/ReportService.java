/* ReportService
 * MAJOR COMPONENT REPORTING: reads live database aggregates for selected order cohort, services, payments, customers and alerts. report() returns JSON; reports.js renders totals/charts/CSV. Date filtering uses first order activity, and recorded payments are lifetime totals for those selected orders.
 */
package _6.Y2.S1.MTR._6.LaundryLink.support;

import static org.springframework.http.HttpStatus.*;

import _6.Y2.S1.MTR._6.LaundryLink.support.SupportAccess.Actor;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
// Run database reads in a read-only transaction.
@Transactional(readOnly = true)
public class ReportService {

  private final SupportRepository repo;
  private final SupportAccess access;

  public ReportService(SupportRepository repo, SupportAccess access) {
    this.repo = repo;
    this.access = access;
  }

  private static final String COHORT = """
  WITH activity AS (SELECT orderID, MIN(logDate) AS firstActivity FROM logs GROUP BY orderID),
  lineTotals AS (SELECT orderID, SUM(CAST(linePrice AS DECIMAL(18,2))) AS value FROM orderLines GROUP BY orderID),
  paid AS (SELECT orderID, SUM(CAST(amount AS DECIMAL(18,2))) AS amount FROM payments GROUP BY orderID),
  selected AS (
    SELECT o.orderID,o.userID,o.statusID,a.firstActivity,
           COALESCE(l.value,0) AS orderValue,COALESCE(p.amount,0) AS recordedPayments
    FROM orders o LEFT JOIN activity a ON a.orderID=o.orderID
    LEFT JOIN lineTotals l ON l.orderID=o.orderID LEFT JOIN paid p ON p.orderID=o.orderID
    WHERE (? IS NULL OR a.firstActivity>=CAST(? AS DATE))
      AND (? IS NULL OR a.firstActivity<=CAST(? AS DATE))
      AND (? IS NULL OR EXISTS(SELECT 1 FROM orderLines x WHERE x.orderID=o.orderID AND x.serviceID=?))
  )
  """;

  public Map<String, Object> report(Actor actor, LocalDate from, LocalDate to, Integer serviceId) {
    access.manager(actor);
    if (from != null && from.isAfter(LocalDate.now(java.time.ZoneId.of("Asia/Colombo"))))
      throw new ResponseStatusException(BAD_REQUEST, "Start date cannot be in the future.");
    if (from != null && to != null && from.isAfter(to)) throw new ResponseStatusException(
      BAD_REQUEST,
      "Start date must be on or before end date."
    );
    if (
      serviceId != null &&
      repo.count("SELECT COUNT(*) FROM services WHERE serviceID=?", serviceId) == 0
    ) throw new ResponseStatusException(BAD_REQUEST, "Unknown service.");
    Object[] args = { from, from, to, to, serviceId, serviceId };
    Map<String, Object> result = new LinkedHashMap<>();
    result.put(
      "summary",
      repo
        .query(
          COHORT +
            """
            SELECT COUNT(*) AS totalOrders,COUNT(DISTINCT userID) AS customers,
            COALESCE(SUM(orderValue),0) AS orderValue,COALESCE(SUM(recordedPayments),0) AS recordedPayments,
            COALESCE(SUM(CASE WHEN statusID IN(14,15) THEN 1 ELSE 0 END),0) AS deliveredOrders,
            COALESCE(SUM(CASE WHEN firstActivity IS NULL THEN 1 ELSE 0 END),0) AS undatedOrders
            FROM selected
            """,
          args
        )
        .getFirst()
    );
    result.put(
      "statuses",
      repo.query(
        COHORT +
          "SELECT COALESCE(s.statusLabel,'Unknown') AS status,COUNT(*) AS orders FROM" +
          " selected o LEFT JOIN status s ON s.statusID=o.statusID GROUP BY s.statusLabel" +
          " ORDER BY COUNT(*) DESC",
        args
      )
    );
    // A service filter selects whole orders; payment totals cannot be allocated to individual
    // services.
    result.put(
      "services",
      repo.query(
        COHORT +
          """
          SELECT s.serviceName AS service,COUNT(DISTINCT l.orderID) AS orders,SUM(l.quantity) AS items,
                 SUM(CAST(l.linePrice AS DECIMAL(18,2))) AS value
          FROM selected o JOIN orderLines l ON l.orderID=o.orderID JOIN services s ON s.serviceID=l.serviceID
          GROUP BY s.serviceName ORDER BY value DESC
          """,
        args
      )
    );
    result.put(
      "customers",
      repo.query(
        COHORT +
          """
          SELECT TOP 100 o.userID AS id,CONCAT(u.firstName,' ',u.lastName) AS customer,
                 COUNT(*) AS orders,SUM(o.orderValue) AS orderValue,SUM(o.recordedPayments) AS recordedPayments
          FROM selected o JOIN users u ON u.userID=o.userID
          GROUP BY o.userID,u.firstName,u.lastName ORDER BY recordedPayments DESC,o.userID
          """,
        args
      )
    );
    result.put(
      "support",
      repo
        .query(
          """
          SELECT COALESCE(SUM(CASE WHEN caseType<>'Feedback' THEN 1 ELSE 0 END),0) AS totalCases,
            COALESCE(SUM(CASE WHEN caseType<>'Feedback' AND caseStatus NOT IN('Resolved','Closed') THEN 1 ELSE 0 END),0) AS openCases,
            AVG(CAST(rating AS DECIMAL(5,2))) AS averageRating,COUNT(rating) AS ratingCount
          FROM feedback WHERE deleted=0
          """
        )
        .getFirst()
    );
    result.put(
      "alerts",
      repo.query(
        """
        SELECT 'High-priority support cases' AS label,COUNT(*) AS total
        FROM feedback WHERE deleted=0 AND caseType<>'Feedback' AND priority='High' AND caseStatus NOT IN('Resolved','Closed')
        UNION ALL
        SELECT 'Unassigned support cases',COUNT(*)
        FROM feedback WHERE deleted=0 AND caseType<>'Feedback' AND assigneeID IS NULL AND caseStatus NOT IN('Resolved','Closed')
        UNION ALL
        SELECT 'Overdue pickups',COUNT(*)
        FROM delivery WHERE pickup_scheduled<SYSDATETIME() AND pickup_actual IS NULL
        UNION ALL
        SELECT 'Orders without timeline activity',COUNT(*)
        FROM orders o WHERE NOT EXISTS(SELECT 1 FROM logs l WHERE l.orderID=o.orderID)
        """
      )
    );
    result.put(
      "serviceOptions",
      repo.query("SELECT serviceID AS id,serviceName AS name FROM services ORDER BY serviceName")
    );
    result.put(
      "period",
      Map.of(
        "from",
        from == null ? "All time" : from.toString(),
        "to",
        to == null ? "All time" : to.toString(),
        "serviceId",
        serviceId == null ? "All services" : serviceId.toString()
      )
    );
    return result;
  }

  // Order lookup. Every filter is optional: passing null switches that condition off
  // ("? IS NULL OR ..."), so the filters can be combined freely.
  // placedOn is the day the order was made: the date of its earliest log row, which is the
  // creation row dbo.trg_order_status_log writes for every new order (migration 016). It is
  // returned as text (style 23 = yyyy-mm-dd) so the page gets the same format from any driver.
  // Both dates are inclusive: "from" alone finds orders made on or after that day, "to" alone
  // orders made on or before it, and the two together a range.
  // "customer" matches any part of the customer's full name, ignoring case; blank counts as not set.
  public List<Map<String, Object>> orders(
    Actor actor,
    Integer orderId,
    String customer,
    Integer statusId,
    LocalDate from,
    LocalDate to,
    int page
  ) {
    access.staff(actor);
    String name = customer == null || customer.isBlank() ? null : customer.trim();
    if (page < 0) throw new ResponseStatusException(BAD_REQUEST, "Invalid page.");
    if (from != null && to != null && from.isAfter(to)) throw new ResponseStatusException(
      BAD_REQUEST,
      "Start date must be on or before end date."
    );
    return repo.query(
      """
      SELECT o.orderID AS id,CONCAT(u.firstName,' ',u.lastName) AS customer,s.statusLabel AS status,
      CONVERT(CHAR(10),placed.placedOn,23) AS placedOn,
      (SELECT COALESCE(SUM(CAST(l.linePrice AS DECIMAL(18,2))),0) FROM orderLines l WHERE l.orderID=o.orderID) AS orderValue
      FROM orders o JOIN users u ON u.userID=o.userID LEFT JOIN status s ON s.statusID=o.statusID
      OUTER APPLY (SELECT MIN(g.logDate) AS placedOn FROM logs g WHERE g.orderID=o.orderID) placed
      WHERE (? IS NULL OR o.orderID=?)
        AND (? IS NULL OR LOWER(CONCAT(u.firstName,' ',u.lastName)) LIKE LOWER(CONCAT('%',?,'%')))
        AND (? IS NULL OR o.statusID=?)
        AND (? IS NULL OR placed.placedOn>=CAST(? AS DATE))
        AND (? IS NULL OR placed.placedOn<=CAST(? AS DATE))
      ORDER BY o.orderID DESC OFFSET ? ROWS FETCH NEXT 25 ROWS ONLY
      """,
      orderId,
      orderId,
      name,
      name,
      statusId,
      statusId,
      from,
      from,
      to,
      to,
      (long) page * 25
    );
  }

  public List<Map<String, Object>> orderHistory(Actor actor, int id) {
    access.staff(actor);
    if (
      repo.count("SELECT COUNT(*) FROM orders WHERE orderID=?", id) == 0
    ) throw new ResponseStatusException(NOT_FOUND, "Order not found.");
    // The creation row (status_before IS NULL) is listed first. Migration 016 added it to older
    // orders afterwards, with the same date and time as their first status change and a higher
    // logID, so sorting on date, time and logID alone would put it second.
    return repo.query(
      """
      SELECT l.logID AS id,l.logDate,l.logTime,b.statusLabel AS beforeStatus,a.statusLabel AS afterStatus
      FROM logs l LEFT JOIN status b ON b.statusID=l.status_before LEFT JOIN status a ON a.statusID=l.status_after
      WHERE l.orderID=? ORDER BY CASE WHEN l.status_before IS NULL THEN 0 ELSE 1 END,l.logDate,l.logTime,l.logID
      """,
      id
    );
  }
}
