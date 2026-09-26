package _6.Y2.S1.MTR._6.LaundryLink.service.support;

import _6.Y2.S1.MTR._6.LaundryLink.dto.support.SupportRequests.PriceInput;
import _6.Y2.S1.MTR._6.LaundryLink.dto.support.SupportRequests.ServiceInput;
import _6.Y2.S1.MTR._6.LaundryLink.dto.support.SupportRequests.StaffInput;
import _6.Y2.S1.MTR._6.LaundryLink.repository.support.SupportRepository;
import _6.Y2.S1.MTR._6.LaundryLink.security.support.SupportAccess;
import _6.Y2.S1.MTR._6.LaundryLink.security.support.SupportAccess.Actor;
import _6.Y2.S1.MTR._6.LaundryLink.service.shared.NotificationService;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service
@Transactional(readOnly = true)
public class AdministrationService {
    private static final Set<String> OPERATIONAL_ROLES = Set.of("RIDER", "STAFF", "CSM", "MANAGER");
    private final SupportRepository repo;
    private final SupportAccess access;
    private final PasswordEncoder passwords;
    private final NotificationService notifications;

    public AdministrationService(SupportRepository repo, SupportAccess access, PasswordEncoder passwords, NotificationService notifications) {
        this.repo = repo;
        this.access = access;
        this.passwords = passwords;
        this.notifications = notifications;
    }

    public Map<String, Object> catalog(Actor actor) {
        access.manager(actor);
        return Map.of(
            "services", repo.query("""
                SELECT serviceID AS id, serviceName AS name, description, turnaroundHours, active, version,
                       (SELECT COUNT(*) FROM servicePricing p WHERE p.serviceID=s.serviceID) AS pricedItems
                FROM services s ORDER BY active DESC, serviceName
                """),
            "items", repo.query("SELECT itemID AS id,itemName AS name FROM items ORDER BY itemName"),
            "prices", repo.query("""
                SELECT p.serviceID AS serviceId,p.itemID AS itemId,i.itemName AS item,p.price
                FROM servicePricing p JOIN items i ON i.itemID=p.itemID
                ORDER BY p.serviceID,i.itemName
                """));
    }

    @Transactional
    public int createService(Actor actor, ServiceInput input) {
        access.manager(actor);
        uniqueService(input.name(), null);
        int id = repo.insert("""
            INSERT INTO services(serviceName,description,turnaroundHours,active)
            OUTPUT INSERTED.serviceID VALUES (?,?,?,?)
            """, input.name().trim(), input.description().trim(), input.turnaroundHours(), input.active());
        repo.audit(null, actor.id(), "Service created", input.name().trim());
        return id;
    }

    @Transactional
    public void updateService(Actor actor, int id, ServiceInput input) {
        access.manager(actor);
        uniqueService(input.name(), id);
        SupportService.changed(repo.update("""
            UPDATE services SET serviceName=?,description=?,turnaroundHours=?,active=?,version=version+1
            WHERE serviceID=? AND version=?
            """, input.name().trim(), input.description().trim(), input.turnaroundHours(), input.active(), id, input.version()));
        repo.audit(null, actor.id(), "Service updated", input.name().trim());
    }

    @Transactional
    public void setPrice(Actor actor, int serviceId, PriceInput input) {
        access.manager(actor);
        if (repo.count("SELECT COUNT(*) FROM services WHERE serviceID=?", serviceId) != 1
                || repo.count("SELECT COUNT(*) FROM items WHERE itemID=?", input.itemId()) != 1)
            throw new ResponseStatusException(NOT_FOUND, "Service or item not found.");
        repo.update("""
            MERGE servicePricing AS target
            USING (SELECT ? AS serviceID,? AS itemID,? AS price) AS source
            ON target.serviceID=source.serviceID AND target.itemID=source.itemID
            WHEN MATCHED THEN UPDATE SET price=source.price
            WHEN NOT MATCHED THEN INSERT(serviceID,itemID,price) VALUES(source.serviceID,source.itemID,source.price);
            """, serviceId, input.itemId(), input.price());
        repo.audit(null, actor.id(), "Service price updated", "Service #" + serviceId + ", item #" + input.itemId());
    }

    public List<Map<String, Object>> staff(Actor actor, String search) {
        access.manager(actor);
        if (search != null && search.length() > 100) throw new ResponseStatusException(BAD_REQUEST, "Search must be at most 100 characters.");
        String term = search == null || search.isBlank() ? null : "%" + search.trim().replace("[", "[[]").replace("%", "[%]").replace("_", "[_]") + "%";
        return repo.query("""
            SELECT userID AS id,firstName,lastName,CONCAT(firstName,' ',lastName) AS name,email,
                   phoneNumber AS phone,UPPER(type) AS role,active,createdAt,updatedAt,version
            FROM users WHERE UPPER(type) IN ('RIDER','STAFF','CSM','MANAGER')
              AND (? IS NULL OR CONCAT(firstName,' ',lastName) LIKE ? OR email LIKE ?)
            ORDER BY active DESC,type,firstName,lastName
            """, term, term, term);
    }

    @Transactional
    public int createStaff(Actor actor, StaffInput input) {
        access.manager(actor);
        validateRole(input.role());
        if (input.password() == null || input.password().isBlank())
            throw new ResponseStatusException(BAD_REQUEST, "A temporary password is required for a new account.");
        try {
            int id = repo.insert("""
                INSERT INTO users(firstName,lastName,email,password,phoneNumber,type,active)
                OUTPUT INSERTED.userID VALUES (?,?,?,?,?,?,?)
                """, input.firstName().trim(), input.lastName().trim(), input.email().trim().toLowerCase(Locale.ROOT),
                    passwords.encode(input.password()), input.phone(), input.role(), input.active());
            repo.audit(null, actor.id(), "Staff account created", input.email().trim().toLowerCase(Locale.ROOT) + " · " + input.role());
            notifications.notifyUser(id, "ACCOUNT", "Operational account created", "Your " + input.role() + " account is ready.", "/html/portal.html", "ACCOUNT", id);
            return id;
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(CONFLICT, "An account with that email already exists.");
        }
    }

    @Transactional
    public void updateStaff(Actor actor, int id, StaffInput input) {
        access.manager(actor);
        validateRole(input.role());
        if (id == actor.id() && !input.active()) throw new ResponseStatusException(BAD_REQUEST, "You cannot deactivate your own account.");
        try {
            int changed;
            Object[] values = {input.firstName().trim(), input.lastName().trim(), input.email().trim().toLowerCase(Locale.ROOT),
                input.phone(), input.role(), input.active(), id, input.version()};
            if (input.password() == null || input.password().isBlank()) {
                changed = repo.update("""
                    UPDATE users SET firstName=?,lastName=?,email=?,phoneNumber=?,type=?,active=?,updatedAt=SYSDATETIME(),version=version+1
                    WHERE userID=? AND version=? AND UPPER(type) IN ('RIDER','STAFF','CSM','MANAGER')
                    """, values);
            } else {
                changed = repo.update("""
                    UPDATE users SET firstName=?,lastName=?,email=?,phoneNumber=?,type=?,active=?,password=?,updatedAt=SYSDATETIME(),version=version+1
                    WHERE userID=? AND version=? AND UPPER(type) IN ('RIDER','STAFF','CSM','MANAGER')
                    """, input.firstName().trim(), input.lastName().trim(), input.email().trim().toLowerCase(Locale.ROOT),
                    input.phone(), input.role(), input.active(), passwords.encode(input.password()), id, input.version());
            }
            SupportService.changed(changed);
            repo.audit(null, actor.id(), "Staff account updated", input.email().trim().toLowerCase(Locale.ROOT) + " · " + input.role() + " · " + (input.active() ? "Active" : "Inactive"));
            if (input.active()) notifications.notifyUser(id, "ACCOUNT", "Account access updated", "Your role or account details were updated.", "/html/portal.html", "ACCOUNT", id);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(CONFLICT, "An account with that email already exists.");
        }
    }

    public Map<String, Object> roles(Actor actor) {
        access.owner(actor);
        return Map.of("roles", List.of(
            Map.of("role", "OWNER", "label", "Shop Owner", "permissions", List.of("Executive reports", "Role assignment", "All administration")),
            Map.of("role", "MANAGER", "label", "Shop Manager", "permissions", List.of("Service catalogue", "Operational accounts", "Daily operations")),
            Map.of("role", "CSM", "label", "Customer Service Manager", "permissions", List.of("All support cases", "Order lookup", "Case resolution")),
            Map.of("role", "STAFF", "label", "Laundry Staff", "permissions", List.of("Assigned processing work", "Issue reporting")),
            Map.of("role", "RIDER", "label", "Delivery Rider", "permissions", List.of("Assigned transport tasks", "Transport status updates")),
            Map.of("role", "CUSTOMER", "label", "Customer", "permissions", List.of("Own orders", "Own payments", "Own support cases"))
        ));
    }

    public List<Map<String, Object>> activity(Actor actor) {
        access.manager(actor);
        return repo.query("""
            SELECT TOP 100 a.action,a.details,a.createdAt,CONCAT(u.firstName,' ',u.lastName) AS actor
            FROM support_activity a JOIN users u ON u.userID=a.actorID
            WHERE a.feedbackID IS NULL ORDER BY a.activityID DESC
            """);
    }

    private void uniqueService(String name, Integer excludedId) {
        int count = excludedId == null
            ? repo.count("SELECT COUNT(*) FROM services WHERE LOWER(serviceName)=LOWER(?)", name.trim())
            : repo.count("SELECT COUNT(*) FROM services WHERE LOWER(serviceName)=LOWER(?) AND serviceID<>?", name.trim(), excludedId);
        if (count > 0) throw new ResponseStatusException(CONFLICT, "A service with that name already exists.");
    }

    private void validateRole(String role) {
        if (!OPERATIONAL_ROLES.contains(role)) throw new ResponseStatusException(BAD_REQUEST, "Unsupported operational role.");
    }
}
