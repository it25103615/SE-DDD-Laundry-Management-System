package _6.Y2.S1.MTR._6.LaundryLink.orders.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class CreateOrderRequest {
    @NotNull
    private Integer userID;

    @NotEmpty
    @Valid
    private List<CreateOrderLineRequest> orderLines;

    // Start of the pickup window the customer chose on the schedule step. Optional so callers
    // that do not schedule a pickup still work; it is stored in delivery.pickup_scheduled.
    @FutureOrPresent
    private LocalDateTime pickupScheduled;
}
