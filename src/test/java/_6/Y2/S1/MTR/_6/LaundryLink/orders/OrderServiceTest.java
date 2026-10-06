package _6.Y2.S1.MTR._6.LaundryLink.orders;

import _6.Y2.S1.MTR._6.LaundryLink.items.ItemRepository;
import _6.Y2.S1.MTR._6.LaundryLink.orderlines.OrderLineService;
import _6.Y2.S1.MTR._6.LaundryLink.log.Log;
import _6.Y2.S1.MTR._6.LaundryLink.log.LogService;
import _6.Y2.S1.MTR._6.LaundryLink.orders.dto.CreateOrderLineRequest;
import _6.Y2.S1.MTR._6.LaundryLink.orders.dto.CreateOrderRequest;
import _6.Y2.S1.MTR._6.LaundryLink.orders.dto.CreateOrderResponse;
import _6.Y2.S1.MTR._6.LaundryLink.orders.dto.ModifyOrderRequest;
import _6.Y2.S1.MTR._6.LaundryLink.servicepricing.ServicePricing;
import _6.Y2.S1.MTR._6.LaundryLink.servicepricing.ServicePricingRepository;
import _6.Y2.S1.MTR._6.LaundryLink.services.ServiceRepository;
import _6.Y2.S1.MTR._6.LaundryLink.status.Status;
import _6.Y2.S1.MTR._6.LaundryLink.status.StatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {
    private OrderRepository orderRepository;
    private ItemRepository itemRepository;
    private ServiceRepository serviceRepository;
    private ServicePricingRepository servicePricingRepository;
    private StatusService statusService;
    private LogService logService;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        itemRepository = mock(ItemRepository.class);
        serviceRepository = mock(ServiceRepository.class);
        servicePricingRepository = mock(ServicePricingRepository.class);
        statusService = mock(StatusService.class);
        logService = mock(LogService.class);

        OrderLineService orderLineService = new OrderLineService(
                itemRepository,
                serviceRepository,
                servicePricingRepository);
        orderService = new OrderService(orderRepository, orderLineService, statusService, logService);
    }

    @Test
    void cancellationRejectsNonUnconfirmedAndConcurrentStatusChanges() {
        Order order = new Order();
        order.setOrderID(100);
        order.setUserID(1);
        Status status = mock(Status.class);
        order.setStatus(status);
        when(orderRepository.countCustomersByUserID(1)).thenReturn(1);
        when(orderRepository.findByOrderIDAndUserID(100, 1)).thenReturn(Optional.of(order));
        for (int id = 2; id <= 20; id++) {
            when(status.getStatusID()).thenReturn(id);
            when(status.getStatusLabel()).thenReturn("Other status");
            assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                    () -> orderService.cancelCustomerOrder(1, 100)).getStatusCode());
        }
        verify(orderRepository, never()).cancelUnconfirmedOrder(any(), any(), any(), any());
        when(status.getStatusID()).thenReturn(1);
        when(status.getStatusLabel()).thenReturn("Unconfirmed");
        Status cancelled = mock(Status.class);
        when(cancelled.getStatusID()).thenReturn(20);
        when(statusService.getByLabel("Cancelled")).thenReturn(cancelled);
        when(orderRepository.cancelUnconfirmedOrder(100, 1, 1, 20)).thenReturn(0);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> orderService.cancelCustomerOrder(1, 100)).getStatusCode());
    }

    @Test
    void cancellationReturnsCancelledOrder() {
        Order order = new Order();
        order.setOrderID(100);
        order.setUserID(1);
        Status initial = mock(Status.class);
        when(initial.getStatusID()).thenReturn(1);
        when(initial.getStatusLabel()).thenReturn("Unconfirmed");
        order.setStatus(initial);
        Status cancelled = mock(Status.class);
        when(cancelled.getStatusID()).thenReturn(20);
        when(cancelled.getStatusLabel()).thenReturn("Cancelled");
        when(statusService.getByLabel("Cancelled")).thenReturn(cancelled);
        when(orderRepository.countCustomersByUserID(1)).thenReturn(1);
        when(orderRepository.findByOrderIDAndUserID(100, 1)).thenReturn(Optional.of(order));
        when(logService.getLogsByOrder(100)).thenReturn(List.of());
        when(orderRepository.cancelUnconfirmedOrder(100, 1, 1, 20)).thenAnswer(invocation -> {
            order.setStatus(cancelled);
            return 1;
        });
        OrderDetailResponse response = orderService.cancelCustomerOrder(1, 100);
        assertEquals(20, response.getStatusID());
        assertEquals("Cancelled", response.getStatusLabel());
    }

    @Test
    void createsOrderWithDatabaseCalculatedLinePriceAndUnconfirmedStatus() {
        ServicePricing pricing = pricingWithDetails(1, 1, 180.0);
        Status status = mock(Status.class);
        when(orderRepository.countCustomersByUserID(1)).thenReturn(1);
        when(itemRepository.existsById(1)).thenReturn(true);
        when(serviceRepository.existsById(1)).thenReturn(true);
        when(servicePricingRepository.findByItemIDAndServiceID(1, 1)).thenReturn(Optional.of(pricing));
        when(statusService.getByLabel("Unconfirmed")).thenReturn(status);
        when(status.getStatusID()).thenReturn(1);
        when(status.getStatusLabel()).thenReturn("Unconfirmed");
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setOrderID(100);
            order.getOrderLines().getFirst().setOrderLineID(200);
            return order;
        });

        CreateOrderRequest request = request(1, line(1, 1, 2));
        LocalDateTime pickup = LocalDateTime.now().plusDays(1).withHour(8).withMinute(0).withSecond(0).withNano(0);
        request.setPickupScheduled(pickup);

        CreateOrderResponse response = orderService.createOrder(request);

        assertEquals(100, response.getOrderID());
        assertEquals("Unconfirmed", response.getStatusLabel());
        assertEquals(360.0, response.getOrderLines().getFirst().getLinePrice());
        verify(orderRepository).save(any(Order.class));
        // The delivery row must be created for the saved order, with the customer's pickup time.
        // No address was chosen, so null is passed and the query falls back to the default address.
        verify(orderRepository).createDelivery(100, 1, pickup, null);
    }

    @Test
    void storesTheChosenAddressOnTheDeliveryRow() {
        ServicePricing pricing = pricingWithDetails(1, 1, 180.0);
        Status status = mock(Status.class);
        when(orderRepository.countCustomersByUserID(1)).thenReturn(1);
        // Address 7 belongs to customer 1.
        when(orderRepository.countAddressesOwnedByCustomer(7, 1)).thenReturn(1);
        when(itemRepository.existsById(1)).thenReturn(true);
        when(serviceRepository.existsById(1)).thenReturn(true);
        when(servicePricingRepository.findByItemIDAndServiceID(1, 1)).thenReturn(Optional.of(pricing));
        when(statusService.getByLabel("Unconfirmed")).thenReturn(status);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setOrderID(100);
            return order;
        });

        CreateOrderRequest request = request(1, line(1, 1, 2));
        request.setAddressID(7);

        orderService.createOrder(request);

        verify(orderRepository).createDelivery(100, 1, null, 7);
    }

    @Test
    void rejectsAnAddressThatBelongsToAnotherCustomerWithoutSavingTheOrder() {
        when(orderRepository.countCustomersByUserID(1)).thenReturn(1);
        // Address 8 is not one of customer 1's addresses.
        when(orderRepository.countAddressesOwnedByCustomer(8, 1)).thenReturn(0);

        CreateOrderRequest request = request(1, line(1, 1, 2));
        request.setAddressID(8);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.createOrder(request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(orderRepository, never()).save(any(Order.class));
        verify(orderRepository, never()).createDelivery(any(), any(), any(), any());
    }

    @Test
    void storesTheNoteAndPreferencesOnTheOrder() {
        prepareSuccessfulOrder();

        CreateOrderRequest request = request(1, line(1, 1, 2));
        request.setInstructions("  Treat the stain on the blue shirt collar.  ");
        // Sent out of order and with a repeat: stored once each, in the enum's order.
        request.setPreferences(List.of("hangers", "fragrance-free", "hangers"));

        orderService.createOrder(request);

        Order saved = savedOrder();
        assertEquals("Treat the stain on the blue shirt collar.", saved.getInstructions());
        assertEquals("fragrance-free,hangers", saved.getPreferences());
    }

    @Test
    void storesNullWhenTheNoteIsBlankAndNothingIsTicked() {
        prepareSuccessfulOrder();

        CreateOrderRequest request = request(1, line(1, 1, 2));
        request.setInstructions("   ");
        request.setPreferences(List.of());

        orderService.createOrder(request);

        Order saved = savedOrder();
        assertNull(saved.getInstructions());
        assertNull(saved.getPreferences());
    }

    @Test
    void rejectsAnUnknownPreferenceWithoutSavingTheOrder() {
        when(orderRepository.countCustomersByUserID(1)).thenReturn(1);

        CreateOrderRequest request = request(1, line(1, 1, 2));
        request.setPreferences(List.of("fragrance-free", "extra-starch"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.createOrder(request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(orderRepository, never()).save(any(Order.class));
        verify(orderRepository, never()).createDelivery(any(), any(), any(), any());
    }

    @Test
    void returnsTheNoteAndPreferenceLabelsWithTheOrderDetails() {
        Order order = orderWithLine(10, 1, 1, 1, 2, 360.0);
        order.setInstructions("Treat the collar stain");
        order.setPreferences("fragrance-free,hangers");
        when(orderRepository.countCustomersByUserID(1)).thenReturn(1);
        when(orderRepository.findByOrderIDAndUserID(10, 1)).thenReturn(Optional.of(order));
        when(logService.getLogsByOrder(10)).thenReturn(List.of());

        OrderDetailResponse response = orderService.getCustomerOrder(1, 10);

        assertEquals("Treat the collar stain", response.getInstructions());
        // The stored codes come back as the labels the pages show.
        assertEquals(
                List.of("Fragrance-free detergent", "Return shirts on hangers"),
                response.getPreferences());
    }

    @Test
    void rejectsUnknownCustomer() {
        when(orderRepository.countCustomersByUserID(99)).thenReturn(0);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.createOrder(request(99, line(1, 1, 1))));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void rejectsUnknownItem() {
        prepareCustomerAndStatus();
        when(itemRepository.existsById(99)).thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.createOrder(request(1, line(99, 1, 1))));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void rejectsUnknownService() {
        prepareCustomerAndStatus();
        when(itemRepository.existsById(1)).thenReturn(true);
        when(serviceRepository.existsById(99)).thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.createOrder(request(1, line(1, 99, 1))));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void rejectsUnavailableItemServiceCombination() {
        prepareCustomerAndStatus();
        when(itemRepository.existsById(1)).thenReturn(true);
        when(serviceRepository.existsById(1)).thenReturn(true);
        when(servicePricingRepository.findByItemIDAndServiceID(1, 1)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.createOrder(request(1, line(1, 1, 1))));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void rejectsNonPositiveQuantity() {
        prepareCustomerAndStatus();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.createOrder(request(1, line(1, 1, 0))));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void doesNotSaveOrderWhenALaterLineFails() {
        prepareCustomerAndStatus();
        ServicePricing pricing = pricing(1, 1, 180.0);
        when(itemRepository.existsById(1)).thenReturn(true);
        when(itemRepository.existsById(99)).thenReturn(false);
        when(serviceRepository.existsById(1)).thenReturn(true);
        when(servicePricingRepository.findByItemIDAndServiceID(1, 1)).thenReturn(Optional.of(pricing));

        assertThrows(
                ResponseStatusException.class,
                () -> orderService.createOrder(request(1, line(1, 1, 1), line(99, 1, 1))));

        verify(orderRepository, never()).save(any(Order.class));
        // A failed order must not leave an orphan delivery row behind.
        verify(orderRepository, never()).createDelivery(any(), any(), any(), any());
    }

    @Test
    void returnsCustomerOrderSummariesWithDatabaseStatusAndCalculatedTotals() {
        Order order = orderWithLine(10, 1, 1, 1, 2, 360.0);
        when(orderRepository.countCustomersByUserID(1)).thenReturn(1);
        when(orderRepository.findByUserID(1)).thenReturn(List.of(order));

        List<OrderSummaryResponse> response = orderService.getCustomerOrders(1);

        assertEquals(1, response.size());
        assertEquals(10, response.getFirst().getOrderID());
        assertEquals("Unconfirmed", response.getFirst().getStatusLabel());
        assertEquals(360.0, response.getFirst().getOrderTotal());
    }

    @Test
    void returnsOwnedOrderWithLineDetailsTotalAndHistory() {
        Order order = orderWithLine(10, 1, 1, 1, 2, 360.0);
        Status before = status(1, "Unconfirmed");
        Status after = status(2, "Payment Verified");
        Log log = new Log();
        log.setLogID(30);
        log.setStatusBefore(before);
        log.setStatusAfter(after);
        when(orderRepository.countCustomersByUserID(1)).thenReturn(1);
        when(orderRepository.findByOrderIDAndUserID(10, 1)).thenReturn(Optional.of(order));
        when(logService.getLogsByOrder(10)).thenReturn(List.of(log));

        OrderDetailResponse response = orderService.getCustomerOrder(1, 10);

        assertEquals("Unconfirmed", response.getStatusLabel());
        assertEquals("Everyday Clothing", response.getOrderLines().getFirst().getItemName());
        assertEquals("Wash and Fold", response.getOrderLines().getFirst().getServiceName());
        assertEquals(360.0, response.getOrderTotal());
        assertEquals("Payment Verified", response.getHistory().getFirst().getStatusAfterLabel());
    }

    @Test
    void rejectsCustomerAccessToAnotherCustomersOrder() {
        when(orderRepository.countCustomersByUserID(2)).thenReturn(1);
        when(orderRepository.findByOrderIDAndUserID(10, 2)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.getCustomerOrder(2, 10));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void modifiesEligibleOrderWithDatabaseCalculatedPrices() {
        Order order = orderWithLine(10, 1, 1, 1, 2, 360.0);
        ServicePricing pricing = pricingWithDetails(1, 1, 180.0);
        when(orderRepository.countCustomersByUserID(1)).thenReturn(1);
        when(orderRepository.findByOrderIDAndUserID(10, 1)).thenReturn(Optional.of(order));
        when(itemRepository.existsById(1)).thenReturn(true);
        when(serviceRepository.existsById(1)).thenReturn(true);
        when(servicePricingRepository.findByItemIDAndServiceID(1, 1)).thenReturn(Optional.of(pricing));
        when(orderRepository.save(order)).thenReturn(order);
        when(logService.getLogsByOrder(10)).thenReturn(List.of());

        OrderDetailResponse response = orderService.modifyCustomerOrder(1, 10, modifyRequest(line(1, 1, 3)));

        assertEquals(1, response.getOrderLines().size());
        assertEquals(540.0, response.getOrderLines().getFirst().getLinePrice());
        verify(orderRepository).save(order);
    }

    @Test
    void rejectsModificationAfterProcessingBegins() {
        Order order = orderWithLine(10, 1, 1, 1, 2, 360.0);
        order.setStatus(status(9, "Washing"));
        when(orderRepository.countCustomersByUserID(1)).thenReturn(1);
        when(orderRepository.findByOrderIDAndUserID(10, 1)).thenReturn(Optional.of(order));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.modifyCustomerOrder(1, 10, modifyRequest(line(1, 1, 3))));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void rejectsInvalidModificationCombinationWithoutSavingOrder() {
        Order order = orderWithLine(10, 1, 1, 1, 2, 360.0);
        when(orderRepository.countCustomersByUserID(1)).thenReturn(1);
        when(orderRepository.findByOrderIDAndUserID(10, 1)).thenReturn(Optional.of(order));
        when(itemRepository.existsById(1)).thenReturn(true);
        when(serviceRepository.existsById(1)).thenReturn(true);
        when(servicePricingRepository.findByItemIDAndServiceID(1, 1)).thenReturn(Optional.empty());

        assertThrows(
                ResponseStatusException.class,
                () -> orderService.modifyCustomerOrder(1, 10, modifyRequest(line(1, 1, 3))));

        verify(orderRepository, never()).save(any(Order.class));
        assertEquals(360.0, order.getOrderLines().getFirst().getLinePrice());
    }

    @Test
    void rejectsInvalidModificationQuantityWithoutSavingOrder() {
        Order order = orderWithLine(10, 1, 1, 1, 2, 360.0);
        when(orderRepository.countCustomersByUserID(1)).thenReturn(1);
        when(orderRepository.findByOrderIDAndUserID(10, 1)).thenReturn(Optional.of(order));

        assertThrows(
                ResponseStatusException.class,
                () -> orderService.modifyCustomerOrder(1, 10, modifyRequest(line(1, 1, 0))));

        verify(orderRepository, never()).save(any(Order.class));
        assertEquals(360.0, order.getOrderLines().getFirst().getLinePrice());
    }

    @Test
    void searchesManagementOrdersByCustomerAndStatus() {
        ManagerOrderSummaryProjection projection = mock(ManagerOrderSummaryProjection.class);
        when(projection.getOrderID()).thenReturn(10);
        when(projection.getUserID()).thenReturn(1);
        when(projection.getFirstName()).thenReturn("Nimal");
        when(projection.getMiddleName()).thenReturn(null);
        when(projection.getLastName()).thenReturn("Perera");
        when(projection.getEmail()).thenReturn("nimal.perera@example.com");
        when(projection.getPhoneNumber()).thenReturn("0711234567");
        when(projection.getStatusID()).thenReturn(3);
        when(projection.getStatusLabel()).thenReturn("Awaiting Pickup");
        when(projection.getOrderTotal()).thenReturn(1240.0);
        when(orderRepository.searchManagementOrders("Nimal", 3)).thenReturn(List.of(projection));

        List<ManagerOrderSummaryResponse> response = orderService.searchManagementOrders(" Nimal ", 3);

        assertEquals(1, response.size());
        assertEquals("Nimal Perera", response.getFirst().getCustomerName());
        assertEquals("Awaiting Pickup", response.getFirst().getStatusLabel());
        assertEquals(1240.0, response.getFirst().getOrderTotal());
        verify(orderRepository).searchManagementOrders("Nimal", 3);
    }

    @Test
    void retrievesManagementOrderDetailsWithoutCustomerScopedLookup() {
        Order order = orderWithLine(10, 1, 1, 1, 2, 360.0);
        when(orderRepository.findById(10)).thenReturn(Optional.of(order));
        when(logService.getLogsByOrder(10)).thenReturn(List.of());

        OrderDetailResponse response = orderService.getManagementOrder(10);

        assertEquals(10, response.getOrderID());
        assertEquals(360.0, response.getOrderTotal());
        verify(orderRepository).findById(10);
    }

    @Test
    void modifiesEligibleOrderThroughManagementEndpointService() {
        Order order = orderWithLine(10, 1, 1, 1, 2, 360.0);
        ServicePricing pricing = pricingWithDetails(1, 1, 180.0);
        when(orderRepository.findById(10)).thenReturn(Optional.of(order));
        when(itemRepository.existsById(1)).thenReturn(true);
        when(serviceRepository.existsById(1)).thenReturn(true);
        when(servicePricingRepository.findByItemIDAndServiceID(1, 1)).thenReturn(Optional.of(pricing));
        when(orderRepository.save(order)).thenReturn(order);
        when(logService.getLogsByOrder(10)).thenReturn(List.of());

        OrderDetailResponse response = orderService.modifyManagementOrder(10, modifyRequest(line(1, 1, 3)));

        assertEquals(540.0, response.getOrderTotal());
        verify(orderRepository).save(order);
    }

    private void prepareCustomerAndStatus() {
        Status status = mock(Status.class);
        when(orderRepository.countCustomersByUserID(1)).thenReturn(1);
        when(statusService.getByLabel("Unconfirmed")).thenReturn(status);
    }

    // Everything createOrder needs to succeed for customer 1 ordering item 1 with service 1.
    private void prepareSuccessfulOrder() {
        prepareCustomerAndStatus();
        when(itemRepository.existsById(1)).thenReturn(true);
        when(serviceRepository.existsById(1)).thenReturn(true);
        when(servicePricingRepository.findByItemIDAndServiceID(1, 1))
                .thenReturn(Optional.of(pricingWithDetails(1, 1, 180.0)));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setOrderID(100);
            return order;
        });
    }

    // The Order object that createOrder handed to the repository to be saved.
    private Order savedOrder() {
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        return captor.getValue();
    }

    private CreateOrderRequest request(Integer userID, CreateOrderLineRequest... lines) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setUserID(userID);
        request.setOrderLines(List.of(lines));
        return request;
    }

    private CreateOrderLineRequest line(Integer itemID, Integer serviceID, Integer quantity) {
        CreateOrderLineRequest line = new CreateOrderLineRequest();
        line.setItemID(itemID);
        line.setServiceID(serviceID);
        line.setQuantity(quantity);
        return line;
    }

    private ModifyOrderRequest modifyRequest(CreateOrderLineRequest... lines) {
        ModifyOrderRequest request = new ModifyOrderRequest();
        request.setOrderLines(List.of(lines));
        return request;
    }

    private ServicePricing pricing(Integer itemID, Integer serviceID, Double price) {
        ServicePricing pricing = new ServicePricing();
        pricing.setItemID(itemID);
        pricing.setServiceID(serviceID);
        pricing.setPrice(price);
        return pricing;
    }

    private ServicePricing pricingWithDetails(Integer itemID, Integer serviceID, Double price) {
        ServicePricing pricing = pricing(itemID, serviceID, price);
        _6.Y2.S1.MTR._6.LaundryLink.items.Item item = new _6.Y2.S1.MTR._6.LaundryLink.items.Item();
        item.setItemID(itemID);
        item.setItemName("Everyday Clothing");
        _6.Y2.S1.MTR._6.LaundryLink.services.Service service = new _6.Y2.S1.MTR._6.LaundryLink.services.Service();
        service.setServiceID(serviceID);
        service.setServiceName("Wash and Fold");
        pricing.setItem(item);
        pricing.setService(service);
        return pricing;
    }

    private Order orderWithLine(
            Integer orderID,
            Integer userID,
            Integer itemID,
            Integer serviceID,
            Integer quantity,
            Double linePrice) {
        _6.Y2.S1.MTR._6.LaundryLink.items.Item item = new _6.Y2.S1.MTR._6.LaundryLink.items.Item();
        item.setItemID(itemID);
        item.setItemName("Everyday Clothing");
        _6.Y2.S1.MTR._6.LaundryLink.services.Service service = new _6.Y2.S1.MTR._6.LaundryLink.services.Service();
        service.setServiceID(serviceID);
        service.setServiceName("Wash and Fold");
        ServicePricing pricing = pricing(itemID, serviceID, 180.0);
        pricing.setItem(item);
        pricing.setService(service);

        _6.Y2.S1.MTR._6.LaundryLink.orderlines.OrderLine line = new _6.Y2.S1.MTR._6.LaundryLink.orderlines.OrderLine();
        line.setOrderLineID(20);
        line.setServicePricing(pricing);
        line.setQuantity(quantity);
        line.setLinePrice(linePrice);

        Order order = new Order();
        order.setOrderID(orderID);
        order.setUserID(userID);
        order.setStatus(status(1, "Unconfirmed"));
        order.setOrderLines(new ArrayList<>(List.of(line)));
        return order;
    }

    private Status status(Integer statusID, String statusLabel) {
        Status status = mock(Status.class);
        when(status.getStatusID()).thenReturn(statusID);
        when(status.getStatusLabel()).thenReturn(statusLabel);
        return status;
    }
}
