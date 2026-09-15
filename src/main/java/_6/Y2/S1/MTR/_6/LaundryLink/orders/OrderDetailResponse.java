package _6.Y2.S1.MTR._6.LaundryLink.orders;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class OrderDetailResponse {
    private Integer orderID;
    private Integer userID;
    private Integer statusID;
    private String statusLabel;
    private List<OrderLineDetailResponse> orderLines;
    private Double orderTotal;
    private List<OrderHistoryResponse> history;
}
