package _6.Y2.S1.MTR._6.LaundryLink.rider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// WHY: Request body for PUT /tasks/{id}/pickup-failed and
//      /delivery-failed — a rider must always give a reason when
//      reporting a failed attempt.
// HOW: @NotBlank/@Size are enforced automatically by @Valid on the
//      controller methods that accept this type, before RiderService
//      ever sees the request.
public record FailureRequest(
        @NotBlank(message = "A failure note is required")
        @Size(max = 250, message = "Failure note cannot exceed 250 characters")
        String note
) {}
