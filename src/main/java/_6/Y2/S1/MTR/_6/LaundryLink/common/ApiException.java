package _6.Y2.S1.MTR._6.LaundryLink.common;
import org.springframework.http.HttpStatus;
public class ApiException extends RuntimeException { public final HttpStatus status; public ApiException(HttpStatus status, String message) { super(message); this.status = status; } }
