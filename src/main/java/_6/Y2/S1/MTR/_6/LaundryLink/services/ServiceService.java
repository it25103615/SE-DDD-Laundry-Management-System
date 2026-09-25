package _6.Y2.S1.MTR._6.LaundryLink.services;

import java.util.List;

@org.springframework.stereotype.Service
public class ServiceService {
    private final ServiceRepository repo;

    public ServiceService(ServiceRepository repo) {
        this.repo = repo;
    }

    public List<Service> getAllServices() {
        return repo.findAll();
    }
}
