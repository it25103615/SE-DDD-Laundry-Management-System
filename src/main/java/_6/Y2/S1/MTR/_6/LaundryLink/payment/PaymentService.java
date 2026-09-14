package _6.Y2.S1.MTR._6.LaundryLink.payment;

import _6.Y2.S1.MTR._6.LaundryLink.billing.BillingDetails;
import _6.Y2.S1.MTR._6.LaundryLink.billing.BillingService;
import _6.Y2.S1.MTR._6.LaundryLink.logs.Log;
import _6.Y2.S1.MTR._6.LaundryLink.logs.LogService;
import _6.Y2.S1.MTR._6.LaundryLink.status.Status;
import _6.Y2.S1.MTR._6.LaundryLink.status.StatusService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

@Service
public class PaymentService {
    private static final String PAYMENT_VERIFIED = "Payment Verified";
    private static final String PAYMENT_FAILED = "Payment Failed";

    private final PaymentRepository paymentRepository;
    private final PaymentManagementRepository paymentManagementRepository;
    private final BillingService billingService;
    private final PaymentAccessService paymentAccessService;
    private final StatusService statusService;
    private final LogService logService;

    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentManagementRepository paymentManagementRepository,
            BillingService billingService,
            PaymentAccessService paymentAccessService,
            StatusService statusService,
            LogService logService
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentManagementRepository = paymentManagementRepository;
        this.billingService = billingService;
        this.paymentAccessService = paymentAccessService;
        this.statusService = statusService;
        this.logService = logService;
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
        verifyOrderExists(request.getOrderID());

        Payment savedPayment = paymentRepository.save(
                new Payment(request.getAmount().doubleValue(), request.getOrderID())
        );
        return toPaymentResponse(savedPayment);
    }

    public PaymentResponse getPayment(Integer requesterID, Integer paymentID) {
        Payment payment = paymentRepository.findById(paymentID).orElseThrow();
        verifyCanViewPaymentRecord(requesterID, payment);
        return toPaymentResponse(payment);
    }

    public List<PaymentResponse> getPayments(Integer managementUserID) {
        paymentAccessService.requireManagementUser(managementUserID);
        return paymentRepository.findAll().stream()
                .map(this::toPaymentResponse)
                .toList();
    }

    public List<PaymentResponse> getPaymentsByOrder(Integer requesterID, Integer orderID) {
        verifyOrderExists(orderID);
        verifyCanViewOrderPaymentRecords(requesterID, billingService.getBillingDetails(orderID));
        return paymentRepository.findByOrderID(orderID).stream()
                .map(this::toPaymentResponse)
                .toList();
    }

    public PaymentResponse updatePayment(Integer managementUserID, Integer paymentID, PaymentCrudRequest request) {
        paymentAccessService.requireManagementUser(managementUserID);
        validatePaymentRequest(request);
        verifyOrderExists(request.getOrderID());

        Payment payment = paymentRepository.findById(paymentID).orElseThrow();
        payment.setAmount(request.getAmount().doubleValue());
        payment.setOrderID(request.getOrderID());
        return toPaymentResponse(paymentRepository.save(payment));
    }

    public void deletePayment(Integer managementUserID, Integer paymentID) {
        paymentAccessService.requireManagementUser(managementUserID);
        Payment payment = paymentRepository.findById(paymentID).orElseThrow();
        paymentRepository.delete(payment);
    }

    public PaymentConfirmationResponse submitPayment(Integer orderID, Integer customerID, PaymentRequest request) {
        PaymentStatusResponse currentStatus = getPaymentStatus(orderID, customerID);

        if (currentStatus.getStatus() == PaymentStatus.PAID) {
            throw new IllegalStateException("This order is already paid");
        }

        if (request.getAmount().compareTo(currentStatus.getOutstandingAmount()) != 0) {
            throw new IllegalArgumentException("Payment amount must match the outstanding amount");
        }

        Payment savedPayment = paymentRepository.save(new Payment(request.getAmount().doubleValue(), orderID));

        return new PaymentConfirmationResponse(
                savedPayment.getPaymentID(),
                orderID,
                BigDecimal.valueOf(savedPayment.getAmount()),
                request.getPaymentMethod(),
                PaymentStatus.PAID,
                "Payment recorded successfully"
        );
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
        return paymentRepository.findAll().stream()
                .filter(payment -> {
                    BillingDetails billingDetails = billingService.getBillingDetails(payment.getOrderID());
                    return billingDetails.getUserID().equals(customerID);
                })
                .map(payment -> {
                    BillingDetails billingDetails = billingService.getBillingDetails(payment.getOrderID());
                    PaymentStatusResponse status = buildPaymentStatus(payment.getOrderID(), billingDetails);
                    return new PaymentHistoryResponse(
                            payment.getPaymentID(),
                            payment.getOrderID(),
                            BigDecimal.valueOf(payment.getAmount()),
                            status.getStatus(),
                            status.getOrderStatus()
                    );
                })
                .toList();
    }

    public List<PaymentRecordResponse> getPaymentRecords(
            Integer managementUserID,
            Integer orderID,
            Integer customerID,
            PaymentStatus paymentStatus,
            String search
    ) {
        paymentAccessService.requireManagementUser(managementUserID);

        String normalizedSearch = search == null ? null : search.trim().toLowerCase();
        return paymentRepository.findAll().stream()
                .map(this::toPaymentRecord)
                .filter(record -> orderID == null || Objects.equals(record.getOrderID(), orderID))
                .filter(record -> customerID == null || Objects.equals(record.getCustomerID(), customerID))
                .filter(record -> paymentStatus == null || record.getPaymentStatus() == paymentStatus)
                .filter(record -> normalizedSearch == null || normalizedSearch.isBlank() || matchesPaymentSearch(record, normalizedSearch))
                .toList();
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

    private PaymentVerificationResponse verifyPaymentDecision(Integer managementUserID, Integer paymentID, boolean approved) {
        paymentAccessService.requireManagementUser(managementUserID);

        Payment payment = paymentRepository.findById(paymentID).orElseThrow();
        PaymentStatusResponse currentStatus = buildPaymentStatus(
                payment.getOrderID(),
                billingService.getBillingDetails(payment.getOrderID())
        );

        if (currentStatus.getStatus() == PaymentStatus.UNPAID || currentStatus.getStatus() == PaymentStatus.PARTIALLY_PAID) {
            throw new IllegalStateException("Only fully paid orders can be verified");
        }

        PaymentOrderStatus previousOrderStatus = paymentManagementRepository.findOrderStatus(payment.getOrderID()).orElseThrow();
        Status updatedStatus = statusService.getByLabel(approved ? PAYMENT_VERIFIED : PAYMENT_FAILED);
        LocalDate verificationDate = LocalDate.now();
        LocalTime verificationTime = LocalTime.now();

        paymentManagementRepository.updateOrderStatus(payment.getOrderID(), updatedStatus.getStatusID());
        recordVerificationLog(previousOrderStatus, updatedStatus, payment.getOrderID(), verificationDate, verificationTime);

        return new PaymentVerificationResponse(
                paymentID,
                payment.getOrderID(),
                previousOrderStatus.getStatusLabel(),
                updatedStatus.getStatusLabel(),
                managementUserID,
                verificationDate,
                verificationTime,
                approved ? "Payment approved" : "Payment rejected"
        );
    }

    private BigDecimal getPaidAmount(Integer orderID) {
        return paymentRepository.findByOrderID(orderID).stream()
                .map(payment -> BigDecimal.valueOf(payment.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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

    private PaymentResponse toPaymentResponse(Payment payment) {
        return new PaymentResponse(
                payment.getPaymentID(),
                BigDecimal.valueOf(payment.getAmount()),
                payment.getOrderID()
        );
    }

    private PaymentStatus calculateStatus(BigDecimal payableAmount, BigDecimal paidAmount) {
        PaymentOrderStatus orderStatus = null;
        return calculateStatus(payableAmount, paidAmount, orderStatus);
    }

    private PaymentStatus calculateStatus(BigDecimal payableAmount, BigDecimal paidAmount, PaymentOrderStatus orderStatus) {
        if (orderStatus != null && PAYMENT_VERIFIED.equalsIgnoreCase(orderStatus.getStatusLabel())) {
            return PaymentStatus.VERIFIED;
        }

        if (orderStatus != null && PAYMENT_FAILED.equalsIgnoreCase(orderStatus.getStatusLabel())) {
            return PaymentStatus.REJECTED;
        }

        if (paidAmount.compareTo(BigDecimal.ZERO) == 0) {
            return PaymentStatus.UNPAID;
        }

        if (paidAmount.compareTo(payableAmount) < 0) {
            return PaymentStatus.PARTIALLY_PAID;
        }

        return PaymentStatus.PAID;
    }

    private PaymentStatusResponse buildPaymentStatus(Integer orderID, BillingDetails billingDetails) {
        BigDecimal payableAmount = billingDetails.getFinalPayableAmount();
        BigDecimal paidAmount = getPaidAmount(orderID);
        BigDecimal outstandingAmount = payableAmount.subtract(paidAmount);
        PaymentOrderStatus orderStatus = paymentManagementRepository.findOrderStatus(orderID).orElseThrow();

        if (outstandingAmount.compareTo(BigDecimal.ZERO) < 0) {
            outstandingAmount = BigDecimal.ZERO;
        } else if (outstandingAmount.compareTo(BigDecimal.ZERO) == 0) {
            outstandingAmount = BigDecimal.ZERO;
        }

        return new PaymentStatusResponse(
                orderID,
                payableAmount,
                paidAmount,
                outstandingAmount,
                calculateStatus(payableAmount, paidAmount, orderStatus),
                orderStatus.getStatusLabel()
        );
    }

    private PaymentRecordResponse toPaymentRecord(Payment payment) {
        BillingDetails billingDetails = billingService.getBillingDetails(payment.getOrderID());
        PaymentStatusResponse status = buildPaymentStatus(payment.getOrderID(), billingDetails);
        PaymentOrderStatus orderStatus = paymentManagementRepository.findOrderStatus(payment.getOrderID()).orElseThrow();

        return new PaymentRecordResponse(
                payment.getPaymentID(),
                payment.getOrderID(),
                billingDetails.getUserID(),
                BigDecimal.valueOf(payment.getAmount()),
                status.getPayableAmount(),
                status.getPaidAmount(),
                status.getOutstandingAmount(),
                status.getStatus(),
                orderStatus.getStatusLabel()
        );
    }

    private void recordVerificationLog(
            PaymentOrderStatus previousOrderStatus,
            Status updatedStatus,
            Integer orderID,
            LocalDate verificationDate,
            LocalTime verificationTime
    ) {
        Log log = new Log();
        log.setStatusBefore(statusService.getById(previousOrderStatus.getStatusID()));
        log.setStatusAfter(updatedStatus);
        log.setLogDate(verificationDate);
        log.setLogTime(verificationTime);
        log.setOrderID(orderID);

        logService.logChange(log);
    }

    private void verifyCanViewPaymentRecord(Integer requesterID, Payment payment) {
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
                || contains(record.getAmount(), search)
                || contains(record.getPaymentStatus(), search)
                || contains(record.getOrderStatus(), search);
    }

    private boolean contains(Object value, String search) {
        return value != null && value.toString().toLowerCase().contains(search);
    }
}
