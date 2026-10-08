package _6.Y2.S1.MTR._6.LaundryLink.orders;

import _6.Y2.S1.MTR._6.LaundryLink.user.User;
import _6.Y2.S1.MTR._6.LaundryLink.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Who may reach which orders. Used by the rules in SecurityConfig and by OrderController.
 *
 * <ul>
 *   <li>A customer can only see and change their own orders.</li>
 *   <li>Staff, riders, managers and owners (and admins) can see every order.</li>
 * </ul>
 *
 * The signed-in user is taken from the Spring Security session; their email is the login
 * name, which is looked up in the users table to get their userID.
 */
@Component
public class OrderAccess {
    private static final String CUSTOMER = "ROLE_CUSTOMER";
    // The roles that work with orders across all customers.
    private static final Set<String> STAFF_ROLES =
            Set.of("ROLE_STAFF", "ROLE_RIDER", "ROLE_MANAGER", "ROLE_OWNER", "ROLE_ADMIN");

    private final UserRepository userRepository;

    public OrderAccess(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** True for staff, riders, managers, owners and admins. */
    public boolean isOrderStaff(Authentication authentication) {
        return hasAnyRole(authentication, STAFF_ROLES);
    }

    /** True when the signed-in user is a customer and {@code userID} is their own account. */
    public boolean isOwnAccount(Authentication authentication, Integer userID) {
        if (userID == null || !hasAnyRole(authentication, Set.of(CUSTOMER))) {
            return false;
        }
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .map(User::getUserID)
                .filter(userID::equals)
                .isPresent();
    }

    /**
     * May the signed-in user look at the orders of customer {@code userID}?
     * Their own orders always; anyone's orders when they are order staff.
     */
    public boolean canViewOrdersOf(Authentication authentication, Integer userID) {
        return isOrderStaff(authentication) || isOwnAccount(authentication, userID);
    }

    /**
     * May the signed-in user place an order for customer {@code userID}? A customer only for
     * themselves; order staff for any customer (for example an order taken at the counter).
     */
    public boolean canPlaceOrderFor(Authentication authentication, Integer userID) {
        return isOrderStaff(authentication) || isOwnAccount(authentication, userID);
    }

    private boolean hasAnyRole(Authentication authentication, Set<String> roles) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(roles::contains);
    }
}
