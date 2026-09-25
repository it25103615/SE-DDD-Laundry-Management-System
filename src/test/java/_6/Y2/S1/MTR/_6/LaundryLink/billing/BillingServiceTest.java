package _6.Y2.S1.MTR._6.LaundryLink.billing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BillingServiceTest {

    @Test
    void calculatesLineTotalsSubtotalAndFinalPayableAmount() {
        BillingRepository repository = new TestBillingRepository(
                Optional.of(7),
                List.of(
                        new BillingLine(1, 1, 1, 5, BigDecimal.valueOf(180.0)),
                        new BillingLine(2, 2, 2, 3, BigDecimal.valueOf(150.0))
                ),
                BigDecimal.ZERO
        );
        BillingService service = new BillingService(repository);

        BillingDetails details = service.getBillingDetails(10);

        assertEquals(BigDecimal.valueOf(900.0), details.getLines().get(0).getLineTotal());
        assertEquals(BigDecimal.valueOf(450.0), details.getLines().get(1).getLineTotal());
        assertEquals(BigDecimal.valueOf(1350.0), details.getSubtotal());
        assertEquals(BigDecimal.ZERO, details.getDiscountAmount());
        assertEquals(BigDecimal.valueOf(1350.0), details.getFinalPayableAmount());
    }

    @Test
    void throwsWhenOrderDoesNotExist() {
        BillingService service = new BillingService(new TestBillingRepository(Optional.empty(), List.of(), BigDecimal.ZERO));

        assertThrows(NoSuchElementException.class, () -> service.getBillingDetails(99));
    }

    @Test
    void throwsWhenOrderHasNoLines() {
        BillingService service = new BillingService(new TestBillingRepository(Optional.of(3), List.of(), BigDecimal.ZERO));

        assertThrows(NoSuchElementException.class, () -> service.getBillingDetails(3));
    }

    @Test
    void reflectsAppliedDiscountInFinalPayableAmount() {
        BillingRepository repository = new TestBillingRepository(
                Optional.of(7),
                List.of(new BillingLine(1, 1, 1, 5, BigDecimal.valueOf(180.0))),
                BigDecimal.valueOf(150.0)
        );
        BillingService service = new BillingService(repository);

        BillingDetails details = service.getBillingDetails(10);

        assertEquals(BigDecimal.valueOf(900.0), details.getSubtotal());
        assertEquals(BigDecimal.valueOf(150.0), details.getDiscountAmount());
        assertEquals(BigDecimal.valueOf(750.0), details.getFinalPayableAmount());
    }

    @Test
    void preventsFinalPayableAmountFromBecomingNegative() {
        BillingRepository repository = new TestBillingRepository(
                Optional.of(7),
                List.of(new BillingLine(1, 1, 1, 1, BigDecimal.valueOf(100.0))),
                BigDecimal.valueOf(150.0)
        );
        BillingService service = new BillingService(repository);

        BillingDetails details = service.getBillingDetails(10);

        assertEquals(BigDecimal.valueOf(100.0), details.getDiscountAmount());
        assertEquals(BigDecimal.ZERO.setScale(1), details.getFinalPayableAmount());
    }

    private static class TestBillingRepository implements BillingRepository {
        private final Optional<Integer> userID;
        private final List<BillingLine> lines;
        private final BigDecimal discountAmount;

        TestBillingRepository(Optional<Integer> userID, List<BillingLine> lines, BigDecimal discountAmount) {
            this.userID = userID;
            this.lines = lines;
            this.discountAmount = discountAmount;
        }

        @Override
        public Optional<Integer> findOrderUserID(Integer orderID) {
            return userID;
        }

        @Override
        public List<BillingLine> findOrderLines(Integer orderID) {
            return lines;
        }

        @Override
        public BigDecimal findAppliedDiscountAmount(Integer orderID) {
            return discountAmount;
        }
    }
}
