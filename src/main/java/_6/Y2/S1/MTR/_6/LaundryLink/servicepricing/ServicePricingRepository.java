package _6.Y2.S1.MTR._6.LaundryLink.servicepricing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServicePricingRepository extends JpaRepository<ServicePricing, ServicePricingId> {
    List<ServicePricing> findByItemID(Integer itemID);

    List<ServicePricing> findByServiceID(Integer serviceID);

    Optional<ServicePricing> findByItemIDAndServiceID(Integer itemID, Integer serviceID);
}
