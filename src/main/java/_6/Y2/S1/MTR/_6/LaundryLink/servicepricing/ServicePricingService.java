/* ServicePricingService
 * SERVICE: business rules and orchestration. Constructor-injected collaborators show composition and dependency injection; private fields encapsulate dependencies.
 */
package _6.Y2.S1.MTR._6.LaundryLink.servicepricing;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ServicePricingService {
  private final ServicePricingRepository repo;

  public ServicePricingService(ServicePricingRepository repo) {
    this.repo = repo;
  }

  public List<ServicePricing> getAllPricing() {
    return repo.findAll();
  }

  public List<ServicePricing> getPricingByItem(Integer itemID) {
    return repo.findByItemID(itemID);
  }

  public List<ServicePricing> getPricingByService(Integer serviceID) {
    return repo.findByServiceID(serviceID);
  }

  public Optional<ServicePricing> getPricingForCombination(Integer itemID, Integer serviceID) {
    return repo.findByItemIDAndServiceID(itemID, serviceID);
  }
}
