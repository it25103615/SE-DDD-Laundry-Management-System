package _6.Y2.S1.MTR._6.LaundryLink.logs;

import _6.Y2.S1.MTR._6.LaundryLink.status.Status;
import _6.Y2.S1.MTR._6.LaundryLink.status.StatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LogService {
    private final LogRepository logRepository;
    private final StatusRepository statusRepository;

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

    public Log createLog(Integer oID, Integer statBefore, Integer statAfter) {
        Status before = statBefore != null
                ? statusRepository.findById(statBefore).orElse(null)
                : null;

        Status after = statusRepository.findById(statAfter)
                .orElseThrow(() -> new IllegalArgumentException("statusAfterId not found: " + statAfter));

        Log log = new Log();
        log.setStatusBefore(before);
        log.setStatusAfter(after);
        log.setOrderID(oID);
        log.setLogDate(LocalDate.now());
        log.setLogTime(LocalTime.now());

        return logRepository.save(log);
    }
}