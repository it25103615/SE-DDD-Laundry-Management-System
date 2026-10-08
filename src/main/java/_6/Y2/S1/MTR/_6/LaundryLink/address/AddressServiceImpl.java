package _6.Y2.S1.MTR._6.LaundryLink.address;

import _6.Y2.S1.MTR._6.LaundryLink.account.AccountService;
import _6.Y2.S1.MTR._6.LaundryLink.address.dto.*;
import _6.Y2.S1.MTR._6.LaundryLink.common.ApiException;
import _6.Y2.S1.MTR._6.LaundryLink.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional
public class AddressServiceImpl implements AddressService {
    private final AddressRepository addressRepository;
    private final AccountService accountService;
    @Override @Transactional(readOnly = true)
    public List<AddressResponse> getCurrentUserAddresses(String email) {
        User user = accountService.getRequiredUserByEmail(email);
        return addressRepository.findByUserUserIDOrderByIsDefaultDescAddressIDAsc(user.getUserID()).stream().map(AddressResponse::from).toList();
    }
    @Override public AddressResponse create(String email, AddressRequest request) {
        User user = accountService.getRequiredUserByEmail(email);
        Address address = Address.builder().user(user).isDefault(Boolean.TRUE.equals(request.isDefault())).build();
        apply(address, request);
        if (Boolean.TRUE.equals(address.getIsDefault()) || addressRepository.findByUserUserIDOrderByIsDefaultDescAddressIDAsc(user.getUserID()).isEmpty()) {
            addressRepository.clearDefaultForUser(user.getUserID());
            address.setIsDefault(true);
        }
        return AddressResponse.from(addressRepository.save(address));
    }
    @Override public AddressResponse update(String email, Integer id, AddressRequest request) {
        Address address = owned(email, id);
        boolean wasDefault = Boolean.TRUE.equals(address.getIsDefault());
        boolean makeDefault = Boolean.TRUE.equals(request.isDefault());
        // The other addresses lose their default flag first, before this address is touched.
        // (Clearing every address after changing this one wiped this one's flag as well, which
        // left the customer with no default at all.)
        if (makeDefault) addressRepository.clearDefaultForUserExcept(address.getUser().getUserID(), address.getAddressID());
        apply(address, request);
        // Editing the default address must not switch its default flag off, because that would
        // leave the customer with no default. The default only moves when another address is
        // made the default.
        if (wasDefault || makeDefault) address.setIsDefault(true);
        return AddressResponse.from(addressRepository.save(address));
    }
    // "Deleting" an address does not remove the row. It only detaches it from the customer
    // (userID becomes NULL), so delivery rows that point at it can still show where an old
    // order was actually collected and delivered. A detached address no longer appears in the
    // customer's list and cannot be edited or chosen for a new order, because every lookup is
    // by owner.
    @Override public void delete(String email, Integer id) {
        Address address = owned(email, id);
        // A customer must always keep at least one address for pickups and deliveries.
        if (addressRepository.countByUserUserID(address.getUser().getUserID()) <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, "You cannot delete your only address. Add another address first.");
        }
        // The default address cannot be removed while it is still the default, otherwise the
        // customer would be left with no default. The profile page first makes another address
        // the default (asking the customer which one) and only then deletes this one.
        if (Boolean.TRUE.equals(address.getIsDefault())) {
            throw new ApiException(HttpStatus.CONFLICT, "This is your default address. Make another address your default before deleting it.");
        }
        address.setUser(null);
        // An address nobody owns cannot be anyone's default. The isDefault column is NOT NULL
        // in the database, so the flag is cleared to false rather than set to NULL.
        address.setIsDefault(false);
        addressRepository.save(address);
    }
    @Override public AddressResponse setDefault(String email, Integer id) {
        Address address = owned(email, id);
        // Only the customer's other addresses are cleared, so choosing the address that is
        // already the default keeps it as the default instead of clearing it.
        addressRepository.clearDefaultForUserExcept(address.getUser().getUserID(), address.getAddressID());
        address.setIsDefault(true);
        return AddressResponse.from(addressRepository.save(address));
    }
    private Address owned(String email, Integer id) {
        User user = accountService.getRequiredUserByEmail(email);
        return addressRepository.findByAddressIDAndUserUserID(id, user.getUserID()).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Address not found."));
    }
    private void apply(Address address, AddressRequest request) {
        address.setNickname(request.nickname().trim()); address.setStreet(request.street().trim()); address.setCity(request.city().trim()); address.setState(request.state().trim());
        address.setDeliveryInstructions(request.deliveryInstructions() == null || request.deliveryInstructions().isBlank() ? null : request.deliveryInstructions().trim());
        if (request.isDefault() != null) address.setIsDefault(request.isDefault());
    }
}
