package _6.Y2.S1.MTR._6.LaundryLink.dto.account;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RegistrationRequestTest {
    @Test void acceptsValidRegistration() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var request = new RegistrationRequest("Viva Customer", "viva@example.com", "0771234567",
                    "Laundry#2026", "Laundry#2026", "Colombo");
            assertTrue(factory.getValidator().validate(request).isEmpty());
        }
    }

    @Test void rejectsLettersAndIncorrectLengthInPhoneNumber() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertFalse(validator.validate(validWithPhone("077ABC4567")).isEmpty());
            assertFalse(validator.validate(validWithPhone("771234567")).isEmpty());
            assertFalse(validator.validate(validWithPhone("07712345678")).isEmpty());
        }
    }

    @Test void rejectsWeakOrMismatchedPasswords() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertFalse(validator.validate(new RegistrationRequest("Viva Customer", "viva@example.com", "0771234567",
                    "password", "password", "Colombo")).isEmpty());
            assertFalse(validator.validate(new RegistrationRequest("Viva Customer", "viva@example.com", "0771234567",
                    "Laundry#2026", "Different#2026", "Colombo")).isEmpty());
        }
    }

    private RegistrationRequest validWithPhone(String phone) {
        return new RegistrationRequest("Viva Customer", "viva@example.com", phone,
                "Laundry#2026", "Laundry#2026", "Colombo");
    }
}
