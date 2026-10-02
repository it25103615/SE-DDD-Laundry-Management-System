package _6.Y2.S1.MTR._6.LaundryLink.orders.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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

    // The saved address the customer chose on the schedule step; stored in delivery.addressID.
    // Optional: when it is left out, the customer's default address is used. It must be one of
    // the customer's own addresses (OrderService checks this).
    @Positive
    private Integer addressID;

    // The note the customer wrote on the instructions step; stored in orders.instructions.
    // Optional. The limit matches the column and the textarea's maxlength.
    @Size(max = 500)
    private String instructions;

    // The preferences the customer ticked on the instructions step, as codes such as
    // "fragrance-free". Optional. Every code must be one listed in OrderPreference
    // (OrderService checks this); they are stored together in orders.preferences.
    private List<String> preferences;
}
