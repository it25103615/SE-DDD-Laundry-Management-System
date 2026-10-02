package _6.Y2.S1.MTR._6.LaundryLink.rider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for the pickup-failed and delivery-failed endpoints.
 *
 * <p>The note is mandatory so staff can see why the attempt failed. The 250-character limit
 * matches {@code delivery.riderNotes VARCHAR(250)}; the database would reject longer text.
 *
 * @param note the failure reason; not blank, at most 250 characters
 */
public record FailureRequest(
        @NotBlank(message = "A failure note is required")
        @Size(max = 250, message = "Failure note cannot exceed 250 characters")
        String note
) {}
