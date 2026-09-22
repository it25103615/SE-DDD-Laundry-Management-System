package _6.Y2.S1.MTR._6.LaundryLink.rider.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Profile("rider-dev")
public class RiderDevSecurityConfig {

    /*
     * Temporary development-only security configuration.
     * It exists because spring-boot-starter-security is already in the project,
     * while the team's real login/authentication function is not ready yet.
     * Remove this profile/config when the real authentication SecurityFilterChain
     * is integrated.
     */
    // SECURITY WARNING: permitAll() on anyRequest() means this profile has
// no auth at all — must not be active in any deployed/shared environment,
// only during local development with the "rider-dev" profile explicitly set.
    @Bean
    SecurityFilterChain riderDevelopmentSecurity(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/rider/**").permitAll()
                .requestMatchers("/rider/**").permitAll()
                .requestMatchers("/js/**", "/css/**").permitAll()
                .anyRequest().permitAll()
            );
        return http.build();
    }
}
