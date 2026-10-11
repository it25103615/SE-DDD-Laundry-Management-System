package _6.Y2.S1.MTR._6.LaundryLink.support;

import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import _6.Y2.S1.MTR._6.LaundryLink.notification.NotificationService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class EmployeeManagementTest {
    SupportRepository repo;
    AdministrationService service;
    BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(4);
    SupportAccess.Actor owner = new SupportAccess.Actor(1,"Owner","OWNER",false);
    SupportAccess.Actor manager = new SupportAccess.Actor(2,"Manager","MANAGER",false);

    @BeforeEach void setup() {
        repo = mock(SupportRepository.class);
        service = new AdministrationService(repo, new SupportAccess(mock(JdbcTemplate.class)), passwords, mock(NotificationService.class));
        when(repo.update(anyString(), any(Object[].class))).thenReturn(1);
    }
    void target(String role) {
        when(repo.query(anyString(), eq(10))).thenReturn(List.of(Map.of("role",role,"email","employee@example.com","version",3)));
    }
    void forbidden(Runnable action) {
        assertEquals(403, assertThrows(ResponseStatusException.class, action::run).getStatusCode().value());
        verify(repo, never()).update(anyString(), any(Object[].class));
    }
    @ParameterizedTest @ValueSource(strings={"RIDER","STAFF","CSM","MANAGER"})
    void ownerCanAssignEachRoleAndManageManagers(String role) {
        target("MANAGER"); service.changeStaffRole(owner,10,role,3);
        verify(repo).update(contains("SET type=?,updatedAt=SYSDATETIME(),version=version+1"),eq(role),eq(10),eq(3));
    }
    @ParameterizedTest @ValueSource(strings={"RIDER","STAFF","CSM"})
    void managerCanChangePermittedRoles(String role) {
        target("STAFF"); service.changeStaffRole(manager,10,role,3);
        verify(repo).update(anyString(),eq(role),eq(10),eq(3));
    }
    @Test void managerCannotPromoteToManager() { target("STAFF"); forbidden(()->service.changeStaffRole(manager,10,"MANAGER",3)); }
    @ParameterizedTest @ValueSource(strings={"OWNER","CUSTOMER","ADMIN"})
    void privilegedAndCustomerTargetsAreProtected(String role) {
        target(role);
        for (var actor : List.of(owner,manager)) {
            forbidden(()->service.changeStaffRole(actor,10,"STAFF",3));
            forbidden(()->service.deleteStaff(actor,10,3));
        }
    }
    @Test void managerCannotManageManager() {
        target("MANAGER"); forbidden(()->service.changeStaffRole(manager,10,"STAFF",3)); forbidden(()->service.deleteStaff(manager,10,3));
    }
    @Test void administratorsCannotManageThemselves() {
        for (var actor : List.of(owner,manager)) {
            forbidden(()->service.changeStaffRole(actor,actor.id(),"STAFF",3));
            forbidden(()->service.deleteStaff(actor,actor.id(),3));
        }
        verify(repo,never()).query(anyString(),any(Object[].class));
    }
    @ParameterizedTest @ValueSource(strings={"CUSTOMER","RIDER","STAFF","CSM"})
    void nonAdministratorsCannotManageEmployees(String role) {
        var actor = new SupportAccess.Actor(10,"Employee",role,false);
        forbidden(()->service.deleteStaff(actor,10,3));
        forbidden(()->service.changeStaffRole(actor,11,"STAFF",3));
    }
    @ParameterizedTest @ValueSource(strings={"OWNER","CUSTOMER","ADMIN","OTHER"})
    void unsupportedDestinationRolesAreRejected(String role) {
        target("STAFF");
        assertEquals(400,assertThrows(ResponseStatusException.class,()->service.changeStaffRole(owner,10,role,3)).getStatusCode().value());
        verify(repo,never()).update(anyString(),any(Object[].class));
    }
    @Test void staleVersionsRejectBothActions() {
        target("STAFF");
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.changeStaffRole(owner,10,"RIDER",2)).getStatusCode().value());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.deleteStaff(owner,10,2)).getStatusCode().value());
        verify(repo,never()).update(anyString(),any(Object[].class));
    }
    @ParameterizedTest @ValueSource(strings={"RIDER","STAFF","CSM","MANAGER"})
    void deletionRetiresCredentialsAndOnlyUpdatesTheUser(String role) {
        target(role);
        service.deleteStaff(role.equals("MANAGER")?owner:manager,10,3);
        var calls = mockingDetails(repo).getInvocations().stream().filter(c->c.getMethod().getName().equals("update")).toList();
        assertEquals(1,calls.size());
        Object[] args=calls.getFirst().getArguments();
        assertEquals("UPDATE users SET active=0,email=?,password=?,updatedAt=SYSDATETIME(),version=version+1 WHERE userID=? AND version=?",args[0]);
        assertTrue(((String)args[1]).matches("deleted_10_[0-9a-f]{32}@deleted\\.invalid"));
        assertFalse(passwords.matches("PreviousPassword",(String)args[2]));
        assertEquals(10,args[3]); assertEquals(3,args[4]);
        verify(repo).audit(isNull(),anyInt(),eq("Employee account deleted"),eq("Account #10"));
    }
    @Test void retiredAccountsCannotBeModifiedAgain() {
        when(repo.query(anyString(),eq(10))).thenReturn(List.of(Map.of("role","STAFF","email","deleted_10_"+"a".repeat(32)+"@deleted.invalid","version",3)));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.changeStaffRole(owner,10,"RIDER",3)).getStatusCode().value());
        verify(repo,never()).update(anyString(),any(Object[].class));
    }
    @Test void missingAccountAndMissingVersionDoNotWrite() {
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.deleteStaff(owner,10,3)).getStatusCode().value());
        assertEquals(400,assertThrows(ResponseStatusException.class,()->service.changeStaffRole(owner,10,"RIDER",null)).getStatusCode().value());
        verify(repo,never()).update(anyString(),any(Object[].class));
    }
    @Test void exhaustedEmailCollisionsDoNotWrite() {
        target("STAFF"); when(repo.count(anyString(),any(Object[].class))).thenReturn(1);
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.deleteStaff(owner,10,3)).getStatusCode().value());
        verify(repo,never()).update(anyString(),any(Object[].class));
    }
    @Test void updateConflictDoesNotRecordSuccess() {
        target("STAFF"); when(repo.update(anyString(),any(Object[].class))).thenReturn(0);
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.changeStaffRole(owner,10,"RIDER",3)).getStatusCode().value());
        verify(repo,never()).audit(any(),anyInt(),anyString(),anyString());
    }
    @Test void listingExcludesRetiredAccountsWithoutHidingOrdinaryInactiveAccounts() {
        service.staff(owner,null);
        verify(repo).query(contains("AND email NOT LIKE 'deleted[_]%@deleted.invalid'"),isNull(),isNull(),isNull());
    }
}
