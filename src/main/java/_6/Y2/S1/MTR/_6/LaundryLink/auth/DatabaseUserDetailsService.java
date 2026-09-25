package _6.Y2.S1.MTR._6.LaundryLink.auth;

import _6.Y2.S1.MTR._6.LaundryLink.user.User;
import _6.Y2.S1.MTR._6.LaundryLink.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class DatabaseUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;
    @Override public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow(() -> new UsernameNotFoundException("Invalid email or password."));
        if (user.getType() == null) throw new UsernameNotFoundException("Invalid email or password.");
        return org.springframework.security.core.userdetails.User.withUsername(user.getEmail()).password(user.getPassword()).authorities(new SimpleGrantedAuthority("ROLE_" + user.getType().name())).build();
    }
}
