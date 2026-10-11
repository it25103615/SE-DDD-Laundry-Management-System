package _6.Y2.S1.MTR._6.LaundryLink.support;

import _6.Y2.S1.MTR._6.LaundryLink.support.dto.SupportRequests.ServiceInput;
import _6.Y2.S1.MTR._6.LaundryLink.support.dto.SupportRequests.StaffInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.List;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import _6.Y2.S1.MTR._6.LaundryLink.notification.NotificationService;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdministrationServiceTest {
    SupportRepository repo;
    SupportAccess access;
    PasswordEncoder passwords;
    AdministrationService service;
    SupportAccess.Actor manager = new SupportAccess.Actor(4, "Manager", "MANAGER", false);
    SupportAccess.Actor owner = new SupportAccess.Actor(5, "Owner", "OWNER", false);

    @BeforeEach void setup() {
        repo = mock(SupportRepository.class);
        access = mock(SupportAccess.class);
        passwords = mock(PasswordEncoder.class);
        service = new AdministrationService(repo, access, passwords, mock(NotificationService.class));
    }

    @ParameterizedTest
    @CsvSource({"MANAGER,STAFF", "MANAGER,RIDER", "MANAGER,CSM", "OWNER,STAFF", "OWNER,RIDER", "OWNER,CSM", "OWNER,MANAGER"})
    void allowedAccountCreationPreservesHashingAndAudit(String actorRole, String role) {
        var actor = new SupportAccess.Actor(4, "Actor", actorRole, false);
        var input = new StaffInput("New", "Employee", "employee@example.com", "0771234567", role, "Password1!", true, null);
        var encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4);
        service = new AdministrationService(repo, access, encoder, mock(NotificationService.class));
        when(repo.insert(anyString(), any(Object[].class))).thenReturn(11);
        assertEquals(11, service.createStaff(actor, input));
        verify(repo).insert(anyString(), eq("New"), eq("Employee"), eq("employee@example.com"), argThat((String hash) -> encoder.matches("Password1!", hash)), eq("0771234567"), eq(role), eq(true));
        verify(repo).audit(null, 4, "Staff account created", "employee@example.com · " + role);
    }

    @ParameterizedTest
    @CsvSource({"MANAGER,MANAGER,403", "MANAGER,OWNER,400", "MANAGER,ADMIN,400", "ADMIN,MANAGER,403"})
    void forbiddenCreationHasNoSideEffects(String actorRole, String role, int status) {
        var input = new StaffInput("New", "Employee", "employee@example.com", "0771234567", role, "Password1!", true, null);
        var error = assertThrows(ResponseStatusException.class, () -> service.createStaff(new SupportAccess.Actor(4, "Actor", actorRole, false), input));
        assertEquals(status, error.getStatusCode().value());
        verifyNoInteractions(repo, passwords);
    }

    @ParameterizedTest
    @CsvSource({"MANAGER,STAFF,200", "MANAGER,RIDER,200", "MANAGER,CSM,200", "MANAGER,MANAGER,403", "MANAGER,OWNER,400", "OWNER,STAFF,200", "OWNER,RIDER,200", "OWNER,CSM,200", "OWNER,MANAGER,200", "ADMIN,MANAGER,403"})
    void roleUpdatesEnforceAssignmentPermissions(String actorRole, String role, int status) {
        var actor = new SupportAccess.Actor(4, "Actor", actorRole, false);
        when(repo.query(anyString(), eq(11))).thenReturn(List.of(Map.of("role", "STAFF", "email", "employee@example.com", "version", 0)));
        if (status == 200) {
            when(repo.update(anyString(), eq(role), eq(11), eq(0))).thenReturn(1);
            service.changeStaffRole(actor, 11, role, 0);
            verify(repo).audit(null, 4, "Employee role changed", "Account #11 · " + role);
        } else {
            var error = assertThrows(ResponseStatusException.class, () -> service.changeStaffRole(actor, 11, role, 0));
            assertEquals(status, error.getStatusCode().value());
            verify(repo, never()).update(anyString(), any(Object[].class));
            verify(repo, never()).audit(any(), anyInt(), anyString(), anyString());
        }
    }

    @Test void managerCannotDemoteExistingManager() {
        when(repo.query(anyString(), eq(11))).thenReturn(List.of(Map.of("role", "MANAGER", "email", "manager@example.com", "version", 0)));
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> service.changeStaffRole(manager, 11, "STAFF", 0)).getStatusCode().value());
        verify(repo, never()).update(anyString(), any(Object[].class));
    }

    @Test void ownerCreatesStaffWithHashedPasswordAndAudit() {
        var input = new StaffInput("New", "Staff", "STAFF@EXAMPLE.COM", "0771234567", "STAFF", "Password1!", true, null);
        when(passwords.encode("Password1!")).thenReturn("hash");
        when(repo.insert(anyString(), any(Object[].class))).thenReturn(12);
        assertEquals(12, service.createStaff(owner, input));
        verify(repo).insert(anyString(), eq("New"), eq("Staff"), eq("staff@example.com"), eq("hash"), eq("0771234567"), eq("STAFF"), eq(true));
        verify(repo).audit(null, 5, "Staff account created", "staff@example.com · STAFF");
    }

    @Test void managementScreenCannotCreateOwner() {
        var input = new StaffInput("Another", "Owner", "owner2@example.com", "0771234567", "OWNER", "Password1!", true, null);
        assertThrows(ResponseStatusException.class, () -> service.createStaff(owner, input));
        verifyNoInteractions(passwords);
    }

    @Test void accountCannotDeactivateItself() {
        var input = new StaffInput("Shop", "Manager", "manager@example.com", "0771234567", "MANAGER", null, false, 0);
        assertThrows(ResponseStatusException.class, () -> service.updateStaff(manager, 4, input));
        verify(repo, never()).update(anyString(), any(Object[].class));
    }

    @Test void duplicateServiceNameIsRejected() {
        when(repo.count(anyString(), eq("Wash"))).thenReturn(1);
        var input = new ServiceInput("Wash", "Standard washing", 48, true, null);
        assertThrows(ResponseStatusException.class, () -> service.createService(manager, input));
        verify(repo, never()).insert(anyString(), any(Object[].class));
    }
}
