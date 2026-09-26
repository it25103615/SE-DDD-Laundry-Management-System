package _6.Y2.S1.MTR._6.LaundryLink.service.support;

import _6.Y2.S1.MTR._6.LaundryLink.dto.support.SupportRequests.ServiceInput;
import _6.Y2.S1.MTR._6.LaundryLink.dto.support.SupportRequests.StaffInput;
import _6.Y2.S1.MTR._6.LaundryLink.repository.support.SupportRepository;
import _6.Y2.S1.MTR._6.LaundryLink.security.support.SupportAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import _6.Y2.S1.MTR._6.LaundryLink.service.shared.NotificationService;
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

    @Test void managerCanCreateAnotherManager() {
        var input = new StaffInput("New", "Manager", "manager@example.com", "0771234567", "MANAGER", "Password1!", true, null);
        when(passwords.encode("Password1!")).thenReturn("hash");
        when(repo.insert(anyString(), any(Object[].class))).thenReturn(11);
        assertEquals(11, service.createStaff(manager, input));
        verify(repo).insert(anyString(), eq("New"), eq("Manager"), eq("manager@example.com"), eq("hash"), eq("0771234567"), eq("MANAGER"), eq(true));
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
