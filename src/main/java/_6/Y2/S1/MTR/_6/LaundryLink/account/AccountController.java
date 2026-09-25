package _6.Y2.S1.MTR._6.LaundryLink.account;

import _6.Y2.S1.MTR._6.LaundryLink.account.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api") @RequiredArgsConstructor
public class AccountController {
    private final AccountService accountService;
    @GetMapping("/account/profile") public UserResponse profile(Authentication authentication) { return accountService.getCurrentUser(authentication.getName()); }
    @PutMapping("/account/profile") public UserResponse updateProfile(Authentication authentication, @Valid @RequestBody ProfileUpdateRequest request) { return accountService.updateCurrentUser(authentication.getName(), request); }
    @PutMapping("/account/password") @ResponseStatus(HttpStatus.NO_CONTENT) public void changePassword(Authentication authentication, @Valid @RequestBody PasswordChangeRequest request) { accountService.changePassword(authentication.getName(), request); }
    @GetMapping("/admin/accounts") @PreAuthorize("hasAnyRole('OWNER','ADMIN','MANAGER')") public List<UserResponse> users(@RequestParam(required = false) String query) { return accountService.searchUsers(query); }
    @PostMapping("/admin/accounts") @PreAuthorize("hasAnyRole('OWNER','ADMIN','MANAGER')") public ResponseEntity<UserResponse> createUser(Authentication authentication, @Valid @RequestBody AdminUserRequest request) { validateManagerRoleAssignment(authentication, request); return ResponseEntity.status(HttpStatus.CREATED).body(accountService.createManagedUser(request)); }
    @PutMapping("/admin/accounts/{userId}") @PreAuthorize("hasAnyRole('OWNER','ADMIN','MANAGER')") public UserResponse updateUser(Authentication authentication, @PathVariable Integer userId, @Valid @RequestBody AdminUserRequest request) { validateManagerRoleAssignment(authentication, request); if (!isAdministrator(authentication)) { UserResponse existing = accountService.getManagedUser(userId); if (existing.type().name().equals("OWNER") || existing.type().name().equals("ADMIN") || existing.type().name().equals("MANAGER")) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "Managers cannot modify owner, administrator or manager accounts."); } return accountService.updateManagedUser(userId, request); }
    private void validateManagerRoleAssignment(Authentication authentication, AdminUserRequest request) {
        boolean administrator = isAdministrator(authentication);
        if (!administrator && (request.type().name().equals("OWNER") || request.type().name().equals("ADMIN") || request.type().name().equals("MANAGER"))) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "Managers cannot assign owner, administrator or manager roles.");
    }
    private boolean isAdministrator(Authentication authentication) { return authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_OWNER") || a.getAuthority().equals("ROLE_ADMIN")); }
}
