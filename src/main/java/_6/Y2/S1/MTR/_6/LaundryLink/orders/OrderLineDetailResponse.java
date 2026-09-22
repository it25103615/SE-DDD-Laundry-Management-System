package _6.Y2.S1.MTR._6.LaundryLink.orders;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OrderLineDetailResponse {
    private Integer orderLineID;
    private Integer itemID;
    private String itemName;
    private Integer serviceID;
    private String serviceName;
    private Integer quantity;
    private Double linePrice;
}
