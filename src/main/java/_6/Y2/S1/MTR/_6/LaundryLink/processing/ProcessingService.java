package _6.Y2.S1.MTR._6.LaundryLink.processing;

import _6.Y2.S1.MTR._6.LaundryLink.common.ApiException;
import _6.Y2.S1.MTR._6.LaundryLink.orders.OrderPreference;
import _6.Y2.S1.MTR._6.LaundryLink.processing.ProcessingRepository.CheckRow;
import _6.Y2.S1.MTR._6.LaundryLink.processing.ProcessingRepository.LineRow;
import _6.Y2.S1.MTR._6.LaundryLink.processing.ProcessingRepository.OrderHeader;
import _6.Y2.S1.MTR._6.LaundryLink.processing.dto.ProcessingOrderDetail;
import _6.Y2.S1.MTR._6.LaundryLink.processing.dto.ProcessingOrderDetail.QualityCheck;
import _6.Y2.S1.MTR._6.LaundryLink.processing.dto.ProcessingOrderDetail.StatusOption;
import _6.Y2.S1.MTR._6.LaundryLink.processing.dto.ProcessingOrderSummary;
import _6.Y2.S1.MTR._6.LaundryLink.processing.dto.QualityCheckRequest;
import _6.Y2.S1.MTR._6.LaundryLink.processing.dto.ReceiveItemsRequest;
import _6.Y2.S1.MTR._6.LaundryLink.processing.dto.ReceiveResult;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static _6.Y2.S1.MTR._6.LaundryLink.processing.ProcessingTransitions.*;

/**
 * The laundry processing workflow: receiving items, moving an order through the cleaning stages,
 * the quality check, packing and marking the order ready for delivery.
 *
 * <p>The rules about which stage may follow which are a Strategy pattern: each order has a
 * {@link ProcessingRoute} (wash, dry-clean, shoe cleaning or ironing), picked from its service by
 * {@link ProcessingTransitions#routeFor}. This class is the context: it checks the order's current
 * state, asks the route what is allowed, saves the data and changes status through
 * {@link ProcessingRepository#updateStatus}, which also writes the status log. Every write
 * method is one transaction, so a failure leaves nothing half-saved.
 */
@Service
public class ProcessingService {

    /** Roles allowed to do processing work (also enforced by URL in SecurityConfig). */
    private static final Set<String> PROCESSING_ROLES = Set.of("STAFF", "MANAGER", "OWNER", "ADMIN");

    private final ProcessingRepository repository;
    private final ProcessingIssueRepository issueRepository;

    public ProcessingService(ProcessingRepository repository, ProcessingIssueRepository issueRepository) {
        this.repository = repository;
        this.issueRepository = issueRepository;
    }

    /**
     * Resolves the signed-in staff member from the login (the principal's name is the email).
     * Throws 401 when nobody is signed in, and 403 for accounts that may not do processing work.
     */
    public StaffMember currentStaff(Principal principal) {
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Sign in with a staff account to use laundry processing.");
        }
        StaffMember staff = repository.findActiveUserByEmail(principal.getName())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Your account was not found or is inactive."));
        if (!PROCESSING_ROLES.contains(staff.role())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only staff, managers and owners can use laundry processing.");
        }
        return staff;
    }

    /** Orders on the processing board, optionally only those at one status. */
    @Transactional(readOnly = true)
    public List<ProcessingOrderSummary> listOrders(Integer statusFilter) {
        if (statusFilter != null && !PROCESSING_STATUSES.contains(statusFilter)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Status " + statusFilter + " is not a processing stage.");
        }
        return repository.findOrdersInProcessing(statusFilter);
    }

    /** Everything the Order Processing page needs for one order. */
    @Transactional(readOnly = true)
    public ProcessingOrderDetail getOrder(int orderID) {
        OrderHeader order = requireOrder(orderID);
        List<LineRow> lines = repository.findLines(orderID);
        ProcessingRoute route = routeOf(lines);
        CheckRow check = repository.findLatestQualityCheck(orderID).orElse(null);
        Map<Integer, String> labels = repository.findStatusLabels();

        // What the page may offer next, worked out by the order's route (the strategy).
        Integer next = route.nextStage(order.statusID());
        StatusOption nextStatus = next == null ? null : new StatusOption(next, labels.get(next));
        // Rework stages matter only during the quality check, which happens at Quality Inspection.
        List<StatusOption> reworkOptions = order.statusID() == QUALITY_INSPECTION
                ? route.reworkTargets().stream().map(id -> new StatusOption(id, labels.get(id))).toList()
                : List.of();

        return new ProcessingOrderDetail(
                order.orderID(),
                order.customerName(),
                order.statusID(),
                order.statusLabel(),
                route.name(),
                order.deliveryInstruction(),
                order.instructions(),
                // Stored as codes on the order; staff are shown the readable labels.
                OrderPreference.labelsOf(order.preferences()),
                lines.stream().map(line -> new ProcessingOrderDetail.Line(
                        line.orderLineID(), line.itemName(), line.serviceName(), line.quantity(),
                        line.receivedQuantity(), line.itemCondition())).toList(),
                repository.findHistory(orderID),
                check == null ? null : new QualityCheck(
                        check.checkID(), check.result(), check.reworkStatusID(), check.reworkStatusLabel(),
                        check.packed(), check.notes(), check.checkedBy(), check.checkedAt()),
                issueRepository.findIssues(orderID),
                nextStatus,
                reworkOptions,
                isReady(order, check));
    }

    /**
     * Records what arrived for an In Shop order (TC-LP01 to LP04).
     *
     * <p>Every order line must be counted exactly once. If every count equals the ordered
     * quantity, the counts are saved and the order moves to Verifying Items. If any count is
     * lower (missing item, LP02) or higher (extra item, LP04), nothing is saved, the status stays
     * In Shop, and the mismatches are returned so staff can recount or report the problem.
     */
    @Transactional
    public ReceiveResult receiveItems(int orderID, ReceiveItemsRequest request, StaffMember staff) {
        OrderHeader order = requireOrder(orderID);
        if (order.statusID() != IN_SHOP) {
            throw new ApiException(HttpStatus.CONFLICT, "Order #" + orderID + " is " + order.statusLabel()
                    + ". Items can only be received while an order is In Shop.");
        }

        // Match every submitted count to one of the order's lines.
        Map<Integer, LineRow> orderLines = new HashMap<>();
        repository.findLines(orderID).forEach(line -> orderLines.put(line.orderLineID(), line));
        Map<Integer, ReceiveItemsRequest.Line> counted = new HashMap<>();
        for (ReceiveItemsRequest.Line line : request.lines()) {
            if (!orderLines.containsKey(line.orderLineID())) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Order line #" + line.orderLineID() + " is not part of order #" + orderID + ".");
            }
            if (counted.put(line.orderLineID(), line) != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Order line #" + line.orderLineID() + " was counted twice.");
            }
        }
        if (counted.size() != orderLines.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter the received quantity for every item on the order.");
        }

        // Compare counts with the order; any difference means nothing is saved.
        List<ReceiveResult.Mismatch> mismatches = orderLines.values().stream()
                .filter(line -> counted.get(line.orderLineID()).receivedQuantity() != line.quantity())
                .sorted((a, b) -> Integer.compare(a.orderLineID(), b.orderLineID()))
                .map(line -> new ReceiveResult.Mismatch(line.orderLineID(), line.itemName(), line.quantity(),
                        counted.get(line.orderLineID()).receivedQuantity()))
                .toList();
        if (!mismatches.isEmpty()) {
            return new ReceiveResult(false, order.statusID(), order.statusLabel(),
                    "Received quantities do not match the order. Recount the items, or report a missing item or count mismatch.",
                    mismatches);
        }

        // Counts match: save them and move the order on (the procedure also logs 7 -> 8).
        for (ReceiveItemsRequest.Line line : counted.values()) {
            String condition = line.condition() == null ? "As expected" : line.condition();
            repository.saveReceivedItem(line.orderLineID(), line.receivedQuantity(), condition, staff.userID());
        }
        repository.updateStatus(orderID, VERIFYING_ITEMS);
        return new ReceiveResult(true, VERIFYING_ITEMS, repository.findStatusLabels().get(VERIFYING_ITEMS),
                "Items received. Order #" + orderID + " is now Verifying Items.", List.of());
    }

    /**
     * Moves an order to the next stage of its route (the last cleaning stage leads to Quality
     * Inspection). Only the single next stage on the order's route is allowed; anything else -
     * skipping ahead, a stage from another route, or a step that needs receiving or a quality
     * check - is rejected with 409 and the procedure is never called, so no log row is written.
     */
    @Transactional
    public ProcessingOrderDetail changeStatus(int orderID, int targetStatusID) {
        OrderHeader order = requireOrder(orderID);
        ProcessingRoute route = routeOf(repository.findLines(orderID));
        if (!route.canAdvance(order.statusID(), targetStatusID)) {
            throw new ApiException(HttpStatus.CONFLICT, invalidTransitionMessage(order, targetStatusID, route));
        }
        repository.updateStatus(orderID, targetStatusID);
        return getOrder(orderID);
    }

    /**
     * Records the quality check, done while the order is at Quality Inspection. A Failed check
     * must name a rework stage on the order's route, and the order moves back there. A Passed
     * check may be packed straight away.
     */
    @Transactional
    public ProcessingOrderDetail recordQualityCheck(int orderID, QualityCheckRequest request, StaffMember staff) {
        OrderHeader order = requireOrder(orderID);
        if (order.statusID() != QUALITY_INSPECTION) {
            throw new ApiException(HttpStatus.CONFLICT, "Order #" + orderID + " is " + order.statusLabel()
                    + ". The quality check happens at Quality Inspection.");
        }
        ProcessingRoute route = routeOf(repository.findLines(orderID));
        boolean failed = "Failed".equals(request.result());
        Integer rework = request.reworkStatusID();
        if (failed && (rework == null || !route.reworkTargets().contains(rework))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a rework stage on this order's route: "
                    + describeStages(route.reworkTargets()) + ".");
        }
        if (!failed && rework != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A passed quality check has no rework stage.");
        }

        boolean packed = !failed && Boolean.TRUE.equals(request.packed());
        String notes = request.notes() == null || request.notes().isBlank() ? null : request.notes().trim();
        repository.insertQualityCheck(orderID, request.result(), rework, packed, notes, staff.userID());
        if (failed) {
            repository.updateStatus(orderID, rework);   // logs e.g. 21 -> 9 as the rework record
        }
        return getOrder(orderID);
    }

    /** Marks the order as packed; allowed only after a passed quality check. */
    @Transactional
    public ProcessingOrderDetail pack(int orderID) {
        OrderHeader order = requireOrder(orderID);
        CheckRow check = repository.findLatestQualityCheck(orderID).orElse(null);
        if (order.statusID() != QUALITY_INSPECTION || check == null || !"Passed".equals(check.result())) {
            throw new ApiException(HttpStatus.CONFLICT, "Pack order #" + orderID + " only after it has passed the quality check.");
        }
        if (!check.packed()) {
            repository.markLatestPassedCheckPacked(orderID);
        }
        return getOrder(orderID);
    }

    /**
     * Releases a passed, packed order for delivery: Quality Inspection -> Awaiting Delivery. The
     * order-notification trigger in the database tells the customer about the new status.
     */
    @Transactional
    public ProcessingOrderDetail markReady(int orderID) {
        OrderHeader order = requireOrder(orderID);
        CheckRow check = repository.findLatestQualityCheck(orderID).orElse(null);
        if (!isReady(order, check)) {
            throw new ApiException(HttpStatus.CONFLICT, "Order #" + orderID
                    + " can be marked ready only after a passed quality check and packing.");
        }
        repository.updateStatus(orderID, AWAITING_DELIVERY);
        return getOrder(orderID);
    }

    // ------------------------------------------------------------------ helpers

    private OrderHeader requireOrder(int orderID) {
        return repository.findOrder(orderID)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order #" + orderID + " was not found."));
    }

    /** An order is ready to release when it is at Quality Inspection and its newest check passed and is packed. */
    private static boolean isReady(OrderHeader order, CheckRow check) {
        return order.statusID() == QUALITY_INSPECTION && check != null && "Passed".equals(check.result()) && check.packed();
    }

    /** Picks the order's route (its strategy) from the service on its lines. */
    private static ProcessingRoute routeOf(List<LineRow> lines) {
        return routeFor(lines.stream().map(LineRow::serviceName).toList());
    }

    /** Explains why a status change was refused and what the correct next step is. */
    private String invalidTransitionMessage(OrderHeader order, int target, ProcessingRoute route) {
        Map<Integer, String> labels = repository.findStatusLabels();
        String targetLabel = labels.getOrDefault(target, "status " + target);
        String prefix = "Order #" + order.orderID() + " cannot move from " + order.statusLabel() + " to " + targetLabel + ". ";
        Integer next = route.nextStage(order.statusID());
        if (next != null) {
            return prefix + "The next stage is " + labels.get(next) + ".";
        }
        return switch (order.statusID()) {
            case IN_SHOP -> prefix + "Receive and count the items first.";
            case QUALITY_INSPECTION -> prefix + "Record the quality check, then pack and mark the order as ready.";
            default -> prefix + "It is not at a processing stage.";
        };
    }

    private String describeStages(List<Integer> statusIDs) {
        Map<Integer, String> labels = repository.findStatusLabels();
        return String.join(", ", statusIDs.stream().map(labels::get).toList());
    }
}
