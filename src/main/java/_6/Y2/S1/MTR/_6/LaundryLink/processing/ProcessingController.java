package _6.Y2.S1.MTR._6.LaundryLink.processing;

import _6.Y2.S1.MTR._6.LaundryLink.processing.dto.IssueRequest;
import _6.Y2.S1.MTR._6.LaundryLink.processing.dto.IssueResponse;
import _6.Y2.S1.MTR._6.LaundryLink.processing.dto.ProcessingOrderDetail;
import _6.Y2.S1.MTR._6.LaundryLink.processing.dto.ProcessingOrderSummary;
import _6.Y2.S1.MTR._6.LaundryLink.processing.dto.QualityCheckRequest;
import _6.Y2.S1.MTR._6.LaundryLink.processing.dto.ReceiveItemsRequest;
import _6.Y2.S1.MTR._6.LaundryLink.processing.dto.ReceiveResult;
import _6.Y2.S1.MTR._6.LaundryLink.processing.dto.StatusChangeRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP endpoints for the staff processing pages under /html/staff.
 * SecurityConfig limits /api/processing/** to STAFF, MANAGER, OWNER and ADMIN logins;
 * every handler also resolves the signed-in staff member through {@link ProcessingService#currentStaff}
 * so their ID can be recorded. Errors are thrown as ApiException and returned as
 * {@code {"message": "..."}} by the global ApiExceptionHandler; invalid request bodies become 400
 * before any business logic runs.
 */
@RestController
@RequestMapping("/api/processing")
public class ProcessingController {

    /** HTTP 422: the request was valid but the counts do not match the order. */
    private static final int UNPROCESSABLE = 422;

    private final ProcessingService processing;
    private final ProcessingIssueService issues;

    public ProcessingController(ProcessingService processing, ProcessingIssueService issues) {
        this.processing = processing;
        this.issues = issues;
    }

    /** Orders on the processing board (statuses 7-11, 19 and 21); status narrows it to one stage. */
    @GetMapping("/orders")
    public List<ProcessingOrderSummary> listOrders(Principal principal, @RequestParam(required = false) Integer status) {
        processing.currentStaff(principal);
        return processing.listOrders(status);
    }

    /** Full detail of one order for the Order Processing page. */
    @GetMapping("/orders/{orderID}")
    public ProcessingOrderDetail getOrder(Principal principal, @PathVariable int orderID) {
        processing.currentStaff(principal);
        return processing.getOrder(orderID);
    }

    /**
     * Records received quantities: 200 when they match and the order moves to
     * Verifying Items; 422 with the mismatches when they don't (nothing is saved).
     */
    @PostMapping("/orders/{orderID}/receive")
    public ResponseEntity<ReceiveResult> receiveItems(Principal principal, @PathVariable int orderID,
                                                      @Valid @RequestBody ReceiveItemsRequest request) {
        StaffMember staff = processing.currentStaff(principal);
        ReceiveResult result = processing.receiveItems(orderID, request, staff);
        return result.accepted() ? ResponseEntity.ok(result) : ResponseEntity.status(UNPROCESSABLE).body(result);
    }

    /** Moves the order to the next cleaning stage; invalid moves return 409. */
    @PutMapping("/orders/{orderID}/status")
    public ProcessingOrderDetail changeStatus(Principal principal, @PathVariable int orderID,
                                              @Valid @RequestBody StatusChangeRequest request) {
        processing.currentStaff(principal);
        return processing.changeStatus(orderID, request.statusID());
    }

    /** Records the quality check at Quality Inspection; a failed check sends the order back for rework. */
    @PostMapping("/orders/{orderID}/quality-check")
    public ResponseEntity<ProcessingOrderDetail> recordQualityCheck(Principal principal, @PathVariable int orderID,
                                                                    @Valid @RequestBody QualityCheckRequest request) {
        StaffMember staff = processing.currentStaff(principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(processing.recordQualityCheck(orderID, request, staff));
    }

    /** Marks a quality-checked order as packed. */
    @PostMapping("/orders/{orderID}/pack")
    public ProcessingOrderDetail pack(Principal principal, @PathVariable int orderID) {
        processing.currentStaff(principal);
        return processing.pack(orderID);
    }

    /** Releases a passed, packed order: Quality Inspection -> Awaiting Delivery. */
    @PostMapping("/orders/{orderID}/ready")
    public ProcessingOrderDetail markReady(Principal principal, @PathVariable int orderID) {
        processing.currentStaff(principal);
        return processing.markReady(orderID);
    }

    /** Issue reports, newest first; orderId limits them to one order. */
    @GetMapping("/issues")
    public List<IssueResponse> listIssues(Principal principal, @RequestParam(required = false) Integer orderId) {
        processing.currentStaff(principal);
        return issues.listIssues(orderId);
    }

    @GetMapping("/issues/permissions")
    public java.util.Map<String, Boolean> issuePermissions(Principal principal) {
        return java.util.Map.of("canReport", "STAFF".equals(processing.currentStaff(principal).role()));
    }

    /** Reports a damaged, stained or missing item; it also opens a CSM complaint case. */
    @PostMapping("/issues")
    public ResponseEntity<IssueResponse> reportIssue(Principal principal, @Valid @RequestBody IssueRequest request) {
        StaffMember staff = processing.currentStaff(principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(issues.reportIssue(request, staff));
    }
}
