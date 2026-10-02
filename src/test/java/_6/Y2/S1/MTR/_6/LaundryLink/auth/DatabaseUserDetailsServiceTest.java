package _6.Y2.S1.MTR._6.LaundryLink.auth;

import _6.Y2.S1.MTR._6.LaundryLink.user.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatabaseUserDetailsServiceTest {
    @Test void mapsDatabaseRoleToSpringSecurityAuthority() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.findByEmailIgnoreCase("rider@example.com")).thenReturn(Optional.of(User.builder().email("rider@example.com").password("encoded").type(UserRole.RIDER).build()));
        UserDetails user = new DatabaseUserDetailsService(repository).loadUserByUsername("rider@example.com");
        assertTrue(user.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_RIDER")));
    }
}
