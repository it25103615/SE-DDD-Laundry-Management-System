package _6.Y2.S1.MTR._6.LaundryLink.status;


import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/status")
public class StatusController {
    private final StatusService service;
    public StatusController(StatusService service) { this.service = service; }

    @GetMapping
    public List<Status> getAll() {
        return service.getAllStatuses();
    }

    @GetMapping("/{id}")
    public Status getOne(@PathVariable Integer id) {
        return service.getById(id);
    }

    @GetMapping("/label/{label}")
    public Status getByLabel(@PathVariable String label) {
        return service.getByLabel(label);
    }
}