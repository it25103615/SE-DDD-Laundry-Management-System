package _6.Y2.S1.MTR._6.LaundryLink.servicepricing;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/service-pricing")
public class ServicePricingController {
    private final ServicePricingService service;

    public ServicePricingController(ServicePricingService service) {
        this.service = service;
    }

    @GetMapping
    public List<ServicePricing> getAll() {
        return service.getAllPricing();
    }

    @GetMapping("/item/{itemID}")
    public List<ServicePricing> getByItem(@PathVariable Integer itemID) {
        return service.getPricingByItem(itemID);
    }

    @GetMapping("/service/{serviceID}")
    public List<ServicePricing> getByService(@PathVariable Integer serviceID) {
        return service.getPricingByService(serviceID);
    }

    @GetMapping("/item/{itemID}/service/{serviceID}")
    public ResponseEntity<ServicePricing> getCombination(
            @PathVariable Integer itemID,
            @PathVariable Integer serviceID) {
        return service.getPricingForCombination(itemID, serviceID)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
