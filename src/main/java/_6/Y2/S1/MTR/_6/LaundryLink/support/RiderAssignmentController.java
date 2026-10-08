package _6.Y2.S1.MTR._6.LaundryLink.support;

import java.security.Principal;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/support/rider-assignments")
public class RiderAssignmentController {
    private final SupportAccess access;
    private final RiderAssignmentService assignments;
    public RiderAssignmentController(SupportAccess access, RiderAssignmentService assignments) {
        this.access=access; this.assignments=assignments;
    }
    public record Assignment(@NotNull @Positive Integer riderId) {}
    @GetMapping public Map<String,Object> list(Principal principal) {
        access.manager(access.actor(principal,null)); return assignments.list();
    }
    @PutMapping("/{id}") public Map<String,String> assign(Principal principal,@PathVariable int id,@Valid @RequestBody Assignment input) {
        access.manager(access.actor(principal,null)); assignments.assign(id,input.riderId());
        return Map.of("message","Rider assigned. The task is now in the rider's work queue.");
    }
}

@Service
class RiderAssignmentService {
    private final JdbcTemplate db;
    RiderAssignmentService(JdbcTemplate db) { this.db=db; }
    @Transactional(readOnly=true)
    public Map<String,Object> list() {
        var tasks=db.queryForList("""
            SELECT d.deliverID AS id,o.orderID,
                CASE WHEN o.statusID=3 THEN 'Pickup' ELSE 'Delivery' END AS taskType,
                CASE WHEN o.statusID=3 THEN d.pickup_scheduled ELSE awaiting.changedAt END AS scheduled,
                COALESCE(a.city,'Address unavailable') AS area
            FROM delivery d JOIN orders o ON o.orderID=d.orderID
            OUTER APPLY (SELECT MAX(DATEADD(SECOND,DATEDIFF(SECOND,CAST('00:00:00' AS TIME),logTime),CAST(logDate AS DATETIME2))) AS changedAt FROM logs WHERE orderID=o.orderID AND status_after=12) awaiting
            OUTER APPLY (SELECT TOP 1 city FROM addresses
                WHERE addressID=d.addressID OR (d.addressID IS NULL AND userID=o.userID AND isDefault=1)
                ORDER BY addressID DESC) a
            WHERE (o.statusID=3 AND d.pickup_riderID IS NULL)
                OR (o.statusID=12 AND d.delivery_riderID IS NULL)
            ORDER BY o.orderID,d.deliverID
            """);
        var riders=db.queryForList("SELECT userID AS id,CONCAT(firstName,' ',lastName) AS name FROM users WHERE active=1 AND UPPER(type)='RIDER' ORDER BY firstName,lastName,userID");
        return Map.of("tasks",tasks,"riders",riders);
    }
    @Transactional
    public void assign(int id,int riderId) {
        var riders=db.queryForList("SELECT userID FROM users WITH (UPDLOCK,HOLDLOCK) WHERE userID=? AND active=1 AND UPPER(type)='RIDER'",riderId);
        if(riders.isEmpty()) throw new ResponseStatusException(BAD_REQUEST,"Select an active rider.");
        var tasks=db.queryForList("""
            SELECT o.orderID,o.statusID,d.pickup_riderID,d.delivery_riderID
            FROM orders o WITH (UPDLOCK,HOLDLOCK) JOIN delivery d WITH (UPDLOCK,HOLDLOCK) ON d.orderID=o.orderID
            WHERE d.deliverID=?
            """,id);
        if(tasks.isEmpty()) throw new ResponseStatusException(NOT_FOUND,"Task not found.");
        var task=tasks.getFirst(); int status=((Number)task.get("statusID")).intValue();
        if((status!=3 && status!=12) || task.get(status==3?"pickup_riderID":"delivery_riderID")!=null)
            throw new ResponseStatusException(CONFLICT,"This task has already been assigned or is no longer awaiting a rider. Refresh the list.");
        String column=status==3?"pickup_riderID":"delivery_riderID";
        int changed=db.update("UPDATE delivery SET "+column+"=? WHERE deliverID=? AND "+column+" IS NULL",riderId,id);
        if(changed!=1) throw new ResponseStatusException(CONFLICT,"The assignment changed. Refresh the list.");
        changed=db.update("UPDATE orders SET statusID=? WHERE orderID=? AND statusID=?",status==3?4:13,task.get("orderID"),status);
        if(changed!=1) throw new ResponseStatusException(CONFLICT,"The order changed. Refresh the list.");
    }
}
