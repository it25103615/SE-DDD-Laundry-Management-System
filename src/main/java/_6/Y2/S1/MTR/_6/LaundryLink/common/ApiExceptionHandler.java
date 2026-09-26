package _6.Y2.S1.MTR._6.LaundryLink.common;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class) ResponseEntity<Map<String, String>> api(ApiException e) { return ResponseEntity.status(e.status).body(Map.of("message", e.getMessage())); }
    @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<Map<String, String>> validation(MethodArgumentNotValidException e) { String message = e.getBindingResult().getFieldErrors().stream().findFirst().map(error -> error.getField() + " " + error.getDefaultMessage()).orElse("Invalid request."); return ResponseEntity.badRequest().body(Map.of("message", message)); }
}
