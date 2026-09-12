package _6.Y2.S1.MTR._6.LaundryLink.rider.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class RiderRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /*
     * The Rider module does not create Order/Delivery entities.
     * These native queries read the existing database tables directly.
     */

    public List<Object[]> findAvailableTasks() {
        String sql = """
                SELECT
                    d.deliverID,
                    d.orderID,
                    CASE WHEN o.statusID = 3 THEN 'pickup' ELSE 'delivery' END AS taskType,
                    o.statusID,
                    s.statusLabel,
                    CONCAT(COALESCE(u.firstName, ''),
                           CASE WHEN u.middleName IS NULL OR u.middleName = '' THEN '' ELSE ' ' + u.middleName END,
                           CASE WHEN u.lastName IS NULL OR u.lastName = '' THEN '' ELSE ' ' + u.lastName END) AS customerName,
                    CONCAT(COALESCE(a.street, ''),
                           CASE WHEN a.city IS NULL OR a.city = '' THEN '' ELSE ', ' + a.city END,
                           CASE WHEN a.state IS NULL OR a.state = '' THEN '' ELSE ', ' + a.state END) AS address,
                    u.phoneNumber,
                    d.pickup_scheduled,
                    CASE
                        WHEN o.statusID = 3 THEN d.pickup_scheduled
                        ELSE awaitingDelivery.awaitingAt
                    END AS priorityTimestamp
                FROM delivery d
                JOIN orders o ON o.orderID = d.orderID
                JOIN status s ON s.statusID = o.statusID
                JOIN users u ON u.userID = o.userID
                OUTER APPLY (
                    SELECT TOP 1
                        DATEADD(SECOND, DATEDIFF(SECOND, CAST('00:00:00' AS time), l.logTime), CAST(l.logDate AS datetime)) AS awaitingAt
                    FROM logs l
                    WHERE l.orderID = d.orderID
                      AND l.status_after = 12
                    ORDER BY l.logDate DESC, l.logTime DESC, l.logID DESC
                ) awaitingDelivery
                OUTER APPLY (
                    SELECT TOP 1 a2.street, a2.city, a2.state
                    FROM addresses a2
                    WHERE a2.userID = o.userID
                      AND a2.isDefault = 1
                    ORDER BY a2.addressID DESC
                ) a
                WHERE
                    (o.statusID = 3 AND d.pickup_riderID IS NULL)
                    OR
                    (o.statusID = 12 AND d.delivery_riderID IS NULL)
                ORDER BY
                    CASE WHEN o.statusID = 3 THEN d.pickup_scheduled ELSE awaitingDelivery.awaitingAt END ASC,
                    d.deliverID ASC
                """;
        return entityManager.createNativeQuery(sql).getResultList();
    }

    public List<Object[]> findMyWork(Integer riderId) {
        String sql = """
                SELECT
                    d.deliverID,
                    d.orderID,
                    CASE
                        WHEN o.statusID IN (4, 5, 6) THEN 'pickup'
                        WHEN o.statusID = 13 THEN 'delivery'
                    END AS taskType,
                    o.statusID,
                    s.statusLabel,
                    CONCAT(COALESCE(u.firstName, ''),
                           CASE WHEN u.middleName IS NULL OR u.middleName = '' THEN '' ELSE ' ' + u.middleName END,
                           CASE WHEN u.lastName IS NULL OR u.lastName = '' THEN '' ELSE ' ' + u.lastName END) AS customerName,
                    CONCAT(COALESCE(a.street, ''),
                           CASE WHEN a.city IS NULL OR a.city = '' THEN '' ELSE ', ' + a.city END,
                           CASE WHEN a.state IS NULL OR a.state = '' THEN '' ELSE ', ' + a.state END) AS address,
                    u.phoneNumber,
                    d.pickup_scheduled,
                    CASE
                        WHEN o.statusID IN (4, 5, 6) THEN d.pickup_scheduled
                        ELSE awaitingDelivery.awaitingAt
                    END AS priorityTimestamp
                FROM delivery d
                JOIN orders o ON o.orderID = d.orderID
                JOIN status s ON s.statusID = o.statusID
                JOIN users u ON u.userID = o.userID
                OUTER APPLY (
                    SELECT TOP 1
                        DATEADD(SECOND, DATEDIFF(SECOND, CAST('00:00:00' AS time), l.logTime), CAST(l.logDate AS datetime)) AS awaitingAt
                    FROM logs l
                    WHERE l.orderID = d.orderID
                      AND l.status_after = 12
                    ORDER BY l.logDate DESC, l.logTime DESC, l.logID DESC
                ) awaitingDelivery
                OUTER APPLY (
                    SELECT TOP 1 a2.street, a2.city, a2.state
                    FROM addresses a2
                    WHERE a2.userID = o.userID
                      AND a2.isDefault = 1
                    ORDER BY a2.addressID DESC
                ) a
                WHERE
                    (o.statusID IN (4, 5, 6) AND d.pickup_riderID = :riderId)
                    OR
                    (o.statusID = 13 AND d.delivery_riderID = :riderId)
                ORDER BY
                    CASE
                        WHEN o.statusID IN (4, 5, 6) THEN d.pickup_scheduled
                        ELSE awaitingDelivery.awaitingAt
                    END ASC,
                    d.deliverID ASC
                """;
        return entityManager.createNativeQuery(sql)
                .setParameter("riderId", riderId)
                .getResultList();
    }

    public long countAvailableTasks() {
        String sql = """
                SELECT COUNT(*)
                FROM delivery d
                JOIN orders o ON o.orderID = d.orderID
                WHERE (o.statusID = 3 AND d.pickup_riderID IS NULL)
                   OR (o.statusID = 12 AND d.delivery_riderID IS NULL)
                """;
        return ((Number) entityManager.createNativeQuery(sql).getSingleResult()).longValue();
    }

    public long countCompletedTasks(Integer riderId) {
        String sql = """
                SELECT COUNT(*)
                FROM delivery d
                JOIN orders o ON o.orderID = d.orderID
                WHERE o.statusID = 15
                  AND d.delivery_riderID = :riderId
                """;
        return ((Number) entityManager.createNativeQuery(sql)
                .setParameter("riderId", riderId)
                .getSingleResult()).longValue();
    }

    public java.util.Optional<Object[]> findTaskById(Integer deliverId) {
        String sql = """
                SELECT d.deliverID,
                       d.orderID,
                       CASE WHEN o.statusID IN (3,4,5,6,7) THEN 'pickup'
                            WHEN o.statusID IN (12,13,15) THEN 'delivery'
                       END AS taskType,
                       o.statusID,
                       s.statusLabel,
                       d.pickup_riderID,
                       d.delivery_riderID
                FROM delivery d
                JOIN orders o ON o.orderID = d.orderID
                JOIN status s ON s.statusID = o.statusID
                WHERE d.deliverID = :deliverId
                """;
        List<?> result = entityManager.createNativeQuery(sql)
                .setParameter("deliverId", deliverId)
                .getResultList();
        return result.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of((Object[]) result.get(0));
    }

    public int updateDeliveryToEnRoute(Integer deliverId, Integer riderId) {
        return updateStatus(deliverId, riderId, "delivery_riderID", 12, 13);
    }

    public Object[] findRider(Integer riderId) {
        String sql = """
                SELECT userID, firstName, lastName, type
                FROM users
                WHERE userID = :riderId
                """;
        List<?> result = entityManager.createNativeQuery(sql)
                .setParameter("riderId", riderId)
                .getResultList();
        return result.isEmpty() ? null : (Object[]) result.get(0);
    }

    public int acceptPickup(Integer deliverId, Integer riderId) {
        String sql = """
                UPDATE d
                SET d.pickup_riderID = :riderId
                FROM delivery d
                JOIN orders o ON o.orderID = d.orderID
                JOIN users r ON r.userID = :riderId
                WHERE d.deliverID = :deliverId
                  AND o.statusID = 3
                  AND d.pickup_riderID IS NULL
                  AND r.type = 'RIDER'
                """;
        return entityManager.createNativeQuery(sql)
                .setParameter("deliverId", deliverId)
                .setParameter("riderId", riderId)
                .executeUpdate();
    }

    public int acceptDelivery(Integer deliverId, Integer riderId) {
        String sql = """
                UPDATE d
                SET d.delivery_riderID = :riderId
                FROM delivery d
                JOIN orders o ON o.orderID = d.orderID
                JOIN users r ON r.userID = :riderId
                WHERE d.deliverID = :deliverId
                  AND o.statusID = 12
                  AND d.delivery_riderID IS NULL
                  AND r.type = 'RIDER'
                """;
        return entityManager.createNativeQuery(sql)
                .setParameter("deliverId", deliverId)
                .setParameter("riderId", riderId)
                .executeUpdate();
    }

    public int movePickupToEnRoute(Integer deliverId, Integer riderId) {
        return updateStatus(deliverId, riderId, "pickup_riderID", 3, 4);
    }

    public int pickupPickedUp(Integer deliverId, Integer riderId) {
        String sql = """
                UPDATE d
                SET d.pickup_actual = GETDATE()
                FROM delivery d
                JOIN orders o ON o.orderID = d.orderID
                WHERE d.deliverID = :deliverId
                  AND d.pickup_riderID = :riderId
                  AND o.statusID = 4
                """;
        int timestampUpdated = entityManager.createNativeQuery(sql)
                .setParameter("deliverId", deliverId)
                .setParameter("riderId", riderId)
                .executeUpdate();
        if (timestampUpdated != 1) return 0;
        return updateStatus(deliverId, riderId, "pickup_riderID", 4, 6);
    }

    public int pickupDeliveredToShop(Integer deliverId, Integer riderId) {
        return updateStatus(deliverId, riderId, "pickup_riderID", 6, 7);
    }

    public int pickupFailed(Integer deliverId, Integer riderId, String note) {
        String sql = """
                UPDATE d
                SET d.riderNotes = :note,
                    d.pickup_riderID = NULL
                FROM delivery d
                JOIN orders o ON o.orderID = d.orderID
                WHERE d.deliverID = :deliverId
                  AND d.pickup_riderID = :riderId
                  AND o.statusID = 4
                """;
        int updated = entityManager.createNativeQuery(sql)
                .setParameter("deliverId", deliverId)
                .setParameter("riderId", riderId)
                .setParameter("note", note)
                .executeUpdate();
        if (updated != 1) return 0;
        return setStatusByDeliveryId(deliverId, 3);
    }

    public int deliveryDelivered(Integer deliverId, Integer riderId) {
        String sql = """
                UPDATE d
                SET d.delivery_time = GETDATE()
                FROM delivery d
                JOIN orders o ON o.orderID = d.orderID
                WHERE d.deliverID = :deliverId
                  AND d.delivery_riderID = :riderId
                  AND o.statusID = 13
                """;
        int timestampUpdated = entityManager.createNativeQuery(sql)
                .setParameter("deliverId", deliverId)
                .setParameter("riderId", riderId)
                .executeUpdate();
        if (timestampUpdated != 1) return 0;
        return updateStatus(deliverId, riderId, "delivery_riderID", 13, 15);
    }

    public int deliveryFailed(Integer deliverId, Integer riderId, String note) {
        String sql = """
                UPDATE d
                SET d.riderNotes = :note,
                    d.delivery_riderID = NULL
                FROM delivery d
                JOIN orders o ON o.orderID = d.orderID
                WHERE d.deliverID = :deliverId
                  AND d.delivery_riderID = :riderId
                  AND o.statusID = 13
                """;
        int updated = entityManager.createNativeQuery(sql)
                .setParameter("deliverId", deliverId)
                .setParameter("riderId", riderId)
                .setParameter("note", note)
                .executeUpdate();
        if (updated != 1) return 0;
        return setStatusByDeliveryId(deliverId, 12);
    }

    public int cancelPickup(Integer deliverId, Integer riderId) {
        String sql = """
                UPDATE d
                SET d.pickup_riderID = NULL
                FROM delivery d
                JOIN orders o ON o.orderID = d.orderID
                WHERE d.deliverID = :deliverId
                  AND d.pickup_riderID = :riderId
                  AND o.statusID = 4
                """;
        int updated = entityManager.createNativeQuery(sql)
                .setParameter("deliverId", deliverId)
                .setParameter("riderId", riderId)
                .executeUpdate();
        if (updated != 1) return 0;
        return setStatusByDeliveryId(deliverId, 3);
    }

    public int cancelDelivery(Integer deliverId, Integer riderId) {
        String sql = """
                UPDATE d
                SET d.delivery_riderID = NULL
                FROM delivery d
                JOIN orders o ON o.orderID = d.orderID
                WHERE d.deliverID = :deliverId
                  AND d.delivery_riderID = :riderId
                  AND o.statusID = 13
                """;
        int updated = entityManager.createNativeQuery(sql)
                .setParameter("deliverId", deliverId)
                .setParameter("riderId", riderId)
                .executeUpdate();
        if (updated != 1) return 0;
        return setStatusByDeliveryId(deliverId, 12);
    }

    private int updateStatus(Integer deliverId, Integer riderId, String riderColumn,
                             int expectedStatus, int nextStatus) {
        String sql = """
                UPDATE o
                SET o.statusID = :nextStatus
                FROM orders o
                JOIN delivery d ON d.orderID = o.orderID
                WHERE d.deliverID = :deliverId
                  AND d.%s = :riderId
                  AND o.statusID = :expectedStatus
                """.formatted(riderColumn);
        return entityManager.createNativeQuery(sql)
                .setParameter("deliverId", deliverId)
                .setParameter("riderId", riderId)
                .setParameter("expectedStatus", expectedStatus)
                .setParameter("nextStatus", nextStatus)
                .executeUpdate();
    }

    private int setStatusByDeliveryId(Integer deliverId, int statusId) {
        String sql = """
                UPDATE o
                SET o.statusID = :statusId
                FROM orders o
                JOIN delivery d ON d.orderID = o.orderID
                WHERE d.deliverID = :deliverId
                """;
        return entityManager.createNativeQuery(sql)
                .setParameter("deliverId", deliverId)
                .setParameter("statusId", statusId)
                .executeUpdate();
    }

    // WHY: Counts unassigned pickup tasks available today.
    // HOW: Uses Awaiting Pickup, no pickup rider and pickup_scheduled today.
    public long countAvailablePickup() {
        String sql = """
            SELECT COUNT(*)
            FROM delivery d
            INNER JOIN orders o ON o.orderID = d.orderID
            WHERE o.statusID = 3
              AND d.pickup_riderID IS NULL
            """;
        return ((Number) entityManager.createNativeQuery(sql).getSingleResult()).longValue();
    }

    // WHY: Counts unassigned delivery tasks entering Awaiting Delivery today.
    // HOW: Uses the latest Awaiting Delivery log timestamp for each order.
    public long countAvailableDelivery() {
        String sql = """
        SELECT COUNT(*)
        FROM delivery d
        INNER JOIN orders o ON o.orderID = d.orderID
        WHERE o.statusID = 12
          AND d.delivery_riderID IS NULL
        """;
        return ((Number) entityManager.createNativeQuery(sql).getSingleResult()).longValue();
    }


    // WHY: Counts pickup work completed by this rider.
    // HOW: Completion is the rider's transition into In Shop (status 7).
    public long countCompletedPickup(Integer riderId) {
        String sql = """
        SELECT COUNT(*)
        FROM delivery d
        INNER JOIN orders o ON o.orderID = d.orderID
        WHERE d.pickup_riderID = :riderId
          AND o.statusID = 7
        """;

        return ((Number) entityManager.createNativeQuery(sql)
                .setParameter("riderId", riderId)
                .getSingleResult()).longValue();
    }

    // WHY: Counts deliveries completed by this rider today.
    // HOW: Uses Completed status and delivery_time.
    public long countCompletedDelivery(Integer riderId) {
        String sql = """
        SELECT COUNT(*)
        FROM delivery d
        INNER JOIN orders o ON o.orderID = d.orderID
        WHERE d.delivery_riderID = :riderId
          AND o.statusID = 15
        """;

        return ((Number) entityManager.createNativeQuery(sql)
                .setParameter("riderId", riderId)
                .getSingleResult()).longValue();
    }

    // WHY: Counts this rider's active pickup workload.
    // HOW: Active pickup statuses are 4, 5 and 6.
    public long countRemainingPickup(Integer riderId) {
        String sql = """
            SELECT COUNT(*)
            FROM delivery d
            INNER JOIN orders o ON o.orderID = d.orderID
            WHERE d.pickup_riderID = :riderId
              AND o.statusID IN (4, 5, 6)
            """;
        return ((Number) entityManager.createNativeQuery(sql)
                .setParameter("riderId", riderId)
                .getSingleResult()).longValue();
    }
    // WHY: Counts this rider's active delivery workload.
    // HOW: Active delivery status is 13.
    public long countRemainingDelivery(Integer riderId) {
        String sql = """
            SELECT COUNT(*)
            FROM delivery d
            INNER JOIN orders o ON o.orderID = d.orderID
            WHERE d.delivery_riderID = :riderId
              AND o.statusID = 13
            """;
        return ((Number) entityManager.createNativeQuery(sql)
                .setParameter("riderId", riderId)
                .getSingleResult()).longValue();
    }


}
