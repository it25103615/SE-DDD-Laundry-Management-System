package _6.Y2.S1.MTR._6.LaundryLink.repository.shared;

import _6.Y2.S1.MTR._6.LaundryLink.entity.shared.Log;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LogRepository extends JpaRepository<Log, Integer> {
    List<Log> findByOrderID(Integer orderID);
}
