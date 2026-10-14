/* PasswordRecoveryService
 * SERVICE: business rules and orchestration. Constructor-injected collaborators show composition and dependency injection; private fields encapsulate dependencies.
 */
package _6.Y2.S1.MTR._6.LaundryLink.account;

import static org.springframework.http.HttpStatus.*;

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

/** Only a digest is persisted; raw reset credentials exist in the recipient's email. */
@Service
public class PasswordRecoveryService {
  private final JdbcTemplate db;
  // OOP — Abstraction: depend on the PasswordEncoder interface, so the caller does not need the
  // concrete hashing implementation.
  private final PasswordEncoder passwords;
  private final ObjectProvider<JavaMailSender> mail;
  private final String baseUrl;
  private final String from;
  private final SecureRandom random = new SecureRandom();

  public PasswordRecoveryService(
      JdbcTemplate db,
      PasswordEncoder passwords,
      ObjectProvider<JavaMailSender> mail,
      @Value("${laundrylink.recovery.base-url:http://localhost:8080}") String baseUrl,
      @Value("${laundrylink.recovery.from:}") String from) {
    this.db = db;
    this.passwords = passwords;
    this.mail = mail;
    this.baseUrl = baseUrl.replaceAll("/+$", "");
    this.from = from;
    URI uri = URI.create(this.baseUrl);
    if (!Set.of("http", "https").contains(uri.getScheme())
        || uri.getHost() == null
        || uri.getUserInfo() != null
        || uri.getQuery() != null
        || uri.getFragment() != null)
      throw new IllegalArgumentException(
          "Recovery base URL must be an absolute HTTP(S) application URL.");
  }

  // Commit these database changes together, or roll them back if the operation fails.
  @Transactional
  public String request(String email) {
    JavaMailSender sender = mail.getIfAvailable();
    if (sender == null || from.isBlank())
      throw new ResponseStatusException(
          SERVICE_UNAVAILABLE, "Email recovery is not configured. Contact the administrator.");
    String normalized = email.trim().toLowerCase(Locale.ROOT);
    Integer recent = db.queryForObject(
        "SELECT COUNT(*) FROM passwordRecoveryCodes WITH (UPDLOCK, HOLDLOCK) WHERE emailHash=?"
            + " AND createdAt>DATEADD(SECOND,-60,SYSUTCDATETIME())", Integer.class, digest(normalized));
    if (recent != null && recent > 0)
      throw new ResponseStatusException(TOO_MANY_REQUESTS, "Wait 60 seconds before requesting another code.");
    var users =
        db.queryForList(
            "SELECT userID,email FROM users WITH (UPDLOCK, HOLDLOCK) WHERE email=? AND active=1",
            normalized);
    // Same response for unknown, inactive and existing accounts.
    Integer id = users.isEmpty() ? null : ((Number) users.getFirst().get("userID")).intValue();
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
    db.update("DELETE FROM passwordRecoveryCodes WHERE emailHash=? OR expiresAt<=SYSUTCDATETIME()", digest(normalized));
    if (id != null) db.update("DELETE FROM passwordResetTokens WHERE userID=?", id);
    db.update(
        "INSERT INTO passwordRecoveryCodes(challengeHash,emailHash,userID,otpHash,expiresAt) VALUES"
            + " (?,?,?,?,DATEADD(MINUTE,10,SYSUTCDATETIME()))",
        digest(challenge), digest(normalized), id, digest(challenge + ":" + code));
    if (id == null) return challenge;
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(String.valueOf(users.getFirst().getOrDefault("email", email.trim())));
    message.setSubject("Your LaundryLink password reset code");
    message.setText(
        "Your LaundryLink verification code is: " + code
            + "\n\nEnter this code in the password recovery page. It expires in 10 minutes and"
            + " can only be used once. If you did not request it, ignore this email."
            + " Never share this code with anyone.");
    try {
      sender.send(message);
    } catch (MailException exception) {
      throw new ResponseStatusException(
          SERVICE_UNAVAILABLE, "The recovery email could not be sent. Please try again later.");
    }
    return challenge;
  }

  /** Failed attempts must commit so rejected guesses cannot reset the attempt limit. */
  @Transactional(noRollbackFor = ResponseStatusException.class)
  public String verify(String challenge, String code) {
    String hash = digest(challenge);
    var rows = db.queryForList(
        "SELECT c.userID,c.otpHash FROM passwordRecoveryCodes c WITH (UPDLOCK, HOLDLOCK)"
            + " JOIN users u ON u.userID=c.userID WHERE c.challengeHash=? AND c.attempts<5"
            + " AND c.expiresAt>SYSUTCDATETIME() AND u.active=1", hash);
    if (rows.isEmpty()) throw invalidCode();
    if (!MessageDigest.isEqual(
        digest(challenge + ":" + code).getBytes(StandardCharsets.UTF_8),
        String.valueOf(rows.getFirst().get("otpHash")).getBytes(StandardCharsets.UTF_8))) {
      db.update("UPDATE passwordRecoveryCodes SET attempts=attempts+1 WHERE challengeHash=?", hash);
      throw invalidCode();
    }
    int id = ((Number) rows.getFirst().get("userID")).intValue();
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    db.update("DELETE FROM passwordResetTokens WHERE userID=?", id);
    db.update("INSERT INTO passwordResetTokens(tokenHash,userID,expiresAt) VALUES"
        + " (?,?,DATEADD(MINUTE,10,SYSUTCDATETIME()))", digest(token), id);
    db.update("DELETE FROM passwordRecoveryCodes WHERE challengeHash=?", hash);
    return token;
  }

  private ResponseStatusException invalidCode() {
    return new ResponseStatusException(BAD_REQUEST,
        "Invalid or expired code, or too many incorrect attempts. Request a new code.");
  }

  // Commit these database changes together, or roll them back if the operation fails.
  @Transactional
  public void reset(String token, String password, String confirmation) {
    if (!password.equals(confirmation))
      throw new ResponseStatusException(BAD_REQUEST, "Passwords do not match.");
    var changed =
        db.queryForList(
            """
            UPDATE u WITH (UPDLOCK, HOLDLOCK) SET password=?,updatedAt=SYSDATETIME(),version=version+1
            OUTPUT INSERTED.userID
            FROM users u JOIN passwordResetTokens t WITH (UPDLOCK, HOLDLOCK) ON t.userID=u.userID
            WHERE t.tokenHash=? AND t.expiresAt>SYSUTCDATETIME() AND u.active=1
            """,
            // OOP — Runtime polymorphism: this interface call runs the configured encoder
            // implementation, BCrypt in this application.
            passwords.encode(password),
            digest(token));
    if (changed.isEmpty())
      throw new ResponseStatusException(
          BAD_REQUEST, "Your password reset session is invalid or expired. Request a new code.");
    int id = ((Number) changed.getFirst().get("userID")).intValue();
    db.update("DELETE FROM passwordResetTokens WHERE userID=?", id);
  }

  static String digest(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
