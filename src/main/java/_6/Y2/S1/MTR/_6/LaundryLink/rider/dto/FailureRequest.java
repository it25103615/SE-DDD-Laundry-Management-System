package _6.Y2.S1.MTR._6.LaundryLink.rider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FailureRequest(
        @NotBlank(message = "A failure note is required")
        @Size(max = 250, message = "Failure note cannot exceed 250 characters")
        String note
) {}
