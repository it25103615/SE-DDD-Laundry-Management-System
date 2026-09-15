package _6.Y2.S1.MTR._6.LaundryLink.logs;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/logs")
public class LogController {
    private final LogService logService;

    public LogController(LogService logService) {
        this.logService = logService;
    }

    @GetMapping
    public List<Log> getAllLogs() {
        return logService.getAllLogs();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Log> getLog(@PathVariable Integer id) {
        return logService.getLogById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/order/{orderID}")
    public List<Log> getLogsByOrder(@PathVariable Integer orderID) {
        return logService.getLogsByOrder(orderID);
    }

    @PostMapping
    public ResponseEntity<Log> createLog(@RequestParam Integer orderID,
                                         @RequestParam(required = false) Integer statusBeforeId,
                                         @RequestParam Integer statusAfterId) {
        Log created = logService.createLog(orderID, statusBeforeId, statusAfterId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
