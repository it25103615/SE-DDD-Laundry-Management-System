package _6.Y2.S1.MTR._6.LaundryLink.rider;

import _6.Y2.S1.MTR._6.LaundryLink.user.User;
import _6.Y2.S1.MTR._6.LaundryLink.user.UserRepository;
import _6.Y2.S1.MTR._6.LaundryLink.user.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Only a signed-in RIDER account may use RiderService (and so every /api/rider endpoint). */
class RiderServiceTest {

    RiderRepository repository;
    UserRepository users;
    RiderService service;

    @BeforeEach
    void setup() {
        repository = mock(RiderRepository.class);
        users = mock(UserRepository.class);
        service = new RiderService(repository, users);
    }

    @AfterEach
    void clearLogin() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void signedOutRequestIsRejectedWith401() {
        assertEquals(401, status(assertThrows(ResponseStatusException.class, service::getAvailableTasks)));
        verifyNoInteractions(repository, users);
    }

    @Test
    void anonymousRequestIsRejectedWith401() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertEquals(401, status(assertThrows(ResponseStatusException.class, service::getMyWork)));
        verifyNoInteractions(repository, users);
    }

    @Test
    void nonRiderAccountIsRejectedWith403BeforeAnyTaskIsTouched() {
        signIn("anna@customer.com", "CUSTOMER");
        when(users.findByEmailIgnoreCase("anna@customer.com")).thenReturn(Optional.of(user(3, UserRole.CUSTOMER)));

        assertEquals(403, status(assertThrows(ResponseStatusException.class, service::getAvailableTasks)));
        assertEquals(403, status(assertThrows(ResponseStatusException.class, () -> service.accept(10))));
        verifyNoInteractions(repository);
    }

    @Test
    void signedInEmailWithoutAnAccountIsRejectedWith404() {
        signIn("ghost@rider.com", "RIDER");
        when(users.findByEmailIgnoreCase("ghost@rider.com")).thenReturn(Optional.empty());

        assertEquals(404, status(assertThrows(ResponseStatusException.class, service::getDashboardSummary)));
        verifyNoInteractions(repository);
    }

    @Test
    void riderSeesTheirOwnWork() {
        signIn("ravi@rider.com", "RIDER");
        when(users.findByEmailIgnoreCase("ravi@rider.com")).thenReturn(Optional.of(user(7, UserRole.RIDER)));
        when(repository.findMyWork(7)).thenReturn(List.of());

        assertEquals(List.of(), service.getMyWork());
        verify(repository).findMyWork(7);
    }

    @Test
    void acceptUsesTheSignedInRidersId() {
        signIn("ravi@rider.com", "RIDER");
        when(users.findByEmailIgnoreCase("ravi@rider.com")).thenReturn(Optional.of(user(7, UserRole.RIDER)));
        when(repository.findTaskById(10)).thenReturn(Optional.of(new Object[]{10, 5, "pickup", 3}));
        when(repository.acceptPickup(10, 7)).thenReturn(1);
        when(repository.movePickupToEnRoute(10, 7)).thenReturn(1);

        service.accept(10);

        verify(repository).acceptPickup(10, 7);
        verify(repository).movePickupToEnRoute(10, 7);
        verify(users, times(1)).findByEmailIgnoreCase(anyString()); // looked up once per request
    }

    @Test
    void currentRiderProfileComesFromTheSignedInAccount() {
        signIn("ravi@rider.com", "RIDER");
        User rider = user(7, UserRole.RIDER);
        rider.setFirstName("Ravi");
        rider.setLastName("Perera");
        when(users.findByEmailIgnoreCase("ravi@rider.com")).thenReturn(Optional.of(rider));

        Map<String, Object> me = service.getCurrentRider();

        assertEquals(7, me.get("userID"));
        assertEquals("RP", me.get("initials"));
    }

    private void signIn(String email, String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                email, null, AuthorityUtils.createAuthorityList("ROLE_" + role)));
    }

    private static User user(int id, UserRole type) {
        User user = new User();
        user.setUserID(id);
        user.setType(type);
        return user;
    }

    private static int status(ResponseStatusException error) {
        return error.getStatusCode().value();
    }
}
