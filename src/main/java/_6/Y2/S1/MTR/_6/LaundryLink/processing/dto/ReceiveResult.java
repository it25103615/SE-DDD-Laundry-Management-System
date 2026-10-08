package _6.Y2.S1.MTR._6.LaundryLink.processing.dto;

import java.util.List;

/**
 * Result of receiving items. When the counts match, {@code accepted} is true and the order has
 * moved to Verifying Items. When they don't, nothing was saved and
 * {@code mismatches} lists every line whose count differs from the order.
 */
public record ReceiveResult(
        boolean accepted,
        Integer statusID,
        String statusLabel,
        String message,
        List<Mismatch> mismatches) {

    /** One order line whose received count differs from the ordered quantity. */
    public record Mismatch(int orderLineID, String item, int ordered, int received) {
    }
}
