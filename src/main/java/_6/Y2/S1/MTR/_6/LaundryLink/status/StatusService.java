package _6.Y2.S1.MTR._6.LaundryLink.status;

import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class StatusService {
    private final StatusRepository repo;

    public StatusService(StatusRepository repo) {
        this.repo = repo;
    }

    public List<Status> getAllStatuses() {
        return repo.findAll();
    }

    public Status getById(Integer id) {
        return repo.findById(id).orElseThrow();
    }

    public Status getByLabel(String label) {
        return repo.findByStatusLabel(label).orElseThrow();
    }

    //TODO: Figure out if we can advance status depending on service and order
}