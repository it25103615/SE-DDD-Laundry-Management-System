package _6.Y2.S1.MTR._6.LaundryLink.address;

import _6.Y2.S1.MTR._6.LaundryLink.address.dto.*;
import java.util.*;
public interface AddressService {
    List<AddressResponse> getCurrentUserAddresses(String email);
    AddressResponse create(String email, AddressRequest request);
    AddressResponse update(String email, Integer addressId, AddressRequest request);
    void delete(String email, Integer addressId);
    AddressResponse setDefault(String email, Integer addressId);
}
