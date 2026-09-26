package _6.Y2.S1.MTR._6.LaundryLink.service.shared;

import _6.Y2.S1.MTR._6.LaundryLink.security.support.SupportAccess.Actor;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.CONFLICT;

@Service
@Transactional(readOnly = true)
public class NotificationService {
    private final JdbcTemplate db;
    public NotificationService(JdbcTemplate db) { this.db = db; }

    public Map<String, Object> inbox(Actor actor, int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Notification limit must be between 1 and 100.");
        var items = db.queryForList("""
            SELECT TOP (?) notificationID AS id,category,title,message,link,relatedType,relatedID,isRead,createdAt,readAt
            FROM notifications WHERE recipientID=? ORDER BY createdAt DESC,notificationID DESC
            """, limit, actor.id());
        Integer unread = db.queryForObject("SELECT COUNT(*) FROM notifications WHERE recipientID=? AND isRead=0", Integer.class, actor.id());
        return Map.of("items", items, "unread", Objects.requireNonNullElse(unread, 0));
    }

    @Transactional
    public void markRead(Actor actor, long id) {
        int changed = db.update("""
            UPDATE notifications SET isRead=1,readAt=COALESCE(readAt,SYSDATETIME())
            WHERE notificationID=? AND recipientID=?
            """, id, actor.id());
        if (changed != 1) throw new ResponseStatusException(CONFLICT, "Notification was not found or already changed.");
    }

    @Transactional
    public void markAllRead(Actor actor) {
        db.update("UPDATE notifications SET isRead=1,readAt=COALESCE(readAt,SYSDATETIME()) WHERE recipientID=? AND isRead=0", actor.id());
    }

    public void notifyUser(int recipientId, String category, String title, String message, String link, String relatedType, Integer relatedId) {
        db.update("""
            INSERT INTO notifications(recipientID,category,title,message,link,relatedType,relatedID)
            SELECT userID,?,?,?,?,?,? FROM users WHERE userID=? AND active=1
            """, category, title, message, link, relatedType, relatedId, recipientId);
    }

    public void notifyRoles(Set<String> roles, String category, String title, String message, String link, String relatedType, Integer relatedId, Integer excludeUser) {
        if (roles.isEmpty()) return;
        String placeholders = String.join(",", Collections.nCopies(roles.size(), "?"));
        List<Object> args = new ArrayList<>(Arrays.asList(category, title, message, link, relatedType, relatedId));
        args.addAll(roles);
        args.add(excludeUser);
        args.add(excludeUser);
        db.update("""
            INSERT INTO notifications(recipientID,category,title,message,link,relatedType,relatedID)
            SELECT userID,?,?,?,?,?,? FROM users
            WHERE active=1 AND UPPER(type) IN (""" + placeholders + ") AND (? IS NULL OR userID<>?)",
            args.toArray());
    }
}
