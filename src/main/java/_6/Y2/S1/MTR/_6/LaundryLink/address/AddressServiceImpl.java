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
        Address address = owned(email, id); apply(address, request);
        if (Boolean.TRUE.equals(request.isDefault())) addressRepository.clearDefaultForUser(address.getUser().getUserID());
        return AddressResponse.from(addressRepository.save(address));
    }
    @Override public void delete(String email, Integer id) { addressRepository.delete(owned(email, id)); }
    @Override public AddressResponse setDefault(String email, Integer id) {
        Address address = owned(email, id); addressRepository.clearDefaultForUser(address.getUser().getUserID()); address.setIsDefault(true);
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
