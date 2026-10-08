package _6.Y2.S1.MTR._6.LaundryLink.account;

import _6.Y2.S1.MTR._6.LaundryLink.notification.NotificationService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AccountDeletionTest {
    private static final String EMAIL = "customer@example.com";
    private static final String LOOKUP = "SELECT userID AS id,password,UPPER(type) AS role FROM users WITH (UPDLOCK, HOLDLOCK) WHERE email=? AND active=1";
    private static final String DEACTIVATE = "UPDATE users SET active=0,email=?,password=? WHERE userID=? AND active=1 AND UPPER(type)='CUSTOMER'";
    private JdbcTemplate db;
    private MockMvc mvc;
    private MockHttpSession session;
    private UsernamePasswordAuthenticationToken authentication;
    private BCryptPasswordEncoder encoder;

    @BeforeEach void setup() {
        db = mock(JdbcTemplate.class);
        encoder = new BCryptPasswordEncoder(4);
        var service = new AccountProfileService(db, encoder, mock(NotificationService.class));
        mvc = MockMvcBuilders.standaloneSetup(new ProfileController(service)).build();
        session = new MockHttpSession();
        authentication = UsernamePasswordAuthenticationToken.authenticated(EMAIL, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    private void account(String role) {
        when(db.queryForList(LOOKUP, EMAIL)).thenReturn(List.of(Map.of(
                "id", 42, "password", encoder.encode("Current#123"), "role", role)));
    }

    @Test void correctPasswordSoftDeletesOnlyTheSignedInUserAndEndsSession() throws Exception {
        account("CUSTOMER");
        when(db.queryForObject(contains("COUNT(*)"), eq(Integer.class), anyString())).thenReturn(0);
        when(db.update(eq(DEACTIVATE), anyString(), anyString(), eq(42))).thenAnswer(invocation -> {
            assertFalse(session.isInvalid(), "The database change must precede logout");
            return 1;
        });
        mvc.perform(delete("/api/account/profile").principal(authentication).session(session)
                .contentType("application/json").content("{\"currentPassword\":\"Current#123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("Account deactivated."));
        assertTrue(session.isInvalid());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(db).queryForList(LOOKUP, EMAIL);
        var email = org.mockito.ArgumentCaptor.forClass(String.class);
        var hash = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(db).update(eq(DEACTIVATE), email.capture(), hash.capture(), eq(42));
        assertTrue(AccountSessionGuard.isRetiredEmail(email.getValue()));
        assertTrue(email.getValue().length() <= 100);
        assertFalse(encoder.matches("Current#123", hash.getValue()));
        verify(db).queryForObject(contains("COUNT(*)"), eq(Integer.class), eq(email.getValue()));
        verifyNoMoreInteractions(db); // No physical deletion or changes to related records.
    }

    @Test void wrongPasswordDoesNotWriteOrLogout() throws Exception {
        account("CUSTOMER");
        mvc.perform(delete("/api/account/profile").principal(authentication).session(session)
                .contentType("application/json").content("{\"currentPassword\":\"wrong\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Current password is incorrect."));
        assertFalse(session.isInvalid());
        assertSame(authentication, SecurityContextHolder.getContext().getAuthentication());
        verify(db).queryForList(LOOKUP, EMAIL);
        verifyNoMoreInteractions(db);
    }

    @ParameterizedTest
    @ValueSource(strings = {"STAFF", "RIDER", "MANAGER", "OWNER", "ADMIN", "CSM", "CUSTOMER_SERVICE_MANAGER"})
    void nonCustomersCannotDeactivateEvenWithCorrectPassword(String role) throws Exception {
        account(role);
        mvc.perform(delete("/api/account/profile").principal(authentication).session(session)
                .contentType("application/json").content("{\"currentPassword\":\"Current#123\"}"))
                .andExpect(status().isForbidden());
        assertFalse(session.isInvalid());
        verify(db).queryForList(LOOKUP, EMAIL);
        verifyNoMoreInteractions(db);
    }

    @Test void missingOrInactiveAccountDoesNotWriteOrLogout() throws Exception {
        when(db.queryForList(LOOKUP, EMAIL)).thenReturn(List.of());
        mvc.perform(delete("/api/account/profile").principal(authentication).session(session)
                .contentType("application/json").content("{\"currentPassword\":\"Current#123\"}"))
                .andExpect(status().isUnauthorized());
        assertFalse(session.isInvalid());
        verify(db).queryForList(LOOKUP, EMAIL);
        verifyNoMoreInteractions(db);
    }

    @Test void blankPasswordIsRejectedBeforeDatabaseAccess() throws Exception {
        mvc.perform(delete("/api/account/profile").principal(authentication).session(session)
                .contentType("application/json").content("{\"currentPassword\":\"\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(db);
        assertFalse(session.isInvalid());
    }

    @Test void missingPrincipalIsRejectedBeforeDatabaseAccess() throws Exception {
        mvc.perform(delete("/api/account/profile").session(session)
                .contentType("application/json").content("{\"currentPassword\":\"Current#123\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(db);
        assertFalse(session.isInvalid());
    }

    @Test void unsuccessfulUpdateDoesNotLogoutOrReportSuccess() throws Exception {
        account("CUSTOMER");
        when(db.queryForObject(contains("COUNT(*)"), eq(Integer.class), anyString())).thenReturn(0);
        when(db.update(eq(DEACTIVATE), anyString(), anyString(), eq(42))).thenReturn(0);
        mvc.perform(delete("/api/account/profile").principal(authentication).session(session)
                .contentType("application/json").content("{\"currentPassword\":\"Current#123\"}"))
                .andExpect(status().isConflict());
        assertFalse(session.isInvalid());
    }

    @Test void collisionRetriesBeforeWriting() throws Exception {
        account("CUSTOMER");
        when(db.queryForObject(contains("COUNT(*)"), eq(Integer.class), anyString())).thenReturn(1, 0);
        when(db.update(eq(DEACTIVATE), anyString(), anyString(), eq(42))).thenReturn(1);
        mvc.perform(delete("/api/account/profile").principal(authentication).session(session)
                .contentType("application/json").content("{\"currentPassword\":\"Current#123\"}"))
                .andExpect(status().isOk());
        var candidates = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(db, times(2)).queryForObject(contains("COUNT(*)"), eq(Integer.class), candidates.capture());
        assertNotEquals(candidates.getAllValues().get(0), candidates.getAllValues().get(1));
    }

    @Test void exhaustedCollisionsLeaveAccountAndSessionUntouched() throws Exception {
        account("CUSTOMER");
        when(db.queryForObject(contains("COUNT(*)"), eq(Integer.class), anyString())).thenReturn(1);
        mvc.perform(delete("/api/account/profile").principal(authentication).session(session)
                .contentType("application/json").content("{\"currentPassword\":\"Current#123\"}"))
                .andExpect(status().isConflict());
        verify(db).queryForList(LOOKUP, EMAIL);
        verify(db, times(5)).queryForObject(contains("COUNT(*)"), eq(Integer.class), anyString());
        verifyNoMoreInteractions(db);
        assertFalse(session.isInvalid());
    }
}
