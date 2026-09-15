package _6.Y2.S1.MTR._6.LaundryLink.orders.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CreatedOrderLineResponse {
    private Integer orderLineID;
    private Integer itemID;
    private Integer serviceID;
    private Integer quantity;
    private Double linePrice;
}
