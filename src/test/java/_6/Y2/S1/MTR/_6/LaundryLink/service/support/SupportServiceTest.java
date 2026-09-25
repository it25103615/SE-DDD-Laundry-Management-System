package _6.Y2.S1.MTR._6.LaundryLink.service.support;

import _6.Y2.S1.MTR._6.LaundryLink.repository.support.SupportRepository;
import _6.Y2.S1.MTR._6.LaundryLink.security.support.SupportAccess;
import _6.Y2.S1.MTR._6.LaundryLink.service.shared.NotificationService;

import java.util.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import _6.Y2.S1.MTR._6.LaundryLink.security.support.SupportAccess.Actor;
import static _6.Y2.S1.MTR._6.LaundryLink.dto.support.SupportRequests.*;

class SupportServiceTest {
    SupportRepository repo;
    SupportService service;
    final Actor customer=new Actor(1,"Customer","CUSTOMER",false);
    final Actor admin=new Actor(9,"Admin","ADMIN",false);
    @BeforeEach void setup() {
        repo=mock(SupportRepository.class);
        service=new SupportService(repo,new SupportAccess(null),mock(NotificationService.class));
    }
    Map<String,Object> item(String status) {
        return new HashMap<>(Map.of("id",1,"customerId",1,"status",status,"version",0));
    }
    Map<String,Object> assignedItem(String status, int assigneeId) {
        var item=item(status); item.put("assigneeId",assigneeId); return item;
    }
    void found(String status) { when(repo.query(anyString(),eq(1))).thenReturn(List.of(item(status))); }
    @ParameterizedTest
    @CsvSource({"New,Assigned,true","New,Resolved,false","Assigned,In Review,true","In Review,Resolved,true","Resolved,Closed,true","Closed,Reopened,true","Closed,In Review,false","Reopened,In Review,true","New,New,true"})
    void enforcesWorkflow(String before,String after,boolean expected) { assertEquals(expected,SupportService.allowed(before,after)); }
    @Test void otherCustomerCannotReadCase() {
        found("New");
        var e=assertThrows(ResponseStatusException.class,()->service.one(new Actor(2,"Other","CUSTOMER",false),1));
        assertEquals(404,e.getStatusCode().value());
    }
    @Test void managerOnlyReadsCasesAssignedToThatManager() {
        var manager=new Actor(5,"Manager","MANAGER",false);
        when(repo.query(anyString(),eq(1))).thenReturn(List.of(item("New")));
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.one(manager,1)).getStatusCode().value());
        when(repo.query(anyString(),eq(1))).thenReturn(List.of(assignedItem("Assigned",5)));
        assertDoesNotThrow(()->service.one(manager,1));
    }
    @Test void laundryStaffOnlyReadsCasesAssignedToThatStaffMember() {
        var staff=new Actor(6,"Laundry Staff","STAFF",false);
        when(repo.query(anyString(),eq(1))).thenReturn(List.of(assignedItem("Assigned",5)));
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.one(staff,1)).getStatusCode().value());
        when(repo.query(anyString(),eq(1))).thenReturn(List.of(assignedItem("Assigned",6)));
        assertDoesNotThrow(()->service.one(staff,1));
    }
    @Test void feedbackRequiresRating() {
        assertThrows(ResponseStatusException.class,()->service.create(customer,new CaseInput("Feedback","Title","Text",null,null,null)));
        verify(repo,never()).insert(anyString(),any(Object[].class));
    }
    @Test void cannotAttachAnotherCustomersOrder() {
        when(repo.count(anyString(),eq(6),eq(1))).thenReturn(0);
        assertThrows(ResponseStatusException.class,()->service.create(customer,new CaseInput("Complaint","Title","Text",6,null,null)));
    }
    @Test void cannotEditHandledCase() {
        found("In Review");
        assertThrows(ResponseStatusException.class,()->service.edit(customer,1,new CaseInput("Complaint","Title","Text",null,null,0)));
    }
    @Test void cannotDeleteHandledCase() {
        found("Resolved");
        assertThrows(ResponseStatusException.class,()->service.delete(customer,1,0));
    }
    @Test void staleVersionDoesNotWriteAudit() {
        found("New");
        when(repo.update(anyString(),eq(1),eq(0))).thenReturn(0);
        var error=assertThrows(ResponseStatusException.class,()->service.delete(customer,1,0));
        assertEquals(409,error.getStatusCode().value());
        verify(repo,never()).audit(any(),anyInt(),anyString(),anyString());
    }
    @Test void customerCannotResolveCase() {
        assertThrows(ResponseStatusException.class,()->service.handle(customer,1,new CaseUpdate("Resolved","Normal",9,"Fixed",0)));
        verifyNoInteractions(repo);
    }
    @Test void progressionNeedsAssignee() {
        found("New");
        assertThrows(ResponseStatusException.class,()->service.handle(admin,1,new CaseUpdate("In Review","Normal",null,"Review",0)));
    }
    @Test void closedCaseCannotReceiveMessages() {
        found("Closed");
        assertThrows(ResponseStatusException.class,()->service.message(customer,1,new MessageInput("Hello")));
    }
    @Test void validatesBlankOversizedAndOutOfRangeInputs() {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var validator=factory.getValidator();
            assertFalse(validator.validate(new CaseInput("Other"," ","x".repeat(501),-1,6,null)).isEmpty());
            assertFalse(validator.validate(new CaseUpdate("Unknown","Urgent",0,"",-1)).isEmpty());
            assertTrue(validator.validate(new CaseInput("Feedback","Service","Very helpful",1,5,null)).isEmpty());
        }
    }
    @Test void createsCaseAndAuditTogether() {
        found("New");
        when(repo.insert(anyString(),any(Object[].class))).thenReturn(1);
        var result=service.create(customer,new CaseInput("Complaint","  Missing button  ","  Please help  ",null,null,null));
        assertEquals(1,result.get("id"));
        verify(repo).insert(anyString(),eq("Please help"),eq(1),isNull(),eq("Complaint"),eq("Missing button"),isNull());
        verify(repo).audit(1,1,"Created","Complaint submitted");
    }
    @Test void editsNewCaseWithExpectedVersion() {
        found("New");
        when(repo.update(anyString(),any(Object[].class))).thenReturn(1);
        service.edit(customer,1,new CaseInput("Question","Updated","New text",null,null,0));
        verify(repo).update(anyString(),eq("New text"),isNull(),eq("Question"),eq("Updated"),isNull(),eq(1),eq(0));
        verify(repo).audit(1,1,"Edited","Customer updated the case details");
    }
    @Test void staffReviewWritesResolutionHistory() {
        found("New");
        when(repo.count(anyString(),eq(9))).thenReturn(1);
        when(repo.update(anyString(),any(Object[].class))).thenReturn(1);
        service.handle(admin,1,new CaseUpdate("In Review","High",9,"Checking the garment",0));
        verify(repo).audit(eq(1),eq(9),eq("Case updated"),contains("Checking the garment"));
    }
    @Test void assignmentDoesNotRequireANote() {
        found("New");
        when(repo.count(anyString(),eq(9))).thenReturn(1);
        when(repo.update(anyString(),any(Object[].class))).thenReturn(1);
        assertDoesNotThrow(()->service.handle(admin,1,new CaseUpdate("Assigned","Normal",9,"",0)));
        verify(repo).audit(eq(1),eq(9),eq("Case updated"),contains("Assigned to:"));
    }
    @Test void resolvingCaseRequiresExplanation() {
        when(repo.query(anyString(),eq(1))).thenReturn(List.of(assignedItem("In Review",9)));
        var error=assertThrows(ResponseStatusException.class,
                ()->service.handle(admin,1,new CaseUpdate("Resolved","Normal",9,"",0)));
        assertEquals(400,error.getStatusCode().value());
        verify(repo,never()).update(anyString(),any(Object[].class));
    }
    @Test void assignedLaundryStaffCanReply() {
        var staff=new Actor(6,"Laundry Staff","STAFF",false);
        when(repo.query(anyString(),eq(1))).thenReturn(List.of(assignedItem("Assigned",6)));
        when(repo.update(anyString(),any(Object[].class))).thenReturn(1);
        assertDoesNotThrow(()->service.message(staff,1,new MessageInput("I will check the garment.")));
        verify(repo).update(contains("INSERT INTO chat"),eq("I will check the garment."),eq(6),eq(1));
    }
    @Test void replyPersistsToChatAndAudit() {
        found("In Review");
        when(repo.update(anyString(),any(Object[].class))).thenReturn(1);
        service.message(customer,1,new MessageInput("Thank you"));
        verify(repo).update(contains("INSERT INTO chat"),eq("Thank you"),eq(1),eq(1));
        verify(repo).audit(1,1,"Message added","Communication recorded");
    }
    @Test void newCaseDeletionRetainsAudit() {
        found("New");
        when(repo.update(anyString(),eq(1),eq(0))).thenReturn(1);
        service.delete(customer,1,0);
        verify(repo).audit(eq(1),eq(1),eq("Deleted"),contains("history retained"));
    }
}
