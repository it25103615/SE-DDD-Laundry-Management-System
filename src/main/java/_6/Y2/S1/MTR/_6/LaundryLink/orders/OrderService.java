package _6.Y2.S1.MTR._6.LaundryLink.orders;

import _6.Y2.S1.MTR._6.LaundryLink.orderlines.OrderLine;
import _6.Y2.S1.MTR._6.LaundryLink.orderlines.OrderLineService;
import _6.Y2.S1.MTR._6.LaundryLink.entity.shared.Log;
import _6.Y2.S1.MTR._6.LaundryLink.service.shared.LogService;
import _6.Y2.S1.MTR._6.LaundryLink.orders.dto.CreateOrderRequest;
import _6.Y2.S1.MTR._6.LaundryLink.orders.dto.CreateOrderResponse;
import _6.Y2.S1.MTR._6.LaundryLink.orders.dto.CreatedOrderLineResponse;
import _6.Y2.S1.MTR._6.LaundryLink.orders.dto.ModifyOrderRequest;
import _6.Y2.S1.MTR._6.LaundryLink.entity.shared.Status;
import _6.Y2.S1.MTR._6.LaundryLink.service.shared.StatusService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Comparator;
import java.util.Set;

@Service
public class OrderService {
    private static final String INITIAL_STATUS_LABEL = "Unconfirmed";
    private static final Set<String> MODIFIABLE_STATUS_LABELS = Set.of(
            "Unconfirmed",
            "Payment Verified",
            "Awaiting Pickup");

    private final OrderRepository orderRepository;
    private final OrderLineService orderLineService;
    private final StatusService statusService;
    private final LogService logService;

    public OrderService(
            OrderRepository orderRepository,
            OrderLineService orderLineService,
            StatusService statusService,
            LogService logService) {
        this.orderRepository = orderRepository;
        this.orderLineService = orderLineService;
        this.statusService = statusService;
        this.logService = logService;
    }

    @Transactional
    public CreateOrderResponse createOrder(CreateOrderRequest request) {
        validateCustomer(request.getUserID());

        Status initialStatus = statusService.getByLabel(INITIAL_STATUS_LABEL);
        Order order = new Order();
        order.setUserID(request.getUserID());
        order.setStatus(initialStatus);

        for (var requestedLine : request.getOrderLines()) {
            OrderLine orderLine = orderLineService.createOrderLine(requestedLine);
            orderLine.setOrder(order);
            order.getOrderLines().add(orderLine);
        }

        Order savedOrder = orderRepository.save(order);
        return toCreateOrderResponse(savedOrder);
    }

    private CreateOrderResponse toCreateOrderResponse(Order order) {
        List<CreatedOrderLineResponse> lines = order.getOrderLines().stream()
                .map(line -> new CreatedOrderLineResponse(
                        line.getOrderLineID(),
                        line.getServicePricing().getItemID(),
                        line.getServicePricing().getServiceID(),
                        line.getQuantity(),
                        line.getLinePrice()))
                .toList();

        return new CreateOrderResponse(
                order.getOrderID(),
                order.getUserID(),
                order.getStatus().getStatusID(),
                order.getStatus().getStatusLabel(),
                lines);
    }

    @Transactional(readOnly = true)
    public List<OrderSummaryResponse> getCustomerOrders(Integer userID) {
        validateCustomer(userID);
        return orderRepository.findByUserID(userID).stream()
                .map(this::toOrderSummaryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse getCustomerOrder(Integer userID, Integer orderID) {
        validateCustomer(userID);
        Order order = orderRepository.findByOrderIDAndUserID(orderID, userID)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        return toOrderDetailResponse(order);
    }

    @Transactional
    public OrderDetailResponse modifyCustomerOrder(
            Integer userID,
            Integer orderID,
            ModifyOrderRequest request) {
        validateCustomer(userID);
        Order order = orderRepository.findByOrderIDAndUserID(orderID, userID)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        return modifyOrderLines(order, request);
    }

    @Transactional(readOnly = true)
    public List<ManagerOrderSummaryResponse> searchManagementOrders(String search, Integer statusID) {
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        return orderRepository.searchManagementOrders(normalizedSearch, statusID).stream()
                .map(order -> new ManagerOrderSummaryResponse(
                        order.getOrderID(),
                        order.getUserID(),
                        customerName(order),
                        order.getEmail(),
                        order.getPhoneNumber(),
                        order.getStatusID(),
                        order.getStatusLabel(),
                        order.getOrderTotal()))
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse getManagementOrder(Integer orderID) {
        Order order = orderRepository.findById(orderID)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        return toOrderDetailResponse(order);
    }

    @Transactional
    public OrderDetailResponse modifyManagementOrder(Integer orderID, ModifyOrderRequest request) {
        Order order = orderRepository.findById(orderID)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        return modifyOrderLines(order, request);
    }

    private OrderDetailResponse modifyOrderLines(Order order, ModifyOrderRequest request) {

        if (!MODIFIABLE_STATUS_LABELS.contains(order.getStatus().getStatusLabel())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This order can no longer be modified after pickup processing begins");
        }

        List<OrderLine> updatedLines = request.getOrderLines().stream()
                .map(orderLineService::createOrderLine)
                .toList();

        order.getOrderLines().clear();
        updatedLines.forEach(line -> {
            line.setOrder(order);
            order.getOrderLines().add(line);
        });

        Order savedOrder = orderRepository.save(order);
        return toOrderDetailResponse(savedOrder);
    }

    private OrderDetailResponse toOrderDetailResponse(Order order) {
        List<OrderHistoryResponse> history = logService.getLogsByOrder(order.getOrderID()).stream()
                .sorted(Comparator
                        .comparing(Log::getLogDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Log::getLogTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(log -> new OrderHistoryResponse(
                        log.getLogID(),
                        statusID(log.getStatusBefore()),
                        statusLabel(log.getStatusBefore()),
                        statusID(log.getStatusAfter()),
                        statusLabel(log.getStatusAfter()),
                        log.getLogDate(),
                        log.getLogTime()))
                .toList();

        return new OrderDetailResponse(
                order.getOrderID(),
                order.getUserID(),
                order.getStatus().getStatusID(),
                order.getStatus().getStatusLabel(),
                toOrderLineDetails(order),
                calculateOrderTotal(order),
                history);
    }

    private OrderSummaryResponse toOrderSummaryResponse(Order order) {
        return new OrderSummaryResponse(
                order.getOrderID(),
                order.getStatus().getStatusID(),
                order.getStatus().getStatusLabel(),
                calculateOrderTotal(order));
    }

    private List<OrderLineDetailResponse> toOrderLineDetails(Order order) {
        return order.getOrderLines().stream()
                .map(line -> new OrderLineDetailResponse(
                        line.getOrderLineID(),
                        line.getServicePricing().getItemID(),
                        line.getServicePricing().getItem().getItemName(),
                        line.getServicePricing().getServiceID(),
                        line.getServicePricing().getService().getServiceName(),
                        line.getQuantity(),
                        line.getLinePrice()))
                .toList();
    }

    private Double calculateOrderTotal(Order order) {
        return order.getOrderLines().stream()
                .map(OrderLine::getLinePrice)
                .reduce(0.0, Double::sum);
    }

    private void validateCustomer(Integer userID) {
        if (orderRepository.countCustomersByUserID(userID) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found");
        }
    }

    private Integer statusID(Status status) {
        return status == null ? null : status.getStatusID();
    }

    private String statusLabel(Status status) {
        return status == null ? null : status.getStatusLabel();
    }

    private String customerName(ManagerOrderSummaryProjection order) {
        return java.util.stream.Stream.of(order.getFirstName(), order.getMiddleName(), order.getLastName())
                .filter(name -> name != null && !name.isBlank())
                .collect(java.util.stream.Collectors.joining(" "));
    }
}
