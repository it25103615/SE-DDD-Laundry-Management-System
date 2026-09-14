package _6.Y2.S1.MTR._6.LaundryLink.exception.support;

import _6.Y2.S1.MTR._6.LaundryLink.controller.support.SupportController;

import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes=SupportController.class)
public class SupportErrors {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> status(ResponseStatusException e) { return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",e.getReason()==null?"Request failed.":e.getReason())); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> validation(MethodArgumentNotValidException e) {
        String message=e.getBindingResult().getFieldErrors().stream().map(f->f.getField()+": "+f.getDefaultMessage()).distinct().reduce((a,b)->a+"; "+b).orElse("Invalid input.");
        return ResponseEntity.badRequest().body(Map.of("message",message));
    }
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<?> duplicate(DuplicateKeyException e) { return ResponseEntity.status(409).body(Map.of("message","A setting with this key already exists.")); }
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<?> database(DataAccessException e) { return ResponseEntity.status(503).body(Map.of("message","The support database is unavailable or its migration is missing. Please contact your administrator.")); }
}
