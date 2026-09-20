package _6.Y2.S1.MTR._6.LaundryLink.payment;

import _6.Y2.S1.MTR._6.LaundryLink.billing.BillingDetails;
import _6.Y2.S1.MTR._6.LaundryLink.billing.BillingService;
import _6.Y2.S1.MTR._6.LaundryLink.logs.Log;
import _6.Y2.S1.MTR._6.LaundryLink.logs.LogService;
import _6.Y2.S1.MTR._6.LaundryLink.status.Status;
import _6.Y2.S1.MTR._6.LaundryLink.status.StatusService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class PaymentServiceTest {

    @Test
    void recordsPaymentForOwnedOrderWhenAmountMatchesOutstandingAmount() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = managementRepository("MANAGER", new PaymentOrderStatus(1, "Unconfirmed"));
        PaymentService service = paymentService(paymentRepository, managementRepository, billingService);
        PaymentRequest request = paymentRequest(BigDecimal.valueOf(1350.0));
        Payment savedPayment = new Payment(1350.0, 1);
        savedPayment.setPaymentID(10);

        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 7, BigDecimal.valueOf(1350.0)));
        when(paymentRepository.findByOrderID(1)).thenReturn(List.of());
        when(paymentRepository.save(Mockito.any(Payment.class))).thenReturn(savedPayment);

        PaymentConfirmationResponse response = service.submitPayment(1, 7, request);

        assertEquals(10, response.getPaymentID());
        assertEquals(PaymentStatus.PAID, response.getStatus());
        assertEquals(BigDecimal.valueOf(1350.0), response.getAmount());
    }

    @Test
    void createsBasicPaymentForExistingOrder() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = managementRepository("MANAGER", new PaymentOrderStatus(1, "Unconfirmed"));
        PaymentService service = paymentService(paymentRepository, managementRepository, billingService);
        PaymentCrudRequest request = paymentCrudRequest(BigDecimal.valueOf(250.0), 1);
        Payment savedPayment = new Payment(250.0, 1);
        savedPayment.setPaymentID(20);

        when(paymentRepository.save(Mockito.any(Payment.class))).thenReturn(savedPayment);

        PaymentResponse response = service.createPayment(11, request);

        assertEquals(20, response.getPaymentID());
        assertEquals(BigDecimal.valueOf(250.0), response.getAmount());
        assertEquals(1, response.getOrderID());
    }

    @Test
    void preventsBasicPaymentForNonExistentOrder() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = Mockito.mock(PaymentManagementRepository.class);
        PaymentService service = paymentService(paymentRepository, managementRepository, billingService);

        when(managementRepository.findUserType(11)).thenReturn(Optional.of("MANAGER"));
        when(managementRepository.findOrderStatus(99)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () ->
                service.createPayment(11, paymentCrudRequest(BigDecimal.valueOf(250.0), 99)));
    }

    @Test
    void updatesBasicPaymentForExistingOrder() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = managementRepository("MANAGER", new PaymentOrderStatus(1, "Unconfirmed"));
        PaymentService service = paymentService(paymentRepository, managementRepository, billingService);
        Payment existingPayment = new Payment(250.0, 1);
        existingPayment.setPaymentID(20);

        when(paymentRepository.findById(20)).thenReturn(Optional.of(existingPayment));
        when(paymentRepository.save(existingPayment)).thenReturn(existingPayment);

        PaymentResponse response = service.updatePayment(11, 20, paymentCrudRequest(BigDecimal.valueOf(300.0), 1));

        assertEquals(20, response.getPaymentID());
        assertEquals(BigDecimal.valueOf(300.0), response.getAmount());
        assertEquals(1, response.getOrderID());
    }

    @Test
    void preventsPaymentForNonExistentOrder() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentService service = paymentService(
                paymentRepository,
                managementRepository("CUSTOMER", new PaymentOrderStatus(1, "Unconfirmed")),
                billingService
        );

        when(billingService.getBillingDetails(99)).thenThrow(NoSuchElementException.class);

        assertThrows(NoSuchElementException.class, () ->
                service.submitPayment(99, 7, paymentRequest(BigDecimal.valueOf(100.0))));
    }

    @Test
    void preventsPaymentForOrderOwnedByAnotherCustomer() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentService service = paymentService(
                paymentRepository,
                managementRepository("CUSTOMER", new PaymentOrderStatus(1, "Unconfirmed")),
                billingService
        );

        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 8, BigDecimal.valueOf(1350.0)));

        assertThrows(AccessDeniedException.class, () ->
                service.submitPayment(1, 7, paymentRequest(BigDecimal.valueOf(1350.0))));
    }

    @Test
    void preventsPaymentWhenAmountDoesNotMatchOutstandingAmount() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentService service = paymentService(
                paymentRepository,
                managementRepository("CUSTOMER", new PaymentOrderStatus(1, "Unconfirmed")),
                billingService
        );

        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 7, BigDecimal.valueOf(1350.0)));
        when(paymentRepository.findByOrderID(1)).thenReturn(List.of());

        assertThrows(IllegalArgumentException.class, () ->
                service.submitPayment(1, 7, paymentRequest(BigDecimal.valueOf(100.0))));
    }

    @Test
    void preventsDuplicatePaymentWhenOrderIsAlreadyPaid() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentService service = paymentService(
                paymentRepository,
                managementRepository("CUSTOMER", new PaymentOrderStatus(1, "Unconfirmed")),
                billingService
        );

        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 7, BigDecimal.valueOf(1350.0)));
        when(paymentRepository.findByOrderID(1)).thenReturn(List.of(new Payment(1350.0, 1)));

        assertThrows(IllegalStateException.class, () ->
                service.submitPayment(1, 7, paymentRequest(BigDecimal.valueOf(1350.0))));
    }

    @Test
    void returnsCustomerPaymentHistoryOnlyForOwnedOrders() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentService service = paymentService(
                paymentRepository,
                managementRepository("CUSTOMER", new PaymentOrderStatus(1, "Unconfirmed")),
                billingService
        );
        Payment ownedPayment = new Payment(900.0, 1);
        ownedPayment.setPaymentID(1);
        Payment otherPayment = new Payment(500.0, 2);
        otherPayment.setPaymentID(2);

        when(paymentRepository.findAll()).thenReturn(new ArrayList<>(List.of(ownedPayment, otherPayment)));
        when(paymentRepository.findByOrderID(1)).thenReturn(List.of(ownedPayment));
        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 7, BigDecimal.valueOf(900.0)));
        when(billingService.getBillingDetails(2)).thenReturn(billingDetails(2, 8, BigDecimal.valueOf(500.0)));

        List<PaymentHistoryResponse> history = service.getPaymentHistory(7);

        assertEquals(1, history.size());
        assertEquals(1, history.get(0).getPaymentID());
        assertEquals(1, history.get(0).getOrderID());
        assertEquals(PaymentStatus.PAID, history.get(0).getPaymentStatus());
    }

    @Test
    void letsCustomerViewOwnPaymentRecord() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = managementRepository("CUSTOMER", new PaymentOrderStatus(1, "Unconfirmed"));
        PaymentService service = paymentService(paymentRepository, managementRepository, billingService);
        Payment payment = new Payment(900.0, 1);
        payment.setPaymentID(1);

        when(paymentRepository.findById(1)).thenReturn(Optional.of(payment));
        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 7, BigDecimal.valueOf(900.0)));

        PaymentResponse response = service.getPayment(7, 1);

        assertEquals(1, response.getPaymentID());
        assertEquals(1, response.getOrderID());
    }

    @Test
    void preventsCustomerFromViewingAnotherCustomersPaymentRecord() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = managementRepository("CUSTOMER", new PaymentOrderStatus(1, "Unconfirmed"));
        PaymentService service = paymentService(paymentRepository, managementRepository, billingService);
        Payment payment = new Payment(900.0, 1);
        payment.setPaymentID(1);

        when(paymentRepository.findById(1)).thenReturn(Optional.of(payment));
        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 8, BigDecimal.valueOf(900.0)));

        assertThrows(AccessDeniedException.class, () -> service.getPayment(7, 1));
    }

    @Test
    void letsStaffViewPaymentStatusForAnOrder() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = managementRepository("STAFF", new PaymentOrderStatus(1, "Unconfirmed"));
        PaymentService service = paymentService(paymentRepository, managementRepository, billingService);

        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 7, BigDecimal.valueOf(1350.0)));
        when(paymentRepository.findByOrderID(1)).thenReturn(List.of(new Payment(1350.0, 1)));

        PaymentStatusResponse response = service.getPaymentStatusForStaff(1, 5);

        assertEquals(PaymentStatus.PAID, response.getStatus());
        assertEquals(BigDecimal.ZERO, response.getOutstandingAmount());
    }

    @Test
    void preventsCustomerFromUsingStaffPaymentStatusEndpoint() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = managementRepository("CUSTOMER", new PaymentOrderStatus(1, "Unconfirmed"));
        PaymentService service = paymentService(paymentRepository, managementRepository, billingService);

        assertThrows(AccessDeniedException.class, () -> service.getPaymentStatusForStaff(1, 7));
    }

    @Test
    void letsManagerViewPaymentRecords() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = managementRepository("MANAGER", new PaymentOrderStatus(1, "Unconfirmed"));
        PaymentService service = paymentService(paymentRepository, managementRepository, billingService);
        Payment payment = new Payment(1350.0, 1);
        payment.setPaymentID(4);

        when(paymentRepository.findAll()).thenReturn(List.of(payment));
        when(paymentRepository.findByOrderID(1)).thenReturn(List.of(payment));
        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 7, BigDecimal.valueOf(1350.0)));

        List<PaymentRecordResponse> records = service.getPaymentRecords(11, null, null, PaymentStatus.PAID, null);

        assertEquals(1, records.size());
        assertEquals(4, records.get(0).getPaymentID());
        assertEquals(PaymentStatus.PAID, records.get(0).getPaymentStatus());
    }

    @Test
    void letsManagerSearchPaymentRecords() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = managementRepository("MANAGER", new PaymentOrderStatus(2, "Payment Verified"));
        PaymentService service = paymentService(paymentRepository, managementRepository, billingService);
        Payment payment = new Payment(1350.0, 1);
        payment.setPaymentID(4);

        when(paymentRepository.findAll()).thenReturn(List.of(payment));
        when(paymentRepository.findByOrderID(1)).thenReturn(List.of(payment));
        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 7, BigDecimal.valueOf(1350.0)));

        List<PaymentRecordResponse> records = service.getPaymentRecords(11, null, null, null, "verified");

        assertEquals(1, records.size());
        assertEquals(PaymentStatus.VERIFIED, records.get(0).getPaymentStatus());
    }

    @Test
    void preventsUnauthorizedUserFromViewingPaymentRecords() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = managementRepository("CUSTOMER", new PaymentOrderStatus(1, "Unconfirmed"));
        PaymentService service = paymentService(paymentRepository, managementRepository, billingService);

        assertThrows(AccessDeniedException.class, () ->
                service.getPaymentRecords(7, null, null, null, null));
    }

    @Test
    void managerApprovesFullyPaidPayment() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = Mockito.mock(PaymentManagementRepository.class);
        StatusService statusService = Mockito.mock(StatusService.class);
        LogService logService = Mockito.mock(LogService.class);
        PaymentAccessService accessService = new PaymentAccessService(managementRepository);
        PaymentService service = new PaymentService(
                paymentRepository,
                managementRepository,
                billingService,
                accessService,
                statusService,
                logService
        );
        Payment payment = new Payment(1350.0, 1);
        payment.setPaymentID(4);
        PaymentVerificationRequest request = new PaymentVerificationRequest();
        request.setApproved(true);
        Status unconfirmed = status(1, "Unconfirmed");
        Status verified = status(2, "Payment Verified");

        when(managementRepository.findUserType(11)).thenReturn(Optional.of("MANAGER"));
        when(paymentRepository.findById(4)).thenReturn(Optional.of(payment));
        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 7, BigDecimal.valueOf(1350.0)));
        when(paymentRepository.findByOrderID(1)).thenReturn(List.of(payment));
        when(managementRepository.findOrderStatus(1)).thenReturn(Optional.of(new PaymentOrderStatus(1, "Unconfirmed")));
        when(statusService.getByLabel("Payment Verified")).thenReturn(verified);
        when(statusService.getById(1)).thenReturn(unconfirmed);
        when(logService.logChange(Mockito.any(Log.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentVerificationResponse response = service.verifyPayment(11, 4, request);

        assertEquals("Unconfirmed", response.getPreviousOrderStatus());
        assertEquals("Payment Verified", response.getUpdatedOrderStatus());
        assertEquals(11, response.getVerifiedByUserID());
        assertNotNull(response.getVerificationDate());
        assertNotNull(response.getVerificationTime());
        Mockito.verify(managementRepository).updateOrderStatus(1, 2);
    }

    @Test
    void managerRejectsFullyPaidPayment() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = Mockito.mock(PaymentManagementRepository.class);
        StatusService statusService = Mockito.mock(StatusService.class);
        LogService logService = Mockito.mock(LogService.class);
        PaymentService service = new PaymentService(
                paymentRepository,
                managementRepository,
                billingService,
                new PaymentAccessService(managementRepository),
                statusService,
                logService
        );
        Payment payment = new Payment(1350.0, 1);
        payment.setPaymentID(4);
        PaymentVerificationRequest request = new PaymentVerificationRequest();
        request.setApproved(false);
        Status unconfirmed = status(1, "Unconfirmed");
        Status failed = status(16, "Payment Failed");

        when(managementRepository.findUserType(11)).thenReturn(Optional.of("MANAGER"));
        when(paymentRepository.findById(4)).thenReturn(Optional.of(payment));
        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 7, BigDecimal.valueOf(1350.0)));
        when(paymentRepository.findByOrderID(1)).thenReturn(List.of(payment));
        when(managementRepository.findOrderStatus(1)).thenReturn(Optional.of(new PaymentOrderStatus(1, "Unconfirmed")));
        when(statusService.getByLabel("Payment Failed")).thenReturn(failed);
        when(statusService.getById(1)).thenReturn(unconfirmed);
        when(logService.logChange(Mockito.any(Log.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentVerificationResponse response = service.verifyPayment(11, 4, request);

        assertEquals("Payment Failed", response.getUpdatedOrderStatus());
        assertEquals(11, response.getVerifiedByUserID());
        assertNotNull(response.getVerificationDate());
        assertNotNull(response.getVerificationTime());
        Mockito.verify(managementRepository).updateOrderStatus(1, 16);
    }

    private PaymentRequest paymentRequest(BigDecimal amount) {
        PaymentRequest request = new PaymentRequest();
        request.setPaymentMethod(PaymentMethod.CARD);
        request.setAmount(amount);
        return request;
    }

    private PaymentCrudRequest paymentCrudRequest(BigDecimal amount, Integer orderID) {
        PaymentCrudRequest request = new PaymentCrudRequest();
        request.setAmount(amount);
        request.setOrderID(orderID);
        return request;
    }

    private BillingDetails billingDetails(Integer orderID, Integer userID, BigDecimal payableAmount) {
        return new BillingDetails(orderID, userID, List.of(), payableAmount, BigDecimal.ZERO, payableAmount);
    }

    private PaymentService paymentService(
            PaymentRepository paymentRepository,
            PaymentManagementRepository managementRepository,
            BillingService billingService
    ) {
        return new PaymentService(
                paymentRepository,
                managementRepository,
                billingService,
                new PaymentAccessService(managementRepository),
                Mockito.mock(StatusService.class),
                Mockito.mock(LogService.class)
        );
    }

    private PaymentManagementRepository managementRepository(String userType, PaymentOrderStatus orderStatus) {
        PaymentManagementRepository repository = Mockito.mock(PaymentManagementRepository.class);
        when(repository.findUserType(Mockito.anyInt())).thenReturn(Optional.of(userType));
        when(repository.findOrderStatus(Mockito.anyInt())).thenReturn(Optional.of(orderStatus));
        return repository;
    }

    private Status status(Integer statusID, String statusLabel) {
        Status status = Mockito.mock(Status.class);
        when(status.getStatusID()).thenReturn(statusID);
        when(status.getStatusLabel()).thenReturn(statusLabel);
        return status;
    }
}
