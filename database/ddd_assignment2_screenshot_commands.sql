/*
    LaundryLink IT2140 DDD Assignment Part 02 screenshot commands

    Run one labelled section at a time in SSMS against laundryLinkDB.
    This file contains no DELETE, DROP, reset, or permanent demonstration change.
*/
USE laundryLinkDB;
GO

/* SECTION 1 - STATUS */
SELECT * FROM dbo.status ORDER BY statusID;
GO

/* SECTION 2 - ITEMS */
SELECT * FROM dbo.items ORDER BY itemID;
GO

/* SECTION 3 - SERVICES */
SELECT * FROM dbo.services ORDER BY serviceID;
GO

/* SECTION 4 - SERVICE PRICING */
SELECT * FROM dbo.servicePricing ORDER BY serviceID,itemID;
GO

/* SECTION 5 - USERS */
SELECT * FROM dbo.users ORDER BY userID;
GO

/* SECTION 6 - ADDRESSES */
SELECT * FROM dbo.addresses ORDER BY addressID;
GO

/* SECTION 7 - ORDERS */
SELECT * FROM dbo.orders ORDER BY orderID;
GO

/* SECTION 8 - ORDER LINES */
SELECT * FROM dbo.orderLines ORDER BY orderLineID;
GO

/* SECTION 9 - PAYMENTS */
SELECT * FROM dbo.payments ORDER BY paymentID;
GO

/* SECTION 10 - LOGS */
SELECT * FROM dbo.logs ORDER BY logID;
GO

/* SECTION 11 - FEEDBACK */
SELECT * FROM dbo.feedback ORDER BY feedbackID;
GO

/* SECTION 12 - CHAT */
SELECT * FROM dbo.chat ORDER BY chatID;
GO

/* SECTION 13 - DELIVERY */
SELECT * FROM dbo.delivery ORDER BY deliverID;
GO

/* SECTION 14 - SUPPORT ACTIVITY */
SELECT * FROM dbo.support_activity ORDER BY activityID;
GO

/* SECTION 15 - SYSTEM SETTINGS */
SELECT * FROM dbo.system_settings ORDER BY settingID;
GO

/* SECTION - QUERY 1: SIMPLE SELECT */
SELECT orderID,userID,statusID
FROM dbo.orders
WHERE statusID<>15
ORDER BY orderID;
GO

/* SECTION - QUERY 2: JOIN */
SELECT o.orderID,
       CONCAT(u.firstName,' ',u.lastName) AS customerName,
       u.email,
       s.statusLabel
FROM dbo.orders o
JOIN dbo.users u ON u.userID=o.userID
JOIN dbo.status s ON s.statusID=o.statusID
ORDER BY o.orderID;
GO

/* SECTION - QUERY 3: AGGREGATION */
SELECT COUNT(*) AS paymentCount,
       SUM(amount) AS totalRecordedPayments,
       AVG(amount) AS averagePayment,
       MIN(amount) AS smallestPayment,
       MAX(amount) AS largestPayment
FROM dbo.payments;
GO

/* SECTION - QUERY 4: GROUP BY AND HAVING */
SELECT s.serviceID,s.serviceName,
       COUNT(*) AS orderLineCount,
       SUM(ol.quantity) AS totalItems
FROM dbo.services s
JOIN dbo.orderLines ol ON ol.serviceID=s.serviceID
GROUP BY s.serviceID,s.serviceName
HAVING COUNT(*)>=2
ORDER BY orderLineCount DESC;
GO

/* SECTION - QUERY 5: SUBQUERY */
SELECT u.userID,
       CONCAT(u.firstName,' ',u.lastName) AS customerName,
       SUM(p.amount) AS customerPaymentTotal
FROM dbo.users u
JOIN dbo.orders o ON o.userID=u.userID
JOIN dbo.payments p ON p.orderID=o.orderID
WHERE u.type='CUSTOMER'
GROUP BY u.userID,u.firstName,u.lastName
HAVING SUM(p.amount)>(
    SELECT AVG(customerTotal)
    FROM (
        SELECT SUM(p2.amount) AS customerTotal
        FROM dbo.orders o2
        JOIN dbo.payments p2 ON p2.orderID=o2.orderID
        GROUP BY o2.userID
    ) totals
)
ORDER BY customerPaymentTotal DESC;
GO

/* SECTION - PROCEDURE DEMO
   The outer transaction displays the update and audit log, then restores both. */
BEGIN TRANSACTION;
DECLARE @DemoOrderID INT=(SELECT TOP(1) orderID FROM dbo.orders ORDER BY orderID);
DECLARE @OldStatusID INT=(SELECT statusID FROM dbo.orders WHERE orderID=@DemoOrderID);
DECLARE @NewStatusID INT=(SELECT TOP(1) statusID FROM dbo.status WHERE statusID<>@OldStatusID ORDER BY statusID);
DECLARE @OldLogCount INT=(SELECT COUNT(*) FROM dbo.logs WHERE orderID=@DemoOrderID);

SELECT @DemoOrderID AS orderID,@OldStatusID AS statusBefore,@OldLogCount AS logCountBefore;
EXEC dbo.sp_UpdateOrderStatus @OrderID=@DemoOrderID,@NewStatusID=@NewStatusID;
SELECT orderID,statusID AS statusAfter FROM dbo.orders WHERE orderID=@DemoOrderID;
SELECT TOP(1) * FROM dbo.logs WHERE orderID=@DemoOrderID ORDER BY logID DESC;
ROLLBACK TRANSACTION;

SELECT orderID,statusID AS restoredStatus FROM dbo.orders WHERE orderID=@DemoOrderID;
SELECT COUNT(*) AS restoredLogCount FROM dbo.logs WHERE orderID=@DemoOrderID;
GO

/* SECTION - VALID TRIGGER DEMO
   A RIDER assignment succeeds inside the test transaction, then is restored. */
BEGIN TRANSACTION;
DECLARE @ValidDeliveryID INT=(SELECT TOP(1) deliverID FROM dbo.delivery ORDER BY deliverID);
DECLARE @ValidRiderID INT=(SELECT TOP(1) userID FROM dbo.users WHERE type='RIDER' ORDER BY userID);
UPDATE dbo.delivery SET pickup_riderID=@ValidRiderID WHERE deliverID=@ValidDeliveryID;
SELECT d.deliverID,d.pickup_riderID,u.type AS assignedRole
FROM dbo.delivery d JOIN dbo.users u ON u.userID=d.pickup_riderID
WHERE d.deliverID=@ValidDeliveryID;
ROLLBACK TRANSACTION;
GO

/* SECTION - INVALID TRIGGER DEMO
   The trigger must reject this statement. CATCH proves the value was not stored. */
DECLARE @InvalidDeliveryID INT=(SELECT TOP(1) deliverID FROM dbo.delivery ORDER BY deliverID);
DECLARE @OriginalPickupRiderID INT=(SELECT pickup_riderID FROM dbo.delivery WHERE deliverID=@InvalidDeliveryID);
DECLARE @NonRiderID INT=(SELECT TOP(1) userID FROM dbo.users WHERE type<>'RIDER' ORDER BY userID);
BEGIN TRY
    BEGIN TRANSACTION;
    UPDATE dbo.delivery SET pickup_riderID=@NonRiderID WHERE deliverID=@InvalidDeliveryID;
    ROLLBACK TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
    SELECT ERROR_NUMBER() AS errorNumber,ERROR_MESSAGE() AS triggerError;
END CATCH;
SELECT deliverID,pickup_riderID AS storedPickupRiderID,@OriginalPickupRiderID AS expectedPickupRiderID
FROM dbo.delivery WHERE deliverID=@InvalidDeliveryID;
GO
