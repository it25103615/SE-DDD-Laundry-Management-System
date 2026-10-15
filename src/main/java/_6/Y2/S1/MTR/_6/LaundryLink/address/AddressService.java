/* AddressService
 * SERVICE: business rules and orchestration. Constructor-injected collaborators show composition and dependency injection; private fields encapsulate dependencies.
 *
 */
package _6.Y2.S1.MTR._6.LaundryLink.address;

import _6.Y2.S1.MTR._6.LaundryLink.address.dto.*;
import java.util.*;

// OOP — Abstraction: this interface describes operations that implementing classes must provide.
public interface AddressService {
  List<AddressResponse> getCurrentUserAddresses(String email);

  AddressResponse create(String email, AddressRequest request);

  AddressResponse update(String email, Integer addressId, AddressRequest request);

  void delete(String email, Integer addressId);

  AddressResponse setDefault(String email, Integer addressId);
}
