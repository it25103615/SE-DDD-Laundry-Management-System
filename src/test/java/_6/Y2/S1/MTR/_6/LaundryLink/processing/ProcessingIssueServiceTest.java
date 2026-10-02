package _6.Y2.S1.MTR._6.LaundryLink.processing;

import _6.Y2.S1.MTR._6.LaundryLink.common.ApiException;
import _6.Y2.S1.MTR._6.LaundryLink.processing.dto.IssueRequest;
import _6.Y2.S1.MTR._6.LaundryLink.processing.dto.IssueResponse;
import _6.Y2.S1.MTR._6.LaundryLink.notification.NotificationService;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Unit tests for staff issue reports (TC-LP06) with a mocked repository and notification service. */
class ProcessingIssueServiceTest {

    ProcessingIssueRepository repository;
    NotificationService notifications;
    ProcessingIssueService service;
    final StaffMember staff = new StaffMember(11, "Sam Staff", "STAFF");

    @BeforeEach
    void setup() {
        repository = mock(ProcessingIssueRepository.class);
        notifications = mock(NotificationService.class);
        service = new ProcessingIssueService(repository, notifications);
    }

    @Test
    void reportCreatesSupportCaseAuditAndNotification() {               // TC-LP06
        when(repository.orderExists(6)).thenReturn(true);
        when(repository.findLineItemName(101, 6)).thenReturn(Optional.of("Shirt/Blouse"));
        when(repository.insertIssueCase(6, "Damaged item", "Shirt/Blouse (order #6)", "Torn sleeve")).thenReturn(40);
        when(repository.findIssue(40)).thenReturn(Optional.of(new IssueResponse(40, 6, "Shirt/Blouse (order #6)",
                "Damaged item", "Torn sleeve", "Sam Staff", LocalDateTime.now(), "New")));

        IssueResponse issue = service.reportIssue(new IssueRequest(6, 101, "Damaged item", "  Torn sleeve "), staff);

        assertEquals(40, issue.caseID());
        // The issue type becomes the case type, the item the subject; the description is trimmed.
        verify(repository).insertIssueCase(6, "Damaged item", "Shirt/Blouse (order #6)", "Torn sleeve");
        // The staff member who reported it is recorded in the case history.
        verify(repository).audit(eq(40), eq(11), eq(ProcessingIssueRepository.REPORTED_ACTION), contains("Torn sleeve"));
        verify(notifications).notifyRoles(eq(Set.of("CSM", "CUSTOMER_SERVICE_MANAGER", "OWNER", "ADMIN")),
                eq("SUPPORT"), eq("Processing issue reported"), contains("order #6"),
                eq("/html/admin/customer-service-manager/complaints.html?caseId=40"), eq("SUPPORT"), eq(40), eq(11));
    }

    @Test
    void issueWithoutAnItemIsAboutTheWholeOrder() {
        when(repository.orderExists(6)).thenReturn(true);
        when(repository.insertIssueCase(anyInt(), any(), any(), any())).thenReturn(41);
        when(repository.findIssue(41)).thenReturn(Optional.of(new IssueResponse(41, 6, "Whole order (order #6)",
                "Missing item", "Bag missing", "Sam Staff", LocalDateTime.now(), "New")));

        service.reportIssue(new IssueRequest(6, null, "Missing item", "Bag missing"), staff);

        verify(repository).insertIssueCase(6, "Missing item", "Whole order (order #6)", "Bag missing");
    }

    @Test
    void unknownOrderIsRejectedBeforeAnythingIsSaved() {
        when(repository.orderExists(99)).thenReturn(false);

        assertThrows(ApiException.class, () -> service.reportIssue(new IssueRequest(99, null, "Missing item", "Bag missing"), staff));

        verify(repository, never()).insertIssueCase(anyInt(), any(), any(), any());
        verifyNoInteractions(notifications);
    }

    @Test
    void lineMustBelongToTheOrder() {
        when(repository.orderExists(6)).thenReturn(true);
        when(repository.findLineItemName(500, 6)).thenReturn(Optional.empty());

        assertThrows(ApiException.class, () -> service.reportIssue(new IssueRequest(6, 500, "Damaged item", "Torn"), staff));
        verify(repository, never()).insertIssueCase(anyInt(), any(), any(), any());
    }
}
