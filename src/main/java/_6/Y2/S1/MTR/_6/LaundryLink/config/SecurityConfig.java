package _6.Y2.S1.MTR._6.LaundryLink.config;

import _6.Y2.S1.MTR._6.LaundryLink.orders.OrderAccess;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import javax.sql.DataSource;

/**
 * Spring Security setup: URL access rules, form login/logout, CSRF settings, the
 * database-backed user lookup and the password encoder.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private static final String[] FINANCE_MANAGEMENT_ROLES = {
            "OWNER", "ADMIN", "MANAGER", "CSM", "CUSTOMER_SERVICE_MANAGER"
    };

    private static final String[] STAFF_OR_FINANCE_MANAGEMENT_ROLES = {
            "STAFF", "OWNER", "ADMIN", "MANAGER", "CSM", "CUSTOMER_SERVICE_MANAGER"
    };

    // The {userID} part of /api/orders/customer/{userID}/... as a number, or null when it is
    // not a number (which then matches no customer, so access is refused).
    private static Integer customerID(RequestAuthorizationContext context) {
        try {
            return Integer.valueOf(context.getVariables().get("userID"));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, OrderAccess orderAccess) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/html/admin/owner/**").hasAnyRole("OWNER", "ADMIN")
                        .requestMatchers("/html/admin/manager/**").hasAnyRole("MANAGER", "OWNER", "ADMIN")
                        .requestMatchers("/html/admin/customer-service-manager/**").hasAnyRole("CSM", "CUSTOMER_SERVICE_MANAGER", "MANAGER", "OWNER", "ADMIN")
                        .requestMatchers("/api/support/**").authenticated()
                        .requestMatchers("/api/notifications/**").authenticated()
                        .requestMatchers("/api/customer/dashboard").authenticated()
                        .requestMatchers("/api/account/**").authenticated()
                        // Laundry processing: the staff pages and their API are for staff, managers and owners only.
                        .requestMatchers("/api/processing/**", "/html/staff/**").hasAnyRole("STAFF", "MANAGER", "OWNER", "ADMIN")
                        // Rider pages and the rider API are for rider accounts only (RiderService also checks this).
                        .requestMatchers("/api/rider/**", "/html/rider/**").hasRole("RIDER")
                        // Orders. A customer can only see and change their own orders; staff, riders,
                        // managers and owners can see every order.
                        //  - /management/**: the all-orders views. Reading is for order staff;
                        //    changing an order's lines there is for managers and owners.
                        //  - /customer/{userID}/**: one customer's orders. Reading is for that
                        //    customer or order staff; changing is for that customer only.
                        //  - anything else under /api/orders (placing an order) needs a signed-in
                        //    user; OrderController then checks who the order is for.
                        .requestMatchers(HttpMethod.GET, "/api/orders/management/**").hasAnyRole("STAFF", "RIDER", "MANAGER", "OWNER", "ADMIN")
                        .requestMatchers("/api/orders/management/**").hasAnyRole("MANAGER", "OWNER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/orders/customer/{userID}/**").access((authentication, context) ->
                                new AuthorizationDecision(orderAccess.canViewOrdersOf(authentication.get(), customerID(context))))
                        .requestMatchers("/api/orders/customer/{userID}/**").access((authentication, context) ->
                                new AuthorizationDecision(orderAccess.isOwnAccount(authentication.get(), customerID(context))))
                        .requestMatchers("/api/orders/**").authenticated()
                        // Billing and invoice data is protected in BillingController:
                        // finance management can inspect all orders, customers only their own.
                        .requestMatchers("/api/billing/**").authenticated()
                        // Payments: management routes are for authorised finance/admin roles;
                        // customer routes still perform per-order ownership checks in PaymentService.
                        .requestMatchers("/api/payments/management/**").hasAnyRole(FINANCE_MANAGEMENT_ROLES)
                        .requestMatchers("/api/payments/staff/**").hasAnyRole(STAFF_OR_FINANCE_MANAGEMENT_ROLES)
                        .requestMatchers(HttpMethod.GET, "/api/payments/history").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/payments/orders/*/amount").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/payments/orders/*/status").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.POST, "/api/payments/orders/*").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/payments/orders/*").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/payments/*/receipt").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/payments/*").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/payments").hasAnyRole(FINANCE_MANAGEMENT_ROLES)
                        .requestMatchers(HttpMethod.PUT, "/api/payments/*").hasAnyRole(FINANCE_MANAGEMENT_ROLES)
                        .requestMatchers(HttpMethod.DELETE, "/api/payments/*").hasAnyRole(FINANCE_MANAGEMENT_ROLES)
                        .requestMatchers(HttpMethod.GET, "/api/payments").hasAnyRole(FINANCE_MANAGEMENT_ROLES)
                        // Promotion application is customer-owned; promotion CRUD/listing is finance management.
                        .requestMatchers(HttpMethod.GET, "/api/promotions/*/orders/*/validate").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.POST, "/api/promotions/*/orders/*/apply").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/promotions/available").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/promotions").authenticated()
                        .requestMatchers("/api/promotions/**").hasAnyRole(FINANCE_MANAGEMENT_ROLES)
                        .anyRequest().permitAll()
                )
                .exceptionHandling(exceptions -> exceptions
                        .accessDeniedHandler((request, response, exception) ->
                                response.sendRedirect("/html/portal.html?accessDenied=true")))
                .formLogin(form -> form
                        .loginPage("/html/auth/login.html")
                        .loginProcessingUrl("/login")
                        .usernameParameter("email")
                        .successHandler((request, response, authentication) -> {
                            String role = authentication.getAuthorities().stream()
                                    .findFirst().map(Object::toString).orElse("");
                            String destination = switch (role) {
                                case "ROLE_CUSTOMER" -> "/html/customer/dashboard.html";
                                case "ROLE_RIDER" -> "/html/rider/dashboard.html";
                                case "ROLE_STAFF" -> "/html/staff/dashboard.html";
                                case "ROLE_MANAGER" -> "/html/admin/manager/dashboard.html";
                                case "ROLE_CSM", "ROLE_CUSTOMER_SERVICE_MANAGER" -> "/html/admin/customer-service-manager/dashboard.html";
                                case "ROLE_OWNER", "ROLE_ADMIN" -> "/html/admin/owner/dashboard.html";
                                default -> "/";
                            };
                            response.sendRedirect(destination);
                        })
                        .failureUrl("/html/auth/login.html?error=true")
                        .permitAll()
                )
                .logout(logout -> logout.logoutSuccessUrl("/html/auth/login.html?logout=true"))
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/rider/**"));
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(DataSource dataSource) {
        JdbcUserDetailsManager users = new JdbcUserDetailsManager(dataSource);
        users.setUsersByUsernameQuery("SELECT email, password, active FROM users WHERE email = ?");
        users.setAuthoritiesByUsernameQuery("SELECT email, CONCAT('ROLE_', UPPER(type)) FROM users WHERE email = ? AND active=1");
        return users;
    }

    @Bean
    public static PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
