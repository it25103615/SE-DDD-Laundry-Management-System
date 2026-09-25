package _6.Y2.S1.MTR._6.LaundryLink.orders;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OrderSummaryResponse {
    private Integer orderID;
    private Integer statusID;
    private String statusLabel;
    private Double orderTotal;
}
