package _6.Y2.S1.MTR._6.LaundryLink.controller.account;

import java.security.Principal;
import java.util.Map;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import _6.Y2.S1.MTR._6.LaundryLink.dto.account.ProfileRequests;
import _6.Y2.S1.MTR._6.LaundryLink.service.account.AccountService;

@RestController
@RequestMapping("/api/account/profile")
public class ProfileController {
    private final AccountService accounts;
    public ProfileController(AccountService accounts) { this.accounts=accounts; }
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
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String,String> invalid(MethodArgumentNotValidException error) {
        return Map.of("message",error.getBindingResult().getAllErrors().stream().findFirst()
                .map(item->item.getDefaultMessage()).orElse("Check your profile details."));
    }
}
