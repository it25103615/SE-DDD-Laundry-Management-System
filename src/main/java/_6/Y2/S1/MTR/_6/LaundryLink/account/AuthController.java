package _6.Y2.S1.MTR._6.LaundryLink.account;

import java.util.Map;
import _6.Y2.S1.MTR._6.LaundryLink.account.dto.CustomerRegistrationRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AccountProfileService accounts;

    public AuthController(AccountProfileService accounts) {
        this.accounts = accounts;
    }
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName(), "parameterName", token.getParameterName());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Integer> register(@Valid @RequestBody CustomerRegistrationRequest request) {
        return Map.of("id", accounts.registerCustomer(request));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> conflict(IllegalArgumentException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> invalid(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getAllErrors().stream()
                .findFirst().map(error -> error.getDefaultMessage()).orElse("Check your registration details.");
        return Map.of("message", message);
    }
}
