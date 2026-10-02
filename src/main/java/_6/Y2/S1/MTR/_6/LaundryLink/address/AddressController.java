package _6.Y2.S1.MTR._6.LaundryLink.address;

import _6.Y2.S1.MTR._6.LaundryLink.address.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/account/addresses") @RequiredArgsConstructor
public class AddressController {
    private final AddressService addressService;
    @GetMapping public List<AddressResponse> list(Authentication authentication) { return addressService.getCurrentUserAddresses(authentication.getName()); }
    @PostMapping public ResponseEntity<AddressResponse> create(Authentication authentication, @Valid @RequestBody AddressRequest request) { return ResponseEntity.status(HttpStatus.CREATED).body(addressService.create(authentication.getName(), request)); }
    @PutMapping("/{addressId}") public AddressResponse update(Authentication authentication, @PathVariable Integer addressId, @Valid @RequestBody AddressRequest request) { return addressService.update(authentication.getName(), addressId, request); }
    @DeleteMapping("/{addressId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(Authentication authentication, @PathVariable Integer addressId) { addressService.delete(authentication.getName(), addressId); }
    @PatchMapping("/{addressId}/default") public AddressResponse setDefault(Authentication authentication, @PathVariable Integer addressId) { return addressService.setDefault(authentication.getName(), addressId); }
}
