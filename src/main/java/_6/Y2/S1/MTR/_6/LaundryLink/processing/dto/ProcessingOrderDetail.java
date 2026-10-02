package _6.Y2.S1.MTR._6.LaundryLink.processing.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Everything the Order Processing page shows for one order (GET /api/processing/orders/{id}).
 *
 * <p>Besides the stored data it carries what the page is allowed to do next, worked out by
 * ProcessingService from the workflow rules, so the page never has to duplicate them:
 * {@code nextStatus} (the one stage a status change may move to, or null), {@code reworkOptions}
 * (stages a failed quality check may send the order back to) and {@code readyForDispatch}.
 */
public record ProcessingOrderDetail(
        int orderID,
        String customerName,
        int statusID,
        String statusLabel,
        String route,
        String deliveryInstruction,
        // The note the customer wrote when placing the order; null when there is none.
        String orderInstructions,
        // The preferences the customer ticked, as display labels; empty when there are none.
        List<String> orderPreferences,
        List<Line> lines,
        List<HistoryEntry> history,
        QualityCheck latestQualityCheck,
        List<IssueResponse> issues,
        StatusOption nextStatus,
        List<StatusOption> reworkOptions,
        boolean readyForDispatch) {

    /** One order line with what was ordered and, once received, what arrived. */
    public record Line(
            int orderLineID,
            String itemName,
            String serviceName,
            int quantity,
            Integer receivedQuantity,
            String itemCondition) {
    }

    /** One row of the order's status log. */
    public record HistoryEntry(int logID, String fromStatus, String toStatus, LocalDateTime changedAt) {
    }

    /** The order's most recent quality check. */
    public record QualityCheck(
            int checkID,
            String result,
            Integer reworkStatusID,
            String reworkStatusLabel,
            boolean packed,
            String notes,
            String checkedBy,
            LocalDateTime checkedAt) {
    }

    /** A status the page can offer as a button or option. */
    public record StatusOption(int statusID, String label) {
    }
}
