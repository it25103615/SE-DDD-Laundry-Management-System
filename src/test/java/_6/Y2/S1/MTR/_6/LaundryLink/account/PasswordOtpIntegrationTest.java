package _6.Y2.S1.MTR._6.LaundryLink.account;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties = "laundrylink.recovery.from=laundry@example.com")
@Transactional
class PasswordOtpIntegrationTest {
  @Autowired PasswordRecoveryService recovery;
  @Autowired JdbcTemplate db;
  @Autowired PasswordEncoder passwords;
  @MockitoBean JavaMailSender sender;

  String userEmail() {
    return db.queryForObject("SELECT TOP 1 email FROM users WHERE active=1 ORDER BY userID", String.class);
  }
  String[] request() {
    String email = userEmail();
    db.update("DELETE FROM passwordRecoveryCodes WHERE emailHash=?", PasswordRecoveryService.digest(email.toLowerCase()));
    String challenge = recovery.request(email);
    var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(sender).send(message.capture());
    assertArrayEquals(new String[]{email}, message.getValue().getTo());
    String code = message.getValue().getText().split("code is: ")[1].split("\\n")[0];
    return new String[]{challenge, code};
  }
  @Test void verifiedCodeChangesPasswordAndCannotBeReused() {
    String email = userEmail();
    String[] sent = request();
    String token = recovery.verify(sent[0], sent[1]);
    assertThrows(ResponseStatusException.class, () -> recovery.verify(sent[0], sent[1]));
    recovery.reset(token, "Fresh#Pass123", "Fresh#Pass123");
    assertTrue(passwords.matches("Fresh#Pass123",
        db.queryForObject("SELECT password FROM users WHERE email=?", String.class, email)));
    assertThrows(ResponseStatusException.class, () -> recovery.reset(token, "Fresh#Pass456", "Fresh#Pass456"));
  }
  @Test void fiveIncorrectAttemptsBlockEvenCorrectCode() {
    String[] sent = request();
    String wrong = sent[1].equals("000000") ? "111111" : "000000";
    for (int i=0; i<5; i++) assertThrows(ResponseStatusException.class, () -> recovery.verify(sent[0], wrong));
    assertEquals(5, db.queryForObject("SELECT attempts FROM passwordRecoveryCodes WHERE challengeHash=?",
        Integer.class, PasswordRecoveryService.digest(sent[0])));
    assertThrows(ResponseStatusException.class, () -> recovery.verify(sent[0], sent[1]));
  }
  @Test void expiredCodeIsRejected() {
    String[] sent = request();
    db.update("UPDATE passwordRecoveryCodes SET createdAt=DATEADD(MINUTE,-20,SYSUTCDATETIME()),"
        + " expiresAt=DATEADD(MINUTE,-1,SYSUTCDATETIME()) WHERE challengeHash=?", PasswordRecoveryService.digest(sent[0]));
    assertThrows(ResponseStatusException.class, () -> recovery.verify(sent[0], sent[1]));
  }
  @Test void resendCooldownAlsoAppliesToUnknownEmails() {
    recovery.request("missing-account@example.invalid");
    var error = assertThrows(ResponseStatusException.class,
        () -> recovery.request("missing-account@example.invalid"));
    assertEquals(429, error.getStatusCode().value());
    verifyNoInteractions(sender);
  }
}
