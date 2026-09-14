package _6.Y2.S1.MTR._6.LaundryLink.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import javax.sql.DataSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/support/**").authenticated()
                        .anyRequest().permitAll()
                )
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
        users.setUsersByUsernameQuery("SELECT email, password, CAST(1 AS BIT) FROM users WHERE email = ?");
        users.setAuthoritiesByUsernameQuery("SELECT email, CONCAT('ROLE_', UPPER(type)) FROM users WHERE email = ?");
        return users;
    }

    @Bean
    public static PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
