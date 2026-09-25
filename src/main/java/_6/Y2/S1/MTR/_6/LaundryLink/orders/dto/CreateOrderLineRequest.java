package _6.Y2.S1.MTR._6.LaundryLink.orders.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateOrderLineRequest {
    @NotNull
    private Integer itemID;

    @NotNull
    private Integer serviceID;

    @NotNull
    @Positive
    private Integer quantity;
}
