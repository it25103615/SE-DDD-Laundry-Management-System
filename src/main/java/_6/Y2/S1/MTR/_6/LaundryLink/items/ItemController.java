/* ItemController
 * CONTROLLER: HTTP endpoint adapter. Binds requests and delegates work; browser JavaScript calls these endpoints.
 */
package _6.Y2.S1.MTR._6.LaundryLink.items;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/items")
public class ItemController {
  private final ItemService service;

  public ItemController(ItemService service) {
    this.service = service;
  }

  @GetMapping
  public List<Item> getAll() {
    return service.getAllItems();
  }
}
