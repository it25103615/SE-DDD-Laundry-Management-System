package _6.Y2.S1.MTR._6.LaundryLink.orders.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ModifyOrderRequest {
    @NotEmpty
    @Valid
    private List<CreateOrderLineRequest> orderLines;
}
