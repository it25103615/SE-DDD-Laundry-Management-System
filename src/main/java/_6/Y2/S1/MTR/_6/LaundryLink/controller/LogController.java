package _6.Y2.S1.MTR._6.LaundryLink.controller;

import _6.Y2.S1.MTR._6.LaundryLink.logs.Log;
import _6.Y2.S1.MTR._6.LaundryLink.service.LogService;
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
}
