package _6.Y2.S1.MTR._6.LaundryLink.status;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface StatusRepository extends JpaRepository<Status, Integer> {
    Optional<Status> findByStatusLabel(String statusLabel);
}