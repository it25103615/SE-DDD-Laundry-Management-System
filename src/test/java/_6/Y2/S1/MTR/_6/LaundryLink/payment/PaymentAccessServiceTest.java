package _6.Y2.S1.MTR._6.LaundryLink.payment;

import _6.Y2.S1.MTR._6.LaundryLink.user.User;
import _6.Y2.S1.MTR._6.LaundryLink.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;

import java.security.Principal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class PaymentAccessServiceTest {

    @Test
    void resolvesAuthenticatedEmailToUserID() {
        PaymentManagementRepository paymentManagementRepository = Mockito.mock(PaymentManagementRepository.class);
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        User user = new User();
        user.setUserID(30);
        when(userRepository.findByEmailIgnoreCase("viyavaas@gmail.com")).thenReturn(Optional.of(user));
        PaymentAccessService service = new PaymentAccessService(paymentManagementRepository, userRepository);

        Integer resolved = service.resolveUserID(principal("viyavaas@gmail.com"));

        assertEquals(30, resolved);
    }

    @Test
    void rejectsAuthenticatedPrincipalThatDoesNotResolveToAUser() {
        PaymentManagementRepository paymentManagementRepository = Mockito.mock(PaymentManagementRepository.class);
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        when(userRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());
        PaymentAccessService service = new PaymentAccessService(paymentManagementRepository, userRepository);

        assertThrows(AccessDeniedException.class, () -> service.resolveUserID(principal("missing@example.com")));
    }

    @Test
    void rejectsMissingAuthenticatedPrincipal() {
        PaymentManagementRepository paymentManagementRepository = Mockito.mock(PaymentManagementRepository.class);
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        PaymentAccessService service = new PaymentAccessService(paymentManagementRepository, userRepository);

        assertThrows(AccessDeniedException.class, () -> service.resolveUserID(null));
    }

    private Principal principal(String name) {
        return () -> name;
    }
}
