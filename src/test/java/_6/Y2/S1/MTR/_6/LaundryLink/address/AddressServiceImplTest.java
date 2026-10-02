package _6.Y2.S1.MTR._6.LaundryLink.address;

import _6.Y2.S1.MTR._6.LaundryLink.account.AccountService;
import _6.Y2.S1.MTR._6.LaundryLink.address.dto.AddressRequest;
import _6.Y2.S1.MTR._6.LaundryLink.common.ApiException;
import _6.Y2.S1.MTR._6.LaundryLink.user.User;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddressServiceImplTest {
    @Mock AddressRepository addresses; @Mock AccountService accounts;
    AddressServiceImpl service;
    @BeforeEach void setup() { service = new AddressServiceImpl(addresses, accounts); }
    @Test void cannotUpdateAddressOutsideAuthenticatedUsersOwnership() {
        User user = User.builder().userID(3).email("three@example.com").build(); when(accounts.getRequiredUserByEmail("three@example.com")).thenReturn(user);
        when(addresses.findByAddressIDAndUserUserID(5, 3)).thenReturn(Optional.empty());
        assertThrows(ApiException.class, () -> service.update("three@example.com", 5, new AddressRequest("Home", "Road", "Colombo", "Western", null, false)));
        verify(addresses, never()).save(any());
    }

    // Deleting keeps the row for old delivery records: it is detached from the customer and
    // loses its default flag, and the repository's real delete is never called.
    @Test void deletingAnAddressDetachesItFromTheCustomerInsteadOfRemovingTheRow() {
        User user = User.builder().userID(3).email("three@example.com").build(); when(accounts.getRequiredUserByEmail("three@example.com")).thenReturn(user);
        Address office = Address.builder().addressID(5).nickname("Office").isDefault(false).user(user).build();
        when(addresses.findByAddressIDAndUserUserID(5, 3)).thenReturn(Optional.of(office));
        when(addresses.countByUserUserID(3)).thenReturn(2L);

        service.delete("three@example.com", 5);

        assertNull(office.getUser());
        assertFalse(office.getIsDefault());
        verify(addresses).save(office);
        verify(addresses, never()).delete(any());
    }

    // The default has to be moved to another address first; the profile page does that.
    @Test void cannotDeleteTheDefaultAddressWhileItIsStillTheDefault() {
        User user = User.builder().userID(3).email("three@example.com").build(); when(accounts.getRequiredUserByEmail("three@example.com")).thenReturn(user);
        Address home = Address.builder().addressID(5).nickname("Home").isDefault(true).user(user).build();
        when(addresses.findByAddressIDAndUserUserID(5, 3)).thenReturn(Optional.of(home));
        when(addresses.countByUserUserID(3)).thenReturn(2L);

        ApiException error = assertThrows(ApiException.class, () -> service.delete("three@example.com", 5));

        assertEquals(org.springframework.http.HttpStatus.CONFLICT, error.status);
        assertSame(user, home.getUser());
        assertTrue(home.getIsDefault());
        verify(addresses, never()).save(any());
    }

    @Test void editingTheDefaultAddressCannotSwitchItsDefaultFlagOff() {
        User user = User.builder().userID(3).email("three@example.com").build(); when(accounts.getRequiredUserByEmail("three@example.com")).thenReturn(user);
        Address home = Address.builder().addressID(5).nickname("Home").isDefault(true).user(user).build();
        when(addresses.findByAddressIDAndUserUserID(5, 3)).thenReturn(Optional.of(home));
        when(addresses.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var saved = service.update("three@example.com", 5, new AddressRequest("Home", "New Road", "Colombo", "Western", null, false));

        assertTrue(saved.isDefault());
        assertEquals("New Road", saved.street());
        // No other address was touched, because the default did not move.
        verify(addresses, never()).clearDefaultForUserExcept(any(), any());
    }

    // Making an address the default clears the flag on the customer's other addresses only,
    // so the chosen address always ends up as the default (even if it already was).
    @Test void makingAnAddressTheDefaultClearsOnlyTheOtherAddresses() {
        User user = User.builder().userID(3).email("three@example.com").build(); when(accounts.getRequiredUserByEmail("three@example.com")).thenReturn(user);
        Address office = Address.builder().addressID(5).nickname("Office").isDefault(false).user(user).build();
        when(addresses.findByAddressIDAndUserUserID(5, 3)).thenReturn(Optional.of(office));
        when(addresses.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var saved = service.setDefault("three@example.com", 5);

        assertTrue(saved.isDefault());
        verify(addresses).clearDefaultForUserExcept(3, 5);
        verify(addresses, never()).clearDefaultForUser(any());
    }

    @Test void cannotDeleteTheOnlyAddressTheCustomerHasLeft() {
        User user = User.builder().userID(3).email("three@example.com").build(); when(accounts.getRequiredUserByEmail("three@example.com")).thenReturn(user);
        Address home = Address.builder().addressID(5).nickname("Home").isDefault(true).user(user).build();
        when(addresses.findByAddressIDAndUserUserID(5, 3)).thenReturn(Optional.of(home));
        when(addresses.countByUserUserID(3)).thenReturn(1L);

        ApiException error = assertThrows(ApiException.class, () -> service.delete("three@example.com", 5));

        assertEquals(org.springframework.http.HttpStatus.CONFLICT, error.status);
        // Nothing changed: the address still belongs to the customer and is still their default.
        assertSame(user, home.getUser());
        assertTrue(home.getIsDefault());
        verify(addresses, never()).save(any());
        verify(addresses, never()).delete(any());
    }
}
