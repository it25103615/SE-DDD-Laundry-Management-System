/* ServiceController
 * CONTROLLER: HTTP endpoint adapter. Binds requests and delegates work; browser JavaScript calls these endpoints.
 */
package _6.Y2.S1.MTR._6.LaundryLink.services;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/services")
public class ServiceController {
  private final ServiceService service;

  public ServiceController(ServiceService service) {
    this.service = service;
  }

  @GetMapping
  public List<Service> getAll() {
    return service.getAllServices();
  }
}
