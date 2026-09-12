package _6.Y2.S1.MTR._6.LaundryLink.logs;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LogRepository extends JpaRepository<Log, Integer> {
    List<Log> findByOrderID(Integer orderID);
}
