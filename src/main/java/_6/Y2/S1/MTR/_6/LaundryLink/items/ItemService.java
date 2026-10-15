/* ItemService
 * SERVICE: business rules and orchestration. Constructor-injected collaborators show composition and dependency injection; private fields encapsulate dependencies.
 */
package _6.Y2.S1.MTR._6.LaundryLink.items;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ItemService {
  private final ItemRepository repo;

  public ItemService(ItemRepository repo) {
    this.repo = repo;
  }

  public List<Item> getAllItems() {
    return repo.findAll();
  }
}
