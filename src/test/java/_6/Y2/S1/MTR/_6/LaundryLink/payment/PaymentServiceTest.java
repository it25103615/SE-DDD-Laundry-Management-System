package _6.Y2.S1.MTR._6.LaundryLink.payment;

import _6.Y2.S1.MTR._6.LaundryLink.billing.BillingDetails;
import _6.Y2.S1.MTR._6.LaundryLink.billing.BillingService;
import _6.Y2.S1.MTR._6.LaundryLink.status.Status;
import _6.Y2.S1.MTR._6.LaundryLink.status.StatusService;
import _6.Y2.S1.MTR._6.LaundryLink.user.User;
import _6.Y2.S1.MTR._6.LaundryLink.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
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

        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 7, BigDecimal.valueOf(1350.0)));
        when(paymentRepository.findByOrderID(1)).thenReturn(List.of());
        when(paymentRepository.save(Mockito.any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setPaymentID(10);
            return payment;
        });

        PaymentConfirmationResponse response = service.submitPayment(1, 7, request);

        assertEquals(10, response.getPaymentID());
        assertEquals(PaymentStatus.PAID, response.getStatus());
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
        assertEquals(PaymentStatus.PAID, persisted.getPaymentStatus());
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
        assertEquals(PaymentStatus.PAID, response.getPaymentStatus());
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
        assertEquals(PaymentStatus.PAID, response.getPaymentStatus());
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

        when(paymentRepository.findAll()).thenReturn(new ArrayList<>(List.of(ownedPayment, otherPayment)));
        when(paymentRepository.findByOrderID(1)).thenReturn(List.of(ownedPayment));
        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 7, BigDecimal.valueOf(900.0)));
        when(billingService.getBillingDetails(2)).thenReturn(billingDetails(2, 8, BigDecimal.valueOf(500.0)));

        List<PaymentHistoryResponse> history = service.getPaymentHistory(7);

        assertEquals(1, history.size());
        assertEquals(1, history.get(0).getPaymentID());
        assertEquals(1, history.get(0).getOrderID());
        assertEquals(PaymentStatus.PAID, history.get(0).getPaymentStatus());
        assertEquals(PaymentMethod.CARD, history.get(0).getPaymentMethod());
        assertNotNull(history.get(0).getTransactionReference());
        assertNotNull(history.get(0).getProcessedAt());
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

        when(paymentRepository.findAll()).thenReturn(List.of());

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
        assertEquals(PaymentStatus.PAID, receipt.getPaymentStatus());
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
        assertEquals(PaymentStatus.UNPAID, receipt.getPaymentStatus());
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
        assertEquals(PaymentStatus.PAID, receipt.getPaymentStatus());
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
        payment.setPaymentMethod(PaymentMethod.CARD);
        payment.ensureRecordedPaymentFields();

        when(paymentRepository.findAll()).thenReturn(List.of(payment));
        when(paymentRepository.findByOrderID(1)).thenReturn(List.of(payment));
        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 7, BigDecimal.valueOf(1350.0)));

        List<PaymentRecordResponse> records = service.getPaymentRecords(11, null, null, PaymentStatus.PAID, null, null);

        assertEquals(1, records.size());
        assertEquals(4, records.get(0).getPaymentID());
        assertEquals(PaymentStatus.PAID, records.get(0).getPaymentStatus());
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

        when(paymentRepository.findAll()).thenReturn(List.of(payment));
        when(paymentRepository.findByOrderID(1)).thenReturn(List.of(payment));
        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 7, BigDecimal.valueOf(1350.0)));

        List<PaymentRecordResponse> records = service.getPaymentRecords(11, null, null, null, null, "verified");

        assertEquals(1, records.size());
        assertEquals(PaymentStatus.VERIFIED, records.get(0).getPaymentStatus());
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
                managementRepository,
                billingService,
                accessService,
                statusService
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
        assertEquals(PaymentStatus.VERIFIED, payment.getPaymentStatus());
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

        when(paymentRepository.findAll()).thenReturn(List.of(payment));
        when(paymentRepository.findByOrderID(1)).thenReturn(List.of(payment));
        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 7, BigDecimal.valueOf(1350.0)));
        when(managementRepository.wasPaymentVerified(1)).thenReturn(true);

        List<PaymentRecordResponse> records = service.getPaymentRecords(11, null, null, null, null, null);

        // The order has moved on, but its log shows the payment was verified, so the
        // payment page keeps showing it as verified (and hides Approve/Reject).
        assertEquals(PaymentStatus.VERIFIED, records.get(0).getPaymentStatus());
    }

    @Test
    void rejectedPaymentDoesNotReleaseOrderForPickup() {
        PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        PaymentManagementRepository managementRepository = Mockito.mock(PaymentManagementRepository.class);
        StatusService statusService = Mockito.mock(StatusService.class);
        PaymentService service = new PaymentService(paymentRepository, managementRepository, billingService,
                new PaymentAccessService(managementRepository, userRepository(11)), statusService);
        Payment payment = new Payment(1350.0, 1);
        payment.setPaymentID(4);
        Status unconfirmed = status(1, "Unconfirmed");
        Status failed = status(16, "Payment Failed");

        when(managementRepository.findUserType(11)).thenReturn(Optional.of("MANAGER"));
        when(paymentRepository.findById(4)).thenReturn(Optional.of(payment));
        when(billingService.getBillingDetails(1)).thenReturn(billingDetails(1, 7, BigDecimal.valueOf(1350.0)));
        when(paymentRepository.findByOrderID(1)).thenReturn(List.of(payment));
        when(managementRepository.findOrderStatus(1)).thenReturn(Optional.of(new PaymentOrderStatus(1, "Unconfirmed")));
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
                managementRepository,
                billingService,
                new PaymentAccessService(managementRepository, userRepository(11)),
                statusService
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
        when(paymentRepository.save(Mockito.any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentVerificationResponse response = service.verifyPayment(11, 4, request);

        assertEquals("Payment Failed", response.getUpdatedOrderStatus());
        assertEquals(11, response.getVerifiedByUserID());
        assertNotNull(response.getVerificationDate());
        assertNotNull(response.getVerificationTime());
        Mockito.verify(managementRepository).updateOrderStatus(1, 16);
        assertEquals(PaymentStatus.REJECTED, payment.getPaymentStatus());
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
        return new PaymentService(
                paymentRepository,
                managementRepository,
                billingService,
                new PaymentAccessService(managementRepository, userRepository(7)),
                Mockito.mock(StatusService.class)
        );
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
