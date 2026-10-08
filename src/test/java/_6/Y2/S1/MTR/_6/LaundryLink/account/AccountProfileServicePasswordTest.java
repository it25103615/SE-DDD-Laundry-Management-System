package _6.Y2.S1.MTR._6.LaundryLink.account;

import _6.Y2.S1.MTR._6.LaundryLink.account.dto.ProfileRequests;
import _6.Y2.S1.MTR._6.LaundryLink.notification.NotificationService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

class AccountProfileServicePasswordTest {
    private static final String EMAIL = "staff@example.com";
    private static final String LOOKUP = "SELECT userID AS id,password FROM users WHERE email=?";
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final AccountProfileService service = new AccountProfileService(db, encoder, notifications);

    @BeforeEach void setup() {
        when(db.queryForList(LOOKUP, EMAIL)).thenReturn(List.of(Map.of(
                "id", 42, "password", encoder.encode("Current#123"))));
    }

    @Test void successfulChangeUpdatesPasswordAndMetadataExactlyOnce() {
        service.updatePassword(EMAIL, new ProfileRequests.Password("Current#123", "NewPassword#456", "NewPassword#456"));

        var hash = ArgumentCaptor.forClass(String.class);
        verify(db).queryForList(LOOKUP, EMAIL);
        verify(db, times(1)).update(eq("UPDATE users SET password=?,updatedAt=SYSDATETIME(),version=version+1 WHERE userID=?"),
                hash.capture(), eq(42));
        assertTrue(encoder.matches("NewPassword#456", hash.getValue()));
        assertFalse(encoder.matches("Current#123", hash.getValue()));
        verifyNoMoreInteractions(db);
        verify(notifications).notifyUser(42, "SECURITY", "Password changed", "Your LaundryLink password was changed.",
                "/html/account/profile.html", "ACCOUNT", 42);
    }

    @Test void wrongCurrentPasswordDoesNotUpdateUser() {
        var error = assertThrows(ResponseStatusException.class, () -> service.updatePassword(EMAIL,
                new ProfileRequests.Password("Wrong#123", "NewPassword#456", "NewPassword#456")));
        assertEquals(BAD_REQUEST, error.getStatusCode());
        assertEquals("Current password is incorrect.", error.getReason());
        verify(db).queryForList(LOOKUP, EMAIL);
        verifyNoMoreInteractions(db);
        verifyNoInteractions(notifications);
    }

    @Test void confirmationMismatchDoesNotUpdateUser() {
        var error = assertThrows(ResponseStatusException.class, () -> service.updatePassword(EMAIL,
                new ProfileRequests.Password("Current#123", "NewPassword#456", "Different#456")));
        assertEquals(BAD_REQUEST, error.getStatusCode());
        assertEquals("New passwords do not match.", error.getReason());
        verifyNoInteractions(db, notifications);
    }
}
