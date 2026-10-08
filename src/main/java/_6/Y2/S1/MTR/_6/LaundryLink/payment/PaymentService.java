package _6.Y2.S1.MTR._6.LaundryLink.payment;

import _6.Y2.S1.MTR._6.LaundryLink.billing.BillingDetails;
import _6.Y2.S1.MTR._6.LaundryLink.billing.BillingService;
import _6.Y2.S1.MTR._6.LaundryLink.notification.NotificationService;
import _6.Y2.S1.MTR._6.LaundryLink.status.Status;
import _6.Y2.S1.MTR._6.LaundryLink.status.StatusService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class PaymentService {
    private static final String PAYMENT_VERIFIED = "Payment Verified";
    private static final String PAYMENT_FAILED = "Payment Failed";
    private static final String AWAITING_PICKUP = "Awaiting Pickup";
    // A cancelled order is finished: it cannot take a new payment, and verifying an old payment
    // must never move it to another status.
    private static final String ORDER_CANCELLED = "Cancelled";
    private static final DateTimeFormatter RECEIPT_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final PaymentManagementRepository paymentManagementRepository;
    private final BillingService billingService;
    private final PaymentAccessService paymentAccessService;
    private final StatusService statusService;
    private final NotificationService notificationService;

    public PaymentService(
            PaymentRepository paymentRepository,
            RefundRepository refundRepository,
            PaymentManagementRepository paymentManagementRepository,
            BillingService billingService,
            PaymentAccessService paymentAccessService,
            StatusService statusService,
            NotificationService notificationService
    ) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.paymentManagementRepository = paymentManagementRepository;
        this.billingService = billingService;
        this.paymentAccessService = paymentAccessService;
        this.statusService = statusService;
        this.notificationService = notificationService;
    }

    public PaymentAmountResponse getAmountDue(Integer orderID, Integer customerID) {
        PaymentStatusResponse status = getPaymentStatus(orderID, customerID);
        return new PaymentAmountResponse(
                orderID,
                status.getPayableAmount(),
                status.getPaidAmount(),
                status.getOutstandingAmount()
        );
    }

    public PaymentResponse createPayment(Integer managementUserID, PaymentCrudRequest request) {
        paymentAccessService.requireManagementUser(managementUserID);
        validatePaymentRequest(request);
        requirePayableOrder(request.getOrderID());

        Payment payment = new Payment(request.getAmount().doubleValue(), request.getOrderID());
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.ensureRecordedPaymentFields();
        Payment savedPayment = paymentRepository.save(payment);
        return toPaymentResponse(savedPayment);
    }

    public PaymentResponse getPayment(Integer requesterID, Integer paymentID) {
        Payment payment = paymentRepository.findById(paymentID).orElseThrow();
        verifyCanViewPaymentRecord(requesterID, payment);
        return toPaymentResponse(payment);
    }

    public PaymentReceiptResponse getCustomerReceipt(Integer customerID, Integer paymentID, Integer requestedOrderID) {
        Payment payment = paymentRepository.findById(paymentID).orElseThrow();
        if (requestedOrderID != null && !requestedOrderID.equals(payment.getOrderID())) {
            throw new IllegalArgumentException("Payment does not belong to the requested order");
        }

        BillingDetails billingDetails = billingService.getBillingDetails(payment.getOrderID());
        verifyCanViewOrderPaymentRecords(customerID, billingDetails);
        PaymentStatusResponse status = buildPaymentStatus(payment.getOrderID(), billingDetails);
        Optional<Refund> refund = refundRepository.findByPaymentID(payment.getPaymentID());

        return new PaymentReceiptResponse(
                payment.getPaymentID(),
                payment.getOrderID(),
                billingDetails.getSubtotal(),
                billingDetails.getAutomaticBulkDiscount(),
                billingDetails.getPromotionDiscount(),
                billingDetails.getDiscountAmount(),
                billingDetails.getTotalDiscount(),
                billingDetails.getFinalPayableAmount(),
                BigDecimal.valueOf(payment.getAmount()),
                payment.getPaymentStatus(),
                payment.getPaymentMethod(),
                payment.getTransactionReference(),
                payment.getProcessedAt(),
                status.getOrderStatus(),
                refund.map(Refund::getRefundAmount).orElse(null),
                refund.map(Refund::getRefundStatus).orElse(null),
                refund.map(Refund::getRequestedAt).orElse(null),
                refund.map(Refund::getProcessedAt).orElse(null),
                refund.map(Refund::getRefundedAt).orElse(null)
        );
    }

    public List<PaymentResponse> getPayments(Integer managementUserID) {
        paymentAccessService.requireManagementUser(managementUserID);
        return paymentRepository.findAll().stream()
                .map(this::toPaymentResponse)
                .toList();
    }

    public List<PaymentResponse> getPaymentsByOrder(Integer requesterID, Integer orderID) {
        verifyOrderExists(orderID);
        if (hasManagementAccess(requesterID)) {
            return paymentRepository.findByOrderID(orderID).stream()
                    .map(this::toPaymentResponse)
                    .toList();
        }

        verifyCanViewOrderPaymentRecords(requesterID, billingService.getBillingDetails(orderID));
        return paymentRepository.findByOrderID(orderID).stream()
                .map(this::toPaymentResponse)
                .toList();
    }

    public PaymentResponse updatePayment(Integer managementUserID, Integer paymentID, PaymentCrudRequest request) {
        paymentAccessService.requireManagementUser(managementUserID);
        validatePaymentRequest(request);
        requirePayableOrder(request.getOrderID());

        Payment payment = paymentRepository.findById(paymentID).orElseThrow();
        payment.setAmount(request.getAmount().doubleValue());
        payment.setOrderID(request.getOrderID());
        if (request.getPaymentMethod() != null) {
            payment.setPaymentMethod(request.getPaymentMethod());
        }
        payment.ensureRecordedPaymentFields();
        return toPaymentResponse(paymentRepository.save(payment));
    }

    public void deletePayment(Integer managementUserID, Integer paymentID) {
        paymentAccessService.requireManagementUser(managementUserID);
        Payment payment = paymentRepository.findById(paymentID).orElseThrow();
        paymentRepository.delete(payment);
    }

    @Transactional
    public PaymentConfirmationResponse submitPayment(Integer orderID, Integer customerID, PaymentRequest request) {
        PaymentStatusResponse currentStatus = getPaymentStatus(orderID, customerID);

        // Checked first: a cancelled order with no payment would otherwise count as UNPAID and
        // accept a payment like any open order. The controller turns this into a 409 response.
        if (isCancelled(currentStatus.getOrderStatus())) {
            throw new IllegalStateException("This order has been cancelled and can no longer be paid");
        }

        if (currentStatus.getStatus() == PaymentStatus.PAID || currentStatus.getStatus() == PaymentStatus.VERIFIED) {
            throw new IllegalStateException("This order is already paid");
        }

        if (currentStatus.getStatus() == PaymentStatus.REFUNDED) {
            throw new IllegalStateException("This order payment has already been refunded");
        }

        if (currentStatus.getStatus() == PaymentStatus.PENDING) {
            throw new IllegalStateException("Payment submitted - awaiting verification");
        }

        if (request.getAmount().compareTo(currentStatus.getPayableAmount()) != 0) {
            throw new IllegalArgumentException("Payment amount must match the final payable amount");
        }

        Payment payment = new Payment(
                request.getAmount().doubleValue(),
                orderID,
                request.getPaymentMethod(),
                null
        );
        payment.ensureRecordedPaymentFields();
        Payment savedPayment = paymentRepository.save(payment);

        notificationService.notifyRoles(
                Set.of("MANAGER"), "PAYMENT", "Payment approval needed",
                "Payment for order #" + orderID + " is awaiting your review.",
                "/html/admin/owner/payment_detail.html?paymentID=" + savedPayment.getPaymentID(),
                "PAYMENT", savedPayment.getPaymentID(), null
        );

        return new PaymentConfirmationResponse(
                savedPayment.getPaymentID(),
                orderID,
                BigDecimal.valueOf(savedPayment.getAmount()),
                savedPayment.getPaymentMethod(),
                savedPayment.getTransactionReference(),
                savedPayment.getPaymentStatus(),
                savedPayment.getProcessedAt(),
                "Payment submitted and awaiting verification"
        );
    }

    public byte[] getCustomerReceiptPdf(Integer customerID, Integer paymentID, Integer requestedOrderID) {
        PaymentReceiptResponse receipt = getCustomerReceipt(customerID, paymentID, requestedOrderID);
        return buildReceiptPdf(receipt);
    }

    public PaymentStatusResponse getPaymentStatus(Integer orderID, Integer customerID) {
        BillingDetails billingDetails = billingService.getBillingDetails(orderID);
        paymentAccessService.verifyOrderBelongsToCustomer(billingDetails, customerID);

        return buildPaymentStatus(orderID, billingDetails);
    }

    public PaymentStatusResponse getPaymentStatusForStaff(Integer orderID, Integer staffOrManagerID) {
        paymentAccessService.requireStaffOrManagementUser(staffOrManagerID);
        BillingDetails billingDetails = billingService.getBillingDetails(orderID);
        return buildPaymentStatus(orderID, billingDetails);
    }

    public List<PaymentHistoryResponse> getPaymentHistory(Integer customerID) {
        return paymentRepository.findAllByOrderByProcessedAtDescPaymentIDDesc().stream()
                .filter(payment -> {
                    BillingDetails billingDetails = billingService.getBillingDetails(payment.getOrderID());
                    return billingDetails.getUserID().equals(customerID);
                })
                .map(payment -> {
                    BillingDetails billingDetails = billingService.getBillingDetails(payment.getOrderID());
                    PaymentStatusResponse status = buildPaymentStatus(payment.getOrderID(), billingDetails);
                    Optional<Refund> refund = refundRepository.findByPaymentID(payment.getPaymentID());
                    return new PaymentHistoryResponse(
                            payment.getPaymentID(),
                            payment.getOrderID(),
                            BigDecimal.valueOf(payment.getAmount()),
                            payment.getPaymentStatus(),
                            payment.getPaymentMethod(),
                            payment.getTransactionReference(),
                            payment.getProcessedAt(),
                            status.getOrderStatus(),
                            refund.map(Refund::getRefundAmount).orElse(null),
                            refund.map(Refund::getRefundStatus).orElse(null),
                            refund.map(Refund::getRequestedAt).orElse(null),
                            refund.map(Refund::getProcessedAt).orElse(null),
                            refund.map(Refund::getRefundedAt).orElse(null)
                    );
                })
                .toList();
    }

    public List<PaymentRecordResponse> getPaymentRecords(
            Integer managementUserID,
            Integer orderID,
            Integer customerID,
            PaymentStatus paymentStatus,
            PaymentMethod paymentMethod,
            String search
    ) {
        paymentAccessService.requireManagementUser(managementUserID);

        String normalizedSearch = search == null ? null : search.trim().toLowerCase();
        List<PaymentRecordResponse> paymentRecords = paymentRepository.findAll().stream()
                .map(this::toPaymentRecord)
                .toList();
        Set<Integer> ordersWithPayments = paymentRecords.stream()
                .map(PaymentRecordResponse::getOrderID)
                .collect(HashSet::new, Set::add, Set::addAll);
        List<PaymentRecordResponse> unpaidOrderRecords = paymentManagementRepository.findBillableOrderSummaries().stream()
                .filter(order -> !ordersWithPayments.contains(order.getOrderID()))
                .map(this::toUnpaidOrderRecord)
                .toList();

        return java.util.stream.Stream.concat(paymentRecords.stream(), unpaidOrderRecords.stream())
                .filter(record -> orderID == null || Objects.equals(record.getOrderID(), orderID))
                .filter(record -> customerID == null || Objects.equals(record.getCustomerID(), customerID))
                .filter(record -> paymentStatus == null || record.getPaymentStatus() == paymentStatus)
                .filter(record -> paymentMethod == null || record.getPaymentMethod() == paymentMethod)
                .filter(record -> normalizedSearch == null || normalizedSearch.isBlank() || matchesPaymentSearch(record, normalizedSearch))
                .sorted(managementRecordComparator())
                .toList();
    }

    public PaymentRecordResponse getPaymentRecord(Integer managementUserID, Integer paymentID) {
        paymentAccessService.requireManagementUser(managementUserID);
        Payment payment = paymentRepository.findById(paymentID).orElseThrow();
        return toPaymentRecord(payment);
    }

    @Transactional
    public PaymentVerificationResponse verifyPayment(Integer managementUserID, Integer paymentID, PaymentVerificationRequest request) {
        return verifyPaymentDecision(managementUserID, paymentID, Boolean.TRUE.equals(request.getApproved()));
    }

    @Transactional
    public PaymentVerificationResponse approvePayment(Integer managementUserID, Integer paymentID) {
        return verifyPaymentDecision(managementUserID, paymentID, true);
    }

    @Transactional
    public PaymentVerificationResponse rejectPayment(Integer managementUserID, Integer paymentID) {
        return verifyPaymentDecision(managementUserID, paymentID, false);
    }

    @Transactional
    public RefundResponse requestRefund(Integer customerID, Integer paymentID, RefundRequest request) {
        paymentAccessService.requireCustomer(customerID);
        Payment payment = paymentRepository.findById(paymentID).orElseThrow();
        BillingDetails billingDetails = billingService.getBillingDetails(payment.getOrderID());
        paymentAccessService.verifyOrderBelongsToCustomer(billingDetails, customerID);
        String reason = validateRefundRequest(request);

        if (payment.getPaymentStatus() != PaymentStatus.PAID && payment.getPaymentStatus() != PaymentStatus.VERIFIED) {
            throw new IllegalStateException("Only verified payments can have a refund requested");
        }

        if (refundRepository.existsByPaymentID(paymentID)) {
            throw new IllegalStateException("A refund workflow already exists for this payment");
        }

        Refund refund = new Refund(
                paymentID,
                BigDecimal.valueOf(payment.getAmount()),
                reason,
                customerID
        );
        Refund savedRefund = refundRepository.save(refund);
        return toRefundResponse(savedRefund, payment, "Refund request submitted");
    }

    @Transactional
    public RefundResponse approveRefund(Integer managementUserID, Integer paymentID) {
        paymentAccessService.requireManagementUser(managementUserID);
        Payment payment = paymentRepository.findById(paymentID).orElseThrow();
        Refund refund = refundRepository.findByPaymentID(paymentID).orElseThrow();

        if (payment.getPaymentStatus() != PaymentStatus.PAID && payment.getPaymentStatus() != PaymentStatus.VERIFIED) {
            throw new IllegalStateException("Only verified payments can be refunded");
        }

        if (refund.getRefundStatus() != RefundStatus.REQUESTED) {
            throw new IllegalStateException("Only requested refunds can be approved");
        }

        refund.setRefundAmount(BigDecimal.valueOf(payment.getAmount()));
        refund.setRefundStatus(RefundStatus.REFUNDED);
        refund.setProcessedAt(LocalDateTime.now());
        refund.setRefundedAt(refund.getProcessedAt());
        refund.setProcessedBy(managementUserID);
        Refund savedRefund = refundRepository.save(refund);

        payment.setPaymentStatus(PaymentStatus.REFUNDED);
        paymentRepository.save(payment);
        notifyRefundApproved(payment, savedRefund);

        return toRefundResponse(savedRefund, payment, "Refund approved");
    }

    @Transactional
    public RefundResponse rejectRefund(Integer managementUserID, Integer paymentID) {
        paymentAccessService.requireManagementUser(managementUserID);
        Payment payment = paymentRepository.findById(paymentID).orElseThrow();
        Refund refund = refundRepository.findByPaymentID(paymentID).orElseThrow();

        if (refund.getRefundStatus() != RefundStatus.REQUESTED) {
            throw new IllegalStateException("Only requested refunds can be rejected");
        }

        if (payment.getPaymentStatus() != PaymentStatus.PAID && payment.getPaymentStatus() != PaymentStatus.VERIFIED) {
            throw new IllegalStateException("Only verified payments can have refund requests rejected");
        }

        refund.setRefundStatus(RefundStatus.REJECTED);
        refund.setProcessedAt(LocalDateTime.now());
        refund.setProcessedBy(managementUserID);
        Refund savedRefund = refundRepository.save(refund);
        notifyRefundRejected(payment, savedRefund);
        return toRefundResponse(savedRefund, payment, "Refund request rejected");
    }

    private PaymentVerificationResponse verifyPaymentDecision(Integer managementUserID, Integer paymentID, boolean approved) {
        paymentAccessService.requireManagementUser(managementUserID);

        Payment payment = paymentRepository.findById(paymentID).orElseThrow();
        PaymentStatusResponse currentStatus = buildPaymentStatus(
                payment.getOrderID(),
                billingService.getBillingDetails(payment.getOrderID())
        );

        if (currentStatus.getStatus() != PaymentStatus.PENDING) {
            throw new IllegalStateException("Only pending full payments can be verified");
        }

        PaymentOrderStatus previousOrderStatus = paymentManagementRepository.findOrderStatus(payment.getOrderID()).orElseThrow();
        boolean orderCancelled = isCancelled(previousOrderStatus.getStatusLabel());

        // A payment left pending on a cancelled order cannot be approved: approving would move
        // the order to "Awaiting Pickup" and send a rider to an order the customer cancelled.
        if (orderCancelled && approved) {
            throw new IllegalStateException("This order has been cancelled, so its payment cannot be approved");
        }

        LocalDate verificationDate = LocalDate.now();
        LocalTime verificationTime = LocalTime.now();

        payment.setPaymentStatus(approved ? PaymentStatus.PAID : PaymentStatus.REJECTED);
        payment.ensureRecordedPaymentFields();
        paymentRepository.save(payment);

        // Rejecting is still allowed on a cancelled order, so the leftover payment can be closed,
        // but only the payment changes. The order keeps its "Cancelled" status instead of being
        // moved to "Payment Failed".
        if (orderCancelled) {
            return new PaymentVerificationResponse(
                    paymentID,
                    payment.getOrderID(),
                    previousOrderStatus.getStatusLabel(),
                    previousOrderStatus.getStatusLabel(),
                    managementUserID,
                    verificationDate,
                    verificationTime,
                    "Payment rejected"
            );
        }

        Status updatedStatus = statusService.getByLabel(approved ? PAYMENT_VERIFIED : PAYMENT_FAILED);

        // No log row is written here. Each status update below fires the database trigger
        // dbo.trg_order_status_log, which adds the dbo.logs row (status before and after, date,
        // time) in the same transaction. Writing one here as well would log every step twice.
        //
        // The update skips cancelled orders, so 0 rows means the customer cancelled the order
        // after the check above. Throwing rolls back the payment change saved a few lines up.
        if (paymentManagementRepository.updateOrderStatus(payment.getOrderID(), updatedStatus.getStatusID()) != 1) {
            throw new IllegalStateException("This order has been cancelled, so its payment cannot be verified");
        }

        // Once the payment is verified the order is released for pickup straight away.
        // Nothing else moves an order from "Payment Verified" to "Awaiting Pickup", and the rider
        // module only offers pickups for orders at "Awaiting Pickup", so without this step a paid
        // order would never reach a rider. The two moves are two separate updates, so the trigger
        // logs both and the history still shows Unconfirmed -> Payment Verified -> Awaiting Pickup
        // (and the customer is notified of each).
        Status finalStatus = updatedStatus;
        if (approved) {
            finalStatus = statusService.getByLabel(AWAITING_PICKUP);
            paymentManagementRepository.updateOrderStatus(payment.getOrderID(), finalStatus.getStatusID());
        }

        return new PaymentVerificationResponse(
                paymentID,
                payment.getOrderID(),
                previousOrderStatus.getStatusLabel(),
                finalStatus.getStatusLabel(),
                managementUserID,
                verificationDate,
                verificationTime,
                approved ? "Payment approved" : "Payment rejected"
        );
    }

    private String validateRefundRequest(RefundRequest request) {
        String reason = request == null ? null : request.resolvedReason();
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Refund reason is required");
        }

        reason = reason.trim();
        if (reason.length() > 255) {
            throw new IllegalArgumentException("Refund reason must be 255 characters or fewer");
        }
        return reason;
    }

    private void validatePaymentRequest(PaymentCrudRequest request) {
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }

        if (request.getOrderID() == null) {
            throw new IllegalArgumentException("Order ID is required");
        }
    }

    private void verifyOrderExists(Integer orderID) {
        paymentManagementRepository.findOrderStatus(orderID).orElseThrow();
    }

    // Used when a manager records or edits a payment by hand: the order must exist and must not
    // be cancelled, so a payment cannot be attached to a cancelled order that way either.
    private void requirePayableOrder(Integer orderID) {
        PaymentOrderStatus orderStatus = paymentManagementRepository.findOrderStatus(orderID).orElseThrow();
        if (isCancelled(orderStatus.getStatusLabel())) {
            throw new IllegalArgumentException("Payments cannot be recorded for a cancelled order");
        }
    }

    private boolean isCancelled(String orderStatusLabel) {
        return ORDER_CANCELLED.equalsIgnoreCase(orderStatusLabel);
    }

    private PaymentResponse toPaymentResponse(Payment payment) {
        return new PaymentResponse(
                payment.getPaymentID(),
                BigDecimal.valueOf(payment.getAmount()),
                payment.getOrderID(),
                payment.getPaymentMethod(),
                payment.getTransactionReference(),
                payment.getPaymentStatus(),
                payment.getProcessedAt()
        );
    }

    private RefundResponse toRefundResponse(Refund refund, Payment payment, String message) {
        return new RefundResponse(
                refund.getRefundID(),
                payment.getPaymentID(),
                payment.getOrderID(),
                refund.getRefundAmount(),
                refund.getRefundStatus(),
                refund.getRequestedAt(),
                refund.getProcessedAt(),
                refund.getRefundedAt(),
                refund.getRequestedBy(),
                refund.getProcessedBy(),
                message
        );
    }

    private void notifyRefundApproved(Payment payment, Refund refund) {
        Integer customerID = billingService.getBillingDetails(payment.getOrderID()).getUserID();
        notificationService.notifyUser(
                customerID,
                "PAYMENT",
                "Refund Approved",
                "Your refund request for Order #" + payment.getOrderID() + " has been approved. LKR "
                        + formatNotificationAmount(refund.getRefundAmount()) + " has been refunded.",
                "/html/customer/receipt.html?paymentID=" + payment.getPaymentID() + "&orderID=" + payment.getOrderID(),
                "REFUND",
                refund.getRefundID()
        );
    }

    private void notifyRefundRejected(Payment payment, Refund refund) {
        Integer customerID = billingService.getBillingDetails(payment.getOrderID()).getUserID();
        notificationService.notifyUser(
                customerID,
                "PAYMENT",
                "Refund Request Rejected",
                "Your refund request for Order #" + payment.getOrderID() + " was not approved.",
                "/html/customer/receipt.html?paymentID=" + payment.getPaymentID() + "&orderID=" + payment.getOrderID(),
                "REFUND",
                refund.getRefundID()
        );
    }

    private String formatNotificationAmount(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }

    private PaymentStatusResponse buildPaymentStatus(Integer orderID, BillingDetails billingDetails) {
        BigDecimal payableAmount = billingDetails.getFinalPayableAmount();
        PaymentOrderStatus orderStatus = paymentManagementRepository.findOrderStatus(orderID).orElseThrow();
        List<Payment> fullPaymentAttempts = paymentRepository.findByOrderID(orderID).stream()
                .filter(payment -> isFullPayment(payment, payableAmount))
                .toList();
        Optional<Payment> latestFullPayment = fullPaymentAttempts.stream()
                .max(this::comparePaymentRecency);
        PaymentStatus status = calculateFullPaymentStatus(orderID, orderStatus, latestFullPayment);
        BigDecimal paidAmount = status == PaymentStatus.PENDING
                || status == PaymentStatus.PAID
                || status == PaymentStatus.VERIFIED
                ? payableAmount
                : BigDecimal.ZERO;
        BigDecimal outstandingAmount = status == PaymentStatus.PENDING
                || status == PaymentStatus.PAID
                || status == PaymentStatus.VERIFIED
                || status == PaymentStatus.REFUNDED
                ? BigDecimal.ZERO
                : payableAmount;

        return new PaymentStatusResponse(
                orderID,
                payableAmount,
                paidAmount,
                outstandingAmount,
                status,
                orderStatus.getStatusLabel()
        );
    }

    private PaymentStatus calculateFullPaymentStatus(
            Integer orderID,
            PaymentOrderStatus orderStatus,
            Optional<Payment> latestFullPayment
    ) {
        if (latestFullPayment.isPresent()) {
            Payment payment = latestFullPayment.get();
            if (payment.getPaymentStatus() == PaymentStatus.REFUNDED) {
                return PaymentStatus.REFUNDED;
            }
            if (payment.getPaymentStatus() == PaymentStatus.REJECTED) {
                return PaymentStatus.REJECTED;
            }
            if (payment.getPaymentStatus() == PaymentStatus.VERIFIED
                    || PAYMENT_VERIFIED.equalsIgnoreCase(orderStatus.getStatusLabel())
                    || paymentManagementRepository.wasPaymentVerified(orderID)) {
                return payment.getPaymentStatus() == PaymentStatus.VERIFIED ? PaymentStatus.VERIFIED : PaymentStatus.PAID;
            }
            if (payment.getPaymentStatus() == PaymentStatus.PAID) {
                return PaymentStatus.PAID;
            }
            return PaymentStatus.PENDING;
        }

        return PaymentStatus.UNPAID;
    }

    private boolean isFullPayment(Payment payment, BigDecimal payableAmount) {
        return BigDecimal.valueOf(payment.getAmount()).compareTo(payableAmount) == 0;
    }

    private int comparePaymentRecency(Payment left, Payment right) {
        if (left.getProcessedAt() != null && right.getProcessedAt() != null) {
            int dateComparison = left.getProcessedAt().compareTo(right.getProcessedAt());
            if (dateComparison != 0) {
                return dateComparison;
            }
        } else if (left.getProcessedAt() != null) {
            return 1;
        } else if (right.getProcessedAt() != null) {
            return -1;
        }

        return Integer.compare(
                left.getPaymentID() == null ? Integer.MIN_VALUE : left.getPaymentID(),
                right.getPaymentID() == null ? Integer.MIN_VALUE : right.getPaymentID()
        );
    }

    private PaymentRecordResponse toPaymentRecord(Payment payment) {
        BillingDetails billingDetails = billingService.getBillingDetails(payment.getOrderID());
        PaymentStatusResponse status = buildPaymentStatus(payment.getOrderID(), billingDetails);
        PaymentManagementOrderSummary orderSummary = paymentManagementRepository.findOrderSummary(payment.getOrderID())
                .orElseGet(() -> new PaymentManagementOrderSummary(
                        payment.getOrderID(),
                        billingDetails.getUserID(),
                        null,
                        status.getOrderStatus(),
                        null
                ));
        Optional<Refund> refund = refundRepository.findByPaymentID(payment.getPaymentID());
        BigDecimal recordPaidAmount = countsAsSubmittedOrAccepted(payment.getPaymentStatus())
                ? BigDecimal.valueOf(payment.getAmount())
                : BigDecimal.ZERO;
        BigDecimal recordOutstandingAmount = countsAsSubmittedOrAccepted(payment.getPaymentStatus())
                ? BigDecimal.ZERO
                : status.getPayableAmount();

        return new PaymentRecordResponse(
                payment.getPaymentID(),
                payment.getOrderID(),
                billingDetails.getUserID(),
                orderSummary.getCustomerName(),
                BigDecimal.valueOf(payment.getAmount()),
                status.getPayableAmount(),
                recordPaidAmount,
                recordOutstandingAmount,
                payment.getPaymentStatus(),
                payment.getPaymentMethod(),
                payment.getTransactionReference(),
                payment.getProcessedAt(),
                payment.getProcessedAt(),
                orderSummary.getOrderStatus(),
                refund.map(Refund::getRefundAmount).orElse(null),
                refund.map(Refund::getRefundReason).orElse(null),
                refund.map(Refund::getRefundStatus).orElse(null),
                refund.map(Refund::getRequestedAt).orElse(null),
                refund.map(Refund::getProcessedAt).orElse(null),
                refund.map(Refund::getRefundedAt).orElse(null),
                refund.map(Refund::getRequestedBy).orElse(null),
                refund.map(Refund::getProcessedBy).orElse(null)
        );
    }

    private boolean countsAsSubmittedOrAccepted(PaymentStatus paymentStatus) {
        return paymentStatus == PaymentStatus.PENDING
                || paymentStatus == PaymentStatus.PAID
                || paymentStatus == PaymentStatus.VERIFIED
                || paymentStatus == PaymentStatus.REFUNDED;
    }

    private PaymentRecordResponse toUnpaidOrderRecord(PaymentManagementOrderSummary orderSummary) {
        BillingDetails billingDetails = billingService.getBillingDetails(orderSummary.getOrderID());
        PaymentStatusResponse status = buildPaymentStatus(orderSummary.getOrderID(), billingDetails);

        return new PaymentRecordResponse(
                null,
                orderSummary.getOrderID(),
                billingDetails.getUserID(),
                orderSummary.getCustomerName(),
                BigDecimal.ZERO,
                status.getPayableAmount(),
                status.getPaidAmount(),
                status.getOutstandingAmount(),
                status.getStatus(),
                null,
                null,
                null,
                orderSummary.getOrderDate(),
                orderSummary.getOrderStatus(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private Comparator<PaymentRecordResponse> managementRecordComparator() {
        return Comparator
                .comparing(
                        (PaymentRecordResponse record) -> record.getRecordDate() == null
                                ? LocalDateTime.MIN
                                : record.getRecordDate(),
                        Comparator.reverseOrder()
                )
                .thenComparing(
                        record -> record.getPaymentID() == null ? Integer.MIN_VALUE : record.getPaymentID(),
                        Comparator.reverseOrder()
                )
                .thenComparing(PaymentRecordResponse::getOrderID, Comparator.reverseOrder());
    }

    private void verifyCanViewPaymentRecord(Integer requesterID, Payment payment) {
        if (hasManagementAccess(requesterID)) {
            return;
        }

        BillingDetails billingDetails = billingService.getBillingDetails(payment.getOrderID());
        verifyCanViewOrderPaymentRecords(requesterID, billingDetails);
    }

    private void verifyCanViewOrderPaymentRecords(Integer requesterID, BillingDetails billingDetails) {
        if (hasManagementAccess(requesterID)) {
            return;
        }

        paymentAccessService.requireCustomer(requesterID);
        paymentAccessService.verifyOrderBelongsToCustomer(billingDetails, requesterID);
    }

    private boolean hasManagementAccess(Integer userID) {
        try {
            paymentAccessService.requireManagementUser(userID);
            return true;
        } catch (AccessDeniedException ex) {
            return false;
        }
    }

    private boolean matchesPaymentSearch(PaymentRecordResponse record, String search) {
        return contains(record.getPaymentID(), search)
                || contains(record.getOrderID(), search)
                || contains(record.getCustomerID(), search)
                || contains(record.getCustomerName(), search)
                || contains(record.getAmount(), search)
                || contains(record.getPaymentStatus(), search)
                || contains(record.getPaymentMethod(), search)
                || contains(record.getTransactionReference(), search)
                || contains(record.getProcessedAt(), search)
                || contains(record.getRecordDate(), search)
                || contains(record.getOrderStatus(), search);
    }

    private boolean contains(Object value, String search) {
        return value != null && value.toString().toLowerCase().contains(search);
    }

    private byte[] buildReceiptPdf(PaymentReceiptResponse receipt) {
        List<String> lines = List.of(
                "LaundryLink",
                "Payment Receipt",
                "Order ID: " + receipt.getOrderID(),
                "Payment/transaction ID: " + receipt.getPaymentID() + " / " + nullToText(receipt.getTransactionReference()),
                "Subtotal: " + money(receipt.getSubtotal()),
                "Automatic Bulk Discount: " + money(receipt.getAutomaticBulkDiscount()),
                "Promotion Discount: " + money(receipt.getPromotionDiscount()),
                "Total Discount: " + money(receipt.getTotalDiscount() != null ? receipt.getTotalDiscount() : receipt.getDiscountAmount()),
                "Final Amount: " + money(receipt.getFinalPayableAmount()),
                "Payment method: " + nullToText(receipt.getPaymentMethod()),
                "Payment submitted date/time: " + (receipt.getProcessedAt() == null ? "Not recorded" : receipt.getProcessedAt().format(RECEIPT_DATE_FORMAT)),
                "Current payment status: " + nullToText(receipt.getPaymentStatus())
        );

        StringBuilder content = new StringBuilder();
        content.append("BT\n/F1 18 Tf\n72 760 Td\n(").append(pdfEscape(lines.get(0))).append(") Tj\n");
        content.append("/F1 16 Tf\n0 -28 Td\n(").append(pdfEscape(lines.get(1))).append(") Tj\n");
        content.append("/F1 11 Tf\n");
        for (int i = 2; i < lines.size(); i++) {
            content.append("0 -22 Td\n(").append(pdfEscape(lines.get(i))).append(") Tj\n");
        }
        content.append("ET\n");

        byte[] stream = content.toString().getBytes(StandardCharsets.US_ASCII);
        List<byte[]> objects = new ArrayList<>();
        objects.add("<< /Type /Catalog /Pages 2 0 R >>\n".getBytes(StandardCharsets.US_ASCII));
        objects.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>\n".getBytes(StandardCharsets.US_ASCII));
        objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>\n".getBytes(StandardCharsets.US_ASCII));
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\n".getBytes(StandardCharsets.US_ASCII));
        objects.add(("<< /Length " + stream.length + " >>\nstream\n" + content + "endstream\n").getBytes(StandardCharsets.US_ASCII));

        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        writeAscii(pdf, "%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        offsets.add(0);
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(pdf.size());
            writeAscii(pdf, (i + 1) + " 0 obj\n");
            pdf.writeBytes(objects.get(i));
            writeAscii(pdf, "endobj\n");
        }
        int xref = pdf.size();
        writeAscii(pdf, "xref\n0 " + (objects.size() + 1) + "\n");
        writeAscii(pdf, "0000000000 65535 f \n");
        for (int i = 1; i < offsets.size(); i++) {
            writeAscii(pdf, String.format("%010d 00000 n \n", offsets.get(i)));
        }
        writeAscii(pdf, "trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
        return pdf.toByteArray();
    }

    private String money(BigDecimal value) {
        BigDecimal normalized = value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
        return "LKR " + normalized.toPlainString();
    }

    private String nullToText(Object value) {
        return value == null ? "Not recorded" : value.toString();
    }

    private String pdfEscape(String value) {
        return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private void writeAscii(ByteArrayOutputStream output, String value) {
        output.writeBytes(value.getBytes(StandardCharsets.US_ASCII));
    }
}
