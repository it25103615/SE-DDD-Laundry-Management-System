package _6.Y2.S1.MTR._6.LaundryLink.auth;

import _6.Y2.S1.MTR._6.LaundryLink.account.AccountService;
import _6.Y2.S1.MTR._6.LaundryLink.account.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/auth") @RequiredArgsConstructor
public class AuthController {
    private final AccountService accountService;
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegistrationRequest request) { return ResponseEntity.status(HttpStatus.CREATED).body(accountService.register(request)); }
    @GetMapping("/me")
    public UserResponse me(Authentication authentication) { return accountService.getCurrentUser(authentication.getName()); }
    @GetMapping("/csrf")
    public Map<String, String> csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword() { return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(Map.of("message", "Password reset is unavailable until secure reset-token storage is approved.")); }
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword() { return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(Map.of("message", "Password reset is unavailable until secure reset-token storage is approved.")); }
}
