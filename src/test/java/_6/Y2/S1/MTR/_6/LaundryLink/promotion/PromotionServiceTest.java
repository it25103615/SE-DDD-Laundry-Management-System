package _6.Y2.S1.MTR._6.LaundryLink.promotion;

import _6.Y2.S1.MTR._6.LaundryLink.billing.BillingDetails;
import _6.Y2.S1.MTR._6.LaundryLink.billing.BillingLine;
import _6.Y2.S1.MTR._6.LaundryLink.billing.BillingRepository;
import _6.Y2.S1.MTR._6.LaundryLink.billing.BillingService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PromotionServiceTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-09-15T00:00:00Z"),
            ZoneOffset.UTC
    );

    @Test
    void validatesActivePromotionWithinDateRange() {
        TestPromotionRepository promotionRepository = new TestPromotionRepository(validPercentagePromotion());
        PromotionService service = newService(promotionRepository, BigDecimal.valueOf(1000.0));

        PromotionValidationResponse response = service.validatePromotion("SAVE10", 1);

        assertTrue(response.isValid());
        assertEquals(BigDecimal.valueOf(100.00).setScale(2), response.getDiscountAmount());
        assertEquals(BigDecimal.valueOf(900.00).setScale(2), response.getFinalPayableAmount());
    }

    @Test
    void rejectsExpiredPromotion() {
        Promotion expired = validPercentagePromotion();
        expired.setValidTo(LocalDate.of(2026, 1, 31));
        TestPromotionRepository promotionRepository = new TestPromotionRepository(expired);
        PromotionService service = newService(promotionRepository, BigDecimal.valueOf(1000.0));

        PromotionValidationResponse response = service.validatePromotion("SAVE10", 1);

        assertFalse(response.isValid());
        assertEquals("Promotion is expired or not yet active", response.getMessage());
        assertEquals(BigDecimal.ZERO, response.getDiscountAmount());
    }

    @Test
    void rejectsInactivePromotion() {
        Promotion inactive = validPercentagePromotion();
        inactive.setActive(false);
        TestPromotionRepository promotionRepository = new TestPromotionRepository(inactive);
        PromotionService service = newService(promotionRepository, BigDecimal.valueOf(1000.0));

        PromotionValidationResponse response = service.validatePromotion("SAVE10", 1);

        assertFalse(response.isValid());
        assertEquals("Promotion is inactive", response.getMessage());
    }

    @Test
    void rejectsInvalidDiscountValues() {
        PromotionRequest request = validRequest();
        request.setDiscountValue(BigDecimal.valueOf(101));
        PromotionService service = newService(new TestPromotionRepository(), BigDecimal.valueOf(1000.0));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.createPromotion(request)
        );

        assertEquals("Percentage discount cannot exceed 100", exception.getMessage());
    }

    @Test
    void appliesPromotionToEligibleOrder() {
        TestPromotionRepository promotionRepository = new TestPromotionRepository(validPercentagePromotion());
        PromotionService service = newService(promotionRepository, BigDecimal.valueOf(1000.0));

        PromotionApplicationResponse response = service.applyPromotion("save10", 1);

        assertEquals(1, response.getOrderID());
        assertEquals(1, response.getPromotionID());
        assertEquals("SAVE10", response.getPromotionCode());
        assertEquals(BigDecimal.valueOf(100.00).setScale(2), response.getDiscountAmount());
        assertEquals(BigDecimal.valueOf(900.00).setScale(2), response.getFinalPayableAmount());
        assertEquals(BigDecimal.valueOf(100.00).setScale(2), promotionRepository.appliedDiscountAmount);
    }

    @Test
    void fixedDiscountCannotMakeFinalAmountNegative() {
        Promotion promotion = validFixedPromotion(BigDecimal.valueOf(200.0));
        TestPromotionRepository promotionRepository = new TestPromotionRepository(promotion);
        PromotionService service = newService(promotionRepository, BigDecimal.valueOf(100.0));

        PromotionValidationResponse response = service.validatePromotion("FIXED", 1);

        assertTrue(response.isValid());
        assertEquals(BigDecimal.valueOf(100.0), response.getDiscountAmount());
        assertEquals(BigDecimal.ZERO.setScale(1), response.getFinalPayableAmount());
    }

    @Test
    void rejectsOrderThatDoesNotMeetMinimumAmount() {
        Promotion promotion = validPercentagePromotion();
        promotion.setMinimumOrderAmount(BigDecimal.valueOf(1500.0));
        TestPromotionRepository promotionRepository = new TestPromotionRepository(promotion);
        PromotionService service = newService(promotionRepository, BigDecimal.valueOf(1000.0));

        PromotionValidationResponse response = service.validatePromotion("SAVE10", 1);

        assertFalse(response.isValid());
        assertEquals("Order does not meet the minimum amount for this promotion", response.getMessage());
    }

    private PromotionService newService(TestPromotionRepository promotionRepository, BigDecimal subtotal) {
        BillingService billingService = new BillingService(new TestBillingRepository(subtotal));
        return new PromotionService(promotionRepository, billingService, FIXED_CLOCK);
    }

    private Promotion validPercentagePromotion() {
        return new Promotion(
                1,
                "SAVE10",
                "Save ten percent",
                DiscountType.PERCENTAGE,
                BigDecimal.valueOf(10.0),
                BigDecimal.ZERO,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                true
        );
    }

    private Promotion validFixedPromotion(BigDecimal discountValue) {
        return new Promotion(
                1,
                "FIXED",
                "Fixed discount",
                DiscountType.FIXED_AMOUNT,
                discountValue,
                BigDecimal.ZERO,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                true
        );
    }

    private PromotionRequest validRequest() {
        PromotionRequest request = new PromotionRequest();
        request.setPromotionCode("SAVE10");
        request.setPromotionName("Save ten percent");
        request.setDiscountType(DiscountType.PERCENTAGE);
        request.setDiscountValue(BigDecimal.valueOf(10.0));
        request.setMinimumOrderAmount(BigDecimal.ZERO);
        request.setValidFrom(LocalDate.of(2026, 1, 1));
        request.setValidTo(LocalDate.of(2026, 12, 31));
        request.setActive(true);
        return request;
    }

    private static class TestPromotionRepository implements PromotionRepository {
        private final List<Promotion> promotions = new ArrayList<>();
        private Integer appliedOrderID;
        private Integer appliedPromotionID;
        private BigDecimal appliedDiscountAmount;

        TestPromotionRepository(Promotion... promotions) {
            this.promotions.addAll(List.of(promotions));
        }

        @Override
        public Promotion save(Promotion promotion) {
            promotion.setPromotionID(promotions.size() + 1);
            promotions.add(promotion);
            return promotion;
        }

        @Override
        public Promotion update(Promotion promotion) {
            promotions.removeIf(existing -> existing.getPromotionID().equals(promotion.getPromotionID()));
            promotions.add(promotion);
            return promotion;
        }

        @Override
        public Optional<Promotion> findById(Integer promotionID) {
            return promotions.stream()
                    .filter(promotion -> promotion.getPromotionID().equals(promotionID))
                    .findFirst();
        }

        @Override
        public Optional<Promotion> findByCode(String promotionCode) {
            return promotions.stream()
                    .filter(promotion -> promotion.getPromotionCode().equalsIgnoreCase(promotionCode))
                    .findFirst();
        }

        @Override
        public List<Promotion> findAll() {
            return promotions;
        }

        @Override
        public List<Promotion> findAvailable(LocalDate today) {
            return promotions.stream()
                    .filter(Promotion::isActive)
                    .filter(promotion -> !today.isBefore(promotion.getValidFrom()))
                    .filter(promotion -> !today.isAfter(promotion.getValidTo()))
                    .toList();
        }

        @Override
        public void updateActive(Integer promotionID, boolean active) {
            findById(promotionID).orElseThrow().setActive(active);
        }

        @Override
        public void applyPromotion(Integer orderID, Integer promotionID, BigDecimal discountAmount) {
            this.appliedOrderID = orderID;
            this.appliedPromotionID = promotionID;
            this.appliedDiscountAmount = discountAmount;
        }
    }

    private static class TestBillingRepository implements BillingRepository {
        private final BigDecimal subtotal;

        TestBillingRepository(BigDecimal subtotal) {
            this.subtotal = subtotal;
        }

        @Override
        public Optional<Integer> findOrderUserID(Integer orderID) {
            return Optional.of(7);
        }

        @Override
        public List<BillingLine> findOrderLines(Integer orderID) {
            return List.of(new BillingLine(1, 1, 1, 1, subtotal));
        }

        @Override
        public BigDecimal findAppliedDiscountAmount(Integer orderID) {
            return BigDecimal.ZERO;
        }
    }
}
