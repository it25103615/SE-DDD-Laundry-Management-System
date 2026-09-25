package _6.Y2.S1.MTR._6.LaundryLink.dto.account;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProfilePasswordTest {
    @Test void validPasswordPassesAndWeakPasswordGetsReadableMessage() {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var validator=factory.getValidator();
            assertTrue(validator.validate(new ProfileRequests.Password("Current#2026","Laundry#2026","Laundry#2026")).isEmpty());
            var errors=validator.validate(new ProfileRequests.Password("Current#2026","password123","password123"));
            assertTrue(errors.stream().anyMatch(error->error.getMessage().startsWith("New password needs")));
            assertTrue(errors.stream().noneMatch(error->error.getMessage().contains("(?=")));
        }
    }
}
