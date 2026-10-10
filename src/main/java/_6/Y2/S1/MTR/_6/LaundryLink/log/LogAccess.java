package _6.Y2.S1.MTR._6.LaundryLink.log;

import _6.Y2.S1.MTR._6.LaundryLink.user.User;
import _6.Y2.S1.MTR._6.LaundryLink.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Who may read which order status logs. Used by LogController.
 *
 * <ul>
 *   <li>A customer can only read the logs of their own orders.</li>
 *   <li>Staff, managers, owners, admins and customer service managers can read every log.</li>
 * </ul>
 *
 * Which roles reach /api/logs at all is decided in SecurityConfig (riders and visitors who
 * are not signed in are refused there), so anyone arriving here who is not a customer is one
 * of the roles that may read everything.
 *
 * The signed-in user is taken from the Spring Security session; their email is the login
 * name, which is looked up in the users table to get their userID.
 */
@Component
public class LogAccess {
    private static final String CUSTOMER = "ROLE_CUSTOMER";

    private final UserRepository userRepository;
    private final LogRepository logRepository;

    public LogAccess(UserRepository userRepository, LogRepository logRepository) {
        this.userRepository = userRepository;
        this.logRepository = logRepository;
    }

    /** True when the signed-in user is a customer, who is limited to their own orders' logs. */
    public boolean isCustomer(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(CUSTOMER::equals);
    }

    /** The signed-in user's userID, or null when their account cannot be found. */
    public Integer userID(Authentication authentication) {
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .map(User::getUserID)
                .orElse(null);
    }

    /**
     * May the signed-in user read the logs of order {@code orderID}? A customer only when
     * the order is their own; every other role allowed on /api/logs for any order.
     */
    public boolean canViewLogsOfOrder(Authentication authentication, Integer orderID) {
        if (!isCustomer(authentication)) {
            return true;
        }
        Integer userID = userID(authentication);
        // A log with no order, or a customer with no account row, matches nothing.
        return orderID != null && userID != null
                && logRepository.countOrdersOwnedByCustomer(orderID, userID) > 0;
    }
}
