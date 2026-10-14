package _6.Y2.S1.MTR._6.LaundryLink.account;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

/** Only a digest is persisted; raw reset credentials exist in the recipient's email. */
@Service
public class PasswordRecoveryService {
    private final JdbcTemplate db;
    private final PasswordEncoder passwords;
    private final ObjectProvider<JavaMailSender> mail;
    private final String baseUrl;
    private final String from;
    private final SecureRandom random = new SecureRandom();

    public PasswordRecoveryService(JdbcTemplate db, PasswordEncoder passwords,
            ObjectProvider<JavaMailSender> mail,
            @Value("${laundrylink.recovery.base-url:http://localhost:8080}") String baseUrl,
            @Value("${laundrylink.recovery.from:}") String from) {
        this.db=db; this.passwords=passwords; this.mail=mail;
        this.baseUrl=baseUrl.replaceAll("/+$", ""); this.from=from;
        URI uri=URI.create(this.baseUrl);
        if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null
                || uri.getQuery()!=null || uri.getFragment()!=null)
            throw new IllegalArgumentException("Recovery base URL must be an absolute HTTP(S) application URL.");
    }

    @Transactional
    public void request(String email) {
        JavaMailSender sender=mail.getIfAvailable();
        if (sender==null || from.isBlank()) throw new ResponseStatusException(SERVICE_UNAVAILABLE,
                "Email recovery is not configured. Contact the administrator.");
        var users=db.queryForList("SELECT userID FROM users WITH (UPDLOCK, HOLDLOCK) WHERE email=? AND active=1",
                email.trim().toLowerCase(Locale.ROOT));
        // Same response for unknown, inactive and existing accounts.
        if (users.isEmpty()) return;
        int id=((Number)users.getFirst().get("userID")).intValue();
        Integer recent=db.queryForObject("SELECT COUNT(*) FROM passwordResetTokens WHERE userID=? AND createdAt>DATEADD(SECOND,-60,SYSUTCDATETIME())", Integer.class,id);
        if (recent!=null && recent>0) return;
        byte[] bytes=new byte[32]; random.nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        db.update("DELETE FROM passwordResetTokens WHERE userID=? OR expiresAt<=SYSUTCDATETIME()",id);
        db.update("INSERT INTO passwordResetTokens(tokenHash,userID,expiresAt) VALUES (?,?,DATEADD(MINUTE,15,SYSUTCDATETIME()))",digest(token),id);
        SimpleMailMessage message=new SimpleMailMessage();
        message.setFrom(from); message.setTo(email.trim());
        message.setSubject("Reset your LaundryLink password");
        // Fragment keeps the raw token out of web server URL logs and referrers.
        message.setText("Open this link to choose a new password:\n"+baseUrl+"/html/auth/reset_password.html#token="+token
                +"\n\nThis single-use link expires in 15 minutes. If you did not request it, ignore this email.");
        try { sender.send(message); }
        catch (MailException exception) { throw new ResponseStatusException(SERVICE_UNAVAILABLE,
                "The recovery email could not be sent. Please try again later."); }
    }

    @Transactional
    public void reset(String token, String password, String confirmation) {
        if (!password.equals(confirmation)) throw new ResponseStatusException(BAD_REQUEST,"Passwords do not match.");
        var changed=db.queryForList("""
                UPDATE u WITH (UPDLOCK, HOLDLOCK) SET password=?,updatedAt=SYSDATETIME(),version=version+1
                OUTPUT INSERTED.userID
                FROM users u JOIN passwordResetTokens t WITH (UPDLOCK, HOLDLOCK) ON t.userID=u.userID
                WHERE t.tokenHash=? AND t.expiresAt>SYSUTCDATETIME() AND u.active=1
                """, passwords.encode(password),digest(token));
        if (changed.isEmpty()) throw new ResponseStatusException(BAD_REQUEST,
                "This reset link is invalid or expired. Request a new email.");
        int id=((Number)changed.getFirst().get("userID")).intValue();
        db.update("DELETE FROM passwordResetTokens WHERE userID=?",id);
    }

    static String digest(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
