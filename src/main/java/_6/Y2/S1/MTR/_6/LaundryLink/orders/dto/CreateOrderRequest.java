package _6.Y2.S1.MTR._6.LaundryLink.orders.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
}
