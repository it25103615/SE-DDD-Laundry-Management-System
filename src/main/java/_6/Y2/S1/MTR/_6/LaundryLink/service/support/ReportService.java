package _6.Y2.S1.MTR._6.LaundryLink.service.support;

import _6.Y2.S1.MTR._6.LaundryLink.repository.support.SupportRepository;
import _6.Y2.S1.MTR._6.LaundryLink.security.support.SupportAccess;

import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;
import _6.Y2.S1.MTR._6.LaundryLink.security.support.SupportAccess.Actor;

@Service
@Transactional(readOnly=true)
public class ReportService {
    private final SupportRepository repo;
    private final SupportAccess access;
    public ReportService(SupportRepository repo,SupportAccess access) { this.repo=repo; this.access=access; }
    private static final String COHORT="""
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
    public Map<String,Object> report(Actor actor,LocalDate from,LocalDate to,Integer serviceId) {
        access.manager(actor);
        if(from!=null && to!=null && from.isAfter(to)) throw new ResponseStatusException(BAD_REQUEST,"Start date must be on or before end date.");
        if(serviceId!=null && repo.count("SELECT COUNT(*) FROM services WHERE serviceID=?",serviceId)==0) throw new ResponseStatusException(BAD_REQUEST,"Unknown service.");
        Object[] args={from,from,to,to,serviceId,serviceId};
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("summary",repo.query(COHORT+"""
            SELECT COUNT(*) AS totalOrders,COUNT(DISTINCT userID) AS customers,
            COALESCE(SUM(orderValue),0) AS orderValue,COALESCE(SUM(recordedPayments),0) AS recordedPayments,
            COALESCE(SUM(CASE WHEN statusID IN(14,15) THEN 1 ELSE 0 END),0) AS deliveredOrders,
            COALESCE(SUM(CASE WHEN firstActivity IS NULL THEN 1 ELSE 0 END),0) AS undatedOrders
            FROM selected
            """,args).getFirst());
        result.put("statuses",repo.query(COHORT+"SELECT COALESCE(s.statusLabel,'Unknown') AS status,COUNT(*) AS orders FROM selected o LEFT JOIN status s ON s.statusID=o.statusID GROUP BY s.statusLabel ORDER BY COUNT(*) DESC",args));
        // A service filter selects whole orders; payment totals cannot be allocated to individual services.
        result.put("services",repo.query(COHORT+"""
            SELECT s.serviceName AS service,COUNT(DISTINCT l.orderID) AS orders,SUM(l.quantity) AS items,
                   SUM(CAST(l.linePrice AS DECIMAL(18,2))) AS value
            FROM selected o JOIN orderLines l ON l.orderID=o.orderID JOIN services s ON s.serviceID=l.serviceID
            GROUP BY s.serviceName ORDER BY value DESC
            """,args));
        result.put("customers",repo.query(COHORT+"""
            SELECT TOP 100 o.userID AS id,CONCAT(u.firstName,' ',u.lastName) AS customer,
                   COUNT(*) AS orders,SUM(o.orderValue) AS orderValue,SUM(o.recordedPayments) AS recordedPayments
            FROM selected o JOIN users u ON u.userID=o.userID
            GROUP BY o.userID,u.firstName,u.lastName ORDER BY recordedPayments DESC,o.userID
            """,args));
        result.put("support",repo.query("""
            SELECT COUNT(*) AS totalCases,
              COALESCE(SUM(CASE WHEN caseStatus NOT IN('Resolved','Closed') THEN 1 ELSE 0 END),0) AS openCases,
              AVG(CAST(rating AS DECIMAL(5,2))) AS averageRating,COUNT(rating) AS ratingCount
            FROM feedback WHERE deleted=0
            """).getFirst());
        result.put("serviceOptions",repo.query("SELECT serviceID AS id,serviceName AS name FROM services ORDER BY serviceName"));
        result.put("period",Map.of("from",from==null?"All time":from.toString(),"to",to==null?"All time":to.toString(),"serviceId",serviceId==null?"All services":serviceId.toString()));
        return result;
    }
    public List<Map<String,Object>> orders(Actor actor,Integer orderId,int page) {
        access.staff(actor);
        if(page<0) throw new ResponseStatusException(BAD_REQUEST,"Invalid page.");
        return repo.query("""
            SELECT o.orderID AS id,CONCAT(u.firstName,' ',u.lastName) AS customer,s.statusLabel AS status,
            (SELECT COALESCE(SUM(CAST(l.linePrice AS DECIMAL(18,2))),0) FROM orderLines l WHERE l.orderID=o.orderID) AS orderValue
            FROM orders o JOIN users u ON u.userID=o.userID LEFT JOIN status s ON s.statusID=o.statusID
            WHERE (? IS NULL OR o.orderID=?) ORDER BY o.orderID DESC OFFSET ? ROWS FETCH NEXT 25 ROWS ONLY
            """,orderId,orderId,(long)page*25);
    }
    public List<Map<String,Object>> orderHistory(Actor actor,int id) {
        access.staff(actor);
        if(repo.count("SELECT COUNT(*) FROM orders WHERE orderID=?",id)==0) throw new ResponseStatusException(NOT_FOUND,"Order not found.");
        return repo.query("""
            SELECT l.logID AS id,l.logDate,l.logTime,b.statusLabel AS beforeStatus,a.statusLabel AS afterStatus
            FROM logs l LEFT JOIN status b ON b.statusID=l.status_before LEFT JOIN status a ON a.statusID=l.status_after
            WHERE l.orderID=? ORDER BY l.logDate,l.logTime,l.logID
            """,id);
    }
}
