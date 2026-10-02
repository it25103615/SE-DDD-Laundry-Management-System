package _6.Y2.S1.MTR._6.LaundryLink.account;

import _6.Y2.S1.MTR._6.LaundryLink.account.dto.*;
import _6.Y2.S1.MTR._6.LaundryLink.user.User;
import java.util.*;

public interface AccountService {
    UserResponse register(RegistrationRequest request);
    UserResponse getCurrentUser(String email);
    UserResponse updateCurrentUser(String email, ProfileUpdateRequest request);
    void changePassword(String email, PasswordChangeRequest request);
    List<UserResponse> searchUsers(String query);
    UserResponse getManagedUser(Integer userId);
    UserResponse createManagedUser(AdminUserRequest request);
    UserResponse updateManagedUser(Integer userId, AdminUserRequest request);
    User getRequiredUserByEmail(String email);
}
