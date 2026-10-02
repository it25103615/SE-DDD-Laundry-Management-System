package _6.Y2.S1.MTR._6.LaundryLink.account;

import _6.Y2.S1.MTR._6.LaundryLink.account.dto.*;
import _6.Y2.S1.MTR._6.LaundryLink.common.ApiException;
import _6.Y2.S1.MTR._6.LaundryLink.user.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {
    @Mock UserRepository userRepository;
    AccountServiceImpl service;
    @BeforeEach void setup() { service = new AccountServiceImpl(userRepository, new BCryptPasswordEncoder(4)); }
    private RegistrationRequest registration() { return new RegistrationRequest("Nimal", null, "Perera", "nimal@example.com", "password123", "password123", "0771234567"); }
    @Test void registersCustomerWithEncodedPassword() {
        when(userRepository.save(any(User.class))).thenAnswer(i -> { User user = i.getArgument(0); user.setUserID(1); return user; });
        UserResponse response = service.register(registration());
        assertEquals(UserRole.CUSTOMER, response.type()); assertEquals("nimal@example.com", response.email());
        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class); verify(userRepository).save(user.capture());
        assertNotEquals("password123", user.getValue().getPassword()); assertTrue(new BCryptPasswordEncoder().matches("password123", user.getValue().getPassword()));
    }
    @Test void rejectsDuplicateEmail() { when(userRepository.existsByEmailIgnoreCase("nimal@example.com")).thenReturn(true); assertThrows(ApiException.class, () -> service.register(registration())); }
    @Test void rejectsDuplicatePhone() { when(userRepository.existsByPhoneNumber("0771234567")).thenReturn(true); assertThrows(ApiException.class, () -> service.register(registration())); }
    @Test void profileUpdateDoesNotChangeRole() {
        User user = User.builder().userID(1).firstName("Old").lastName("Name").email("old@example.com").phoneNumber("0711111111").type(UserRole.CUSTOMER).password("hash").build();
        when(userRepository.findByEmailIgnoreCase("old@example.com")).thenReturn(Optional.of(user)); when(userRepository.save(user)).thenReturn(user);
        service.updateCurrentUser("old@example.com", new ProfileUpdateRequest("New", null, "Name", "new@example.com", "0722222222"));
        assertEquals(UserRole.CUSTOMER, user.getType()); assertEquals("new@example.com", user.getEmail());
    }
    @Test void passwordChangeRequiresCurrentPassword() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4); User user = User.builder().userID(1).email("a@example.com").password(encoder.encode("currentpass")).build();
        when(userRepository.findByEmailIgnoreCase("a@example.com")).thenReturn(Optional.of(user));
        assertThrows(ApiException.class, () -> service.changePassword("a@example.com", new PasswordChangeRequest("wrong", "newpassword", "newpassword")));
    }
}
