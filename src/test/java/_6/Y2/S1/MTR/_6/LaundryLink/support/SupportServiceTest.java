package _6.Y2.S1.MTR._6.LaundryLink.support;

import static _6.Y2.S1.MTR._6.LaundryLink.support.dto.SupportRequests.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import _6.Y2.S1.MTR._6.LaundryLink.notification.NotificationService;
import _6.Y2.S1.MTR._6.LaundryLink.support.SupportAccess.Actor;
import jakarta.validation.Validation;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.server.ResponseStatusException;

class SupportServiceTest {
  SupportRepository repo;
  SupportService service;
  final Actor customer = new Actor(1, "Customer", "CUSTOMER", false);
  final Actor admin = new Actor(9, "Customer Service Manager", "CSM", false);

  @BeforeEach
  void setup() {
    repo = mock(SupportRepository.class);
    service = new SupportService(repo, new SupportAccess(null), mock(NotificationService.class));
  }

  Map<String, Object> item(String status) {
    return new HashMap<>(Map.of("id", 1, "customerId", 1, "status", status, "version", 0));
  }

  Map<String, Object> assignedItem(String status, int assigneeId) {
    var item = item(status);
    item.put("assigneeId", assigneeId);
    return item;
  }

  void found(String status) {
    when(repo.query(anyString(), eq(1))).thenReturn(List.of(item(status)));
  }

  @ParameterizedTest
  @CsvSource({
    "New,Assigned,true",
    "New,Resolved,false",
    "Assigned,In Review,true",
    "In Review,Resolved,true",
    "Resolved,Closed,true",
    "Closed,Reopened,true",
    "Closed,In Review,false",
    "Reopened,In Review,true",
    "New,New,true"
  })
  void enforcesWorkflow(String before, String after, boolean expected) {
    assertEquals(expected, SupportService.allowed(before, after));
  }

  @Test
  void otherCustomerCannotReadCase() {
    found("New");
    var e =
        assertThrows(
            ResponseStatusException.class,
            () -> service.one(new Actor(2, "Other", "CUSTOMER", false), 1));
    assertEquals(404, e.getStatusCode().value());
  }

  @Test
  void managerOnlyReadsCasesAssignedToThatManager() {
    var manager = new Actor(5, "Manager", "MANAGER", false);
    when(repo.query(anyString(), eq(1))).thenReturn(List.of(item("New")));
    assertEquals(
        404,
        assertThrows(ResponseStatusException.class, () -> service.one(manager, 1))
            .getStatusCode()
            .value());
    when(repo.query(anyString(), eq(1))).thenReturn(List.of(assignedItem("Assigned", 5)));
    assertDoesNotThrow(() -> service.one(manager, 1));
  }

  @Test
  void laundryStaffOnlyReadsCasesAssignedToThatStaffMember() {
    var staff = new Actor(6, "Laundry Staff", "STAFF", false);
    when(repo.query(anyString(), eq(1))).thenReturn(List.of(assignedItem("Assigned", 5)));
    assertEquals(
        404,
        assertThrows(ResponseStatusException.class, () -> service.one(staff, 1))
            .getStatusCode()
            .value());
    when(repo.query(anyString(), eq(1))).thenReturn(List.of(assignedItem("Assigned", 6)));
    assertDoesNotThrow(() -> service.one(staff, 1));
  }

  @Test
  void feedbackRequiresRating() {
    assertThrows(
        ResponseStatusException.class,
        () ->
            service.create(customer, new CaseInput("Feedback", "Title", "Text", null, null, null)));
    verify(repo, never()).insert(anyString(), any(Object[].class));
  }

  @Test
  void cannotAttachAnotherCustomersOrder() {
    when(repo.count(anyString(), eq(6), eq(1))).thenReturn(0);
    assertThrows(
        ResponseStatusException.class,
        () -> service.create(customer, new CaseInput("Complaint", "Title", "Text", 6, null, null)));
  }

  @Test
  void cannotEditHandledCase() {
    found("In Review");
    assertThrows(
        ResponseStatusException.class,
        () ->
            service.edit(customer, 1, new CaseInput("Complaint", "Title", "Text", null, null, 0)));
  }

  @Test
  void cannotDeleteHandledCase() {
    found("Resolved");
    assertThrows(ResponseStatusException.class, () -> service.delete(customer, 1, 0));
  }

  @Test
  void staleVersionDoesNotWriteAudit() {
    found("New");
    when(repo.update(anyString(), eq(1), eq(0))).thenReturn(0);
    var error = assertThrows(ResponseStatusException.class, () -> service.delete(customer, 1, 0));
    assertEquals(409, error.getStatusCode().value());
    verify(repo, never()).audit(any(), anyInt(), anyString(), anyString());
  }

  @Test
  void customerCannotResolveCase() {
    assertThrows(
        ResponseStatusException.class,
        () -> service.handle(customer, 1, new CaseUpdate("Resolved", "Normal", 9, "Fixed", 0)));
    verifyNoInteractions(repo);
  }

  @Test
  void progressionNeedsAssignee() {
    found("New");
    assertThrows(
        ResponseStatusException.class,
        () -> service.handle(admin, 1, new CaseUpdate("In Review", "Normal", null, "Review", 0)));
  }

  @Test
  void closedCaseCannotReceiveMessages() {
    found("Closed");
    assertThrows(
        ResponseStatusException.class,
        () -> service.message(customer, 1, new MessageInput("Hello")));
  }

  @Test
  void validatesBlankOversizedAndOutOfRangeInputs() {
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var validator = factory.getValidator();
      assertFalse(
          validator.validate(new CaseInput("Other", " ", "x".repeat(501), -1, 6, null)).isEmpty());
      assertFalse(validator.validate(new CaseUpdate("Unknown", "Urgent", 0, "", -1)).isEmpty());
      assertTrue(
          validator
              .validate(new CaseInput("Feedback", "Service", "Very helpful", 1, 5, null))
              .isEmpty());
    }
  }

  @Test
  void requiresOrderEvenWhenServiceIsCalledDirectly() {
    for (String type : List.of("Complaint", "Question", "Feedback")) {
      var error = assertThrows(ResponseStatusException.class,
          () -> service.create(customer, new CaseInput(type, "Help", "Please help", null,
              "Feedback".equals(type) ? 5 : null, null)));
      assertEquals(400, error.getStatusCode().value());
      assertEquals("Select a related order.", error.getReason());
    }
    verifyNoInteractions(repo);
  }

  @Test
  void relatedOrderRejectsAnotherCustomerAndUnassignedStaff() {
    var linked = assignedItem("Assigned", 5);
    linked.put("orderId", 6);
    when(repo.query(anyString(), eq(1))).thenReturn(List.of(linked));
    for (Actor outsider : List.of(new Actor(2, "Other", "CUSTOMER", false),
        new Actor(8, "Other staff", "STAFF", false))) {
      assertEquals(404, assertThrows(ResponseStatusException.class,
          () -> service.relatedOrder(outsider, 1)).getStatusCode().value());
    }
    verify(repo, never()).query(anyString(), eq(6), eq(1));
    verify(repo, never()).query(anyString(), eq(6));
  }

  @Test
  void oldCaseWithoutOrderRemainsReadable() {
    found("New");
    assertDoesNotThrow(() -> service.one(customer, 1));
    assertEquals(404, assertThrows(ResponseStatusException.class,
        () -> service.relatedOrder(customer, 1)).getStatusCode().value());
  }

  @Test
  void createsCaseAndAuditTogether() {
    found("New");
    when(repo.count(anyString(), eq(6), eq(1))).thenReturn(1);
    when(repo.insert(anyString(), any(Object[].class))).thenReturn(1);
    var result =
        service.create(
            customer,
            new CaseInput("Complaint", "  Missing button  ", "  Please help  ", 6, null, null));
    assertEquals(1, result.get("id"));
    verify(repo)
        .insert(
            anyString(),
            eq("Please help"),
            eq(1),
            eq(6),
            eq("Complaint"),
            eq("Missing button"),
            isNull(),
            eq("General"));
    verify(repo).audit(1, 1, "Created", "Complaint submitted");
  }

  @Test
  void editsNewCaseWithExpectedVersion() {
    found("New");
    when(repo.count(anyString(), eq(6), eq(1))).thenReturn(1);
    when(repo.update(anyString(), any(Object[].class))).thenReturn(1);
    service.edit(customer, 1, new CaseInput("Question", "Updated", "New text", 6, null, 0));
    verify(repo)
        .update(
            anyString(),
            eq("New text"),
            eq(6),
            eq("Question"),
            eq("Updated"),
            isNull(),
            eq("General"),
            eq(1),
            eq(0));
    verify(repo).audit(1, 1, "Edited", "Customer updated the case details");
  }

  @Test
  void staffReviewWritesResolutionHistory() {
    found("New");
    when(repo.count(anyString(), eq(9))).thenReturn(1);
    when(repo.update(anyString(), any(Object[].class))).thenReturn(1);
    service.handle(admin, 1, new CaseUpdate("In Review", "High", 9, "Checking the garment", 0));
    verify(repo).audit(eq(1), eq(9), eq("Case updated"), contains("Checking the garment"));
  }

  @Test
  void assignmentDoesNotRequireANote() {
    found("New");
    when(repo.count(anyString(), eq(9))).thenReturn(1);
    when(repo.update(anyString(), any(Object[].class))).thenReturn(1);
    assertDoesNotThrow(
        () -> service.handle(admin, 1, new CaseUpdate("Assigned", "Normal", 9, "", 0)));
    verify(repo).audit(eq(1), eq(9), eq("Case updated"), contains("Assigned to:"));
  }

  @Test
  void resolvingCaseRequiresExplanation() {
    when(repo.query(anyString(), eq(1))).thenReturn(List.of(assignedItem("In Review", 9)));
    var error =
        assertThrows(
            ResponseStatusException.class,
            () -> service.handle(admin, 1, new CaseUpdate("Resolved", "Normal", 9, "", 0)));
    assertEquals(400, error.getStatusCode().value());
    verify(repo, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void assignedLaundryStaffCanReply() {
    var staff = new Actor(6, "Laundry Staff", "STAFF", false);
    when(repo.query(anyString(), eq(1))).thenReturn(List.of(assignedItem("Assigned", 6)));
    when(repo.update(anyString(), any(Object[].class))).thenReturn(1);
    assertDoesNotThrow(
        () -> service.message(staff, 1, new MessageInput("I will check the garment.")));
    verify(repo)
        .update(contains("INSERT INTO chat"), eq("I will check the garment."), eq(6), eq(1));
  }

  @Test
  void csmFeedbackReplyDoesNotResolveOrAssignIt() {
    var feedback = assignedItem("In Review", 9);
    feedback.put("type", "Feedback");
    when(repo.query(anyString(), eq(1))).thenReturn(List.of(feedback));
    when(repo.update(anyString(), any(Object[].class))).thenReturn(1);
    service.message(admin, 1, new MessageInput("Thank you for your feedback."));
    verify(repo, never()).update(contains("caseStatus='Resolved'"), eq(1));
    verify(repo, never()).audit(eq(1), eq(9), eq("Case updated"), anyString());
  }

  @Test
  void feedbackCannotContinueAsChat() {
    var feedback = item("New");
    feedback.put("type", "Feedback");
    when(repo.query(anyString(), eq(1))).thenReturn(List.of(feedback));
    assertThrows(
        ResponseStatusException.class,
        () -> service.message(customer, 1, new MessageInput("Another message")));
    when(repo.count(anyString(), eq(1))).thenReturn(1);
    assertThrows(
        ResponseStatusException.class,
        () -> service.message(admin, 1, new MessageInput("Second reply")));
    verify(repo, never()).update(anyString(), any(Object[].class));
  }

  @ParameterizedTest
  @CsvSource({"true,Closed", "false,Reopened"})
  void customerConfirmsResponse(boolean satisfied, String expectedStatus) {
    var request = item("Resolved");
    request.put("type", "Complaint");
    when(repo.query(anyString(), eq(1)))
        .thenAnswer(
            call ->
                call.getArgument(0, String.class).contains("TOP 1 UPPER")
                    ? List.of(Map.of("role", "STAFF"))
                    : List.of(request));
    when(repo.update(anyString(), any(Object[].class))).thenReturn(1);
    service.satisfaction(customer, 1, new SatisfactionInput(satisfied, 0));
    verify(repo).update(contains("SET caseStatus=?"), eq(expectedStatus), eq(1), eq(0));
    verify(repo).update(contains("INSERT INTO chat"), anyString(), eq(1), eq(1));
  }

  @Test
  void confirmationRequiresStaffReplyAndRejectsFeedback() {
    var request = item("In Review");
    request.put("type", "Question");
    when(repo.query(anyString(), eq(1)))
        .thenAnswer(
            call ->
                call.getArgument(0, String.class).contains("TOP 1 UPPER")
                    ? List.of(Map.of("role", "CUSTOMER"))
                    : List.of(request));
    assertThrows(
        ResponseStatusException.class,
        () -> service.satisfaction(customer, 1, new SatisfactionInput(true, 0)));
    request.put("type", "Feedback");
    assertThrows(
        ResponseStatusException.class,
        () -> service.satisfaction(customer, 1, new SatisfactionInput(true, 0)));
    verify(repo, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void replyPersistsToChatAndAudit() {
    found("In Review");
    when(repo.update(anyString(), any(Object[].class))).thenReturn(1);
    service.message(customer, 1, new MessageInput("Thank you"));
    verify(repo).update(contains("INSERT INTO chat"), eq("Thank you"), eq(1), eq(1));
    verify(repo).audit(1, 1, "Message added", "Communication recorded");
  }

  @Test
  void customerCannotEditOrDeleteProcessingIssueRaisedByStaff() {
    var item = item("New");
    item.put("type", "Damaged item");
    when(repo.query(anyString(), eq(1))).thenReturn(List.of(item));
    assertEquals(
        403,
        assertThrows(
                ResponseStatusException.class,
                () ->
                    service.edit(
                        customer, 1, new CaseInput("Complaint", "Title", "Text", null, null, 0)))
            .getStatusCode()
            .value());
    assertEquals(
        403,
        assertThrows(ResponseStatusException.class, () -> service.delete(customer, 1, 0))
            .getStatusCode()
            .value());
    verify(repo, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void newCaseDeletionRetainsAudit() {
    found("New");
    when(repo.update(anyString(), eq(1), eq(0))).thenReturn(1);
    service.delete(customer, 1, 0);
    verify(repo).audit(eq(1), eq(1), eq("Deleted"), contains("history retained"));
  }
}
