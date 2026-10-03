package _6.Y2.S1.MTR._6.LaundryLink.orders;

import _6.Y2.S1.MTR._6.LaundryLink.user.User;
import _6.Y2.S1.MTR._6.LaundryLink.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// The rules behind the /api/orders entries in SecurityConfig: a customer reaches only their
// own orders, while staff, riders, managers and owners can see everyone's.
class OrderAccessTest {
    private UserRepository userRepository;
    private OrderAccess orderAccess;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        orderAccess = new OrderAccess(userRepository);
        // The signed-in customer in these tests is user 7.
        when(userRepository.findByEmailIgnoreCase("anna@customer.com"))
                .thenReturn(Optional.of(User.builder().userID(7).email("anna@customer.com").build()));
    }

    private Authentication signedIn(String email, String role) {
        return new UsernamePasswordAuthenticationToken(email, "n/a", AuthorityUtils.createAuthorityList(role));
    }

    @Test
    void customerCanViewAndOrderForTheirOwnAccountOnly() {
        Authentication customer = signedIn("anna@customer.com", "ROLE_CUSTOMER");

        assertTrue(orderAccess.canViewOrdersOf(customer, 7));
        assertTrue(orderAccess.canPlaceOrderFor(customer, 7));
        assertTrue(orderAccess.isOwnAccount(customer, 7));

        assertFalse(orderAccess.canViewOrdersOf(customer, 8));
        assertFalse(orderAccess.canPlaceOrderFor(customer, 8));
        assertFalse(orderAccess.isOwnAccount(customer, 8));
        assertFalse(orderAccess.isOrderStaff(customer));
    }

    @Test
    void staffRidersManagersAndOwnersCanViewAnyCustomersOrders() {
        for (String role : new String[]{"ROLE_STAFF", "ROLE_RIDER", "ROLE_MANAGER", "ROLE_OWNER", "ROLE_ADMIN"}) {
            Authentication user = signedIn("someone@laundrylink.lk", role);
            assertTrue(orderAccess.isOrderStaff(user), role);
            assertTrue(orderAccess.canViewOrdersOf(user, 7), role);
            // Being staff does not make another customer's account their own.
            assertFalse(orderAccess.isOwnAccount(user, 7), role);
        }
    }

    @Test
    void visitorsWhoAreNotSignedInAndOtherRolesAreRefused() {
        Authentication anonymous = new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        // Customer service managers work through the support screens, not the orders API.
        Authentication supportManager = signedIn("cathy@csm.com", "ROLE_CSM");

        assertFalse(orderAccess.canViewOrdersOf(anonymous, 7));
        assertFalse(orderAccess.canPlaceOrderFor(anonymous, 7));
        assertFalse(orderAccess.canViewOrdersOf(supportManager, 7));
        assertFalse(orderAccess.canViewOrdersOf(null, 7));
        // A path whose customer part is not a number reaches the check as null.
        assertFalse(orderAccess.isOwnAccount(signedIn("anna@customer.com", "ROLE_CUSTOMER"), null));
    }
}
