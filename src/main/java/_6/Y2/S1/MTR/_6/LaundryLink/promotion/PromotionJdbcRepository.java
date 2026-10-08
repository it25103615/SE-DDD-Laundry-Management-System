package _6.Y2.S1.MTR._6.LaundryLink.promotion;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class PromotionJdbcRepository implements PromotionRepository {
    private final JdbcTemplate jdbcTemplate;

    public PromotionJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Promotion save(Promotion promotion) {
        Integer promotionID = jdbcTemplate.queryForObject(
                """
                        INSERT INTO promotions(
                            promotionCode, promotionName, discountType, discountValue,
                            minimumOrderAmount, validFrom, validTo, active
                        )
                        OUTPUT INSERTED.promotionID
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                Integer.class,
                promotion.getPromotionCode(),
                promotion.getPromotionName(),
                promotion.getDiscountType().name(),
                promotion.getDiscountValue(),
                promotion.getMinimumOrderAmount(),
                Date.valueOf(promotion.getValidFrom()),
                Date.valueOf(promotion.getValidTo()),
                promotion.isActive()
        );
        promotion.setPromotionID(promotionID);
        return promotion;
    }

    @Override
    public Promotion update(Promotion promotion) {
        int updated = jdbcTemplate.update(
                """
                        UPDATE promotions
                        SET promotionCode = ?,
                            promotionName = ?,
                            discountType = ?,
                            discountValue = ?,
                            minimumOrderAmount = ?,
                            validFrom = ?,
                            validTo = ?,
                            active = ?
                        WHERE promotionID = ?
                        """,
                promotion.getPromotionCode(),
                promotion.getPromotionName(),
                promotion.getDiscountType().name(),
                promotion.getDiscountValue(),
                promotion.getMinimumOrderAmount(),
                Date.valueOf(promotion.getValidFrom()),
                Date.valueOf(promotion.getValidTo()),
                promotion.isActive(),
                promotion.getPromotionID()
        );

        if (updated == 0) {
            throw new EmptyResultDataAccessException(1);
        }
        return promotion;
    }

    @Override
    public Optional<Promotion> findById(Integer promotionID) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT * FROM promotions WHERE promotionID = ?",
                    mapper(),
                    promotionID
            ));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Promotion> findByCode(String promotionCode) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT * FROM promotions WHERE UPPER(promotionCode) = UPPER(?)",
                    mapper(),
                    promotionCode
            ));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public List<Promotion> findAll() {
        return jdbcTemplate.query("SELECT * FROM promotions ORDER BY promotionID", mapper());
    }

    @Override
    public List<Promotion> findAvailable(LocalDate today) {
        return jdbcTemplate.query(
                """
                        SELECT *
                        FROM promotions
                        WHERE active = 1
                          AND validFrom <= ?
                          AND validTo >= ?
                        ORDER BY promotionID
                        """,
                mapper(),
                Date.valueOf(today),
                Date.valueOf(today)
        );
    }

    @Override
    public void updateActive(Integer promotionID, boolean active) {
        int updated = jdbcTemplate.update(
                "UPDATE promotions SET active = ? WHERE promotionID = ?",
                active,
                promotionID
        );
        if (updated == 0) {
            throw new EmptyResultDataAccessException(1);
        }
    }

    @Override
    public void applyPromotion(Integer orderID, Integer promotionID, BigDecimal discountAmount) {
        jdbcTemplate.update(
                """
                        MERGE orderPromotions AS target
                        USING (SELECT ? AS orderID, ? AS promotionID, ? AS discountAmount) AS source
                        ON target.orderID = source.orderID
                        WHEN MATCHED THEN
                            UPDATE SET promotionID = source.promotionID,
                                       discountAmount = source.discountAmount,
                                       appliedAt = GETDATE()
                        WHEN NOT MATCHED THEN
                            INSERT (orderID, promotionID, discountAmount)
                            VALUES (source.orderID, source.promotionID, source.discountAmount);
                        """,
                orderID,
                promotionID,
                discountAmount
        );
    }

    private RowMapper<Promotion> mapper() {
        return (rs, rowNum) -> new Promotion(
                rs.getInt("promotionID"),
                rs.getString("promotionCode"),
                rs.getString("promotionName"),
                DiscountType.valueOf(rs.getString("discountType")),
                rs.getBigDecimal("discountValue"),
                rs.getBigDecimal("minimumOrderAmount"),
                rs.getDate("validFrom").toLocalDate(),
                rs.getDate("validTo").toLocalDate(),
                rs.getBoolean("active")
        );
    }
}
