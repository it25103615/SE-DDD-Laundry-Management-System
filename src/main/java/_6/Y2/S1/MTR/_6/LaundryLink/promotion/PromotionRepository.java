package _6.Y2.S1.MTR._6.LaundryLink.promotion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PromotionRepository {
    Promotion save(Promotion promotion);

    Promotion update(Promotion promotion);

    Optional<Promotion> findById(Integer promotionID);

    Optional<Promotion> findByCode(String promotionCode);

    List<Promotion> findAll();

    List<Promotion> findAvailable(LocalDate today);

    void updateActive(Integer promotionID, boolean active);

    void applyPromotion(Integer orderID, Integer promotionID, BigDecimal discountAmount);
}
