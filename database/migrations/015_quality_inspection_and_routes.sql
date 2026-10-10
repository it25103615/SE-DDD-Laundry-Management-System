/*
    Migration 015 - Quality Inspection status and the new processing routes
    ------------------------------------------------------------------
    The processing module now picks an order's route with the Strategy
    pattern (see Documentation/Design Patterns/Strategy-Pattern-Processing-Routes.md):

      Wash route:           8 -> Washing (9) -> Drying (10) -> Ironing (11) -> Quality Inspection (21)
      Dry-Clean route:      8 -> Dry Clean (19) -> Ironing (11) -> Quality Inspection (21)
      Shoe Cleaning route:  8 -> Washing (9) -> Drying (10) -> Quality Inspection (21)
      Ironing route:        8 -> Ironing (11) -> Quality Inspection (21)

    PART 1 adds status 21 "Quality Inspection": the quality check, packing and
           "Mark as Ready" now happen there instead of at Ironing. The
           application needs this row; without it an order cannot leave its
           last cleaning stage.
    PART 2 adds one Shoe Cleaning test order for the processing test customer
           (created by migration 005), so the new route can be tried out.
    PART 3 lists orders in processing that do not fit the routes (expected:
           no rows). It only reads data.

    Safe to run more than once: the status and the test order are created
    only when they do not exist yet. No table is changed - the qualityChecks
    constraint already allows every rework stage (9, 10, 11, 19).

    An order that was at Ironing with a passed quality check before this
    migration is not lost: move it on to Quality Inspection and it can be
    packed and released with the check it already has.
*/
USE laundryLinkDB;
GO

/* ================================================================
   PART 1 - STATUS
   ================================================================ */
IF NOT EXISTS (SELECT 1 FROM dbo.status WHERE statusID = 21)
    INSERT dbo.status(statusID, statusLabel) VALUES(21, 'Quality Inspection');
GO

/* ================================================================
   PART 2 - SHOE CLEANING TEST ORDER
   ================================================================ */
SET XACT_ABORT ON;
SET NOCOUNT ON;

IF NOT EXISTS (SELECT 1 FROM dbo.users WHERE email = 'priya.fernando@assignment.laundrylink.lk')
BEGIN
    PRINT 'Processing test customer not found - skipping the Shoe Cleaning test order. Run the sample data and migration 005, then run this migration again.';
END
ELSE
BEGIN TRY
    BEGIN TRANSACTION;

    -- The catalogue rows the order needs, looked up by name (IDs differ between databases).
    IF NOT EXISTS (SELECT 1 FROM dbo.services WHERE serviceName = 'Shoe Cleaning')
        INSERT dbo.services(serviceName) VALUES('Shoe Cleaning');
    IF NOT EXISTS (SELECT 1 FROM dbo.items WHERE itemName = 'Everyday Shoes')
        INSERT dbo.items(itemName) VALUES('Everyday Shoes');

    DECLARE @Shoe  INT = (SELECT TOP(1) serviceID FROM dbo.services WHERE serviceName = 'Shoe Cleaning');
    DECLARE @Shoes INT = (SELECT TOP(1) itemID FROM dbo.items WHERE itemName = 'Everyday Shoes');
    IF NOT EXISTS (SELECT 1 FROM dbo.servicePricing WHERE serviceID = @Shoe AND itemID = @Shoes)
        INSERT dbo.servicePricing(serviceID, itemID, price) VALUES(@Shoe, @Shoes, 750.00);

    DECLARE @Customer INT = (SELECT userID FROM dbo.users WHERE email = 'priya.fernando@assignment.laundrylink.lk');

    -- Create the order only once: "does this customer already have a shoe order" is the rerun check.
    IF NOT EXISTS (SELECT 1 FROM dbo.orders o JOIN dbo.orderLines ol ON ol.orderID = o.orderID
                   WHERE o.userID = @Customer AND ol.serviceID = @Shoe)
    BEGIN
        DECLARE @Arrived DATETIME2 = DATEADD(MINUTE, -20, SYSDATETIME());

        -- One service on the order (Shoe Cleaning only), already counted: Verifying Items (8).
        INSERT dbo.orders(statusID, userID) VALUES(8, @Customer);
        DECLARE @ShoeOrder INT = CONVERT(INT, SCOPE_IDENTITY());
        INSERT dbo.orderLines(orderID, itemID, serviceID, quantity, linePrice)
            VALUES(@ShoeOrder, @Shoes, @Shoe, 2, 1500.00);
        INSERT dbo.logs(status_before, status_after, logDate, logTime, orderID)
            VALUES(7, 8, CONVERT(DATE, @Arrived), CONVERT(TIME(0), @Arrived), @ShoeOrder);

        -- Past receiving, so record its counts like the Receive Items page would have.
        DECLARE @Staff INT = (SELECT TOP(1) userID FROM dbo.users WHERE email = 'sam@staff.com');
        IF @Staff IS NOT NULL
            INSERT dbo.receivedItems(orderLineID, receivedQuantity, itemCondition, receivedBy)
            SELECT orderLineID, quantity, 'As expected', @Staff
            FROM dbo.orderLines WHERE orderID = @ShoeOrder;
    END;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
GO

/* ================================================================
   PART 3 - CHECK (read only)
   Orders in processing that the routes cannot finish: more than one
   service on the order, or a stage the order's route does not have.
   Expected result: no rows. Fix any row by setting the order to a
   stage on its route.
   ================================================================ */
WITH orderServices AS (
    SELECT o.orderID, o.statusID,
           COUNT(DISTINCT sv.serviceName) AS serviceCount,
           MIN(sv.serviceName) AS serviceName
    FROM dbo.orders o
    JOIN dbo.orderLines ol ON ol.orderID = o.orderID
    JOIN dbo.services sv ON sv.serviceID = ol.serviceID
    WHERE o.statusID IN (7, 8, 9, 10, 11, 19, 21)
    GROUP BY o.orderID, o.statusID
)
SELECT os.orderID, s.statusLabel,
       CASE WHEN os.serviceCount > 1 THEN 'Mixed services' ELSE os.serviceName END AS service,
       CASE WHEN os.serviceCount > 1 THEN 'More than one service on the order'
            ELSE 'Stage is not on the route of this service' END AS problem
FROM orderServices os
JOIN dbo.status s ON s.statusID = os.statusID
WHERE os.serviceCount > 1
   OR (os.serviceName = 'Shoe Cleaning' AND os.statusID IN (11, 19))
   OR (os.serviceName = 'Ironing'       AND os.statusID IN (9, 10, 19))
   OR (os.serviceName = 'Dry Cleaning'  AND os.statusID IN (9, 10))
   OR (os.serviceName NOT IN ('Shoe Cleaning', 'Ironing', 'Dry Cleaning') AND os.statusID = 19)
ORDER BY os.orderID;
GO
