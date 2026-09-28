package _6.Y2.S1.MTR._6.LaundryLink.processing.dto;

import java.time.LocalDateTime;

/**
 * One order on the processing board or the staff dashboard (GET /api/processing/orders).
 * {@code lastUpdated} is the time of the order's latest status log, or null when it has none.
 */
public record ProcessingOrderSummary(
        int orderID,
        String customerName,
        int statusID,
        String statusLabel,
        String route,
        int lineCount,
        int itemCount,
        int openIssues,
        LocalDateTime lastUpdated) {
}
