/* AccountServiceImpl
 * ABSTRACTION / POLYMORPHISM: implements the declared contract. @Override identifies the contract methods supplied by this type.
 */
package _6.Y2.S1.MTR._6.LaundryLink.account;

import _6.Y2.S1.MTR._6.LaundryLink.account.dto.*;
import _6.Y2.S1.MTR._6.LaundryLink.common.ApiException;
import _6.Y2.S1.MTR._6.LaundryLink.user.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
// Commit these database changes together, or roll them back if the operation fails.
@Transactional
public class AccountServiceImpl implements AccountService {
  private final UserRepository userRepository;
  // OOP — Abstraction: depend on the PasswordEncoder interface, so the caller does not need the
  // concrete hashing implementation.
  private final PasswordEncoder passwordEncoder;

  @Override
  public UserResponse register(RegistrationRequest request) {
    if (!request.password().equals(request.confirmPassword()))
      throw new ApiException(HttpStatus.BAD_REQUEST, "Password confirmation does not match.");
    User user =
        buildUser(
            request.firstName(),
            request.middleName(),
            request.lastName(),
            request.email(),
            request.phoneNumber(),
            UserRole.CUSTOMER);
    // OOP — Runtime polymorphism: this interface call runs the configured encoder implementation,
    // BCrypt in this application.
    user.setPassword(passwordEncoder.encode(request.password()));
    return UserResponse.from(userRepository.save(user));
  }

  @Override
  // Run database reads in a read-only transaction.
  @Transactional(readOnly = true)
  public UserResponse getCurrentUser(String email) {
    return UserResponse.from(getRequiredUserByEmail(email));
  }

  @Override
  public UserResponse updateCurrentUser(String email, ProfileUpdateRequest request) {
    User user = getRequiredUserByEmail(email);
    applyUserFields(
        user,
        request.firstName(),
        request.middleName(),
        request.lastName(),
        request.email(),
        request.phoneNumber());
    return UserResponse.from(userRepository.save(user));
  }

  @Override
  public void changePassword(String email, PasswordChangeRequest request) {
    if (!request.newPassword().equals(request.confirmPassword()))
      throw new ApiException(HttpStatus.BAD_REQUEST, "Password confirmation does not match.");
    User user = getRequiredUserByEmail(email);
    if (!passwordEncoder.matches(request.currentPassword(), user.getPassword()))
      throw new ApiException(HttpStatus.BAD_REQUEST, "Current password is incorrect.");
    if (passwordEncoder.matches(request.newPassword(), user.getPassword()))
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "New password must be different from the current password.");
    // OOP — Runtime polymorphism: this interface call runs the configured encoder implementation,
    // BCrypt in this application.
    user.setPassword(passwordEncoder.encode(request.newPassword()));
    userRepository.save(user);
  }

  @Override
  // Run database reads in a read-only transaction.
  @Transactional(readOnly = true)
  public List<UserResponse> searchUsers(String query) {
    String term = query == null ? "" : query.trim();
    List<User> users =
        term.isEmpty()
            ? userRepository.findAll()
            : userRepository
                .findByEmailContainingIgnoreCaseOrFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(
                    term, term, term);
    return users.stream().map(UserResponse::from).toList();
  }

  @Override
  // Run database reads in a read-only transaction.
  @Transactional(readOnly = true)
  public UserResponse getManagedUser(Integer userId) {
    return UserResponse.from(
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found.")));
  }

  @Override
  public UserResponse createManagedUser(AdminUserRequest request) {
    if (request.password() == null || request.password().isBlank())
      throw new ApiException(HttpStatus.BAD_REQUEST, "An initial password is required.");
    User user =
        buildUser(
            request.firstName(),
            request.middleName(),
            request.lastName(),
            request.email(),
            request.phoneNumber(),
            request.type());
    // OOP — Runtime polymorphism: this interface call runs the configured encoder implementation,
    // BCrypt in this application.
    user.setPassword(passwordEncoder.encode(request.password()));
    return UserResponse.from(userRepository.save(user));
  }

  @Override
  public UserResponse updateManagedUser(Integer userId, AdminUserRequest request) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found."));
    applyUserFields(
        user,
        request.firstName(),
        request.middleName(),
        request.lastName(),
        request.email(),
        request.phoneNumber());
    user.setType(request.type());
    if (request.password() != null && !request.password().isBlank())
      // OOP — Runtime polymorphism: this interface call runs the configured encoder implementation,
      // BCrypt in this application.
      user.setPassword(passwordEncoder.encode(request.password()));
    return UserResponse.from(userRepository.save(user));
  }

  @Override
  // Run database reads in a read-only transaction.
  @Transactional(readOnly = true)
  public User getRequiredUserByEmail(String email) {
    return userRepository
        .findByEmailIgnoreCase(email)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found."));
  }

  private User buildUser(
      String firstName,
      String middleName,
      String lastName,
      String email,
      String phone,
      UserRole role) {
    String normalizedEmail = normalizeEmail(email);
    String normalizedPhone = normalizePhone(phone);
    if (userRepository.existsByEmailIgnoreCase(normalizedEmail))
      throw new ApiException(HttpStatus.CONFLICT, "An account already uses this email address.");
    if (userRepository.existsByPhoneNumber(normalizedPhone))
      throw new ApiException(HttpStatus.CONFLICT, "An account already uses this phone number.");
    return User.builder()
        .firstName(firstName.trim())
        .middleName(blankToNull(middleName))
        .lastName(lastName.trim())
        .email(normalizedEmail)
        .phoneNumber(normalizedPhone)
        .type(role)
        .build();
  }

  private void applyUserFields(
      User user, String firstName, String middleName, String lastName, String email, String phone) {
    String normalizedEmail = normalizeEmail(email), normalizedPhone = normalizePhone(phone);
    userRepository
        .findByEmailIgnoreCase(normalizedEmail)
        .filter(other -> !other.getUserID().equals(user.getUserID()))
        .ifPresent(
            other -> {
              throw new ApiException(
                  HttpStatus.CONFLICT, "An account already uses this email address.");
            });
    userRepository
        .findByPhoneNumber(normalizedPhone)
        .filter(other -> !other.getUserID().equals(user.getUserID()))
        .ifPresent(
            other -> {
              throw new ApiException(
                  HttpStatus.CONFLICT, "An account already uses this phone number.");
            });
    user.setFirstName(firstName.trim());
    user.setMiddleName(blankToNull(middleName));
    user.setLastName(lastName.trim());
    user.setEmail(normalizedEmail);
    user.setPhoneNumber(normalizedPhone);
  }

  private String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String normalizePhone(String phone) {
    String digits = phone.replaceAll("\\D", "");
    if (digits.length() == 11 && digits.startsWith("94")) digits = "0" + digits.substring(2);
    if (!digits.matches("\\d{10}"))
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "Phone number must contain exactly 10 digits.");
    return digits;
  }
}
