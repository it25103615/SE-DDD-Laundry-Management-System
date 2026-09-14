package _6.Y2.S1.MTR._6.LaundryLink.payment;

import _6.Y2.S1.MTR._6.LaundryLink.billing.BillingDetails;
import _6.Y2.S1.MTR._6.LaundryLink.billing.BillingService;
import _6.Y2.S1.MTR._6.LaundryLink.logs.Log;
import _6.Y2.S1.MTR._6.LaundryLink.logs.LogService;
import _6.Y2.S1.MTR._6.LaundryLink.status.Status;
import _6.Y2.S1.MTR._6.LaundryLink.status.StatusService;
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
                .map(payment -> new PaymentHistoryResponse(
                        payment.getPaymentID(),
                        payment.getOrderID(),
                        BigDecimal.valueOf(payment.getAmount())
                ))
                .toList();
    }

    public List<PaymentRecordResponse> getPaymentRecords(
            Integer managementUserID,
            Integer orderID,
            Integer customerID,
            PaymentStatus paymentStatus
    ) {
        paymentAccessService.requireManagementUser(managementUserID);

        return paymentRepository.findAll().stream()
                .map(this::toPaymentRecord)
                .filter(record -> orderID == null || Objects.equals(record.getOrderID(), orderID))
                .filter(record -> customerID == null || Objects.equals(record.getCustomerID(), customerID))
                .filter(record -> paymentStatus == null || record.getPaymentStatus() == paymentStatus)
                .toList();
    }

    @Transactional
    public PaymentVerificationResponse verifyPayment(Integer managementUserID, Integer paymentID, PaymentVerificationRequest request) {
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
        Status updatedStatus = statusService.getByLabel(Boolean.TRUE.equals(request.getApproved()) ? PAYMENT_VERIFIED : PAYMENT_FAILED);

        paymentManagementRepository.updateOrderStatus(payment.getOrderID(), updatedStatus.getStatusID());
        recordVerificationLog(previousOrderStatus, updatedStatus, payment.getOrderID());

        return new PaymentVerificationResponse(
                paymentID,
                payment.getOrderID(),
                previousOrderStatus.getStatusLabel(),
                updatedStatus.getStatusLabel(),
                Boolean.TRUE.equals(request.getApproved()) ? "Payment approved" : "Payment rejected"
        );
    }

    private BigDecimal getPaidAmount(Integer orderID) {
        return paymentRepository.findByOrderID(orderID).stream()
                .map(payment -> BigDecimal.valueOf(payment.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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
        }

        return new PaymentStatusResponse(
                orderID,
                payableAmount,
                paidAmount,
                outstandingAmount,
                calculateStatus(payableAmount, paidAmount, orderStatus)
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

    private void recordVerificationLog(PaymentOrderStatus previousOrderStatus, Status updatedStatus, Integer orderID) {
        Log log = new Log();
        log.setStatusBefore(statusService.getById(previousOrderStatus.getStatusID()));
        log.setStatusAfter(updatedStatus);
        log.setLogDate(LocalDate.now());
        log.setLogTime(LocalTime.now());
        log.setOrderID(orderID);

        logService.logChange(log);
    }
}
