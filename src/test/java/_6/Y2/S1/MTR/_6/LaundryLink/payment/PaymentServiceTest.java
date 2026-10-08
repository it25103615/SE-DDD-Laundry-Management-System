package _6.Y2.S1.MTR._6.LaundryLink.payment;

import _6.Y2.S1.MTR._6.LaundryLink.billing.BillingDetails;
import _6.Y2.S1.MTR._6.LaundryLink.billing.BillingService;
import _6.Y2.S1.MTR._6.LaundryLink.notification.NotificationService;
import _6.Y2.S1.MTR._6.LaundryLink.status.Status;
import _6.Y2.S1.MTR._6.LaundryLink.status.StatusService;
import _6.Y2.S1.MTR._6.LaundryLink.user.User;
import _6.Y2.S1.MTR._6.LaundryLink.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 7, BigDecimal.valueOf(1350.0)));
        when(paymentRepository.findByOrderID(1)).thenReturn(List.of());
        when(paymentRepository.save(Mockito.any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setPaymentID(10);
            return payment;
        });

        PaymentConfirmationResponse response = service.submitPayment(1, 7, request);

        assertEquals(10, response.getPaymentID());
        assertEquals(PaymentStatus.PENDING, response.getStatus());
        assertEquals(BigDecimal.valueOf(1350.0), response.getAmount());
        assertEquals(PaymentMethod.CARD, response.getPaymentMethod());
        assertNotNull(response.getTransactionReference());
        assertTrue(response.getTransactionReference().startsWith("LLPAY-1-"));
        assertNotNull(response.getProcessedAt());

        Payment persisted = Mockito.mockingDetails(paymentRepository).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("save"))
                .findFirst()
                .map(invocation -> (Payment) invocation.getArgument(0))
                .orElseThrow();
        assertEquals(PaymentMethod.CARD, persisted.getPaymentMethod());
        assertEquals(PaymentStatus.PENDING, persisted.getPaymentStatus());
        assertNotNull(persisted.getTransactionReference());
        assertNotNull(persisted.getProcessedAt());
    }

    @Test
    void createsBasicPaymentForExistingOrder() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = managementRepository("MANAGER", new PaymentOrderStatus(1, "Unconfirmed"));
        PaymentService service = paymentService(paymentRepository, managementRepository, billingService);
        PaymentCrudRequest request = paymentCrudRequest(BigDecimal.valueOf(250.0), 1);
        request.setPaymentMethod(PaymentMethod.CASH);

        when(paymentRepository.save(Mockito.any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setPaymentID(20);
            return payment;
        });

        PaymentResponse response = service.createPayment(11, request);

        assertEquals(20, response.getPaymentID());
        assertEquals(BigDecimal.valueOf(250.0), response.getAmount());
        assertEquals(1, response.getOrderID());
        assertEquals(PaymentMethod.CASH, response.getPaymentMethod());
        assertEquals(PaymentStatus.PENDING, response.getPaymentStatus());
        assertNotNull(response.getTransactionReference());
        assertNotNull(response.getProcessedAt());
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
        assertEquals(PaymentStatus.PENDING, response.getPaymentStatus());
        assertNotNull(response.getTransactionReference());
        assertNotNull(response.getProcessedAt());
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
    void preventsPaymentWhenAmountDoesNotMatchFinalPayableAmount() {
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
    void ignoresOldPartialPaymentRowsWhenCalculatingOutstandingAmount() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentService service = paymentService(
                paymentRepository,
                managementRepository("CUSTOMER", new PaymentOrderStatus(1, "Unconfirmed")),
                billingService
        );
        Payment oldPartialPayment = new Payment(1998.0, 14);
        oldPartialPayment.setPaymentID(25);

        when(billingService.getBillingDetails(14)).thenReturn(billingDetails(14, 7, BigDecimal.valueOf(4995.0)));
        when(paymentRepository.findByOrderID(14)).thenReturn(List.of(oldPartialPayment));

        PaymentStatusResponse response = service.getPaymentStatus(14, 7);

        assertEquals(PaymentStatus.UNPAID, response.getStatus());
        assertEquals(BigDecimal.ZERO, response.getPaidAmount());
        assertEquals(BigDecimal.valueOf(4995.0), response.getOutstandingAmount());
    }

    @Test
    void acceptsFullPaymentEvenWhenOldPartialRowsExist() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentService service = paymentService(
                paymentRepository,
                managementRepository("CUSTOMER", new PaymentOrderStatus(1, "Unconfirmed")),
                billingService
        );
        Payment oldPartialPayment = new Payment(2997.0, 14);
        oldPartialPayment.setPaymentID(25);

        when(billingService.getBillingDetails(14)).thenReturn(billingDetails(14, 7, BigDecimal.valueOf(4995.0)));
        when(paymentRepository.findByOrderID(14)).thenReturn(List.of(oldPartialPayment));
        when(paymentRepository.save(Mockito.any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setPaymentID(26);
            return payment;
        });

        PaymentConfirmationResponse response = service.submitPayment(14, 7, paymentRequest(BigDecimal.valueOf(4995.0)));

        assertEquals(26, response.getPaymentID());
        assertEquals(BigDecimal.valueOf(4995.0), response.getAmount());
    }

    @Test
    void acceptsPaymentForExactFinalPayableAfterBulkDiscount() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentService service = paymentService(
                paymentRepository,
                managementRepository("CUSTOMER", new PaymentOrderStatus(1, "Unconfirmed")),
                billingService
        );

        when(billingService.getBillingDetails(1)).thenReturn(
                billingDetails(
                        1,
                        7,
                        BigDecimal.valueOf(10000.0),
                        BigDecimal.valueOf(1000.0),
                        BigDecimal.ZERO,
                        BigDecimal.valueOf(1000.0),
                        BigDecimal.valueOf(9000.0)
                )
        );
        when(paymentRepository.findByOrderID(1)).thenReturn(List.of());
        when(paymentRepository.save(Mockito.any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setPaymentID(30);
            return payment;
        });

        PaymentConfirmationResponse response = service.submitPayment(1, 7, paymentRequest(BigDecimal.valueOf(9000.0)));

        assertEquals(30, response.getPaymentID());
        assertEquals(BigDecimal.valueOf(9000.0), response.getAmount());
    }

    @Test
    void returnsOutstandingAmountWithBulkDiscountOnly() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentService service = paymentService(
                paymentRepository,
                managementRepository("CUSTOMER", new PaymentOrderStatus(1, "Unconfirmed")),
                billingService
        );

        when(billingService.getBillingDetails(23)).thenReturn(
                billingDetails(
                        23,
                        7,
                        BigDecimal.valueOf(9240.0),
                        BigDecimal.valueOf(924.0),
                        BigDecimal.ZERO,
                        BigDecimal.valueOf(924.0),
                        BigDecimal.valueOf(8316.0)
                )
        );
        when(paymentRepository.findByOrderID(23)).thenReturn(List.of());

        PaymentStatusResponse response = service.getPaymentStatus(23, 7);

        assertEquals(BigDecimal.valueOf(8316.0), response.getPayableAmount());
        assertEquals(BigDecimal.valueOf(8316.0), response.getOutstandingAmount());
    }

    @Test
    void returnsOutstandingAmountWithBulkAndPromotionDiscounts() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentService service = paymentService(
                paymentRepository,
                managementRepository("CUSTOMER", new PaymentOrderStatus(1, "Unconfirmed")),
                billingService
        );

        when(billingService.getBillingDetails(23)).thenReturn(
                billingDetails(
                        23,
                        7,
                        BigDecimal.valueOf(9240.0),
                        BigDecimal.valueOf(924.0),
                        BigDecimal.valueOf(831.60),
                        BigDecimal.valueOf(1755.60),
                        BigDecimal.valueOf(7484.40)
                )
        );
        when(paymentRepository.findByOrderID(23)).thenReturn(List.of());

        PaymentStatusResponse response = service.getPaymentStatus(23, 7);

        assertEquals(BigDecimal.valueOf(7484.40), response.getPayableAmount());
        assertEquals(BigDecimal.valueOf(7484.40), response.getOutstandingAmount());
    }

    @Test
    void rejectsManipulatedSubtotalAmountAfterBulkDiscount() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentService service = paymentService(
                paymentRepository,
                managementRepository("CUSTOMER", new PaymentOrderStatus(1, "Unconfirmed")),
                billingService
        );

        when(billingService.getBillingDetails(1)).thenReturn(
                billingDetails(
                        1,
                        7,
                        BigDecimal.valueOf(10000.0),
                        BigDecimal.valueOf(1000.0),
                        BigDecimal.ZERO,
                        BigDecimal.valueOf(1000.0),
                        BigDecimal.valueOf(9000.0)
                )
        );
        when(paymentRepository.findByOrderID(1)).thenReturn(List.of());

        assertThrows(IllegalArgumentException.class, () ->
                service.submitPayment(1, 7, paymentRequest(BigDecimal.valueOf(10000.0))));
    }

    @Test
    void rejectsPrePromotionAmountAfterPromotionHasBeenApplied() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentService service = paymentService(
                paymentRepository,
                managementRepository("CUSTOMER", new PaymentOrderStatus(1, "Unconfirmed")),
                billingService
        );

        when(billingService.getBillingDetails(23)).thenReturn(
                billingDetails(
                        23,
                        7,
                        BigDecimal.valueOf(9240.0),
                        BigDecimal.valueOf(924.0),
                        BigDecimal.valueOf(831.60),
                        BigDecimal.valueOf(1755.60),
                        BigDecimal.valueOf(7484.40)
                )
        );
        when(paymentRepository.findByOrderID(23)).thenReturn(List.of());

        assertThrows(IllegalArgumentException.class, () ->
                service.submitPayment(23, 7, paymentRequest(BigDecimal.valueOf(8316.0))));
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
    void rejectedPaymentDoesNotReduceOutstandingAndAllowsRetry() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentService service = paymentService(
                paymentRepository,
                managementRepository("CUSTOMER", new PaymentOrderStatus(16, "Payment Failed")),
                billingService
        );
        Payment rejectedPayment = new Payment(4995.0, 14);
        rejectedPayment.setPaymentID(25);
        rejectedPayment.setPaymentStatus(PaymentStatus.REJECTED);

        when(billingService.getBillingDetails(14)).thenReturn(billingDetails(14, 7, BigDecimal.valueOf(4995.0)));
        when(paymentRepository.findByOrderID(14)).thenReturn(List.of(rejectedPayment));
        when(paymentRepository.save(Mockito.any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setPaymentID(26);
            return payment;
        });

        PaymentStatusResponse status = service.getPaymentStatus(14, 7);

        assertEquals(PaymentStatus.REJECTED, status.getStatus());
        assertEquals(BigDecimal.ZERO, status.getPaidAmount());
        assertEquals(BigDecimal.valueOf(4995.0), status.getOutstandingAmount());

        PaymentConfirmationResponse retry = service.submitPayment(14, 7, paymentRequest(BigDecimal.valueOf(4995.0)));
        assertEquals(26, retry.getPaymentID());
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
        ownedPayment.setPaymentMethod(PaymentMethod.CARD);
        ownedPayment.ensureRecordedPaymentFields();
        Payment otherPayment = new Payment(500.0, 2);
        otherPayment.setPaymentID(2);

        when(paymentRepository.findAllByOrderByProcessedAtDescPaymentIDDesc()).thenReturn(new ArrayList<>(List.of(ownedPayment, otherPayment)));
        when(paymentRepository.findByOrderID(1)).thenReturn(List.of(ownedPayment));
        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 7, BigDecimal.valueOf(900.0)));
        when(billingService.getBillingDetails(2)).thenReturn(billingDetails(2, 8, BigDecimal.valueOf(500.0)));

        List<PaymentHistoryResponse> history = service.getPaymentHistory(7);

        assertEquals(1, history.size());
        assertEquals(1, history.get(0).getPaymentID());
        assertEquals(1, history.get(0).getOrderID());
        assertEquals(PaymentStatus.PENDING, history.get(0).getPaymentStatus());
        assertEquals(PaymentMethod.CARD, history.get(0).getPaymentMethod());
        assertNotNull(history.get(0).getTransactionReference());
        assertNotNull(history.get(0).getProcessedAt());
    }

    @Test
    void returnsCustomerPaymentHistoryNewestFirstWithPaymentIdTieBreaker() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentService service = paymentService(
                paymentRepository,
                managementRepository("CUSTOMER", new PaymentOrderStatus(1, "Unconfirmed")),
                billingService
        );
        LocalDateTime newest = LocalDateTime.of(2026, 10, 6, 14, 0);
        LocalDateTime sameTime = LocalDateTime.of(2026, 10, 6, 13, 0);
        Payment newestPayment = new Payment(900.0, 1);
        newestPayment.setPaymentID(3);
        newestPayment.setProcessedAt(newest);
        Payment sameTimeHigherId = new Payment(900.0, 1);
        sameTimeHigherId.setPaymentID(2);
        sameTimeHigherId.setProcessedAt(sameTime);
        Payment sameTimeLowerId = new Payment(900.0, 1);
        sameTimeLowerId.setPaymentID(1);
        sameTimeLowerId.setProcessedAt(sameTime);

        when(paymentRepository.findAllByOrderByProcessedAtDescPaymentIDDesc()).thenReturn(List.of(
                newestPayment,
                sameTimeHigherId,
                sameTimeLowerId
        ));
        when(paymentRepository.findByOrderID(1)).thenReturn(List.of(newestPayment, sameTimeHigherId, sameTimeLowerId));
        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 7, BigDecimal.valueOf(900.0)));

        List<PaymentHistoryResponse> history = service.getPaymentHistory(7);

        assertEquals(List.of(3, 2, 1), history.stream().map(PaymentHistoryResponse::getPaymentID).toList());
    }

    @Test
    void returnsEmptyCustomerPaymentHistoryWhenNoPaymentsExist() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentService service = paymentService(
                paymentRepository,
                managementRepository("CUSTOMER", new PaymentOrderStatus(1, "Unconfirmed")),
                billingService
        );

        when(paymentRepository.findAllByOrderByProcessedAtDescPaymentIDDesc()).thenReturn(List.of());

        List<PaymentHistoryResponse> history = service.getPaymentHistory(7);

        assertTrue(history.isEmpty());
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
    void returnsReceiptForRecordedPayment() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = managementRepository("CUSTOMER", new PaymentOrderStatus(1, "Unconfirmed"));
        PaymentService service = paymentService(paymentRepository, managementRepository, billingService);
        Payment payment = new Payment(900.0, 1);
        payment.setPaymentID(12);
        payment.setPaymentMethod(PaymentMethod.CASH);
        payment.ensureRecordedPaymentFields();

        when(paymentRepository.findById(12)).thenReturn(Optional.of(payment));
        when(paymentRepository.findByOrderID(1)).thenReturn(List.of(payment));
        when(billingService.getBillingDetails(1)).thenReturn(
                billingDetails(1, 7, BigDecimal.valueOf(1000.0), BigDecimal.valueOf(100.0), BigDecimal.valueOf(900.0))
        );

        PaymentReceiptResponse receipt = service.getCustomerReceipt(7, 12, 1);

        assertEquals(12, receipt.getPaymentID());
        assertEquals(1, receipt.getOrderID());
        assertEquals(BigDecimal.valueOf(1000.0), receipt.getSubtotal());
        assertEquals(BigDecimal.valueOf(100.0), receipt.getDiscountAmount());
        assertEquals(BigDecimal.valueOf(900.0), receipt.getFinalPayableAmount());
        assertEquals(BigDecimal.valueOf(900.0), receipt.getAmountPaid());
        assertEquals(PaymentStatus.PENDING, receipt.getPaymentStatus());
        assertEquals(PaymentMethod.CASH, receipt.getPaymentMethod());
        assertNotNull(receipt.getTransactionReference());
        assertNotNull(receipt.getProcessedAt());
    }

    @Test
    void rejectsReceiptForUnknownPayment() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentService service = paymentService(
                paymentRepository,
                managementRepository("CUSTOMER", new PaymentOrderStatus(1, "Unconfirmed")),
                billingService
        );

        when(paymentRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.getCustomerReceipt(7, 99, 1));
    }

    @Test
    void rejectsReceiptWhenRequestedOrderDoesNotMatchPaymentOrder() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentService service = paymentService(
                paymentRepository,
                managementRepository("CUSTOMER", new PaymentOrderStatus(1, "Unconfirmed")),
                billingService
        );
        Payment payment = new Payment(900.0, 1);
        payment.setPaymentID(12);

        when(paymentRepository.findById(12)).thenReturn(Optional.of(payment));

        assertThrows(IllegalArgumentException.class, () -> service.getCustomerReceipt(7, 12, 2));
    }

    @Test
    void preventsCustomerFromViewingAnotherCustomersReceipt() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = managementRepository("CUSTOMER", new PaymentOrderStatus(1, "Unconfirmed"));
        PaymentService service = paymentService(paymentRepository, managementRepository, billingService);
        Payment payment = new Payment(900.0, 1);
        payment.setPaymentID(12);

        when(paymentRepository.findById(12)).thenReturn(Optional.of(payment));
        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 8, BigDecimal.valueOf(900.0)));

        assertThrows(AccessDeniedException.class, () -> service.getCustomerReceipt(7, 12, 1));
    }

    @Test
    void receiptAmountComesFromRecordedPaymentAndBillingComesFromBackend() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = managementRepository("CUSTOMER", new PaymentOrderStatus(1, "Unconfirmed"));
        PaymentService service = paymentService(paymentRepository, managementRepository, billingService);
        Payment payment = new Payment(800.0, 1);
        payment.setPaymentID(12);

        when(paymentRepository.findById(12)).thenReturn(Optional.of(payment));
        when(paymentRepository.findByOrderID(1)).thenReturn(List.of(payment));
        when(billingService.getBillingDetails(1)).thenReturn(
                billingDetails(1, 7, BigDecimal.valueOf(1000.0), BigDecimal.valueOf(100.0), BigDecimal.valueOf(900.0))
        );

        PaymentReceiptResponse receipt = service.getCustomerReceipt(7, 12, 1);

        assertEquals(BigDecimal.valueOf(800.0), receipt.getAmountPaid());
        assertEquals(BigDecimal.valueOf(1000.0), receipt.getSubtotal());
        assertEquals(BigDecimal.valueOf(100.0), receipt.getDiscountAmount());
        assertEquals(BigDecimal.valueOf(900.0), receipt.getFinalPayableAmount());
        assertEquals(PaymentStatus.PENDING, receipt.getPaymentStatus());
    }

    @Test
    void discountedOrderReceiptReturnsCorrectBillingAmounts() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = managementRepository("CUSTOMER", new PaymentOrderStatus(1, "Unconfirmed"));
        PaymentService service = paymentService(paymentRepository, managementRepository, billingService);
        Payment payment = new Payment(750.0, 1);
        payment.setPaymentID(12);

        when(paymentRepository.findById(12)).thenReturn(Optional.of(payment));
        when(paymentRepository.findByOrderID(1)).thenReturn(List.of(payment));
        when(billingService.getBillingDetails(1)).thenReturn(
                billingDetails(1, 7, BigDecimal.valueOf(1000.0), BigDecimal.valueOf(250.0), BigDecimal.valueOf(750.0))
        );

        PaymentReceiptResponse receipt = service.getCustomerReceipt(7, 12, 1);

        assertEquals(BigDecimal.valueOf(1000.0), receipt.getSubtotal());
        assertEquals(BigDecimal.valueOf(250.0), receipt.getDiscountAmount());
        assertEquals(BigDecimal.valueOf(750.0), receipt.getFinalPayableAmount());
        assertEquals(PaymentStatus.PENDING, receipt.getPaymentStatus());
    }

    @Test
    void customerCanDownloadPdfReceiptWithBackendValues() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = managementRepository("CUSTOMER", new PaymentOrderStatus(1, "Unconfirmed"));
        PaymentService service = paymentService(paymentRepository, managementRepository, billingService);
        Payment payment = new Payment(750.0, 1);
        payment.setPaymentID(12);
        payment.setPaymentMethod(PaymentMethod.CARD);
        payment.setTransactionReference("LLPAY-1-TESTREF");
        payment.ensureRecordedPaymentFields();

        when(paymentRepository.findById(12)).thenReturn(Optional.of(payment));
        when(paymentRepository.findByOrderID(1)).thenReturn(List.of(payment));
        when(billingService.getBillingDetails(1)).thenReturn(
                billingDetails(1, 7, BigDecimal.valueOf(1000.0), BigDecimal.valueOf(100.0), BigDecimal.valueOf(150.0), BigDecimal.valueOf(250.0), BigDecimal.valueOf(750.0))
        );

        byte[] pdf = service.getCustomerReceiptPdf(7, 12, 1);
        String pdfText = new String(pdf);

        assertTrue(pdfText.startsWith("%PDF-1.4"));
        assertTrue(pdfText.contains("LaundryLink"));
        assertTrue(pdfText.contains("Payment Receipt"));
        assertTrue(pdfText.contains("Order ID: 1"));
        assertTrue(pdfText.contains("Payment/transaction ID: 12 / LLPAY-1-TESTREF"));
        assertTrue(pdfText.contains("Final Amount: LKR 750.00"));
        assertTrue(pdfText.contains("Current payment status: PENDING"));
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

        assertEquals(PaymentStatus.PENDING, response.getStatus());
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
        payment.setPaymentMethod(PaymentMethod.CARD);
        payment.ensureRecordedPaymentFields();

        when(paymentRepository.findAll()).thenReturn(List.of(payment));
        when(paymentRepository.findByOrderID(1)).thenReturn(List.of(payment));
        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 7, BigDecimal.valueOf(1350.0)));

        List<PaymentRecordResponse> records = service.getPaymentRecords(11, null, null, PaymentStatus.PENDING, null, null);

        assertEquals(1, records.size());
        assertEquals(4, records.get(0).getPaymentID());
        assertEquals(PaymentStatus.PENDING, records.get(0).getPaymentStatus());
        assertEquals(PaymentMethod.CARD, records.get(0).getPaymentMethod());
        assertNotNull(records.get(0).getTransactionReference());
        assertNotNull(records.get(0).getProcessedAt());
    }

    @Test
    void managementRecordsIncludeUnpaidBillableOrdersWithoutCreatingPaymentRows() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = managementRepository("MANAGER", new PaymentOrderStatus(1, "Unconfirmed"));
        PaymentService service = paymentService(paymentRepository, managementRepository, billingService);

        when(paymentRepository.findAll()).thenReturn(List.of());
        when(paymentRepository.findByOrderID(2)).thenReturn(List.of());
        when(managementRepository.findBillableOrderSummaries()).thenReturn(List.of(
                new PaymentManagementOrderSummary(2, 8, "Ravi Perera", "Unconfirmed", null)
        ));
        when(billingService.getBillingDetails(2)).thenReturn(billingDetails(2, 8, BigDecimal.valueOf(2500.0)));

        List<PaymentRecordResponse> records = service.getPaymentRecords(11, null, null, null, null, null);

        assertEquals(1, records.size());
        assertNull(records.get(0).getPaymentID());
        assertEquals(2, records.get(0).getOrderID());
        assertEquals(8, records.get(0).getCustomerID());
        assertEquals("Ravi Perera", records.get(0).getCustomerName());
        assertEquals(PaymentStatus.UNPAID, records.get(0).getPaymentStatus());
        assertEquals(BigDecimal.valueOf(2500.0), records.get(0).getOutstandingAmount());
        Mockito.verify(paymentRepository, Mockito.never()).save(Mockito.any(Payment.class));
    }

    @Test
    void letsManagerSearchPaymentRecords() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = managementRepository("MANAGER", new PaymentOrderStatus(2, "Payment Verified"));
        PaymentService service = paymentService(paymentRepository, managementRepository, billingService);
        Payment payment = new Payment(1350.0, 1);
        payment.setPaymentID(4);
        payment.setPaymentStatus(PaymentStatus.PAID);

        when(paymentRepository.findAll()).thenReturn(List.of(payment));
        when(paymentRepository.findByOrderID(1)).thenReturn(List.of(payment));
        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 7, BigDecimal.valueOf(1350.0)));

        List<PaymentRecordResponse> records = service.getPaymentRecords(11, null, null, null, null, "verified");

        assertEquals(1, records.size());
        assertEquals(PaymentStatus.PAID, records.get(0).getPaymentStatus());
    }

    @Test
    void letsManagerFilterPaymentRecordsByMethod() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = managementRepository("MANAGER", new PaymentOrderStatus(1, "Unconfirmed"));
        PaymentService service = paymentService(paymentRepository, managementRepository, billingService);
        Payment cardPayment = new Payment(1350.0, 1);
        cardPayment.setPaymentID(4);
        cardPayment.setPaymentMethod(PaymentMethod.CARD);
        Payment cashPayment = new Payment(500.0, 2);
        cashPayment.setPaymentID(5);
        cashPayment.setPaymentMethod(PaymentMethod.CASH);

        when(paymentRepository.findAll()).thenReturn(List.of(cardPayment, cashPayment));
        when(paymentRepository.findByOrderID(1)).thenReturn(List.of(cardPayment));
        when(paymentRepository.findByOrderID(2)).thenReturn(List.of(cashPayment));
        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 7, BigDecimal.valueOf(1350.0)));
        when(billingService.getBillingDetails(2)).thenReturn(billingDetails(2, 8, BigDecimal.valueOf(500.0)));

        List<PaymentRecordResponse> records = service.getPaymentRecords(11, null, null, null, PaymentMethod.CARD, null);

        assertEquals(1, records.size());
        assertEquals(4, records.get(0).getPaymentID());
        assertEquals(PaymentMethod.CARD, records.get(0).getPaymentMethod());
    }

    @Test
    void letsManagerLoadSinglePaymentRecord() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = managementRepository("MANAGER", new PaymentOrderStatus(1, "Unconfirmed"));
        PaymentService service = paymentService(paymentRepository, managementRepository, billingService);
        Payment payment = new Payment(1350.0, 1);
        payment.setPaymentID(4);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        payment.setPaymentMethod(PaymentMethod.CARD);

        when(paymentRepository.findById(4)).thenReturn(Optional.of(payment));
        when(paymentRepository.findByOrderID(1)).thenReturn(List.of(payment));
        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 7, BigDecimal.valueOf(1350.0)));

        PaymentRecordResponse record = service.getPaymentRecord(11, 4);

        assertEquals(4, record.getPaymentID());
        assertEquals(1, record.getOrderID());
        assertEquals(PaymentMethod.CARD, record.getPaymentMethod());
    }

    @Test
    void preventsUnauthorizedUserFromViewingPaymentRecords() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = managementRepository("CUSTOMER", new PaymentOrderStatus(1, "Unconfirmed"));
        PaymentService service = paymentService(paymentRepository, managementRepository, billingService);

        assertThrows(AccessDeniedException.class, () ->
                service.getPaymentRecords(7, null, null, null, null, null));
    }

    @Test
    void managerApprovesFullyPaidPayment() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = Mockito.mock(PaymentManagementRepository.class);
        StatusService statusService = Mockito.mock(StatusService.class);
        PaymentAccessService accessService = new PaymentAccessService(managementRepository, userRepository(11));
        PaymentService service = new PaymentService(
                paymentRepository,
                refundRepository(),
                managementRepository,
                billingService,
                accessService,
                statusService,
                Mockito.mock(NotificationService.class)
        );
        Payment payment = new Payment(1350.0, 1);
        payment.setPaymentID(4);
        PaymentVerificationRequest request = new PaymentVerificationRequest();
        request.setApproved(true);
        Status unconfirmed = status(1, "Unconfirmed");
        Status verified = status(2, "Payment Verified");
        Status awaitingPickup = status(3, "Awaiting Pickup");

        when(managementRepository.findUserType(11)).thenReturn(Optional.of("MANAGER"));
        when(paymentRepository.findById(4)).thenReturn(Optional.of(payment));
        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 7, BigDecimal.valueOf(1350.0)));
        when(paymentRepository.findByOrderID(1)).thenReturn(List.of(payment));
        when(managementRepository.findOrderStatus(1)).thenReturn(Optional.of(new PaymentOrderStatus(1, "Unconfirmed")));
        when(managementRepository.updateOrderStatus(Mockito.any(), Mockito.any())).thenReturn(1);
        when(statusService.getByLabel("Payment Verified")).thenReturn(verified);
        when(statusService.getByLabel("Awaiting Pickup")).thenReturn(awaitingPickup);
        when(statusService.getById(1)).thenReturn(unconfirmed);
        when(paymentRepository.save(Mockito.any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentVerificationResponse response = service.verifyPayment(11, 4, request);

        // Approval verifies the payment, then releases the order for pickup straight away.
        assertEquals("Unconfirmed", response.getPreviousOrderStatus());
        assertEquals("Awaiting Pickup", response.getUpdatedOrderStatus());
        assertEquals(11, response.getVerifiedByUserID());
        assertNotNull(response.getVerificationDate());
        assertNotNull(response.getVerificationTime());
        InOrder statusChanges = Mockito.inOrder(managementRepository);
        statusChanges.verify(managementRepository).updateOrderStatus(1, 2);
        statusChanges.verify(managementRepository).updateOrderStatus(1, 3);
        // The two moves are two separate status updates. The database trigger
        // dbo.trg_order_status_log logs each one (Unconfirmed -> Payment Verified, then
        // Payment Verified -> Awaiting Pickup), so the service writes no log rows itself.
        Mockito.verify(managementRepository, Mockito.times(2)).updateOrderStatus(Mockito.any(), Mockito.any());
        assertEquals(PaymentStatus.PAID, payment.getPaymentStatus());
        Mockito.verify(paymentRepository).save(payment);
    }

    @Test
    void approvedOrderStillShowsVerifiedAfterMovingToAwaitingPickup() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = managementRepository("MANAGER", new PaymentOrderStatus(3, "Awaiting Pickup"));
        PaymentService service = paymentService(paymentRepository, managementRepository, billingService);
        Payment payment = new Payment(1350.0, 1);
        payment.setPaymentID(4);
        payment.setPaymentStatus(PaymentStatus.PAID);

        when(paymentRepository.findAll()).thenReturn(List.of(payment));
        when(paymentRepository.findByOrderID(1)).thenReturn(List.of(payment));
        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 7, BigDecimal.valueOf(1350.0)));
        when(managementRepository.wasPaymentVerified(1)).thenReturn(true);

        List<PaymentRecordResponse> records = service.getPaymentRecords(11, null, null, null, null, null);

        // The order has moved on, but the payment itself is approved, so the
        // payment page keeps showing it as paid (and hides Approve/Reject).
        assertEquals(PaymentStatus.PAID, records.get(0).getPaymentStatus());
    }

    @Test
    void rejectedPaymentDoesNotReleaseOrderForPickup() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = Mockito.mock(PaymentManagementRepository.class);
        StatusService statusService = Mockito.mock(StatusService.class);
        PaymentService service = new PaymentService(paymentRepository, refundRepository(), managementRepository, billingService,
                new PaymentAccessService(managementRepository, userRepository(11)), statusService, Mockito.mock(NotificationService.class));
        Payment payment = new Payment(1350.0, 1);
        payment.setPaymentID(4);
        Status unconfirmed = status(1, "Unconfirmed");
        Status failed = status(16, "Payment Failed");

        when(managementRepository.findUserType(11)).thenReturn(Optional.of("MANAGER"));
        when(paymentRepository.findById(4)).thenReturn(Optional.of(payment));
        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 7, BigDecimal.valueOf(1350.0)));
        when(paymentRepository.findByOrderID(1)).thenReturn(List.of(payment));
        when(managementRepository.findOrderStatus(1)).thenReturn(Optional.of(new PaymentOrderStatus(1, "Unconfirmed")));
        when(managementRepository.updateOrderStatus(Mockito.any(), Mockito.any())).thenReturn(1);
        when(statusService.getByLabel("Payment Failed")).thenReturn(failed);
        when(statusService.getById(1)).thenReturn(unconfirmed);
        when(paymentRepository.save(Mockito.any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.rejectPayment(11, 4);

        Mockito.verify(managementRepository).updateOrderStatus(1, 16);
        Mockito.verify(managementRepository, Mockito.never()).updateOrderStatus(1, 3);
        Mockito.verify(statusService, Mockito.never()).getByLabel("Awaiting Pickup");
        assertEquals(PaymentStatus.REJECTED, payment.getPaymentStatus());
        Mockito.verify(paymentRepository).save(payment);
    }

    @Test
    void managerRejectsFullyPaidPayment() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = Mockito.mock(PaymentManagementRepository.class);
        StatusService statusService = Mockito.mock(StatusService.class);
        PaymentService service = new PaymentService(
                paymentRepository,
                refundRepository(),
                managementRepository,
                billingService,
                new PaymentAccessService(managementRepository, userRepository(11)),
                statusService,
                Mockito.mock(NotificationService.class)
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
        when(managementRepository.updateOrderStatus(Mockito.any(), Mockito.any())).thenReturn(1);
        when(statusService.getByLabel("Payment Failed")).thenReturn(failed);
        when(statusService.getById(1)).thenReturn(unconfirmed);
        when(paymentRepository.save(Mockito.any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentVerificationResponse response = service.verifyPayment(11, 4, request);

        assertEquals("Payment Failed", response.getUpdatedOrderStatus());
        assertEquals(11, response.getVerifiedByUserID());
        assertNotNull(response.getVerificationDate());
        assertNotNull(response.getVerificationTime());
        Mockito.verify(managementRepository).updateOrderStatus(1, 16);
        assertEquals(PaymentStatus.REJECTED, payment.getPaymentStatus());
    }

    @Test
    void ownerApprovesPendingPayment() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = Mockito.mock(PaymentManagementRepository.class);
        StatusService statusService = Mockito.mock(StatusService.class);
        PaymentService service = new PaymentService(paymentRepository, refundRepository(), managementRepository, billingService,
                new PaymentAccessService(managementRepository, userRepository(11)), statusService, Mockito.mock(NotificationService.class));
        Payment payment = new Payment(1350.0, 1);
        payment.setPaymentID(4);
        Status verified = status(2, "Payment Verified");
        Status awaitingPickup = status(3, "Awaiting Pickup");

        when(managementRepository.findUserType(11)).thenReturn(Optional.of("OWNER"));
        when(paymentRepository.findById(4)).thenReturn(Optional.of(payment));
        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 7, BigDecimal.valueOf(1350.0)));
        when(paymentRepository.findByOrderID(1)).thenReturn(List.of(payment));
        when(managementRepository.findOrderStatus(1)).thenReturn(Optional.of(new PaymentOrderStatus(1, "Unconfirmed")));
        when(managementRepository.updateOrderStatus(Mockito.any(), Mockito.any())).thenReturn(1);
        when(statusService.getByLabel("Payment Verified")).thenReturn(verified);
        when(statusService.getByLabel("Awaiting Pickup")).thenReturn(awaitingPickup);
        when(paymentRepository.save(Mockito.any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentVerificationResponse response = service.approvePayment(11, 4);

        assertEquals("Awaiting Pickup", response.getUpdatedOrderStatus());
        assertEquals(PaymentStatus.PAID, payment.getPaymentStatus());
        Mockito.verify(managementRepository).updateOrderStatus(1, 2);
        Mockito.verify(managementRepository).updateOrderStatus(1, 3);
    }

    @Test
    void ownerRejectsPendingPayment() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = Mockito.mock(PaymentManagementRepository.class);
        StatusService statusService = Mockito.mock(StatusService.class);
        PaymentService service = new PaymentService(paymentRepository, refundRepository(), managementRepository, billingService,
                new PaymentAccessService(managementRepository, userRepository(11)), statusService, Mockito.mock(NotificationService.class));
        Payment payment = new Payment(1350.0, 1);
        payment.setPaymentID(4);
        Status failed = status(16, "Payment Failed");

        when(managementRepository.findUserType(11)).thenReturn(Optional.of("OWNER"));
        when(paymentRepository.findById(4)).thenReturn(Optional.of(payment));
        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 7, BigDecimal.valueOf(1350.0)));
        when(paymentRepository.findByOrderID(1)).thenReturn(List.of(payment));
        when(managementRepository.findOrderStatus(1)).thenReturn(Optional.of(new PaymentOrderStatus(1, "Unconfirmed")));
        when(managementRepository.updateOrderStatus(Mockito.any(), Mockito.any())).thenReturn(1);
        when(statusService.getByLabel("Payment Failed")).thenReturn(failed);
        when(paymentRepository.save(Mockito.any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentVerificationResponse response = service.rejectPayment(11, 4);

        assertEquals("Payment Failed", response.getUpdatedOrderStatus());
        assertEquals(PaymentStatus.REJECTED, payment.getPaymentStatus());
        Mockito.verify(managementRepository).updateOrderStatus(1, 16);
        Mockito.verify(managementRepository, Mockito.never()).updateOrderStatus(1, 3);
    }

    @Test
    void customerCannotApproveOrRejectPayment() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = managementRepository("CUSTOMER", new PaymentOrderStatus(1, "Unconfirmed"));
        PaymentService service = paymentService(paymentRepository, managementRepository, billingService);

        assertThrows(AccessDeniedException.class, () -> service.approvePayment(7, 4));
        assertThrows(AccessDeniedException.class, () -> service.rejectPayment(7, 4));
        Mockito.verify(paymentRepository, Mockito.never()).save(Mockito.any(Payment.class));
    }

    @Test
    void customerRequestsRefundForOwnVerifiedPayment() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        RefundRepository refundRepository = refundRepository();
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = managementRepository("CUSTOMER", new PaymentOrderStatus(3, "Awaiting Pickup"));
        PaymentService service = paymentService(paymentRepository, refundRepository, managementRepository, billingService);
        Payment payment = new Payment(1350.0, 1);
        payment.setPaymentID(4);
        payment.setPaymentStatus(PaymentStatus.VERIFIED);

        when(paymentRepository.findById(4)).thenReturn(Optional.of(payment));
        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 7, BigDecimal.valueOf(1350.0)));
        when(refundRepository.save(Mockito.any(Refund.class))).thenAnswer(invocation -> {
            Refund refund = invocation.getArgument(0);
            refund.setRefundID(30);
            refund.ensureRecordedRefundFields();
            return refund;
        });

        RefundResponse response = service.requestRefund(7, 4, refundRequest("Customer requested refund"));

        assertEquals(30, response.getRefundID());
        assertEquals(4, response.getPaymentID());
        assertEquals(1, response.getOrderID());
        assertEquals(BigDecimal.valueOf(1350.0), response.getRefundAmount());
        assertEquals(RefundStatus.REQUESTED, response.getRefundStatus());
        assertEquals(7, response.getRequestedBy());
        assertNotNull(response.getRequestedAt());
        assertNull(response.getProcessedBy());
        assertNull(response.getRefundedAt());
        assertEquals(PaymentStatus.VERIFIED, payment.getPaymentStatus());
        Mockito.verify(paymentRepository, Mockito.never()).save(payment);
        Mockito.verify(managementRepository, Mockito.never()).updateOrderStatus(Mockito.any(), Mockito.any());
    }

    @Test
    void preventsCustomerFromRequestingRefundForAnotherCustomersPayment() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        RefundRepository refundRepository = refundRepository();
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = managementRepository("CUSTOMER", new PaymentOrderStatus(3, "Awaiting Pickup"));
        PaymentService service = paymentService(paymentRepository, refundRepository, managementRepository, billingService);
        Payment payment = new Payment(1350.0, 1);
        payment.setPaymentID(4);
        payment.setPaymentStatus(PaymentStatus.VERIFIED);

        when(paymentRepository.findById(4)).thenReturn(Optional.of(payment));
        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 8, BigDecimal.valueOf(1350.0)));

        assertThrows(AccessDeniedException.class, () ->
                service.requestRefund(7, 4, refundRequest("Not my payment")));
        Mockito.verify(refundRepository, Mockito.never()).save(Mockito.any(Refund.class));
    }

    @Test
    void preventsRefundRequestForPaymentAwaitingVerification() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        RefundRepository refundRepository = refundRepository();
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = managementRepository("CUSTOMER", new PaymentOrderStatus(1, "Unconfirmed"));
        PaymentService service = paymentService(paymentRepository, refundRepository, managementRepository, billingService);
        Payment payment = new Payment(1350.0, 1);
        payment.setPaymentID(4);
        payment.setPaymentStatus(PaymentStatus.PENDING);

        when(paymentRepository.findById(4)).thenReturn(Optional.of(payment));
        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 7, BigDecimal.valueOf(1350.0)));

        assertThrows(IllegalStateException.class, () ->
                service.requestRefund(7, 4, refundRequest("Not eligible")));
        Mockito.verify(refundRepository, Mockito.never()).save(Mockito.any(Refund.class));
    }

    @Test
    void preventsRefundRequestForRejectedPayment() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        RefundRepository refundRepository = refundRepository();
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = managementRepository("CUSTOMER", new PaymentOrderStatus(16, "Payment Failed"));
        PaymentService service = paymentService(paymentRepository, refundRepository, managementRepository, billingService);
        Payment payment = new Payment(1350.0, 1);
        payment.setPaymentID(4);
        payment.setPaymentStatus(PaymentStatus.REJECTED);

        when(paymentRepository.findById(4)).thenReturn(Optional.of(payment));
        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 7, BigDecimal.valueOf(1350.0)));

        assertThrows(IllegalStateException.class, () ->
                service.requestRefund(7, 4, refundRequest("Rejected payment")));
        Mockito.verify(refundRepository, Mockito.never()).save(Mockito.any(Refund.class));
    }

    @Test
    void preventsRefundRequestForRefundedPayment() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        RefundRepository refundRepository = refundRepository();
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = managementRepository("CUSTOMER", new PaymentOrderStatus(3, "Awaiting Pickup"));
        PaymentService service = paymentService(paymentRepository, refundRepository, managementRepository, billingService);
        Payment payment = new Payment(1350.0, 1);
        payment.setPaymentID(4);
        payment.setPaymentStatus(PaymentStatus.REFUNDED);

        when(paymentRepository.findById(4)).thenReturn(Optional.of(payment));
        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 7, BigDecimal.valueOf(1350.0)));

        assertThrows(IllegalStateException.class, () ->
                service.requestRefund(7, 4, refundRequest("Already refunded")));
        Mockito.verify(refundRepository, Mockito.never()).save(Mockito.any(Refund.class));
    }

    @Test
    void preventsRefundRequestForUnknownPayment() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        RefundRepository refundRepository = refundRepository();
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = managementRepository("CUSTOMER", new PaymentOrderStatus(1, "Unconfirmed"));
        PaymentService service = paymentService(paymentRepository, refundRepository, managementRepository, billingService);

        when(paymentRepository.findById(404)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () ->
                service.requestRefund(7, 404, refundRequest("Missing payment")));
    }

    @Test
    void preventsDuplicateRefundRequest() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        RefundRepository refundRepository = refundRepository();
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = managementRepository("CUSTOMER", new PaymentOrderStatus(3, "Awaiting Pickup"));
        PaymentService service = paymentService(paymentRepository, refundRepository, managementRepository, billingService);
        Payment payment = new Payment(1350.0, 1);
        payment.setPaymentID(4);
        payment.setPaymentStatus(PaymentStatus.VERIFIED);

        when(paymentRepository.findById(4)).thenReturn(Optional.of(payment));
        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 7, BigDecimal.valueOf(1350.0)));
        when(refundRepository.existsByPaymentID(4)).thenReturn(true);

        assertThrows(IllegalStateException.class, () ->
                service.requestRefund(7, 4, refundRequest("Already requested")));
        Mockito.verify(refundRepository, Mockito.never()).save(Mockito.any(Refund.class));
    }

    @Test
    void preventsRefundRequestWithBlankReason() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        RefundRepository refundRepository = refundRepository();
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = managementRepository("CUSTOMER", new PaymentOrderStatus(3, "Awaiting Pickup"));
        PaymentService service = paymentService(paymentRepository, refundRepository, managementRepository, billingService);
        Payment payment = new Payment(1350.0, 1);
        payment.setPaymentID(4);
        payment.setPaymentStatus(PaymentStatus.VERIFIED);

        when(paymentRepository.findById(4)).thenReturn(Optional.of(payment));
        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 7, BigDecimal.valueOf(1350.0)));

        assertThrows(IllegalArgumentException.class, () ->
                service.requestRefund(7, 4, refundRequest("   ")));
        Mockito.verify(refundRepository, Mockito.never()).save(Mockito.any(Refund.class));
    }

    @Test
    void ownerApprovesRequestedRefundAndMarksPaymentRefunded() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        RefundRepository refundRepository = refundRepository();
        BillingService billingService = Mockito.mock(BillingService.class);
        NotificationService notifications = Mockito.mock(NotificationService.class);
        PaymentManagementRepository managementRepository = managementRepository("MANAGER", new PaymentOrderStatus(3, "Awaiting Pickup"));
        PaymentService service = paymentService(paymentRepository, refundRepository, managementRepository, billingService, notifications);
        Payment payment = new Payment(1350.0, 1);
        payment.setPaymentID(4);
        payment.setPaymentStatus(PaymentStatus.VERIFIED);
        Refund refund = requestedRefund(4, BigDecimal.valueOf(1350.0), 7);

        when(paymentRepository.findById(4)).thenReturn(Optional.of(payment));
        when(refundRepository.findByPaymentID(4)).thenReturn(Optional.of(refund));
        when(refundRepository.save(Mockito.any(Refund.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.save(Mockito.any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 7, BigDecimal.valueOf(1350.0)));

        RefundResponse response = service.approveRefund(11, 4);

        assertEquals(RefundStatus.REFUNDED, response.getRefundStatus());
        assertEquals(BigDecimal.valueOf(1350.0), response.getRefundAmount());
        assertEquals(11, response.getProcessedBy());
        assertNotNull(response.getProcessedAt());
        assertNotNull(response.getRefundedAt());
        assertEquals(PaymentStatus.REFUNDED, payment.getPaymentStatus());
        Mockito.verify(paymentRepository).save(payment);
        Mockito.verify(managementRepository, Mockito.never()).updateOrderStatus(Mockito.any(), Mockito.any());
        Mockito.verify(notifications).notifyUser(
                Mockito.eq(7),
                Mockito.eq("PAYMENT"),
                Mockito.eq("Refund Approved"),
                Mockito.eq("Your refund request for Order #1 has been approved. LKR 1350 has been refunded."),
                Mockito.eq("/html/customer/receipt.html?paymentID=4&orderID=1"),
                Mockito.eq("REFUND"),
                Mockito.eq(30)
        );
    }

    @Test
    void ownerRejectsRequestedRefundAndKeepsPaymentVerified() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        RefundRepository refundRepository = refundRepository();
        BillingService billingService = Mockito.mock(BillingService.class);
        NotificationService notifications = Mockito.mock(NotificationService.class);
        PaymentManagementRepository managementRepository = managementRepository("MANAGER", new PaymentOrderStatus(3, "Awaiting Pickup"));
        PaymentService service = paymentService(paymentRepository, refundRepository, managementRepository, billingService, notifications);
        Payment payment = new Payment(1350.0, 1);
        payment.setPaymentID(4);
        payment.setPaymentStatus(PaymentStatus.VERIFIED);
        Refund refund = requestedRefund(4, BigDecimal.valueOf(1350.0), 7);

        when(paymentRepository.findById(4)).thenReturn(Optional.of(payment));
        when(refundRepository.findByPaymentID(4)).thenReturn(Optional.of(refund));
        when(refundRepository.save(Mockito.any(Refund.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 7, BigDecimal.valueOf(1350.0)));

        RefundResponse response = service.rejectRefund(11, 4);

        assertEquals(RefundStatus.REJECTED, response.getRefundStatus());
        assertEquals(11, response.getProcessedBy());
        assertNotNull(response.getProcessedAt());
        assertNull(response.getRefundedAt());
        assertEquals(PaymentStatus.VERIFIED, payment.getPaymentStatus());
        Mockito.verify(paymentRepository, Mockito.never()).save(payment);
        Mockito.verify(notifications).notifyUser(
                Mockito.eq(7),
                Mockito.eq("PAYMENT"),
                Mockito.eq("Refund Request Rejected"),
                Mockito.eq("Your refund request for Order #1 was not approved."),
                Mockito.eq("/html/customer/receipt.html?paymentID=4&orderID=1"),
                Mockito.eq("REFUND"),
                Mockito.eq(30)
        );
    }

    @Test
    void preventsApprovedRefundFromBeingApprovedAgain() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        RefundRepository refundRepository = refundRepository();
        BillingService billingService = Mockito.mock(BillingService.class);
        NotificationService notifications = Mockito.mock(NotificationService.class);
        PaymentManagementRepository managementRepository = managementRepository("MANAGER", new PaymentOrderStatus(3, "Awaiting Pickup"));
        PaymentService service = paymentService(paymentRepository, refundRepository, managementRepository, billingService, notifications);
        Payment payment = new Payment(1350.0, 1);
        payment.setPaymentID(4);
        payment.setPaymentStatus(PaymentStatus.REFUNDED);
        Refund refund = requestedRefund(4, BigDecimal.valueOf(1350.0), 7);
        refund.setRefundStatus(RefundStatus.REFUNDED);
        refund.setProcessedAt(LocalDateTime.now());
        refund.setRefundedAt(refund.getProcessedAt());

        when(paymentRepository.findById(4)).thenReturn(Optional.of(payment));
        when(refundRepository.findByPaymentID(4)).thenReturn(Optional.of(refund));

        assertThrows(IllegalStateException.class, () -> service.approveRefund(11, 4));
        Mockito.verifyNoInteractions(notifications);
    }

    @Test
    void failedRefundRejectionDoesNotCreateNotification() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        RefundRepository refundRepository = refundRepository();
        BillingService billingService = Mockito.mock(BillingService.class);
        NotificationService notifications = Mockito.mock(NotificationService.class);
        PaymentManagementRepository managementRepository = managementRepository("MANAGER", new PaymentOrderStatus(3, "Awaiting Pickup"));
        PaymentService service = paymentService(paymentRepository, refundRepository, managementRepository, billingService, notifications);
        Payment payment = new Payment(1350.0, 1);
        payment.setPaymentID(4);
        payment.setPaymentStatus(PaymentStatus.VERIFIED);
        Refund refund = requestedRefund(4, BigDecimal.valueOf(1350.0), 7);
        refund.setRefundStatus(RefundStatus.REJECTED);
        refund.setProcessedAt(LocalDateTime.now());

        when(paymentRepository.findById(4)).thenReturn(Optional.of(payment));
        when(refundRepository.findByPaymentID(4)).thenReturn(Optional.of(refund));

        assertThrows(IllegalStateException.class, () -> service.rejectRefund(11, 4));
        Mockito.verifyNoInteractions(notifications);
    }

    @Test
    void managementRecordsShowPendingRefundRequestDetails() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        RefundRepository refundRepository = refundRepository();
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = managementRepository("MANAGER", new PaymentOrderStatus(3, "Awaiting Pickup"));
        PaymentService service = paymentService(paymentRepository, refundRepository, managementRepository, billingService);
        Payment payment = new Payment(1350.0, 1);
        payment.setPaymentID(4);
        payment.setPaymentStatus(PaymentStatus.VERIFIED);
        Refund refund = requestedRefund(4, BigDecimal.valueOf(1350.0), 7);

        when(paymentRepository.findAll()).thenReturn(List.of(payment));
        when(paymentRepository.findByOrderID(1)).thenReturn(List.of(payment));
        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 7, BigDecimal.valueOf(1350.0)));
        when(refundRepository.findByPaymentID(4)).thenReturn(Optional.of(refund));

        List<PaymentRecordResponse> records = service.getPaymentRecords(11, null, null, null, null, null);

        assertEquals(1, records.size());
        assertEquals(PaymentStatus.VERIFIED, records.get(0).getPaymentStatus());
        assertEquals(RefundStatus.REQUESTED, records.get(0).getRefundStatus());
        assertEquals(BigDecimal.valueOf(1350.0), records.get(0).getRefundAmount());
        assertEquals("Customer requested refund", records.get(0).getRefundReason());
        assertEquals(BigDecimal.ZERO, records.get(0).getOutstandingAmount());
    }

    @Test
    void customerHistoryAndReceiptShowRefundStatuses() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        RefundRepository refundRepository = refundRepository();
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = managementRepository("CUSTOMER", new PaymentOrderStatus(3, "Awaiting Pickup"));
        PaymentService service = paymentService(paymentRepository, refundRepository, managementRepository, billingService);
        Payment payment = new Payment(1350.0, 1);
        payment.setPaymentID(4);
        payment.setPaymentStatus(PaymentStatus.VERIFIED);
        Refund refund = requestedRefund(4, BigDecimal.valueOf(1350.0), 7);

        when(paymentRepository.findAllByOrderByProcessedAtDescPaymentIDDesc()).thenReturn(List.of(payment));
        when(paymentRepository.findById(4)).thenReturn(Optional.of(payment));
        when(paymentRepository.findByOrderID(1)).thenReturn(List.of(payment));
        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 7, BigDecimal.valueOf(1350.0)));
        when(refundRepository.findByPaymentID(4)).thenReturn(Optional.of(refund));

        List<PaymentHistoryResponse> history = service.getPaymentHistory(7);
        PaymentReceiptResponse receipt = service.getCustomerReceipt(7, 4, 1);

        assertEquals(PaymentStatus.VERIFIED, history.get(0).getPaymentStatus());
        assertEquals(RefundStatus.REQUESTED, history.get(0).getRefundStatus());
        assertEquals(BigDecimal.valueOf(1350.0), history.get(0).getRefundAmount());
        assertNotNull(history.get(0).getRefundRequestedAt());
        assertEquals(PaymentStatus.VERIFIED, receipt.getPaymentStatus());
        assertEquals(RefundStatus.REQUESTED, receipt.getRefundStatus());
        assertEquals(BigDecimal.valueOf(1350.0), receipt.getRefundAmount());
        assertNotNull(receipt.getRefundRequestedAt());
    }

    @Test
    void customerHistoryAndReceiptShowApprovedRefundStatus() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        RefundRepository refundRepository = refundRepository();
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = managementRepository("CUSTOMER", new PaymentOrderStatus(3, "Awaiting Pickup"));
        PaymentService service = paymentService(paymentRepository, refundRepository, managementRepository, billingService);
        Payment payment = new Payment(1350.0, 1);
        payment.setPaymentID(4);
        payment.setPaymentStatus(PaymentStatus.REFUNDED);
        Refund refund = requestedRefund(4, BigDecimal.valueOf(1350.0), 7);
        refund.setRefundStatus(RefundStatus.REFUNDED);
        refund.setProcessedAt(LocalDateTime.now());
        refund.setRefundedAt(refund.getProcessedAt());

        when(paymentRepository.findAllByOrderByProcessedAtDescPaymentIDDesc()).thenReturn(List.of(payment));
        when(paymentRepository.findById(4)).thenReturn(Optional.of(payment));
        when(paymentRepository.findByOrderID(1)).thenReturn(List.of(payment));
        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 7, BigDecimal.valueOf(1350.0)));
        when(refundRepository.findByPaymentID(4)).thenReturn(Optional.of(refund));

        List<PaymentHistoryResponse> history = service.getPaymentHistory(7);
        PaymentReceiptResponse receipt = service.getCustomerReceipt(7, 4, 1);

        assertEquals(PaymentStatus.REFUNDED, history.get(0).getPaymentStatus());
        assertEquals(RefundStatus.REFUNDED, history.get(0).getRefundStatus());
        assertNotNull(history.get(0).getRefundedAt());
        assertEquals(PaymentStatus.REFUNDED, receipt.getPaymentStatus());
        assertEquals(RefundStatus.REFUNDED, receipt.getRefundStatus());
        assertNotNull(receipt.getRefundedAt());
    }

    @Test
    void customerHistoryAndReceiptShowRejectedRefundStatusWhilePaymentStaysVerified() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        RefundRepository refundRepository = refundRepository();
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = managementRepository("CUSTOMER", new PaymentOrderStatus(3, "Awaiting Pickup"));
        PaymentService service = paymentService(paymentRepository, refundRepository, managementRepository, billingService);
        Payment payment = new Payment(1350.0, 1);
        payment.setPaymentID(4);
        payment.setPaymentStatus(PaymentStatus.VERIFIED);
        Refund refund = requestedRefund(4, BigDecimal.valueOf(1350.0), 7);
        refund.setRefundStatus(RefundStatus.REJECTED);
        refund.setProcessedAt(LocalDateTime.now());

        when(paymentRepository.findAllByOrderByProcessedAtDescPaymentIDDesc()).thenReturn(List.of(payment));
        when(paymentRepository.findById(4)).thenReturn(Optional.of(payment));
        when(paymentRepository.findByOrderID(1)).thenReturn(List.of(payment));
        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 7, BigDecimal.valueOf(1350.0)));
        when(refundRepository.findByPaymentID(4)).thenReturn(Optional.of(refund));

        List<PaymentHistoryResponse> history = service.getPaymentHistory(7);
        PaymentReceiptResponse receipt = service.getCustomerReceipt(7, 4, 1);

        assertEquals(PaymentStatus.VERIFIED, history.get(0).getPaymentStatus());
        assertEquals(RefundStatus.REJECTED, history.get(0).getRefundStatus());
        assertNotNull(history.get(0).getRefundProcessedAt());
        assertEquals(PaymentStatus.VERIFIED, receipt.getPaymentStatus());
        assertEquals(RefundStatus.REJECTED, receipt.getRefundStatus());
        assertNotNull(receipt.getRefundProcessedAt());
    }

    // ---- Cancelled orders: they cannot be paid, and a payment decision never revives them ----

    @Test
    void preventsPaymentForCancelledOrder() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = managementRepository("CUSTOMER", new PaymentOrderStatus(20, "Cancelled"));
        PaymentService service = paymentService(paymentRepository, managementRepository, billingService);

        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 7, BigDecimal.valueOf(1350.0)));
        when(paymentRepository.findByOrderID(1)).thenReturn(List.of());

        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                service.submitPayment(1, 7, paymentRequest(BigDecimal.valueOf(1350.0))));

        assertEquals("This order has been cancelled and can no longer be paid", error.getMessage());
        Mockito.verify(paymentRepository, Mockito.never()).save(Mockito.any(Payment.class));
    }

    @Test
    void preventsManagerFromRecordingPaymentForCancelledOrder() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = managementRepository("MANAGER", new PaymentOrderStatus(20, "Cancelled"));
        PaymentService service = paymentService(paymentRepository, managementRepository, billingService);

        assertThrows(IllegalArgumentException.class, () ->
                service.createPayment(11, paymentCrudRequest(BigDecimal.valueOf(250.0), 1)));

        Mockito.verify(paymentRepository, Mockito.never()).save(Mockito.any(Payment.class));
    }

    @Test
    void approvingPendingPaymentDoesNotReviveCancelledOrder() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = managementRepository("MANAGER", new PaymentOrderStatus(20, "Cancelled"));
        PaymentService service = paymentService(paymentRepository, managementRepository, billingService);
        Payment payment = new Payment(1350.0, 1);
        payment.setPaymentID(4);

        when(paymentRepository.findById(4)).thenReturn(Optional.of(payment));
        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 7, BigDecimal.valueOf(1350.0)));
        when(paymentRepository.findByOrderID(1)).thenReturn(List.of(payment));

        assertThrows(IllegalStateException.class, () -> service.approvePayment(11, 4));

        // Nothing changed: the payment is still pending and the order was never touched.
        assertEquals(PaymentStatus.PENDING, payment.getPaymentStatus());
        Mockito.verify(paymentRepository, Mockito.never()).save(Mockito.any(Payment.class));
        Mockito.verify(managementRepository, Mockito.never()).updateOrderStatus(Mockito.any(), Mockito.any());
    }

    @Test
    void rejectingPendingPaymentLeavesCancelledOrderCancelled() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = managementRepository("MANAGER", new PaymentOrderStatus(20, "Cancelled"));
        PaymentService service = paymentService(paymentRepository, managementRepository, billingService);
        Payment payment = new Payment(1350.0, 1);
        payment.setPaymentID(4);

        when(paymentRepository.findById(4)).thenReturn(Optional.of(payment));
        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 7, BigDecimal.valueOf(1350.0)));
        when(paymentRepository.findByOrderID(1)).thenReturn(List.of(payment));
        when(paymentRepository.save(Mockito.any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentVerificationResponse response = service.rejectPayment(11, 4);

        // The payment is closed, but the order is not moved to "Payment Failed".
        assertEquals(PaymentStatus.REJECTED, payment.getPaymentStatus());
        assertEquals("Cancelled", response.getPreviousOrderStatus());
        assertEquals("Cancelled", response.getUpdatedOrderStatus());
        Mockito.verify(managementRepository, Mockito.never()).updateOrderStatus(Mockito.any(), Mockito.any());
    }

    @Test
    void approvalFailsWhenOrderIsCancelledBeforeTheStatusUpdate() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = Mockito.mock(PaymentManagementRepository.class);
        StatusService statusService = Mockito.mock(StatusService.class);
        PaymentService service = new PaymentService(paymentRepository, refundRepository(), managementRepository, billingService,
                new PaymentAccessService(managementRepository, userRepository(11)), statusService, Mockito.mock(NotificationService.class));
        Payment payment = new Payment(1350.0, 1);
        payment.setPaymentID(4);
        Status verified = status(2, "Payment Verified");

        when(managementRepository.findUserType(11)).thenReturn(Optional.of("MANAGER"));
        when(paymentRepository.findById(4)).thenReturn(Optional.of(payment));
        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 7, BigDecimal.valueOf(1350.0)));
        when(paymentRepository.findByOrderID(1)).thenReturn(List.of(payment));
        // The order looked open when it was read, but the guarded update then changes no rows,
        // which is what happens when the customer cancels in between.
        when(managementRepository.findOrderStatus(1)).thenReturn(Optional.of(new PaymentOrderStatus(1, "Unconfirmed")));
        when(managementRepository.updateOrderStatus(1, 2)).thenReturn(0);
        when(statusService.getByLabel("Payment Verified")).thenReturn(verified);
        when(paymentRepository.save(Mockito.any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThrows(IllegalStateException.class, () -> service.approvePayment(11, 4));

        // The order is never released for pickup.
        Mockito.verify(managementRepository, Mockito.never()).updateOrderStatus(1, 3);
    }

    private PaymentRequest paymentRequest(BigDecimal amount) {
        PaymentRequest request = new PaymentRequest();
        request.setPaymentMethod(PaymentMethod.CARD);
        request.setAmount(amount);
        return request;
    }

    private RefundRequest refundRequest(String reason) {
        RefundRequest request = new RefundRequest();
        request.setRefundReason(reason);
        return request;
    }

    private Refund requestedRefund(Integer paymentID, BigDecimal amount, Integer requestedBy) {
        Refund refund = new Refund(paymentID, amount, "Customer requested refund", requestedBy);
        refund.setRefundID(30);
        refund.ensureRecordedRefundFields();
        return refund;
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

    private BillingDetails billingDetails(
            Integer orderID,
            Integer userID,
            BigDecimal subtotal,
            BigDecimal discountAmount,
            BigDecimal finalPayableAmount
    ) {
        return new BillingDetails(orderID, userID, List.of(), subtotal, discountAmount, finalPayableAmount);
    }

    private BillingDetails billingDetails(
            Integer orderID,
            Integer userID,
            BigDecimal subtotal,
            BigDecimal automaticBulkDiscount,
            BigDecimal promotionDiscount,
            BigDecimal totalDiscount,
            BigDecimal finalPayableAmount
    ) {
        return new BillingDetails(
                orderID,
                userID,
                List.of(),
                subtotal,
                automaticBulkDiscount,
                promotionDiscount,
                totalDiscount,
                finalPayableAmount
        );
    }

    private PaymentService paymentService(
            PaymentRepository paymentRepository,
            PaymentManagementRepository managementRepository,
            BillingService billingService
    ) {
        return paymentService(paymentRepository, refundRepository(), managementRepository, billingService);
    }

    private PaymentService paymentService(
            PaymentRepository paymentRepository,
            RefundRepository refundRepository,
            PaymentManagementRepository managementRepository,
            BillingService billingService
    ) {
        return paymentService(
                paymentRepository,
                refundRepository,
                managementRepository,
                billingService,
                Mockito.mock(NotificationService.class)
        );
    }

    private PaymentService paymentService(
            PaymentRepository paymentRepository,
            RefundRepository refundRepository,
            PaymentManagementRepository managementRepository,
            BillingService billingService,
            NotificationService notificationService
    ) {
        return new PaymentService(
                paymentRepository,
                refundRepository,
                managementRepository,
                billingService,
                new PaymentAccessService(managementRepository, userRepository(7)),
                Mockito.mock(StatusService.class),
                notificationService
        );
    }

    private RefundRepository refundRepository() {
        RefundRepository repository = Mockito.mock(RefundRepository.class);
        when(repository.findByPaymentID(Mockito.anyInt())).thenReturn(Optional.empty());
        when(repository.existsByPaymentID(Mockito.anyInt())).thenReturn(false);
        return repository;
    }

    private PaymentManagementRepository managementRepository(String userType, PaymentOrderStatus orderStatus) {
        PaymentManagementRepository repository = Mockito.mock(PaymentManagementRepository.class);
        when(repository.findUserType(Mockito.anyInt())).thenReturn(Optional.of(userType));
        when(repository.findOrderStatus(Mockito.anyInt())).thenReturn(Optional.of(orderStatus));
        when(repository.findOrderSummary(Mockito.anyInt())).thenAnswer(invocation -> Optional.of(
                new PaymentManagementOrderSummary(
                        invocation.getArgument(0),
                        7,
                        "Test Customer",
                        orderStatus.getStatusLabel(),
                        null
                )
        ));
        when(repository.findBillableOrderSummaries()).thenReturn(List.of());
        // One row updated, as for any order that is not cancelled.
        when(repository.updateOrderStatus(Mockito.any(), Mockito.any())).thenReturn(1);
        return repository;
    }

    private UserRepository userRepository(Integer userID) {
        UserRepository repository = Mockito.mock(UserRepository.class);
        User user = new User();
        user.setUserID(userID);
        when(repository.findByEmailIgnoreCase(Mockito.anyString())).thenReturn(Optional.of(user));
        return repository;
    }

    private Status status(Integer statusID, String statusLabel) {
        Status status = Mockito.mock(Status.class);
        when(status.getStatusID()).thenReturn(statusID);
        when(status.getStatusLabel()).thenReturn(statusLabel);
        return status;
    }
}
