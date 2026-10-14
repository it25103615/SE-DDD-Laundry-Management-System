/* ServicePricingRepository
 * REPOSITORY: persistence boundary. JPA repository interfaces are implemented by Spring; JDBC implementations execute explicit SQL.
 * INHERITANCE: interface extends JpaRepository and inherits repository operations; Spring supplies its implementation.
 */
package _6.Y2.S1.MTR._6.LaundryLink.servicepricing;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// OOP — Abstraction: this interface describes operations that implementing classes must provide.
// OOP — Inheritance: this repository interface inherits CRUD operations from JpaRepository; Spring
// creates the implementation.
public interface ServicePricingRepository extends JpaRepository<ServicePricing, ServicePricingId> {
  List<ServicePricing> findByItemID(Integer itemID);

  List<ServicePricing> findByServiceID(Integer serviceID);

  Optional<ServicePricing> findByItemIDAndServiceID(Integer itemID, Integer serviceID);
}
