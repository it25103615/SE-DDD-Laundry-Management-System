package _6.Y2.S1.MTR._6.LaundryLink.items;

import org.springframework.stereotype.Service;

import java.util.List;

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
