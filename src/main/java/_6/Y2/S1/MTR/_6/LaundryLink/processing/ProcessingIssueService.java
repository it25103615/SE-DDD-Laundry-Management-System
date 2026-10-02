package _6.Y2.S1.MTR._6.LaundryLink.processing;

import _6.Y2.S1.MTR._6.LaundryLink.common.ApiException;
import _6.Y2.S1.MTR._6.LaundryLink.processing.dto.IssueRequest;
import _6.Y2.S1.MTR._6.LaundryLink.processing.dto.IssueResponse;
import _6.Y2.S1.MTR._6.LaundryLink.notification.NotificationService;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Staff issue reports: damaged, stained or missing items found during processing.
 *
 * <p>Reporting an issue does three things in one transaction:
 * <ol>
 *   <li>opens a High-priority support case under the order's customer, with the issue type as its
 *       case type and the item as its subject, so it appears in the CSM queue;</li>
 *   <li>adds an entry to the case's activity history naming the staff member who reported it;</li>
 *   <li>notifies customer service managers, owners and admins.</li>
 * </ol>
 */
@Service
public class ProcessingIssueService {

    /** Same roles the support module notifies about new cases. */
    private static final Set<String> CUSTOMER_SERVICE_ROLES = Set.of("CSM", "CUSTOMER_SERVICE_MANAGER", "OWNER", "ADMIN");

    /** notifications.message is NVARCHAR(300). */
    private static final int MAX_NOTIFICATION_LENGTH = 300;

    private final ProcessingIssueRepository repository;
    private final NotificationService notifications;

    public ProcessingIssueService(ProcessingIssueRepository repository, NotificationService notifications) {
        this.repository = repository;
        this.notifications = notifications;
    }

    /** Issue reports, newest first; only one order when orderID is given. */
    @Transactional(readOnly = true)
    public List<IssueResponse> listIssues(Integer orderID) {
        return repository.findIssues(orderID);
    }

    /** Saves an issue report and opens its support case (see the class comment). */
    @Transactional
    public IssueResponse reportIssue(IssueRequest request, StaffMember staff) {
        if (!repository.orderExists(request.orderID())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Order #" + request.orderID() + " was not found.");
        }
        // The subject names the item the issue is about, e.g. "Shirt/Blouse (order #6)".
        String item = "Whole order";
        if (request.orderLineID() != null) {
            item = repository.findLineItemName(request.orderLineID(), request.orderID())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                            "Order line #" + request.orderLineID() + " is not part of order #" + request.orderID() + "."));
        }
        String subject = item + " (order #" + request.orderID() + ")";

        String description = request.description().trim();
        int feedbackID = repository.insertIssueCase(request.orderID(), request.issueType(), subject, description);
        repository.audit(feedbackID, staff.userID(), ProcessingIssueRepository.REPORTED_ACTION,
                staff.name() + " reported " + request.issueType().toLowerCase() + ": " + description);

        String message = request.issueType() + " on order #" + request.orderID() + ": " + description;
        if (message.length() > MAX_NOTIFICATION_LENGTH) {
            message = message.substring(0, MAX_NOTIFICATION_LENGTH - 1) + "…";
        }
        notifications.notifyRoles(CUSTOMER_SERVICE_ROLES, "SUPPORT", "Processing issue reported", message,
                "/html/admin/customer-service-manager/complaints.html?caseId=" + feedbackID,
                "SUPPORT", feedbackID, staff.userID());

        return repository.findIssue(feedbackID)
                .orElseThrow(() -> new IllegalStateException("Case #" + feedbackID + " was saved but could not be read back."));
    }
}
