package _6.Y2.S1.MTR._6.LaundryLink.auth;

import _6.Y2.S1.MTR._6.LaundryLink.user.User;
import _6.Y2.S1.MTR._6.LaundryLink.user.UserRepository;
import _6.Y2.S1.MTR._6.LaundryLink.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseUserDetailsServiceTest {
    @Test void mapsDatabaseRoleToSpringSecurityAuthority() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.findByEmailIgnoreCase("rider@example.com")).thenReturn(Optional.of(User.builder().email("rider@example.com").password("encoded").type(UserRole.RIDER).build()));
        UserDetails user = new DatabaseUserDetailsService(repository).loadUserByUsername("rider@example.com");
        assertTrue(user.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_RIDER")));
    }
}
