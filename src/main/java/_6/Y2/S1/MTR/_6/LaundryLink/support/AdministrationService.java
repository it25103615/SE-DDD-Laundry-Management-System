/* AdministrationService
 * MAJOR COMPONENT ADMINISTRATION: service catalogue/prices and staff account CRUD. Role checks precede SQL. PasswordEncoder hashes account passwords. Version checks prevent overwriting newer changes; notifications communicate account updates. Change validation/business rules here, not in HTML.
 */
package _6.Y2.S1.MTR._6.LaundryLink.support;

import static org.springframework.http.HttpStatus.*;

import _6.Y2.S1.MTR._6.LaundryLink.notification.NotificationService;
import _6.Y2.S1.MTR._6.LaundryLink.support.SupportAccess.Actor;
import _6.Y2.S1.MTR._6.LaundryLink.support.dto.SupportRequests.ItemInput;
import _6.Y2.S1.MTR._6.LaundryLink.support.dto.SupportRequests.PriceInput;
import _6.Y2.S1.MTR._6.LaundryLink.support.dto.SupportRequests.ServiceInput;
import _6.Y2.S1.MTR._6.LaundryLink.support.dto.SupportRequests.StaffInput;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
// Run database reads in a read-only transaction.
@Transactional(readOnly = true)
public class AdministrationService {

  private static final Set<String> OPERATIONAL_ROLES = Set.of("RIDER", "STAFF", "CSM", "MANAGER");
  private final SupportRepository repo;
  private final SupportAccess access;
  // OOP — Abstraction: depend on the PasswordEncoder interface, so the caller does not need the
  // concrete hashing implementation.
  private final PasswordEncoder passwords;
  private final NotificationService notifications;

  public AdministrationService(
    SupportRepository repo,
    SupportAccess access,
    PasswordEncoder passwords,
    NotificationService notifications
  ) {
    this.repo = repo;
    this.access = access;
    this.passwords = passwords;
    this.notifications = notifications;
  }

  // READ ONLY: catalogue, prices and items for administration forms.
  public Map<String, Object> catalog(Actor actor) {
    access.manager(actor);
    return Map.of(
      "services",
      repo.query(
        """
        SELECT serviceID AS id, serviceName AS name, description, turnaroundHours, active, version,
               (SELECT COUNT(*) FROM servicePricing p WHERE p.serviceID=s.serviceID) AS pricedItems
        FROM services s ORDER BY active DESC, serviceName
        """
      ),
      "items",
      repo.query("SELECT itemID AS id,itemName AS name FROM items ORDER BY itemName"),
      "prices",
      repo.query(
        """
        SELECT p.serviceID AS serviceId,p.itemID AS itemId,i.itemName AS item,p.price
        FROM servicePricing p JOIN items i ON i.itemID=p.itemID
        ORDER BY p.serviceID,i.itemName
        """
      )
    );
  }

  // Commit these database changes together, or roll them back if the operation fails.
  @Transactional
  // DATABASE CREATE: validate manager input and insert a services row with audit.
  public int createService(Actor actor, ServiceInput input) {
    access.manager(actor);
    uniqueService(input.name(), null);
    int id = repo.insert(
      """
      INSERT INTO services(serviceName,description,turnaroundHours,active)
      OUTPUT INSERTED.serviceID VALUES (?,?,?,?)
      """,
      input.name().trim(),
      input.description().trim(),
      input.turnaroundHours(),
      input.active()
    );
    // Record who performed this action in the audit history.
    repo.audit(null, actor.id(), "Service created", input.name().trim());
    return id;
  }

  // Commit these database changes together, or roll them back if the operation fails.
  @Transactional
  // DATABASE UPDATE: services fields are updated using expected version; preserves
  // concurrent-change protection.
  public void updateService(Actor actor, int id, ServiceInput input) {
    access.manager(actor);
    uniqueService(input.name(), id);
    // Require the database write to affect the expected row; reject stale updates.
    SupportService.changed(
      repo.update(
        """
        UPDATE services SET serviceName=?,description=?,turnaroundHours=?,active=?,version=version+1
        WHERE serviceID=? AND version=?
        """,
        input.name().trim(),
        input.description().trim(),
        input.turnaroundHours(),
        input.active(),
        id,
        input.version()
      )
    );
    // Record who performed this action in the audit history.
    repo.audit(null, actor.id(), "Service updated", input.name().trim());
  }

  // Commit these database changes together, or roll them back if the operation fails.
  @Transactional
  // DATABASE WRITE: validate catalogue item/service and update or insert servicePricing.
  public void setPrice(Actor actor, int serviceId, PriceInput input) {
    access.manager(actor);
    if (
      repo.count("SELECT COUNT(*) FROM services WHERE serviceID=?", serviceId) != 1 ||
      repo.count("SELECT COUNT(*) FROM items WHERE itemID=?", input.itemId()) != 1
    ) throw new ResponseStatusException(NOT_FOUND, "Service or item not found.");
    repo.update(
      """
      MERGE servicePricing AS target
      USING (SELECT ? AS serviceID,? AS itemID,? AS price) AS source
      ON target.serviceID=source.serviceID AND target.itemID=source.itemID
      WHEN MATCHED THEN UPDATE SET price=source.price
      WHEN NOT MATCHED THEN INSERT(serviceID,itemID,price) VALUES(source.serviceID,source.itemID,source.price);
      """,
      serviceId,
      input.itemId(),
      input.price()
    );
    // Record who performed this action in the audit history.
    repo.audit(
      null,
      actor.id(),
      "Service price updated",
      "Service #" + serviceId + ", item #" + input.itemId()
    );
  }

  // Adds an item to a service by name. Items are shared between services, so a name that already
  // exists (ignoring case) is reused and only priced for this service; otherwise a new item is
  // created first. An item that is already in the service is rejected: its price is changed with
  // setPrice. (A page that lets the manager pick an existing item instead of typing its name
  // needs no new method here: setPrice already adds a known item to a service.)
  @Transactional
  public int addItem(Actor actor, int serviceId, ItemInput input) {
    access.manager(actor);
    if (
      repo.count("SELECT COUNT(*) FROM services WHERE serviceID=?", serviceId) != 1
    ) throw new ResponseStatusException(NOT_FOUND, "Service not found.");
    String name = input.name().trim();
    var existing = repo.query(
      "SELECT TOP(1) itemID AS id FROM items WHERE LOWER(itemName)=LOWER(?) ORDER BY itemID",
      name
    );
    int itemId = existing.isEmpty()
      ? repo.insert("INSERT INTO items(itemName) OUTPUT INSERTED.itemID VALUES (?)", name)
      : ((Number) existing.getFirst().get("id")).intValue();
    if (
      repo.count(
        "SELECT COUNT(*) FROM servicePricing WHERE serviceID=? AND itemID=?",
        serviceId,
        itemId
      ) > 0
    ) throw new ResponseStatusException(
      CONFLICT,
      "That item is already in this service. Use Edit items to change its price."
    );
    repo.update(
      "INSERT INTO servicePricing(serviceID,itemID,price) VALUES (?,?,?)",
      serviceId,
      itemId,
      input.price()
    );
    repo.audit(null, actor.id(), "Service item added", "Service #" + serviceId + ", " + name);
    return itemId;
  }

  public List<Map<String, Object>> staff(Actor actor, String search) {
    access.manager(actor);
    if (search != null && search.length() > 100) throw new ResponseStatusException(
      BAD_REQUEST,
      "Search must be at most 100 characters."
    );
    String term =
      search == null || search.isBlank()
        ? null
        : "%" + search.trim().replace("[", "[[]").replace("%", "[%]").replace("_", "[_]") + "%";
    return repo.query(
      """
      SELECT userID AS id,firstName,lastName,CONCAT(firstName,' ',lastName) AS name,email,
             phoneNumber AS phone,UPPER(type) AS role,active,createdAt,updatedAt,version
      FROM users WHERE UPPER(type) IN ('RIDER','STAFF','CSM','MANAGER')
        AND email NOT LIKE 'deleted[_]%@deleted.invalid'
        AND (? IS NULL OR CONCAT(firstName,' ',lastName) LIKE ? OR email LIKE ?)
      ORDER BY active DESC,type,firstName,lastName
      """,
      term,
      term,
      term
    );
  }

  // Commit these database changes together, or roll them back if the operation fails.
  @Transactional
  // DATABASE CREATE: role checks, email validation, password hashing, users insertion and account
  // notification.
  public int createStaff(Actor actor, StaffInput input) {
    access.manager(actor);
    validateRole(actor, input.role());
    if (input.password() == null || input.password().isBlank()) throw new ResponseStatusException(
      BAD_REQUEST,
      "A temporary password is required for a new account."
    );
    try {
      int id = repo.insert(
        """
        INSERT INTO users(firstName,lastName,email,password,phoneNumber,type,active)
        OUTPUT INSERTED.userID VALUES (?,?,?,?,?,?,?)
        """,
        input.firstName().trim(),
        input.lastName().trim(),
        input.email().trim().toLowerCase(Locale.ROOT),
        // OOP — Runtime polymorphism: this interface call runs the configured encoder
        // implementation, BCrypt in this application.
        passwords.encode(input.password()),
        input.phone(),
        input.role(),
        input.active()
      );
      // Record who performed this action in the audit history.
      repo.audit(
        null,
        actor.id(),
        "Staff account created",
        input.email().trim().toLowerCase(Locale.ROOT) + " · " + input.role()
      );
      // Create a notification for the affected user or role.
      notifications.notifyUser(
        id,
        "ACCOUNT",
        "Operational account created",
        "Your " + input.role() + " account is ready.",
        "/html/portal.html",
        "ACCOUNT",
        id
      );
      return id;
    } catch (DuplicateKeyException exception) {
      throw new ResponseStatusException(CONFLICT, "An account with that email already exists.");
    }
  }

  // Commit these database changes together, or roll them back if the operation fails.
  @Transactional
  // DATABASE UPDATE: role/account/active flags with version protection; disabling access is
  // different from deleting records.
  public void updateStaff(Actor actor, int id, StaffInput input) {
    throw new ResponseStatusException(
      FORBIDDEN,
      "Use Change Role or Delete Account. Personal details are managed by the employee."
    );
  }

  private void managedEmployee(Actor actor, int id, Integer version) {
    access.manager(actor);
    if (id == actor.id()) throw new ResponseStatusException(
      FORBIDDEN,
      "You cannot manage your own role or delete your own account."
    );
    if (version == null || version < 0) throw new ResponseStatusException(
      BAD_REQUEST,
      "An account version is required."
    );
    // Keep target authorization and version stable until the transaction commits.
    var rows = repo.query(
      "SELECT UPPER(type) AS role,email,version FROM users WITH (UPDLOCK, HOLDLOCK) WHERE userID=?",
      id
    );
    if (rows.isEmpty()) throw new ResponseStatusException(NOT_FOUND, "Employee account not found.");
    var target = rows.getFirst();
    String role = (String) target.get("role");
    if (
      !OPERATIONAL_ROLES.contains(role) || (!actor.owner() && "MANAGER".equals(role))
    ) throw new ResponseStatusException(FORBIDDEN, "You cannot manage this account.");
    if (
      _6.Y2.S1.MTR._6.LaundryLink.account.AccountSessionGuard.isRetiredEmail(
        (String) target.get("email")
      )
    ) throw new ResponseStatusException(CONFLICT, "This account has already been deleted.");
    if (((Number) target.get("version")).intValue() != version) throw new ResponseStatusException(
      CONFLICT,
      "Account changed. Refresh the list and try again."
    );
  }

  @Transactional
  public void changeStaffRole(Actor actor, int id, String role, Integer version) {
    managedEmployee(actor, id, version);
    validateRole(actor, role);
    SupportService.changed(
      repo.update(
        "UPDATE users SET type=?,updatedAt=SYSDATETIME(),version=version+1 WHERE userID=? AND version=?",
        role,
        id,
        version
      )
    );
    repo.audit(null, actor.id(), "Employee role changed", "Account #" + id + " · " + role);
  }

  @Transactional
  public void deleteStaff(Actor actor, int id, Integer version) {
    managedEmployee(actor, id, version);
    String retiredEmail = null;
    for (int attempt = 0; attempt < 5; attempt++) {
      String candidate =
        "deleted_" + id + "_" + UUID.randomUUID().toString().replace("-", "") + "@deleted.invalid";
      if (
        repo.count(
          "SELECT COUNT(*) FROM users WITH (UPDLOCK, HOLDLOCK) WHERE email=?",
          candidate
        ) == 0
      ) {
        retiredEmail = candidate;
        break;
      }
    }
    if (retiredEmail == null) throw new ResponseStatusException(
      CONFLICT,
      "Account could not be deleted. Try again."
    );
    // Preserve the ID and related records; never retain or disclose the random secret.
    SupportService.changed(
      repo.update(
        "UPDATE users SET active=0,email=?,password=?,updatedAt=SYSDATETIME(),version=version+1 WHERE userID=? AND version=?",
        retiredEmail,
        passwords.encode(UUID.randomUUID().toString()),
        id,
        version
      )
    );
    repo.audit(null, actor.id(), "Employee account deleted", "Account #" + id);
  }

  // READ ONLY: role capability summary for administration.
  public Map<String, Object> roles(Actor actor) {
    access.owner(actor);
    return Map.of(
      "roles",
      List.of(
        Map.of(
          "role",
          "OWNER",
          "label",
          "Shop Owner",
          "permissions",
          List.of("Executive reports", "Role assignment", "All administration")
        ),
        Map.of(
          "role",
          "MANAGER",
          "label",
          "Shop Manager",
          "permissions",
          List.of("Service catalogue", "Operational accounts", "Daily operations")
        ),
        Map.of(
          "role",
          "CSM",
          "label",
          "Customer Service Manager",
          "permissions",
          List.of("All support cases", "Order lookup", "Case resolution")
        ),
        Map.of(
          "role",
          "STAFF",
          "label",
          "Laundry Staff",
          "permissions",
          List.of("Assigned processing work", "Issue reporting")
        ),
        Map.of(
          "role",
          "RIDER",
          "label",
          "Delivery Rider",
          "permissions",
          List.of("Assigned transport tasks", "Transport status updates")
        ),
        Map.of(
          "role",
          "CUSTOMER",
          "label",
          "Customer",
          "permissions",
          List.of("Own orders", "Own payments", "Own support cases")
        )
      )
    );
  }

  // READ ONLY: recent administration audit entries.
  public List<Map<String, Object>> activity(Actor actor) {
    access.manager(actor);
    return repo.query(
      """
      SELECT TOP 100 a.action,a.details,a.createdAt,CONCAT(u.firstName,' ',u.lastName) AS actor
      FROM support_activity a JOIN users u ON u.userID=a.actorID
      WHERE a.feedbackID IS NULL ORDER BY a.activityID DESC
      """
    );
  }

  private void uniqueService(String name, Integer excludedId) {
    int count =
      excludedId == null
        ? repo.count("SELECT COUNT(*) FROM services WHERE LOWER(serviceName)=LOWER(?)", name.trim())
        : repo.count(
            "SELECT COUNT(*) FROM services WHERE LOWER(serviceName)=LOWER(?) AND serviceID<>?",
            name.trim(),
            excludedId
          );
    if (count > 0) throw new ResponseStatusException(
      CONFLICT,
      "A service with that name already exists."
    );
  }

  private void validateRole(Actor actor, String role) {
    if (!OPERATIONAL_ROLES.contains(role)) throw new ResponseStatusException(
      BAD_REQUEST,
      "Unsupported operational role."
    );
    if (!"OWNER".equals(actor.role()) && "MANAGER".equals(role)) throw new ResponseStatusException(
      FORBIDDEN,
      "Only the Owner can assign the Manager role."
    );
  }
}
