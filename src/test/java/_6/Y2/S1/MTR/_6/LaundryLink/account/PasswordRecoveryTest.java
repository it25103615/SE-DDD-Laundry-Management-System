package _6.Y2.S1.MTR._6.LaundryLink.account;

import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PasswordRecoveryTest {
    JdbcTemplate db;
    JavaMailSender sender;
    ObjectProvider<JavaMailSender> provider;
    PasswordRecoveryService service;

    @SuppressWarnings("unchecked")
    @BeforeEach void setup() {
        db=mock(JdbcTemplate.class); sender=mock(JavaMailSender.class);
        provider=mock(ObjectProvider.class); when(provider.getIfAvailable()).thenReturn(sender);
        service=new PasswordRecoveryService(db,new BCryptPasswordEncoder(4),provider,"http://localhost:8080","laundry@example.com");
    }
    void existing() {
        when(db.queryForList(contains("SELECT userID FROM users"),anyString())).thenReturn(List.of(Map.of("userID",42)));
        when(db.queryForObject(contains("COUNT(*)"),eq(Integer.class),eq(42))).thenReturn(0);
    }
    @Test void sendsSingleUseLinkAndStoresOnlyDigest() {
        existing(); service.request(" User@Example.com ");
        var message=ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(message.capture());
        String text=message.getValue().getText();
        String token=text.split("#token=")[1].split("\\n")[0];
        assertTrue(token.matches("[A-Za-z0-9_-]{43}"));
        verify(db).update(contains("INSERT INTO passwordResetTokens"),eq(PasswordRecoveryService.digest(token)),eq(42));
        assertEquals("laundry@example.com",message.getValue().getFrom());
        assertArrayEquals(new String[]{"User@Example.com"},message.getValue().getTo());
    }
    @Test void unknownAccountDoesNotSendMailOrWriteToken() {
        when(db.queryForList(anyString(),anyString())).thenReturn(List.of());
        service.request("unknown@example.com"); verifyNoInteractions(sender);
        verify(db,never()).update(anyString(),any(Object[].class));
    }
    @Test void unconfiguredMailFailsClearly() {
        when(provider.getIfAvailable()).thenReturn(null);
        var error=assertThrows(ResponseStatusException.class,()->service.request("user@example.com"));
        assertEquals(503,error.getStatusCode().value()); verifyNoInteractions(db);
    }
    @Test void smtpFailureDoesNotReportSuccess() {
        existing(); doThrow(new MailSendException("offline")).when(sender).send(any(SimpleMailMessage.class));
        assertEquals(503,assertThrows(ResponseStatusException.class,()->service.request("user@example.com")).getStatusCode().value());
    }
    @Test void repeatedRequestWithinCooldownDoesNotSendAnotherEmail() {
        existing(); when(db.queryForObject(contains("COUNT(*)"),eq(Integer.class),eq(42))).thenReturn(1);
        service.request("user@example.com"); verifyNoInteractions(sender);
    }
    @Test void invalidOrExpiredTokenIsRejected() {
        when(db.queryForList(contains("UPDATE u"),anyString(),anyString())).thenReturn(List.of());
        assertEquals(400,assertThrows(ResponseStatusException.class,()->service.reset("x".repeat(43),"New#Pass123","New#Pass123")).getStatusCode().value());
        verify(db,never()).update(anyString(),anyInt());
    }
    @Test void mismatchIsRejectedBeforeDatabaseChanges() {
        assertThrows(ResponseStatusException.class,()->service.reset("x".repeat(43),"New#Pass123","Other#123"));
        verifyNoInteractions(db);
    }
    @Test void controllerRejectsInvalidEmailAndWeakOrMismatchedPasswords() throws Exception {
        var recovery=mock(PasswordRecoveryService.class);
        MockMvc mvc=MockMvcBuilders.standaloneSetup(new PasswordRecoveryController(recovery))
                .setControllerAdvice(new _6.Y2.S1.MTR._6.LaundryLink.common.ApiExceptionHandler()).build();
        mvc.perform(post("/api/auth/forgot-password").contentType("application/json").content("{\"email\":\"bad\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/reset-password").contentType("application/json").content("{\"token\":\""+"x".repeat(43)+"\",\"password\":\"weak\",\"confirmPassword\":\"weak\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/reset-password").contentType("application/json").content("{\"token\":\""+"x".repeat(43)+"\",\"password\":\"New#Pass123\",\"confirmPassword\":\"Other#123\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(recovery);
    }
    @Test void controllerReturnsGenericResponseForValidRequestAndClearServiceError() throws Exception {
        var recovery=mock(PasswordRecoveryService.class);
        MockMvc mvc=MockMvcBuilders.standaloneSetup(new PasswordRecoveryController(recovery)).build();
        mvc.perform(post("/api/auth/forgot-password").contentType("application/json").content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("If an active account")));
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Email unavailable")).when(recovery).request(anyString());
        mvc.perform(post("/api/auth/forgot-password").contentType("application/json").content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").value("Email unavailable"));
    }
}
