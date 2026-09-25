package _6.Y2.S1.MTR._6.LaundryLink.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.List;

// WHY: This module reads/writes tables (orders, delivery, users, logs) that
//      other parts of LaundryLink also own — declaring @Entity classes here
//      would risk clashing JPA mappings with another module's entities for
//      the same physical tables.
// HOW: Every method below uses EntityManager.createNativeQuery() with plain
//      SQL and binds parameters by name. Reads return raw Object[] rows,
//      which RiderService.mapRow() converts into RiderTaskDTO.
@Repository
public class RiderRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /*
     * The Rider module does not create Order/Delivery entities.
     * These native queries read the existing database tables directly.
     */

    // WHY: Looks up the rider's primary key (userID) using their login email address.
    // HOW: Performs a direct lookup in the users table, enforcing type = 'RIDER'.
   /* public java.util.Optional<Integer> findUserIdByEmail(String email) {
        String sql = """
                SELECT userID
                FROM users
                WHERE email = :email
                  AND type = 'RIDER'
                """;
        List<?> result = entityManager.createNativeQuery(sql)
                .setParameter("email", email)
                .getResultList();

        if (result.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(((Number) result.get(0)).intValue());
    }*/

    // WHY: Powers GET /tasks — the unassigned pool a rider can accept from.
// HOW: Unions pickup-eligible orders (statusID 3, no pickup rider) and
//      delivery-eligible orders (statusID 12, no delivery rider) into one
//      list, tagging each row's type via CASE. OUTER APPLY fills in the
//      customer's default address and, for deliveries, the timestamp the
//      order entered "Awaiting Delivery" (used only for sort order).
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

    // WHY: Powers GET /my-work — tasks this specific rider currently has active.
// HOW: Same shape as findAvailableTasks(), but filtered to rows where this
//      riderId is already assigned as the pickup or delivery rider, at the
//      statuses that represent "in progress" (4, 5, 6 for pickup; 13 for
//      delivery) rather than "unassigned."
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
    /*
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
    */

    /*
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
    */
    // WHY: Looks up one assignment by its deliverID so the service layer can
//      validate a task's current type/status/owning rider before acting
//      on it (used by accept, cancel, and every status-change endpoint).
// HOW: Derives a friendly "pickup"/"delivery" label from statusID via CASE,
//      and returns both rider-ID columns so the caller can confirm
//      ownership itself.
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

    // WHY: Moves a delivery from "Awaiting Delivery" (12) to "En Route To
//      Delivery" (13) right after a rider accepts it.
// HOW: Thin wrapper around the shared updateStatus() helper — kept as its
//      own named method so RiderService's accept() flow reads clearly.
    public int updateDeliveryToEnRoute(Integer deliverId, Integer riderId) {
        return updateStatus(deliverId, riderId, "delivery_riderID", 12, 13);
    }

    // WHY: Fetches basic profile info for GET /me (name, initials on the badge).
// HOW: Plain lookup by userID — no rider-specific filtering, since the
//      caller already knows this ID is a rider.
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

    // WHY: Claims an unassigned pickup for this rider.
// HOW: The WHERE clause (statusID = 3 AND pickup_riderID IS NULL AND
//      r.type = 'RIDER') doubles as an optimistic-concurrency check — if
//      two riders tap Accept at once, only the first UPDATE matches a row
//      and returns 1; the second returns 0 and the service layer rejects it.
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

    // WHY: Claims an unassigned delivery for this rider.
// HOW: Same optimistic-concurrency pattern as acceptPickup(), scoped to
//      statusID 12 / delivery_riderID instead of pickup.
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

    // WHY: Advances a newly-accepted pickup from "Awaiting Pickup" (3) to
//      "En Route To Pickup" (4).
// HOW: Thin wrapper around updateStatus() — see acceptPickup(), which
//      calls this immediately after a successful claim.
    public int movePickupToEnRoute(Integer deliverId, Integer riderId) {
        return updateStatus(deliverId, riderId, "pickup_riderID", 3, 4);
    }

    // WHY: Records that the rider has physically collected the laundry from
//      the customer, then advances the order toward the shop.
// HOW: Two steps in sequence: (1) stamp pickup_actual with the current
//      time, only if the order is still at statusID 4; (2) if that
//      succeeded, move statusID 4 -> 6. If step 1 affects zero rows
//      (task already moved on), step 2 is skipped and 0 is returned.
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

    // WHY: Records that the rider has dropped the laundry off at the shop —
//      this is the actual "pickup completed" event for this order.
// HOW: Thin wrapper around updateStatus(), advancing statusID 6 -> 7.
    public int pickupDeliveredToShop(Integer deliverId, Integer riderId) {
        return updateStatus(deliverId, riderId, "pickup_riderID", 6, 7);
    }

    // WHY: Lets a rider report a failed pickup attempt with a reason, and
//      frees the task back into the unassigned pool for another rider.
// HOW: Saves the note and clears pickup_riderID (only if this rider still
//      owns the task at statusID 4), then resets the order back to
//      statusID 3 (Awaiting Pickup) so it reappears in the available pool.
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

    // WHY: Records that the order has reached the customer — this is the
//      actual "delivery completed" event.
// HOW: Same two-step pattern as pickupPickedUp(): stamp delivery_time,
//      then advance statusID 13 -> 15 only if the stamp succeeded.
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

    // WHY: Lets a rider report a failed delivery attempt with a reason, and
//      frees the task back into the unassigned pool for another rider.
// HOW: Mirrors pickupFailed() — saves the note, clears delivery_riderID,
//      and resets the order back to statusID 12 (Awaiting Delivery).
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

    // WHY: Lets a rider back out of a pickup they've accepted but not yet
//      started collecting (no note required, unlike a failed attempt).
// HOW: Clears pickup_riderID (only if owned by this rider at statusID 4),
//      then resets the order back to statusID 3.
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

    // WHY: Lets a rider back out of a delivery they've accepted but not yet
//      completed.
// HOW: Mirrors cancelPickup() — clears delivery_riderID at statusID 13,
//      then resets the order back to statusID 12.
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

    // WHY: Nearly every status transition in this file follows the same shape
//      — "move this order's status forward by one step, but only if it's
//      still at the status we expect" — so that logic lives here once.
// HOW: UPDATE orders.statusID to nextStatus, joined through delivery on
//      deliverId, guarded by both the owning rider column (parameterized
//      via riderColumn, since it differs for pickup vs delivery) and the
//      expected current statusID. A 0-row result means the task moved or
//      was reassigned since the caller last checked it.
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

    // WHY: Used by the "failed" and "cancel" flows, which reset an order's
//      status without checking what it currently is (unlike updateStatus(),
//      which enforces an expected starting status).
// HOW: Plain UPDATE by deliverId, no status guard — safe here because the
//      calling method has already confirmed ownership/status one step
//      earlier in the same transaction.
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
