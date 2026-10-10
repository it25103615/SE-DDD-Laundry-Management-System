package _6.Y2.S1.MTR._6.LaundryLink.account;

import java.security.Principal;
import java.util.Map;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import _6.Y2.S1.MTR._6.LaundryLink.account.dto.ProfileRequests;

@RestController
@RequestMapping("/api/account/profile")
public class ProfileController {
    private final AccountProfileService accounts;
    public ProfileController(AccountProfileService accounts) { this.accounts=accounts; }
    private String email(Principal principal) {
        if(principal==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Sign in first.");
        return principal.getName();
    }
    @GetMapping public Map<String,Object> get(Principal principal) { return accounts.profile(email(principal)); }
    @PutMapping public Map<String,Object> update(Principal principal,@Valid @RequestBody ProfileRequests.Details input) {
        return accounts.updateProfile(email(principal),input);
    }
    @PutMapping("/password") public Map<String,String> password(Principal principal,@Valid @RequestBody ProfileRequests.Password input) {
        accounts.updatePassword(email(principal),input);
        return Map.of("message","Password updated.");
    }
    @DeleteMapping
    public ResponseEntity<Map<String,String>> delete(Authentication authentication,
            @Valid @RequestBody ProfileRequests.Deletion input,
            HttpServletRequest request, HttpServletResponse response) {
        try {
            accounts.deactivateAccount(email(authentication), input);
        } catch (ResponseStatusException error) {
            return ResponseEntity.status(error.getStatusCode()).body(Map.of("message", error.getReason()));
        }
        // The transactional service has committed before the current session is invalidated.
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return ResponseEntity.ok(Map.of("message", "Account deactivated."));
    }

    // The service reports problems (wrong current password, email already taken) as a
    // ResponseStatusException. Return its reason as "message", which is what the profile
    // page reads, instead of the default error body that leaves the reason out.
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,String>> rejected(ResponseStatusException error) {
        String reason = error.getReason() == null ? "Unable to save your profile." : error.getReason();
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message", reason));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String,String> invalid(MethodArgumentNotValidException error) {
        return Map.of("message",error.getBindingResult().getAllErrors().stream().findFirst()
                .map(item->item.getDefaultMessage()).orElse("Check your profile details."));
    }
}
