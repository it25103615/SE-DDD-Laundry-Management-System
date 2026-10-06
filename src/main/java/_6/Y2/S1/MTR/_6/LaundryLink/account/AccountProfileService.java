package _6.Y2.S1.MTR._6.LaundryLink.account;

import _6.Y2.S1.MTR._6.LaundryLink.account.dto.CustomerRegistrationRequest;
import _6.Y2.S1.MTR._6.LaundryLink.notification.NotificationService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;
import _6.Y2.S1.MTR._6.LaundryLink.account.dto.ProfileRequests;

@Service
public class AccountProfileService {
    private final JdbcTemplate db;
    private final PasswordEncoder passwords;
    private final NotificationService notifications;

    public AccountProfileService(JdbcTemplate db, PasswordEncoder passwords, NotificationService notifications) {
        this.db = db;
        this.passwords = passwords;
        this.notifications = notifications;
    }

    @Transactional
    public int registerCustomer(CustomerRegistrationRequest request) {
        String[] names = request.fullName().trim().split("\\s+", 2);
        String firstName = names[0];
        String lastName = names.length > 1 ? names[1] : "";
        Integer id;
        try {
            id = db.queryForObject("""
                    INSERT INTO users(firstName,lastName,email,password,phoneNumber,type)
                    OUTPUT INSERTED.userID VALUES (?,?,?,?,?,'CUSTOMER')
                    """, Integer.class, firstName, lastName, request.email().trim().toLowerCase(),
                    passwords.encode(request.password()), request.phone());
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("An account with this email already exists.");
        }
        if (request.address() != null && !request.address().isBlank()) {
            db.update("INSERT INTO addresses(nickname,street,isDefault,userID) VALUES ('Home',?,1,?)",
                    request.address().trim(), id);
        }
        notifications.notifyUser(id, "ACCOUNT", "Welcome to LaundryLink", "Your customer account was created successfully.", "/html/customer/dashboard.html", "ACCOUNT", id);
        return id;
    }

    public Map<String,Object> profile(String email) {
        var rows=db.queryForList("""
                SELECT userID AS id, CONCAT(firstName,' ',lastName) AS name,
                       email, phoneNumber AS phone, UPPER(type) AS role
                FROM users WHERE email=?
                """,email);
        if(rows.isEmpty()) throw new ResponseStatusException(UNAUTHORIZED,"Account not found. Sign in again.");
        return rows.getFirst();
    }

    @Transactional
    public Map<String,Object> updateProfile(String signedInEmail, ProfileRequests.Details input) {
        int id=((Number)profile(signedInEmail).get("id")).intValue();
        String[] names=input.fullName().trim().split("\\s+",2);
        if(names[0].length()>50 || (names.length>1 && names[1].length()>50))
            throw new ResponseStatusException(BAD_REQUEST,"First and last name must each be at most 50 characters.");
        String nextEmail=input.email().trim().toLowerCase(Locale.ROOT);
        if(db.queryForObject("SELECT COUNT(*) FROM users WHERE email=? AND userID<>?",Integer.class,nextEmail,id)>0)
            throw new ResponseStatusException(CONFLICT,"That email address belongs to another account.");
        db.update("UPDATE users SET firstName=?,lastName=?,email=?,phoneNumber=? WHERE userID=?",
                names[0],names.length>1?names[1]:"",nextEmail,input.phone(),id);
        var saved=profile(nextEmail);
        var result=new LinkedHashMap<String,Object>(saved);
        result.put("emailChanged",!signedInEmail.equalsIgnoreCase(nextEmail));
        notifications.notifyUser(id, "ACCOUNT", "Profile updated", "Your LaundryLink profile details were updated.", "/html/account/profile.html", "ACCOUNT", id);
        return result;
    }

    @Transactional
    public void deactivateAccount(String signedInEmail, ProfileRequests.Deletion input) {
        // Keep the verified password and role stable until the transaction commits.
        var rows = db.queryForList("SELECT userID AS id,password,UPPER(type) AS role FROM users WITH (UPDLOCK, HOLDLOCK) WHERE email=? AND active=1", signedInEmail);
        if (rows.isEmpty()) throw new ResponseStatusException(UNAUTHORIZED, "Account not found or already inactive.");
        var user = rows.getFirst();
        if (!"CUSTOMER".equals(user.get("role")))
            throw new ResponseStatusException(FORBIDDEN, "Only customers can deactivate their own account.");
        if (!passwords.matches(input.currentPassword(), (String) user.get("password")))
            throw new ResponseStatusException(BAD_REQUEST, "Current password is incorrect.");
        String retiredEmail = replacementEmail(((Number) user.get("id")).intValue());
        // The random secret is never retained or disclosed; the previous password no longer matches.
        String retiredPassword = passwords.encode(UUID.randomUUID().toString());
        int changed = db.update("UPDATE users SET active=0,email=?,password=? WHERE userID=? AND active=1 AND UPPER(type)='CUSTOMER'",
                retiredEmail, retiredPassword, user.get("id"));
        if (changed != 1) throw new ResponseStatusException(CONFLICT, "Account could not be deactivated. Try again.");
    }

    private String replacementEmail(int userId) {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = "deleted_" + userId + "_" + UUID.randomUUID().toString().replace("-", "") + "@deleted.invalid";
            // Range locking reserves an unused candidate until this transaction commits.
            Integer count = db.queryForObject("SELECT COUNT(*) FROM users WITH (UPDLOCK, HOLDLOCK) WHERE email=?", Integer.class, candidate);
            if (Integer.valueOf(0).equals(count)) return candidate;
        }
        throw new ResponseStatusException(CONFLICT, "Account could not be deactivated. Try again.");
    }

    @Transactional
    public void updatePassword(String signedInEmail, ProfileRequests.Password input) {
        if(!input.newPassword().equals(input.confirmPassword()))
            throw new ResponseStatusException(BAD_REQUEST,"New passwords do not match.");
        var rows=db.queryForList("SELECT userID AS id,password FROM users WHERE email=?",signedInEmail);
        if(rows.isEmpty()) throw new ResponseStatusException(UNAUTHORIZED,"Account not found. Sign in again.");
        var row=rows.getFirst();
        if(!passwords.matches(input.currentPassword(),String.valueOf(row.get("password"))))
            throw new ResponseStatusException(BAD_REQUEST,"Current password is incorrect.");
        db.update("UPDATE users SET password=? WHERE userID=?",passwords.encode(input.newPassword()),row.get("id"));
        notifications.notifyUser(((Number)row.get("id")).intValue(), "SECURITY", "Password changed", "Your LaundryLink password was changed.", "/html/account/profile.html", "ACCOUNT", ((Number)row.get("id")).intValue());
    }
}
