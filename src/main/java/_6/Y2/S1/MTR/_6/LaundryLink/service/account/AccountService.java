package _6.Y2.S1.MTR._6.LaundryLink.service.account;

import _6.Y2.S1.MTR._6.LaundryLink.dto.account.RegistrationRequest;
import _6.Y2.S1.MTR._6.LaundryLink.service.shared.NotificationService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;
import _6.Y2.S1.MTR._6.LaundryLink.dto.account.ProfileRequests;

@Service
public class AccountService {
    private final JdbcTemplate db;
    private final PasswordEncoder passwords;
    private final NotificationService notifications;

    public AccountService(JdbcTemplate db, PasswordEncoder passwords, NotificationService notifications) {
        this.db = db;
        this.passwords = passwords;
        this.notifications = notifications;
    }

    @Transactional
    public int registerCustomer(RegistrationRequest request) {
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
