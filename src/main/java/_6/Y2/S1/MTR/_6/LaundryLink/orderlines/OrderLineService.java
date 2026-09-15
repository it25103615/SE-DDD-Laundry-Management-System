package _6.Y2.S1.MTR._6.LaundryLink.orderlines;

import _6.Y2.S1.MTR._6.LaundryLink.items.ItemRepository;
import _6.Y2.S1.MTR._6.LaundryLink.orders.dto.CreateOrderLineRequest;
import _6.Y2.S1.MTR._6.LaundryLink.servicepricing.ServicePricing;
import _6.Y2.S1.MTR._6.LaundryLink.servicepricing.ServicePricingRepository;
import _6.Y2.S1.MTR._6.LaundryLink.services.ServiceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderLineService {
    private final ItemRepository itemRepository;
    private final ServiceRepository serviceRepository;
    private final ServicePricingRepository servicePricingRepository;

    public OrderLineService(
            ItemRepository itemRepository,
            ServiceRepository serviceRepository,
            ServicePricingRepository servicePricingRepository) {
        this.itemRepository = itemRepository;
        this.serviceRepository = serviceRepository;
        this.servicePricingRepository = servicePricingRepository;
    }

    public OrderLine createOrderLine(CreateOrderLineRequest request) {
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be greater than zero");
        }

        if (!itemRepository.existsById(request.getItemID())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found");
        }

        if (!serviceRepository.existsById(request.getServiceID())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found");
        }

        ServicePricing servicePricing = servicePricingRepository
                .findByItemIDAndServiceID(request.getItemID(), request.getServiceID())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "The selected item and service combination is not available"));

        if (servicePricing.getPrice() == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Service pricing is unavailable");
        }

        OrderLine orderLine = new OrderLine();
        orderLine.setServicePricing(servicePricing);
        orderLine.setQuantity(request.getQuantity());
        orderLine.setLinePrice(servicePricing.getPrice() * request.getQuantity());
        return orderLine;
    }
}
