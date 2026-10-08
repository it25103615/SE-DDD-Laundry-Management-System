package _6.Y2.S1.MTR._6.LaundryLink.promotion;

import _6.Y2.S1.MTR._6.LaundryLink.billing.BillingDetails;
import _6.Y2.S1.MTR._6.LaundryLink.billing.BillingService;
import _6.Y2.S1.MTR._6.LaundryLink.payment.PaymentAccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
public class PromotionService {
    private final PromotionRepository promotionRepository;
    private final BillingService billingService;
    private final PaymentAccessService paymentAccessService;
    private final Clock clock;

    @Autowired
    public PromotionService(
            PromotionRepository promotionRepository,
            BillingService billingService,
            PaymentAccessService paymentAccessService
    ) {
        this(promotionRepository, billingService, paymentAccessService, Clock.systemDefaultZone());
    }

    PromotionService(PromotionRepository promotionRepository, BillingService billingService, Clock clock) {
        this(promotionRepository, billingService, null, clock);
    }

    PromotionService(
            PromotionRepository promotionRepository,
            BillingService billingService,
            PaymentAccessService paymentAccessService,
            Clock clock
    ) {
        this.promotionRepository = promotionRepository;
        this.billingService = billingService;
        this.paymentAccessService = paymentAccessService;
        this.clock = clock;
    }

    public Promotion createPromotion(PromotionRequest request) {
        Promotion promotion = toPromotion(null, request);
        validatePromotionDefinition(promotion);
        ensurePromotionCodeAvailable(promotion);
        return promotionRepository.save(promotion);
    }

    public Promotion updatePromotion(Integer promotionID, PromotionRequest request) {
        promotionRepository.findById(promotionID).orElseThrow();
        Promotion promotion = toPromotion(promotionID, request);
        validatePromotionDefinition(promotion);
        ensurePromotionCodeAvailable(promotion);
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
        return validatePromotionForBilling(promotion, billingDetails);
    }

    public PromotionValidationResponse validatePromotionForCustomer(String promotionCode, Integer orderID, Integer customerID) {
        Promotion promotion = findByCode(promotionCode);
        BillingDetails billingDetails = billingService.getBillingDetails(orderID);
        verifyOrderBelongsToCustomer(billingDetails, customerID);
        return validatePromotionForBilling(promotion, billingDetails);
    }

    @Transactional
    public PromotionApplicationResponse applyPromotion(String promotionCode, Integer orderID) {
        Promotion promotion = findByCode(promotionCode);
        BillingDetails billingDetails = billingService.getBillingDetails(orderID);
        PromotionValidationResponse validation = validatePromotionForBilling(promotion, billingDetails);

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

    @Transactional
    public PromotionApplicationResponse applyPromotionForCustomer(String promotionCode, Integer orderID, Integer customerID) {
        Promotion promotion = findByCode(promotionCode);
        BillingDetails billingDetails = billingService.getBillingDetails(orderID);
        verifyOrderBelongsToCustomer(billingDetails, customerID);
        PromotionValidationResponse validation = validatePromotionForBilling(promotion, billingDetails);

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

        // Strategy pattern: the promotion's discount type supplies the calculation, so there is
        // no if/else on the type here. A new discount type only needs a new DiscountStrategy.
        DiscountStrategy strategy = promotion.getDiscountType().strategy();
        BigDecimal discountAmount = strategy.calculate(subtotal, promotion.getDiscountValue());

        if (discountAmount.compareTo(subtotal) > 0) {
            return subtotal;
        }
        return discountAmount;
    }

    private PromotionValidationResponse validatePromotionForSubtotal(Promotion promotion, BigDecimal subtotal) {
        return validatePromotionForAmount(promotion, subtotal, subtotal);
    }

    private PromotionValidationResponse validatePromotionForBilling(Promotion promotion, BillingDetails billingDetails) {
        return validatePromotionForAmount(
                promotion,
                billingDetails.getSubtotal(),
                billingDetails.getAmountAfterBulkDiscount()
        );
    }

    private PromotionValidationResponse validatePromotionForAmount(
            Promotion promotion,
            BigDecimal subtotal,
            BigDecimal eligibleAmount
    ) {
        try {
            validatePromotionDefinition(promotion);
        } catch (IllegalArgumentException ex) {
            return invalid(ex.getMessage(), eligibleAmount);
        }

        if (!promotion.isActive()) {
            return invalid("Promotion is inactive", eligibleAmount);
        }

        LocalDate today = today();
        if (today.isBefore(promotion.getValidFrom()) || today.isAfter(promotion.getValidTo())) {
            return invalid("Promotion is expired or not yet active", eligibleAmount);
        }

        if (subtotal.compareTo(promotion.getMinimumOrderAmount()) < 0) {
            return invalid("Order does not meet the minimum amount for this promotion", eligibleAmount);
        }

        BigDecimal discountAmount = calculateDiscount(promotion, eligibleAmount);
        BigDecimal finalPayableAmount = eligibleAmount.subtract(discountAmount);
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

    private void verifyOrderBelongsToCustomer(BillingDetails billingDetails, Integer customerID) {
        if (paymentAccessService != null) {
            paymentAccessService.verifyOrderBelongsToCustomer(billingDetails, customerID);
            return;
        }

        if (!billingDetails.getUserID().equals(customerID)) {
            throw new org.springframework.security.access.AccessDeniedException("Order does not belong to the current customer");
        }
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

    private void ensurePromotionCodeAvailable(Promotion promotion) {
        promotionRepository.findByCode(promotion.getPromotionCode())
                .filter(existing -> !Objects.equals(existing.getPromotionID(), promotion.getPromotionID()))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Promotion code already exists.");
                });
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
