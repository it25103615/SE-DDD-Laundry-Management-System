package _6.Y2.S1.MTR._6.LaundryLink.account;

import java.util.Map;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import _6.Y2.S1.MTR._6.LaundryLink.account.dto.PasswordRecoveryRequests.*;

@RestController
@RequestMapping("/api/auth")
public class PasswordRecoveryController {
    private final PasswordRecoveryService recovery;
    public PasswordRecoveryController(PasswordRecoveryService recovery) { this.recovery=recovery; }
    @PostMapping("/forgot-password")
    public Map<String,String> forgot(@Valid @RequestBody Forgot input) {
        recovery.request(input.email());
        return Map.of("message","If an active account matches that email, a reset link will be sent. Check your inbox and spam folder.");
    }
    @PostMapping("/reset-password")
    public Map<String,String> reset(@Valid @RequestBody Reset input) {
        recovery.reset(input.token(),input.password(),input.confirmPassword());
        return Map.of("message","Password updated. You can now log in with your new password.");
    }
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<Map<String,String>> error(org.springframework.web.server.ResponseStatusException exception) {
        return org.springframework.http.ResponseEntity.status(exception.getStatusCode())
                .body(Map.of("message", exception.getReason()==null ? "Recovery request failed." : exception.getReason()));
    }
}
