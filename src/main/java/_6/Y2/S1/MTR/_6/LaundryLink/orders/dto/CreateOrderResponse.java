package _6.Y2.S1.MTR._6.LaundryLink.orders.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class CreateOrderResponse {
    private Integer orderID;
    private Integer userID;
    private Integer statusID;
    private String statusLabel;
    private List<CreatedOrderLineResponse> orderLines;
}
