package _6.Y2.S1.MTR._6.LaundryLink.logs;

import _6.Y2.S1.MTR._6.LaundryLink.status.StatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class LogService {
    private final LogRepository logRepository;

    public List<Log> getAllLogs() {
        return logRepository.findAll();
    }

    public Optional<Log> getLogById(Integer id) {
        return logRepository.findById(id);
    }

    public List<Log> getLogsByOrder(Integer orderID) {
        return logRepository.findByOrderID(orderID);
    }

    public Log logChange(Log log) {
        return logRepository.save(log);
    }
    //TODO: change logChange to handle the change rather than depending on other classes to pass in a new log entity
}