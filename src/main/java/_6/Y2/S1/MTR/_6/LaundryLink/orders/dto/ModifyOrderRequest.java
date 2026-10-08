package _6.Y2.S1.MTR._6.LaundryLink.orders.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class ModifyOrderRequest {
    @NotEmpty
    @Valid
    private List<CreateOrderLineRequest> orderLines;

    // Customer updates include these fields; management's line-only update ignores them.
    private LocalDateTime pickupScheduled;
    private Integer addressID;
    private String instructions;
    private List<String> preferences;
}
