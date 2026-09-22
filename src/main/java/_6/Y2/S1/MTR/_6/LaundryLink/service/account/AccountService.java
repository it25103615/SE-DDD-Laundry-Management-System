package _6.Y2.S1.MTR._6.LaundryLink.service.account;

import _6.Y2.S1.MTR._6.LaundryLink.dto.account.RegistrationRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final JdbcTemplate db;
    private final PasswordEncoder passwords;

    public AccountService(JdbcTemplate db, PasswordEncoder passwords) {
        this.db = db;
        this.passwords = passwords;
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
        return id;
    }
}
