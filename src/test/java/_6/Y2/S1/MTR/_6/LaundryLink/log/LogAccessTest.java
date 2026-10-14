package _6.Y2.S1.MTR._6.LaundryLink.log;

import _6.Y2.S1.MTR._6.LaundryLink.user.User;
import _6.Y2.S1.MTR._6.LaundryLink.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// The ownership rule behind /api/logs: a customer reaches only the logs of their own orders,
// while the other roles SecurityConfig lets through can read every log.
class LogAccessTest {
    private LogRepository logRepository;
    private LogAccess logAccess;

    @BeforeEach
    void setUp() {
        UserRepository userRepository = mock(UserRepository.class);
        logRepository = mock(LogRepository.class);
        logAccess = new LogAccess(userRepository, logRepository);
        // The signed-in customer in these tests is user 7, who placed order 100 but not order 200.
        when(userRepository.findByEmailIgnoreCase("anna@customer.com"))
                .thenReturn(Optional.of(User.builder().userID(7).email("anna@customer.com").build()));
        when(logRepository.countOrdersOwnedByCustomer(100, 7)).thenReturn(1);
        when(logRepository.countOrdersOwnedByCustomer(200, 7)).thenReturn(0);
    }

    private Authentication signedIn(String email, String role) {
        return new UsernamePasswordAuthenticationToken(email, "n/a", AuthorityUtils.createAuthorityList(role));
    }

    @Test
    void customerCanViewLogsOfTheirOwnOrdersOnly() {
        Authentication customer = signedIn("anna@customer.com", "ROLE_CUSTOMER");

        assertTrue(logAccess.isCustomer(customer));
        assertTrue(logAccess.canViewLogsOfOrder(customer, 100));
        assertFalse(logAccess.canViewLogsOfOrder(customer, 200));
        // A log that is not tied to any order belongs to no customer.
        assertFalse(logAccess.canViewLogsOfOrder(customer, null));
    }

    @Test
    void customerWithoutAnAccountRowSeesNothing() {
        Authentication stranger = signedIn("ghost@customer.com", "ROLE_CUSTOMER");

        assertFalse(logAccess.canViewLogsOfOrder(stranger, 100));
    }

    @Test
    void staffManagersAdminsAndSupportManagersCanViewAnyOrdersLogs() {
        for (String role : new String[]{"ROLE_STAFF", "ROLE_MANAGER", "ROLE_OWNER", "ROLE_ADMIN",
                "ROLE_CSM", "ROLE_CUSTOMER_SERVICE_MANAGER"}) {
            Authentication user = signedIn("someone@laundrylink.lk", role);
            assertFalse(logAccess.isCustomer(user), role);
            assertTrue(logAccess.canViewLogsOfOrder(user, 200), role);
        }
    }
}
