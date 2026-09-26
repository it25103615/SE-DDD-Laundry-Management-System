package _6.Y2.S1.MTR._6.LaundryLink.security.support;

import java.security.Principal;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

/** Resolves the signed-in database user and their support-workflow permissions. */
@Service
public class SupportAccess {
    private final JdbcTemplate db;
    public SupportAccess(JdbcTemplate db) {
        this.db = db;
    }
    public record Actor(int id, String name, String role, boolean demo) {
        public boolean staff() { return Set.of("ADMIN", "MANAGER", "OWNER", "CSM", "CUSTOMER_SERVICE_MANAGER", "STAFF", "RIDER").contains(role); }
        public boolean coordinator() { return Set.of("ADMIN", "OWNER", "CSM", "CUSTOMER_SERVICE_MANAGER").contains(role); }
        public boolean seesAllCases() { return coordinator(); }
        public boolean manager() { return Set.of("ADMIN", "MANAGER", "OWNER").contains(role); }
        public boolean owner() { return Set.of("ADMIN", "OWNER").contains(role); }
    }
    public Actor actor(Principal principal, Integer demoUser) {
        boolean demo = false;
        List<Actor> actors;
        String select = "SELECT userID, CONCAT(firstName, ' ', lastName), UPPER(type) FROM users WHERE active=1 AND ";
        if (principal != null) {
            actors = db.query(select + "email = ?", (r, n) -> new Actor(r.getInt(1), r.getString(2), r.getString(3), demo), principal.getName());
        } else throw new ResponseStatusException(UNAUTHORIZED, "Sign in first.");
        if (actors.isEmpty()) throw new ResponseStatusException(UNAUTHORIZED, "User account not found.");
        Actor actor = actors.getFirst();
        if (!actor.staff() && !"CUSTOMER".equals(actor.role())) throw new ResponseStatusException(FORBIDDEN, "This account cannot access customer support.");
        return actor;
    }
    public void staff(Actor actor) { if (!actor.staff()) throw new ResponseStatusException(FORBIDDEN, "Support staff access required."); }
    public void coordinator(Actor actor) { if (!actor.coordinator()) throw new ResponseStatusException(FORBIDDEN, "Customer service manager access required."); }
    public void manager(Actor actor) { if (!actor.manager()) throw new ResponseStatusException(FORBIDDEN, "Manager or owner access required."); }
    public void owner(Actor actor) { if (!actor.owner()) throw new ResponseStatusException(FORBIDDEN, "Owner access required."); }
}
