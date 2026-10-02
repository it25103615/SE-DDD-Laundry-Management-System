package _6.Y2.S1.MTR._6.LaundryLink.rider;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Native-SQL data access for the rider module (reads and conditional writes on
 * {@code delivery} and {@code orders}).
 *
 * <p>The rider module does not own the Order/Delivery entities, so it reads the existing
 * tables directly. Who is signed in, and that they are a RIDER, is resolved by
 * {@code RiderService} through {@code UserRepository}; this class only receives the
 * rider's user ID.
 *
 * <p><b>Status IDs used:</b>
 * <ul>
 *   <li>3 Awaiting Pickup: open pickup pool (no {@code pickup_riderID})</li>
 *   <li>4 En Route To Pickup: pickup accepted</li>
 *   <li>6 En Route To Shop: laundry collected</li>
 *   <li>7 In Shop: the rider's pickup work ends here</li>
 *   <li>12 Awaiting Delivery: open delivery pool (no {@code delivery_riderID})</li>
 *   <li>13 En Route To Delivery: delivery accepted</li>
 *   <li>15 Completed: delivery finished</li>
 * </ul>
 * Status 5 (Picked Up) is still included in some queries, but this module never sets it.
 *
 * <p><b>Write convention:</b> every UPDATE is conditional on the expected status and rider.
 * The return value is the affected row count: 1 means success, 0 means the task changed
 * or is not this rider's. {@code RiderService} turns 0 into HTTP 409.
 */
@Repository
public class RiderRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Returns the open task pool: pickups waiting for a rider (status 3) and deliveries
     * waiting for a rider (status 12).
     *
     * <p>Joins order, status and customer. One {@code OUTER APPLY} finds when the order last
     * entered Awaiting Delivery (from {@code logs}); another picks the address chosen for the
     * order ({@code delivery.addressID}), or the customer's default address when the order
     * has none. {@code taskType} is derived from the status. Rows are sorted oldest first
     * ({@code pickup_scheduled} for pickups, awaiting-delivery time for deliveries), which
     * gives riders a first-come-first-served queue.
     *
     * <p>{@code OUTER APPLY} (not a plain JOIN) keeps a task visible even if the log row or
     * address is missing.
     *
     * @return rows as {@code Object[]} in the column order read by {@code RiderService.mapRow}
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
                    -- The address the customer chose for this order (delivery.addressID). It is
                    -- matched by ID only, because an address the customer deleted later is kept
                    -- with userID NULL so the order still knows where to go.
                    -- Orders without one fall back to the customer's default address.
                    SELECT TOP 1 a2.street, a2.city, a2.state
                    FROM addresses a2
                    WHERE a2.addressID = d.addressID
                       OR (d.addressID IS NULL AND a2.userID = o.userID AND a2.isDefault = 1)
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

    /**
     * Returns the rider's active assignments: pickups at status 4/5/6 or deliveries at
     * status 13.
     *
     * <p>Same query shape as {@link #findAvailableTasks()}, filtered by whichever rider column
     * matches the status. Finished work (7, 15) is excluded on purpose because this list
     * drives the "My Work" tables; finished totals come from the count queries.
     *
     * @param riderId user ID of the signed-in rider
     * @return rows as {@code Object[]} in the column order read by {@code RiderService.mapRow}
     */
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
                    -- The address the customer chose for this order (delivery.addressID). It is
                    -- matched by ID only, because an address the customer deleted later is kept
                    -- with userID NULL so the order still knows where to go.
                    -- Orders without one fall back to the customer's default address.
                    SELECT TOP 1 a2.street, a2.city, a2.state
                    FROM addresses a2
                    WHERE a2.addressID = d.addressID
                       OR (d.addressID IS NULL AND a2.userID = o.userID AND a2.isDefault = 1)
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

    /**
     * Loads one task's type, status and assigned riders.
     *
     * <p>{@code taskType} is derived from the status (3 to 7 = pickup, 12/13/15 = delivery).
     * Any other status gives a {@code NULL} type, so callers must handle that.
     *
     * @param deliverId the {@code delivery.deliverID}
     * @return the row, or empty if no such task exists
     */
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

    /**
     * Moves a delivery from Awaiting Delivery (12) to En Route To Delivery (13).
     *
     * <p>Called right after {@link #acceptDelivery} has set {@code delivery_riderID}, so the
     * shared compare-and-set helper can confirm the caller is the assigned rider.
     *
     * @param deliverId the delivery task
     * @param riderId   the rider who accepted it
     * @return 1 on success, 0 if the status or rider did not match
     */
    public int updateDeliveryToEnRoute(Integer deliverId, Integer riderId) {
        return updateStatus(deliverId, riderId, "delivery_riderID", 12, 13);
    }

    /**
     * Claims an open pickup (status 3, no rider yet) for this rider.
     *
     * <p>A single conditional UPDATE: it only succeeds if the task is still unclaimed, so two
     * riders accepting at the same moment cannot both win (the loser gets 0 rows). It also
     * joins {@code users} and requires {@code UPPER(type) = 'RIDER'}, matching how
     * {@code SecurityConfig} builds the role ({@code ROLE_} + {@code UPPER(type)}).
     *
     * <p>This is a defence-in-depth layer: {@code SecurityConfig} is the first guard,
     * {@code RiderService} the second, this query the third, and the
     * {@code trg_delivery_rider_check} trigger the fourth.
     *
     * @param deliverId the pickup task to claim
     * @param riderId   the claiming rider's user ID
     * @return 1 if claimed, 0 if already taken, wrong status, or not a RIDER
     */
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
                  AND UPPER(r.type) = 'RIDER'
                """;
        return entityManager.createNativeQuery(sql)
                .setParameter("deliverId", deliverId)
                .setParameter("riderId", riderId)
                .executeUpdate();
    }

    /**
     * Claims an open delivery (status 12, no rider yet) for this rider.
     * Same logic and guards as {@link #acceptPickup}, but for {@code delivery_riderID}.
     *
     * @param deliverId the delivery task to claim
     * @param riderId   the claiming rider's user ID
     * @return 1 if claimed, 0 otherwise
     */
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
                  AND UPPER(r.type) = 'RIDER'
                """;
        return entityManager.createNativeQuery(sql)
                .setParameter("deliverId", deliverId)
                .setParameter("riderId", riderId)
                .executeUpdate();
    }

    /**
     * Moves a claimed pickup from Awaiting Pickup (3) to En Route To Pickup (4).
     * Accepting a task starts it immediately, so {@code RiderService.accept} runs the claim
     * and this move together.
     *
     * @param deliverId the pickup task
     * @param riderId   the assigned rider
     * @return 1 on success, 0 otherwise
     */
    public int movePickupToEnRoute(Integer deliverId, Integer riderId) {
        return updateStatus(deliverId, riderId, "pickup_riderID", 3, 4);
    }

    /**
     * Records that the rider collected the laundry and moves the order from 4 to 6.
     *
     * <p>Step 1 stamps {@code pickup_actual = GETDATE()} (only for this rider's order at
     * status 4). Step 2 changes the status. If step 1 matches nothing, it returns 0 without
     * touching the status. Both steps must run inside the caller's {@code @Transactional}
     * so a failed step 2 rolls back the timestamp.
     *
     * @param deliverId the pickup task
     * @param riderId   the assigned rider
     * @return 1 on success, 0 otherwise
     */
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

    /**
     * Marks the laundry as handed over at the shop (6 to 7, In Shop). Status 7 is where the
     * rider's pickup work ends and the shop's processing begins.
     *
     * @param deliverId the pickup task
     * @param riderId   the assigned rider
     * @return 1 on success, 0 otherwise
     */
    public int pickupDeliveredToShop(Integer deliverId, Integer riderId) {
        return updateStatus(deliverId, riderId, "pickup_riderID", 6, 7);
    }

    /**
     * Records a failed pickup and returns the task to the open pool.
     *
     * <p>Saves the note in {@code riderNotes}, clears {@code pickup_riderID}, then resets the
     * order to status 3 (Awaiting Pickup) so another rider can retry. Only works from
     * status 4 and for the assigned rider.
     *
     * <p><b>Note:</b> {@code riderNotes} holds a single note, so it is overwritten by the next
     * failure, and status 17 (Pickup Failed) is not used by this flow.
     *
     * @param deliverId the pickup task
     * @param riderId   the assigned rider
     * @param note      the failure reason (already trimmed)
     * @return 1 on success, 0 otherwise
     */
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

    /**
     * Completes a delivery (13 to 15).
     *
     * <p>Stamps {@code delivery_time = GETDATE()} (the proof-of-delivery time), then changes
     * the status. Same two-step pattern as {@link #pickupPickedUp}; it relies on the caller's
     * transaction.
     *
     * @param deliverId the delivery task
     * @param riderId   the assigned rider
     * @return 1 on success, 0 otherwise
     */
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

    /**
     * Records a failed delivery and returns it to the open pool.
     *
     * <p>Saves the note, clears {@code delivery_riderID} and resets the status to 12
     * (Awaiting Delivery). Same reasoning as {@link #pickupFailed}; status 18
     * (Delivery Failed) is not used here.
     *
     * @param deliverId the delivery task
     * @param riderId   the assigned rider
     * @param note      the failure reason (already trimmed)
     * @return 1 on success, 0 otherwise
     */
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

    /**
     * Lets the rider give up an accepted pickup before collecting it (status 4 only).
     * Clears {@code pickup_riderID} and resets the status to 3. Same as a failure but with no
     * note, because nothing went wrong at the customer.
     *
     * @param deliverId the pickup task
     * @param riderId   the assigned rider
     * @return 1 on success, 0 otherwise
     */
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

    /**
     * Lets the rider give up an accepted delivery (status 13 only).
     * Mirrors {@link #cancelPickup}: clears {@code delivery_riderID} and resets the status to 12.
     *
     * @param deliverId the delivery task
     * @param riderId   the assigned rider
     * @return 1 on success, 0 otherwise
     */
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

    /**
     * Generic compare-and-set on {@code orders.statusID}.
     *
     * <p>Updates only if the order is at {@code expectedStatus} AND the given rider column holds
     * {@code riderId}. This prevents stale or duplicate actions and enforces ownership in one
     * statement.
     *
     * <p><b>Logging:</b> every rider step ends here or in {@link #setStatusByDeliveryId}, and
     * neither writes a log row. The database trigger {@code dbo.trg_order_status_log} adds the
     * {@code dbo.logs} row whenever {@code orders.statusID} changes, so the rider's steps
     * appear in the order's status history.
     *
     * <p><b>Security:</b> {@code riderColumn} is inserted with {@code formatted()}, so it must
     * only ever be a hardcoded column name from this class, never user input.
     *
     * @param riderColumn    {@code "pickup_riderID"} or {@code "delivery_riderID"}
     * @param expectedStatus the status the order must currently have
     * @param nextStatus     the status to set
     * @return 1 on success, 0 otherwise
     */
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

    /**
     * Sets an order's status from a delivery ID with no status or owner check.
     *
     * <p>Only used right after a guarded UPDATE has succeeded (failure and cancel), so the
     * checks have already happened. Do not call it on its own.
     *
     * @param deliverId the delivery task
     * @param statusId  the status to set
     * @return rows updated
     */
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

    /**
     * Counts unassigned pickups waiting in the pool (status 3, no pickup rider).
     * Feeds the dashboard "available pickups" tile. Not limited to today.
     */
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

    /**
     * Counts unassigned deliveries waiting in the pool (status 12, no delivery rider).
     * Feeds the dashboard "available deliveries" tile. Not limited to today.
     */
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


    /**
     * Counts this rider's pickups currently at In Shop (status 7).
     * <p><b>Note:</b> once staff advance the order past 7, it stops being counted.
     *
     * @param riderId the rider's user ID
     */
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

    /**
     * Counts this rider's deliveries currently at Completed (status 15).
     * <p><b>Note:</b> not limited to today; it uses the status only, not {@code delivery_time}.
     *
     * @param riderId the rider's user ID
     */
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

    /**
     * Counts this rider's active pickups (statuses 4, 5, 6).
     *
     * @param riderId the rider's user ID
     */
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
    /**
     * Counts this rider's active deliveries (status 13).
     *
     * @param riderId the rider's user ID
     */
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
