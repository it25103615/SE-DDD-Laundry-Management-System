package _6.Y2.S1.MTR._6.LaundryLink.controller.account;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@RestController
public class CustomerDashboardController {
    private final JdbcTemplate db;

    public CustomerDashboardController(JdbcTemplate db) { this.db = db; }

    @GetMapping("/api/customer/dashboard")
    public Map<String, Object> dashboard(Principal principal) {
        if (principal == null) throw new ResponseStatusException(UNAUTHORIZED, "Sign in to view your dashboard.");
        List<Map<String, Object>> accounts = db.queryForList("""
                SELECT userID AS id, CONCAT(firstName, ' ', lastName) AS name
                FROM users WHERE email=? AND UPPER(type)='CUSTOMER'
                """, principal.getName());
        if (accounts.isEmpty()) throw new ResponseStatusException(FORBIDDEN, "Customer account required.");
        int id = ((Number) accounts.getFirst().get("id")).intValue();
        List<Map<String, Object>> orders = db.queryForList("""
                SELECT TOP 1 o.orderID AS id, s.statusLabel AS status
                FROM orders o LEFT JOIN status s ON s.statusID=o.statusID
                WHERE o.userID=? ORDER BY o.orderID DESC
                """, id);
        Integer total = db.queryForObject("SELECT COUNT(*) FROM orders WHERE userID=?", Integer.class, id);
        Integer active = db.queryForObject("""
                SELECT COUNT(*) FROM orders o LEFT JOIN status s ON s.statusID=o.statusID
                WHERE o.userID=? AND (s.statusLabel IS NULL OR
                LOWER(s.statusLabel) NOT LIKE '%deliver%' AND LOWER(s.statusLabel) NOT LIKE '%complete%'
                AND LOWER(s.statusLabel) NOT LIKE '%cancel%')
                """, Integer.class, id);
        Integer open = db.queryForObject("SELECT COUNT(*) FROM feedback WHERE userID=? AND deleted=0 AND caseStatus NOT IN ('Closed','Resolved')", Integer.class, id);
        return Map.of("name", accounts.getFirst().get("name"), "orders", total, "activeOrders", active,
                "openSupport", open, "latestOrder", orders.isEmpty() ? Map.of() : orders.getFirst());
    }
}
