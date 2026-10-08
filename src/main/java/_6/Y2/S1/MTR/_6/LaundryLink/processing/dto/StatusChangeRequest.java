package _6.Y2.S1.MTR._6.LaundryLink.processing.dto;

import jakarta.validation.constraints.NotNull;

/** Body of PUT /api/processing/orders/{orderID}/status: the stage to move the order to. */
public record StatusChangeRequest(@NotNull(message = "is required") Integer statusID) {
}
