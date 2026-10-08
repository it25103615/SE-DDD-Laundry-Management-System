package _6.Y2.S1.MTR._6.LaundryLink.orders;

import _6.Y2.S1.MTR._6.LaundryLink.orders.dto.CreateOrderRequest;
import _6.Y2.S1.MTR._6.LaundryLink.orders.dto.CreateOrderResponse;
import _6.Y2.S1.MTR._6.LaundryLink.orders.dto.ModifyOrderRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService service;
    private final OrderAccess orderAccess;

    public OrderController(OrderService service, OrderAccess orderAccess) {
        this.service = service;
        this.orderAccess = orderAccess;
    }

    // The customer the order is for arrives in the request body, where the URL-based rules in
    // SecurityConfig cannot see it, so it is checked here: a customer may only place an order
    // for their own account.
    @PostMapping
    public ResponseEntity<CreateOrderResponse> createOrder(
            Authentication authentication,
            @Valid @RequestBody CreateOrderRequest request) {
        if (!orderAccess.canPlaceOrderFor(authentication, request.getUserID())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only place orders for your own account.");
        }
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

    @PostMapping("/customer/{userID}/{orderID}/cancel")
    public OrderDetailResponse cancelCustomerOrder(
            Authentication authentication,
            @PathVariable Integer userID,
            @PathVariable Integer orderID) {
        if (!orderAccess.isOwnAccount(authentication, userID)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only cancel your own orders.");
        }
        return service.cancelCustomerOrder(userID, orderID);
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
