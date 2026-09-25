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
}
