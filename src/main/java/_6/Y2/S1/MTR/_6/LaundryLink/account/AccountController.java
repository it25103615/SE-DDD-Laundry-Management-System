/* AccountController
 * CONTROLLER: HTTP endpoint adapter. Binds requests and delegates work; browser JavaScript calls these endpoints.
 */
package _6.Y2.S1.MTR._6.LaundryLink.account;

import _6.Y2.S1.MTR._6.LaundryLink.account.dto.*;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AccountController {

  private final AccountService accountService;

  @GetMapping("/admin/accounts")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','MANAGER')")
  public List<UserResponse> users(@RequestParam(required = false) String query) {
    return accountService.searchUsers(query);
  }

  @PostMapping("/admin/accounts")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','MANAGER')")
  public ResponseEntity<UserResponse> createUser(
    Authentication authentication,
    @Valid @RequestBody AdminUserRequest request
  ) {
    validateManagerRoleAssignment(authentication, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(
      accountService.createManagedUser(request)
    );
  }

  @PutMapping("/admin/accounts/{userId}")
  @PreAuthorize("hasAnyRole('OWNER','ADMIN','MANAGER')")
  public UserResponse updateUser(
    Authentication authentication,
    @PathVariable Integer userId,
    @Valid @RequestBody AdminUserRequest request
  ) {
    throw new org.springframework.web.server.ResponseStatusException(
      HttpStatus.FORBIDDEN,
      "Use the employee role-change endpoint. Personal details are managed by the employee."
    );
  }

  private void validateManagerRoleAssignment(
    Authentication authentication,
    AdminUserRequest request
  ) {
    if (
      request.type().name().equals("MANAGER") &&
      authentication
        .getAuthorities()
        .stream()
        .noneMatch(a -> a.getAuthority().equals("ROLE_OWNER"))
    ) throw new org.springframework.web.server.ResponseStatusException(
      HttpStatus.FORBIDDEN,
      "Only the Owner can assign the Manager role."
    );
    boolean administrator = isAdministrator(authentication);
    if (
      !administrator &&
      (request.type().name().equals("OWNER") ||
        request.type().name().equals("ADMIN") ||
        request.type().name().equals("MANAGER"))
    ) throw new org.springframework.web.server.ResponseStatusException(
      HttpStatus.FORBIDDEN,
      "Managers cannot assign owner, administrator or manager roles."
    );
  }

  private boolean isAdministrator(Authentication authentication) {
    return authentication
      .getAuthorities()
      .stream()
      .anyMatch(
        a -> a.getAuthority().equals("ROLE_OWNER") || a.getAuthority().equals("ROLE_ADMIN")
      );
  }
}
