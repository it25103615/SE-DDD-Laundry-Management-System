package _6.Y2.S1.MTR._6.LaundryLink.orders;

import _6.Y2.S1.MTR._6.LaundryLink.orders.dto.CreateOrderRequest;
import _6.Y2.S1.MTR._6.LaundryLink.orders.dto.CreateOrderResponse;
import _6.Y2.S1.MTR._6.LaundryLink.orders.dto.ModifyOrderRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CreateOrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createOrder(request));
    }

    @GetMapping("/customer/{userID}")
    public List<OrderSummaryResponse> getCustomerOrders(@PathVariable Integer userID) {
        return service.getCustomerOrders(userID);
    }

    @GetMapping("/customer/{userID}/{orderID}")
    public OrderDetailResponse getCustomerOrder(
            @PathVariable Integer userID,
            @PathVariable Integer orderID) {
        return service.getCustomerOrder(userID, orderID);
    }

    @PutMapping("/customer/{userID}/{orderID}/lines")
    public OrderDetailResponse modifyCustomerOrder(
            @PathVariable Integer userID,
            @PathVariable Integer orderID,
            @Valid @RequestBody ModifyOrderRequest request) {
        return service.modifyCustomerOrder(userID, orderID, request);
    }

    @GetMapping("/management")
    public List<ManagerOrderSummaryResponse> searchManagementOrders(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer statusID) {
        return service.searchManagementOrders(search, statusID);
    }

    @GetMapping("/management/{orderID}")
    public OrderDetailResponse getManagementOrder(@PathVariable Integer orderID) {
        return service.getManagementOrder(orderID);
    }

    @PutMapping("/management/{orderID}/lines")
    public OrderDetailResponse modifyManagementOrder(
            @PathVariable Integer orderID,
            @Valid @RequestBody ModifyOrderRequest request) {
        return service.modifyManagementOrder(orderID, request);
    }
}
