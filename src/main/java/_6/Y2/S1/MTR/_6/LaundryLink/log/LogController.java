package _6.Y2.S1.MTR._6.LaundryLink.log;


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

// SecurityConfig decides which roles reach /api/logs. Which order a log belongs to is not
// in the URL for every route, so the "customers only see their own logs" rule is checked here.
@RestController
@RequestMapping("/api/logs")
public class LogController {
    private final LogService logService;
    private final LogAccess logAccess;

    public LogController(LogService logService, LogAccess logAccess) {
        this.logService = logService;
        this.logAccess = logAccess;
    }

    // A customer gets the logs of their own orders; the other allowed roles get every log.
    @GetMapping
    public List<Log> getAllLogs(Authentication authentication) {
        if (logAccess.isCustomer(authentication)) {
            return logService.getLogsOfCustomer(logAccess.userID(authentication));
        }
        return logService.getAllLogs();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Log> getLog(Authentication authentication, @PathVariable Integer id) {
        var log = logService.getLogById(id);
        // The order is only known once the log is loaded, so the check comes after the lookup.
        if (log.isPresent() && !logAccess.canViewLogsOfOrder(authentication, log.get().getOrderID())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only view the logs of your own orders.");
        }
        return log.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/order/{orderID}")
    public List<Log> getLogsByOrder(Authentication authentication, @PathVariable Integer orderID) {
        if (!logAccess.canViewLogsOfOrder(authentication, orderID)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only view the logs of your own orders.");
        }
        return logService.getLogsByOrder(orderID);
    }
}
