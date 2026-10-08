package _6.Y2.S1.MTR._6.LaundryLink.account;

import _6.Y2.S1.MTR._6.LaundryLink.account.dto.CustomerRegistrationRequest;
import _6.Y2.S1.MTR._6.LaundryLink.account.dto.ProfileRequests;
import _6.Y2.S1.MTR._6.LaundryLink.config.SecurityConfig;
import _6.Y2.S1.MTR._6.LaundryLink.notification.NotificationService;
import _6.Y2.S1.MTR._6.LaundryLink.orders.OrderAccess;
import jakarta.servlet.FilterChain;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AccountEmailReuseTest {
    private static final String EMAIL = "customer@example.com";
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);

    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    private static void authenticate(int id) {
        var principal = new AccountSessionGuard.AccountPrincipal(id, EMAIL, "hash", true, "CUSTOMER");
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    private static MockHttpServletRequest request() {
        var request = new MockHttpServletRequest("GET", "/api/account/profile");
        request.getSession();
        return request;
    }

    @Test void unchangedRegistrationInsertsANewIdentityAfterEmailRelease() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        NotificationService notifications = mock(NotificationService.class);
        var service = new AccountProfileService(db, encoder, notifications);
        var oldUser = new HashMap<String,Object>(Map.of("id", 10, "email", EMAIL,
                "password", encoder.encode("Old#12345"), "role", "CUSTOMER", "active", true));
        when(db.queryForList(contains("UPDLOCK"), eq(EMAIL))).thenReturn(List.of(oldUser));
        when(db.queryForObject(contains("COUNT(*)"), eq(Integer.class), anyString())).thenReturn(0);
        when(db.update(startsWith("UPDATE users SET active=0"), anyString(), anyString(), eq(10)))
                .thenAnswer(call -> {
                    oldUser.put("email", call.getArgument(1));
                    oldUser.put("password", call.getArgument(2));
                    oldUser.put("active", false);
                    return 1;
                });
        service.deactivateAccount(EMAIL, new ProfileRequests.Deletion("Old#12345"));
        assertEquals(10, oldUser.get("id"));
        assertEquals(false, oldUser.get("active"));
        assertNotEquals(EMAIL, oldUser.get("email"));
        assertFalse(encoder.matches("Old#12345", (String) oldUser.get("password")));
        when(db.queryForObject(contains("OUTPUT INSERTED.userID"), eq(Integer.class),
                eq("New"), eq("Customer"), eq(EMAIL), anyString(), eq("0771234567")))
                .thenAnswer(call -> {
                    assertTrue(encoder.matches("New#12345", call.getArgument(5)));
                    return 11; // Simulated database-generated ID; no live database is used.
                });
        int newId = service.registerCustomer(new CustomerRegistrationRequest(
                "New Customer", EMAIL, "0771234567", "New#12345", "New#12345", null));
        assertEquals(11, newId);
        assertNotEquals(oldUser.get("id"), newId);
        // Inspect every database mutation: there is no child-row deletion, update, or reassignment.
        var mutations = mockingDetails(db).getInvocations().stream()
                .map(call -> (String) call.getArgument(0))
                .filter(sql -> sql.stripLeading().startsWith("UPDATE") || sql.stripLeading().startsWith("INSERT"))
                .toList();
        assertEquals(2, mutations.size());
        assertTrue(mutations.get(0).startsWith("UPDATE users SET active=0,email=?,password=? WHERE userID=?"));
        assertTrue(mutations.get(1).contains("INSERT INTO users(firstName,lastName,email,password,phoneNumber,type)"));
        verify(notifications).notifyUser(eq(11), eq("ACCOUNT"), anyString(), anyString(), anyString(), eq("ACCOUNT"), eq(11));
    }

    @Test void staleSessionIsRejectedEvenAfterAnotherIdOwnsTheEmail() throws Exception {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.query(anyString(), any(RowMapper.class), eq(10))).thenReturn(List.of());
        authenticate(10);
        var request = request();
        var session = (MockHttpSession) request.getSession();
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        new AccountSessionGuard(db).doFilter(request, response, chain);
        assertEquals(401, response.getStatus());
        assertTrue(session.isInvalid());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(chain);
        verify(db).query(eq("SELECT email FROM users WHERE userID=? AND active=1"), any(RowMapper.class), eq(10));
        verifyNoMoreInteractions(db); // Never looks up the replacement account by email.
    }

    @Test void legacySessionWithoutIdMustSignInAgain() throws Exception {
        JdbcTemplate db = mock(JdbcTemplate.class);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(EMAIL, null, List.of()));
        var response = new MockHttpServletResponse();
        new AccountSessionGuard(db).doFilter(request(), response, mock(FilterChain.class));
        assertEquals(401, response.getStatus());
        verifyNoInteractions(db);
    }

    @Test void changedEmailAndRetiredEmailAreRejected() throws Exception {
        for (String email : List.of("changed@example.com", "deleted_10_" + "a".repeat(32) + "@deleted.invalid")) {
            JdbcTemplate db = mock(JdbcTemplate.class);
            when(db.query(anyString(), any(RowMapper.class), eq(10))).thenReturn(List.of(email));
            authenticate(10);
            var response = new MockHttpServletResponse();
            new AccountSessionGuard(db).doFilter(request(), response, mock(FilterChain.class));
            assertEquals(401, response.getStatus());
        }
    }

    @Test void requestWaitingBehindDeletionRechecksOldIdBeforeControllerRuns() throws Exception {
        JdbcTemplate db = mock(JdbcTemplate.class);
        AtomicBoolean active = new AtomicBoolean(true);
        when(db.query(anyString(), any(RowMapper.class), eq(10)))
                .thenAnswer(call -> active.get() ? List.of(EMAIL) : List.of());
        var guard = new AccountSessionGuard(db);
        CountDownLatch insideDeletion = new CountDownLatch(1);
        CountDownLatch finishDeletion = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicBoolean secondReachedController = new AtomicBoolean();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var deletion = executor.submit(() -> {
                authenticate(10);
                try {
                    guard.doFilter(request(), new MockHttpServletResponse(), (req, res) -> {
                        insideDeletion.countDown();
                        try { if (!finishDeletion.await(5, TimeUnit.SECONDS)) throw new AssertionError("Timed out"); }
                        catch (InterruptedException e) { throw new AssertionError(e); }
                        active.set(false); // Deletion committed; original email may now be reused.
                    });
                } finally { SecurityContextHolder.clearContext(); }
                return null;
            });
            assertTrue(insideDeletion.await(5, TimeUnit.SECONDS));
            var other = executor.submit(() -> {
                authenticate(10);
                var response = new MockHttpServletResponse();
                secondStarted.countDown();
                try { guard.doFilter(request(), response, (req, res) -> secondReachedController.set(true)); }
                finally { SecurityContextHolder.clearContext(); }
                return response.getStatus();
            });
            try {
                assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> other.get(100, TimeUnit.MILLISECONDS));
            } finally { finishDeletion.countDown(); }
            deletion.get(5, TimeUnit.SECONDS);
            assertEquals(401, other.get(5, TimeUnit.SECONDS));
            assertFalse(secondReachedController.get());
        }
    }

    @Configuration
    @Import(SecurityConfig.class)
    static class SecurityFixture {
        @Bean DataSource dataSource() { return mock(DataSource.class); }
        @Bean OrderAccess orderAccess() { return mock(OrderAccess.class); }
    }

    @Test void realSecurityChainPreservesLoginRolesCsrfAndLogoutAndRejectsRetiredCredentials() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(SecurityFixture.class);
            context.refresh();
            var ds = context.getBean(DataSource.class);
            Connection connection = mock(Connection.class);
            when(ds.getConnection()).thenReturn(connection);
            AtomicReference<String> email = new AtomicReference<>(EMAIL);
            AtomicReference<String> role = new AtomicReference<>("CUSTOMER");
            AtomicBoolean active = new AtomicBoolean(true);
            String hash = encoder.encode("Current#123");
            when(connection.prepareStatement(anyString())).thenAnswer(call -> {
                PreparedStatement statement = mock(PreparedStatement.class);
                when(statement.executeQuery()).thenAnswer(query -> {
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.next()).thenReturn(true, false);
                    when(rs.getInt("userID")).thenReturn(10);
                    when(rs.getString("email")).thenReturn(email.get());
                    when(rs.getString("password")).thenReturn(hash);
                    when(rs.getString("role")).thenReturn(role.get());
                    when(rs.getBoolean("active")).thenReturn(active.get());
                    return rs;
                });
                return statement;
            });
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            var destinations = Map.of("CUSTOMER", "customer/dashboard.html", "RIDER", "rider/dashboard.html",
                    "STAFF", "staff/dashboard.html", "MANAGER", "admin/manager/dashboard.html",
                    "OWNER", "admin/owner/dashboard.html", "ADMIN", "admin/owner/dashboard.html",
                    "CSM", "admin/customer-service-manager/dashboard.html",
                    "CUSTOMER_SERVICE_MANAGER", "admin/customer-service-manager/dashboard.html");
            for (var entry : destinations.entrySet()) {
                role.set(entry.getKey());
                mvc.perform(formLogin("/login").userParameter("email").user(EMAIL).password("Current#123"))
                        .andExpect(authenticated()).andExpect(redirectedUrl("/html/" + entry.getValue()));
            }
            role.set("CUSTOMER");
            var login = mvc.perform(formLogin("/login").userParameter("email").user(EMAIL).password("Current#123"))
                    .andReturn();
            var session = (MockHttpSession) login.getRequest().getSession(false);
            mvc.perform(delete("/api/account/profile").session(session))
                    .andExpect(redirectedUrl("/html/portal.html?accessDenied=true"));
            mvc.perform(get("/html/admin/owner/dashboard.html").session(session))
                    .andExpect(redirectedUrl("/html/portal.html?accessDenied=true"));
            mvc.perform(post("/logout").session(session).with(csrf()))
                    .andExpect(redirectedUrl("/html/auth/login.html?logout=true")).andExpect(unauthenticated());
            assertTrue(session.isInvalid());
            active.set(false);
            mvc.perform(formLogin("/login").userParameter("email").user(EMAIL).password("Current#123"))
                    .andExpect(unauthenticated());
            active.set(true); // Even an accidentally re-enabled retired identity must be refused.
            email.set("deleted_10_" + "a".repeat(32) + "@deleted.invalid");
            mvc.perform(formLogin("/login").userParameter("email").user(email.get()).password("Current#123"))
                    .andExpect(unauthenticated());
        }
    }
}
