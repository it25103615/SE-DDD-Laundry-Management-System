package _6.Y2.S1.MTR._6.LaundryLink.promotion;

import _6.Y2.S1.MTR._6.LaundryLink.billing.BillingDetails;
import _6.Y2.S1.MTR._6.LaundryLink.billing.BillingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class PromotionService {
    private final PromotionRepository promotionRepository;
    private final BillingService billingService;
    private final Clock clock;

    public PromotionService(PromotionRepository promotionRepository, BillingService billingService) {
        this(promotionRepository, billingService, Clock.systemDefaultZone());
    }

    PromotionService(PromotionRepository promotionRepository, BillingService billingService, Clock clock) {
        this.promotionRepository = promotionRepository;
        this.billingService = billingService;
        this.clock = clock;
    }

    public Promotion createPromotion(PromotionRequest request) {
        Promotion promotion = toPromotion(null, request);
        validatePromotionDefinition(promotion);
        return promotionRepository.save(promotion);
    }

    public Promotion updatePromotion(Integer promotionID, PromotionRequest request) {
        promotionRepository.findById(promotionID).orElseThrow();
        Promotion promotion = toPromotion(promotionID, request);
        validatePromotionDefinition(promotion);
        return promotionRepository.update(promotion);
    }

    public Promotion setPromotionActive(Integer promotionID, boolean active) {
        Promotion promotion = promotionRepository.findById(promotionID).orElseThrow();
        promotionRepository.updateActive(promotionID, active);
        promotion.setActive(active);
        return promotion;
    }

    public Promotion getPromotion(Integer promotionID) {
        return promotionRepository.findById(promotionID).orElseThrow();
    }

    public List<Promotion> listPromotions() {
        return promotionRepository.findAll();
    }

    public List<Promotion> listAvailablePromotions() {
        return promotionRepository.findAvailable(today());
    }

    public PromotionValidationResponse validatePromotion(String promotionCode, Integer orderID) {
        Promotion promotion = findByCode(promotionCode);
        BillingDetails billingDetails = billingService.getBillingDetails(orderID);
        return validatePromotionForSubtotal(promotion, billingDetails.getSubtotal());
    }

    @Transactional
    public PromotionApplicationResponse applyPromotion(String promotionCode, Integer orderID) {
        Promotion promotion = findByCode(promotionCode);
        BillingDetails billingDetails = billingService.getBillingDetails(orderID);
        PromotionValidationResponse validation = validatePromotionForSubtotal(promotion, billingDetails.getSubtotal());

        if (!validation.isValid()) {
            throw new IllegalArgumentException(validation.getMessage());
        }

        promotionRepository.applyPromotion(orderID, promotion.getPromotionID(), validation.getDiscountAmount());
        return new PromotionApplicationResponse(
                orderID,
                promotion.getPromotionID(),
                promotion.getPromotionCode(),
                validation.getDiscountAmount(),
                validation.getFinalPayableAmount(),
                "Promotion applied successfully"
        );
    }

    public BigDecimal calculateDiscount(Promotion promotion, BigDecimal subtotal) {
        validatePromotionDefinition(promotion);
        if (subtotal == null || subtotal.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Subtotal must not be negative");
        }

        BigDecimal discountAmount;
        if (promotion.getDiscountType() == DiscountType.PERCENTAGE) {
            discountAmount = subtotal
                    .multiply(promotion.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else {
            discountAmount = promotion.getDiscountValue();
        }

        if (discountAmount.compareTo(subtotal) > 0) {
            return subtotal;
        }
        return discountAmount;
    }

    private PromotionValidationResponse validatePromotionForSubtotal(Promotion promotion, BigDecimal subtotal) {
        try {
            validatePromotionDefinition(promotion);
        } catch (IllegalArgumentException ex) {
            return invalid(ex.getMessage(), subtotal);
        }

        if (!promotion.isActive()) {
            return invalid("Promotion is inactive", subtotal);
        }

        LocalDate today = today();
        if (today.isBefore(promotion.getValidFrom()) || today.isAfter(promotion.getValidTo())) {
            return invalid("Promotion is expired or not yet active", subtotal);
        }

        if (subtotal.compareTo(promotion.getMinimumOrderAmount()) < 0) {
            return invalid("Order does not meet the minimum amount for this promotion", subtotal);
        }

        BigDecimal discountAmount = calculateDiscount(promotion, subtotal);
        BigDecimal finalPayableAmount = subtotal.subtract(discountAmount);
        if (finalPayableAmount.compareTo(BigDecimal.ZERO) < 0) {
            finalPayableAmount = BigDecimal.ZERO;
        }

        return new PromotionValidationResponse(true, "Promotion is valid", discountAmount, finalPayableAmount);
    }

    private Promotion findByCode(String promotionCode) {
        if (promotionCode == null || promotionCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Promotion code is required");
        }
        return promotionRepository.findByCode(normalizeCode(promotionCode)).orElseThrow(NoSuchElementException::new);
    }

    private Promotion toPromotion(Integer promotionID, PromotionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Promotion request is required");
        }

        BigDecimal minimumOrderAmount = request.getMinimumOrderAmount() == null
                ? BigDecimal.ZERO
                : request.getMinimumOrderAmount();

        return new Promotion(
                promotionID,
                normalizeCode(request.getPromotionCode()),
                request.getPromotionName() == null ? null : request.getPromotionName().trim(),
                request.getDiscountType(),
                request.getDiscountValue(),
                minimumOrderAmount,
                request.getValidFrom(),
                request.getValidTo(),
                request.getActive() == null || request.getActive()
        );
    }

    private void validatePromotionDefinition(Promotion promotion) {
        if (promotion.getPromotionCode() == null || promotion.getPromotionCode().isBlank()) {
            throw new IllegalArgumentException("Promotion code is required");
        }

        if (promotion.getPromotionName() == null || promotion.getPromotionName().isBlank()) {
            throw new IllegalArgumentException("Promotion name is required");
        }

        if (promotion.getDiscountType() == null) {
            throw new IllegalArgumentException("Discount type is required");
        }

        if (promotion.getDiscountValue() == null || promotion.getDiscountValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Discount value must be greater than zero");
        }

        if (promotion.getDiscountType() == DiscountType.PERCENTAGE
                && promotion.getDiscountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Percentage discount cannot exceed 100");
        }

        if (promotion.getMinimumOrderAmount() == null || promotion.getMinimumOrderAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Minimum order amount must not be negative");
        }

        if (promotion.getValidFrom() == null || promotion.getValidTo() == null) {
            throw new IllegalArgumentException("Promotion valid date range is required");
        }

        if (promotion.getValidTo().isBefore(promotion.getValidFrom())) {
            throw new IllegalArgumentException("Promotion end date cannot be before start date");
        }
    }

    private PromotionValidationResponse invalid(String message, BigDecimal subtotal) {
        BigDecimal safeSubtotal = subtotal == null ? BigDecimal.ZERO : subtotal;
        return new PromotionValidationResponse(false, message, BigDecimal.ZERO, safeSubtotal);
    }

    private String normalizeCode(String promotionCode) {
        return promotionCode == null ? null : promotionCode.trim().toUpperCase();
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
