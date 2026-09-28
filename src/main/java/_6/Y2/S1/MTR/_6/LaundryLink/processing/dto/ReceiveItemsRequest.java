package _6.Y2.S1.MTR._6.LaundryLink.processing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/**
 * Body of POST /api/processing/orders/{orderID}/receive: what staff counted for each order
 * line when the bags arrived.
 *
 * <p>Bean Validation rejects a quantity below 1 before any business logic runs
 * (the global ApiExceptionHandler turns it into a 400 with a message).
 */
public record ReceiveItemsRequest(
        @NotEmpty(message = "must list the received quantity for every item")
        @Valid
        List<Line> lines) {

    /** One counted order line. {@code condition} defaults to "As expected" when omitted. */
    public record Line(
            @NotNull(message = "is required")
            Integer orderLineID,

            @NotNull(message = "is required")
            @Min(value = 1, message = "must be at least 1")
            Integer receivedQuantity,

            @Pattern(regexp = "As expected|Stained|Damaged", message = "must be As expected, Stained or Damaged")
            String condition) {
    }
}
