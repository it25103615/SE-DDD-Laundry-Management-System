package _6.Y2.S1.MTR._6.LaundryLink.account;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

/** Real SQL Server reset, expiry and token reuse checks; all changes roll back. No emails are sent. */
@SpringBootTest
@Transactional
class PasswordRecoveryIntegrationTest {
    @Autowired JdbcTemplate db;
    @Autowired PasswordRecoveryService recovery;
    @Autowired PasswordEncoder passwords;

    int customer() { return db.queryForObject("SELECT userID FROM users WHERE email='anna@customer.com'",Integer.class); }
    String token() { return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(UUID.randomUUID().toString().substring(0,32).getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    void insert(String token,int user,int expiryMinutes) {
        db.update("INSERT INTO passwordResetTokens(tokenHash,userID,createdAt,expiresAt) VALUES (?,?,DATEADD(MINUTE,-20,SYSUTCDATETIME()),DATEADD(MINUTE,?,SYSUTCDATETIME()))",PasswordRecoveryService.digest(token),user,expiryMinutes);
    }
    @Test void changesHashAndRejectsReuse() {
        int user=customer(); String token=token(); insert(token,user,15);
        recovery.reset(token,"Fresh#Pass123","Fresh#Pass123");
        assertTrue(passwords.matches("Fresh#Pass123",db.queryForObject("SELECT password FROM users WHERE userID=?",String.class,user)));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM passwordResetTokens WHERE userID=?",Integer.class,user));
        assertThrows(ResponseStatusException.class,()->recovery.reset(token,"Other#Pass123","Other#Pass123"));
    }
    @Test void expiredLinkDoesNotChangePassword() {
        int user=customer(); String before=db.queryForObject("SELECT password FROM users WHERE userID=?",String.class,user);
        String token=token(); insert(token,user,-1);
        assertThrows(ResponseStatusException.class,()->recovery.reset(token,"Fresh#Pass123","Fresh#Pass123"));
        assertEquals(before,db.queryForObject("SELECT password FROM users WHERE userID=?",String.class,user));
    }
    @Test void inactiveAccountCannotResetPassword() {
        int user=customer(); String token=token(); insert(token,user,15);
        db.update("UPDATE users SET active=0 WHERE userID=?",user);
        assertThrows(ResponseStatusException.class,()->recovery.reset(token,"Fresh#Pass123","Fresh#Pass123"));
    }
}
