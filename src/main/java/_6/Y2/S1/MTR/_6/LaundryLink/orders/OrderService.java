package _6.Y2.S1.MTR._6.LaundryLink.orders;

import _6.Y2.S1.MTR._6.LaundryLink.orderlines.OrderLine;
import _6.Y2.S1.MTR._6.LaundryLink.orderlines.OrderLineService;
import _6.Y2.S1.MTR._6.LaundryLink.log.Log;
import _6.Y2.S1.MTR._6.LaundryLink.log.LogService;
import _6.Y2.S1.MTR._6.LaundryLink.orders.dto.CreateOrderRequest;
import _6.Y2.S1.MTR._6.LaundryLink.orders.dto.CreateOrderResponse;
import _6.Y2.S1.MTR._6.LaundryLink.orders.dto.CreatedOrderLineResponse;
import _6.Y2.S1.MTR._6.LaundryLink.orders.dto.ModifyOrderRequest;
import _6.Y2.S1.MTR._6.LaundryLink.status.Status;
import _6.Y2.S1.MTR._6.LaundryLink.status.StatusService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.Set;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
public class OrderService {
    private static final String INITIAL_STATUS_LABEL = "Unconfirmed";
    private static final Set<String> CANCELLABLE_STATUS_LABELS = Set.of(
            "Unconfirmed", "Payment Verified", "Awaiting Pickup", "En Route To Pickup",
            "Picked Up", "En Route To Shop", "In Shop", "Verifying Items");
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
        validatePickupSchedule(request.getPickupScheduled());
        // Checked before anything is saved, so a bad address never leaves a half-made order.
        Integer pickupAddressID = validatedPickupAddress(request.getAddressID(), request.getUserID());
        // Also checked before anything is saved: an unknown preference code stops the order here.
        String preferences = toStoredPreferences(request.getPreferences());

        Status initialStatus = statusService.getByLabel(INITIAL_STATUS_LABEL);
        Order order = new Order();
        order.setUserID(request.getUserID());
        order.setStatus(initialStatus);
        order.setInstructions(trimToNull(request.getInstructions()));
        order.setPreferences(preferences);

        for (var requestedLine : request.getOrderLines()) {
            OrderLine orderLine = orderLineService.createOrderLine(requestedLine);
            orderLine.setOrder(order);
            order.getOrderLines().add(orderLine);
        }

        Order savedOrder = orderRepository.save(order);

        // The save above has already inserted the order, so its generated orderID is available.
        // Creating the delivery row in this same @Transactional method means the order, its
        // lines and its delivery row are either all saved or all rolled back together.
        // The validated chosen/default address goes on the delivery row.
        orderRepository.createDelivery(
                savedOrder.getOrderID(),
                savedOrder.getUserID(),
                request.getPickupScheduled(),
                pickupAddressID);

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

        if (!canCustomerModify(order)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only Payment Failed orders or Unconfirmed orders without a pending, paid, or verified payment can be modified.");
        }
        validatePickupSchedule(request.getPickupScheduled());
        if (request.getAddressID() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select an existing pickup address.");
        }
        Integer pickupAddressID = validatedPickupAddress(request.getAddressID(), userID);
        if (request.getInstructions() != null && request.getInstructions().length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Instructions must not exceed 500 characters.");
        }
        String preferences = toStoredPreferences(request.getPreferences());
        // Validate every line before mutating the order or its pickup details.
        List<OrderLine> updatedLines = validatedOrderLines(request);
        if (orderRepository.updatePickupSchedule(orderID, userID,
                request.getPickupScheduled(), pickupAddressID) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The order must have exactly one pickup record. No changes were saved.");
        }
        order.setInstructions(trimToNull(request.getInstructions()));
        order.setPreferences(preferences);
        return replaceOrderLines(order, updatedLines);
    }

    @Transactional
    public OrderDetailResponse cancelCustomerOrder(Integer userID, Integer orderID) {
        validateCustomer(userID);
        Order order = orderRepository.findByOrderIDAndUserID(orderID, userID)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (!CANCELLABLE_STATUS_LABELS.contains(order.getStatus().getStatusLabel())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Orders can only be cancelled up to and including Verifying Items.");
        }
        Status cancelled = statusService.getByLabel("Cancelled");
        if (!Integer.valueOf(20).equals(cancelled.getStatusID())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The cancellation status is not configured correctly.");
        }
        if (orderRepository.cancelEligibleOrder(orderID, userID, order.getStatus().getStatusID(), cancelled.getStatusID()) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The order status changed. Refresh the order before attempting cancellation again.");
        }
        return getCustomerOrder(userID, orderID);
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
        if (!MODIFIABLE_STATUS_LABELS.contains(order.getStatus().getStatusLabel())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This order can no longer be modified after pickup processing begins");
        }
        return modifyOrderLines(order, request);
    }

    // Each caller validates its own eligibility before this shared line-update operation.
    private OrderDetailResponse modifyOrderLines(Order order, ModifyOrderRequest request) {
        return replaceOrderLines(order, validatedOrderLines(request));
    }

    private List<OrderLine> validatedOrderLines(ModifyOrderRequest request) {
        if (request.getOrderLines() == null || request.getOrderLines().isEmpty()
                || request.getOrderLines().stream().anyMatch(java.util.Objects::isNull)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at least one valid order line.");
        }
        return request.getOrderLines().stream()
                .map(orderLineService::createOrderLine)
                .toList();
    }

    private OrderDetailResponse replaceOrderLines(Order order, List<OrderLine> updatedLines) {
        order.getOrderLines().clear();
        updatedLines.forEach(line -> {
            line.setOrder(order);
            order.getOrderLines().add(line);
        });

        Order savedOrder = orderRepository.save(order);
        return toOrderDetailResponse(savedOrder);
    }

    private OrderDetailResponse toOrderDetailResponse(Order order) {
        OrderRepository.PickupDetails pickup = orderRepository
                .findPickupDetails(order.getOrderID(), order.getUserID()).orElse(null);
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
                history,
                order.getInstructions(),
                // Stored as codes; the pages are given the readable labels.
                OrderPreference.labelsOf(order.getPreferences()),
                canCustomerModify(order),
                pickup == null ? null : pickup.getPickupScheduled(),
                pickup == null ? null : pickup.getAddressID(),
                java.util.Arrays.stream(OrderPreference.values())
                        .filter(preference -> order.getPreferences() != null
                                && java.util.Arrays.asList(order.getPreferences().split(",")).contains(preference.getCode()))
                        .map(OrderPreference::getCode).toList());
    }

    private OrderSummaryResponse toOrderSummaryResponse(Order order) {
        return new OrderSummaryResponse(
                order.getOrderID(),
                order.getStatus().getStatusID(),
                order.getStatus().getStatusLabel(),
                calculateOrderTotal(order),
                canCustomerModify(order));
    }

    private boolean canCustomerModify(Order order) {
        return "Payment Failed".equals(order.getStatus().getStatusLabel())
                || (INITIAL_STATUS_LABEL.equals(order.getStatus().getStatusLabel())
                    && orderRepository.countSubmittedOrSuccessfulPayments(order.getOrderID()) == 0);
    }

    private void validatePickupSchedule(LocalDateTime pickupScheduled) {
        LocalDateTime now = LocalDateTime.now();
        if (pickupScheduled == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a pickup date and time window.");
        }
        if (pickupScheduled.toLocalDate().isBefore(now.toLocalDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please choose today or a future date.");
        }
        if (!pickupScheduled.isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a pickup window that has not started yet.");
        }
        if (pickupScheduled.toLocalDate().isAfter(now.toLocalDate().plusMonths(1))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pickup can only be scheduled up to one calendar month in advance.");
        }
        if (!Set.of(LocalTime.of(8, 0), LocalTime.of(11, 0), LocalTime.of(14, 0), LocalTime.of(17, 0))
                .contains(pickupScheduled.toLocalTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a valid pickup time window.");
        }
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

    // Resolve the existing default fallback before saving; never leave pickup without an address.
    private Integer validatedPickupAddress(Integer addressID, Integer userID) {
        Integer resolvedAddressID = addressID == null
                ? orderRepository.findDefaultAddressID(userID).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select an existing pickup address or save a default address."))
                : addressID;
        if (resolvedAddressID <= 0 || orderRepository.countAddressesOwnedByCustomer(resolvedAddressID, userID) == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pickup address not found for this customer");
        }
        return resolvedAddressID;
    }

    // Turns the preference codes sent by the form into the string stored in orders.preferences.
    // Every code must be one of OrderPreference's; anything else is refused with 400 so a typo
    // or a made-up value is never saved. No preferences at all gives null (the column stays NULL).
    private String toStoredPreferences(List<String> codes) {
        if (codes == null) {
            return null;
        }
        List<OrderPreference> preferences = new ArrayList<>();
        for (String code : codes) {
            preferences.add(OrderPreference.fromCode(code)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Unknown order preference: " + code)));
        }
        // Removes duplicates and puts the codes in a fixed order.
        return OrderPreference.toStoredValue(preferences);
    }

    // A note made only of spaces is the same as no note, so it is stored as NULL.
    private String trimToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
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
