package _6.Y2.S1.MTR._6.LaundryLink.controller.shared;

import _6.Y2.S1.MTR._6.LaundryLink.security.support.SupportAccess;
import _6.Y2.S1.MTR._6.LaundryLink.service.shared.NotificationService;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notifications;
    private final SupportAccess access;
    public NotificationController(NotificationService notifications, SupportAccess access) {
        this.notifications = notifications;
        this.access = access;
    }
    @GetMapping
    public Object inbox(Principal principal, @RequestParam(defaultValue = "30") int limit) {
        return notifications.inbox(access.actor(principal, null), limit);
    }
    @PatchMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(Principal principal, @PathVariable long id) {
        notifications.markRead(access.actor(principal, null), id);
    }
    @PatchMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll(Principal principal) {
        notifications.markAllRead(access.actor(principal, null));
    }
}
