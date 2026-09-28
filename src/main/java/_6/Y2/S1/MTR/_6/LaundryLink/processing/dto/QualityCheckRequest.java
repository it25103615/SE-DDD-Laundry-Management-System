package _6.Y2.S1.MTR._6.LaundryLink.processing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /api/processing/orders/{orderID}/quality-check.
 *
 * <p>A Failed check must name the stage to rework ({@code reworkStatusID}); a Passed check may
 * already be marked {@code packed}. Those cross-field rules are checked in ProcessingService,
 * because they depend on the order's route.
 */
public record QualityCheckRequest(
        @NotNull(message = "is required")
        @Pattern(regexp = "Passed|Failed", message = "must be Passed or Failed")
        String result,

        Integer reworkStatusID,

        Boolean packed,

        @Size(max = 250, message = "must be at most 250 characters")
        String notes) {
}
