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

/* SECTION 15 - NOTIFICATIONS */
SELECT * FROM dbo.notifications ORDER BY notificationID;
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
SELECT userID,firstName,lastName
FROM dbo.users
WHERE type='CUSTOMER'
  AND userID IN (SELECT userID FROM dbo.orders);
GO

/* SECTION - PART E MODULE ROUTINES
   Run the ASSIGNMENT PART E batch from
   database/ddd_assignment2_procedure_trigger_demo.sql for the six calls
   and their sample outputs. This query lists routines for schema evidence. */
SELECT name AS routineName,type_desc
FROM sys.objects
WHERE type IN ('IF','P') AND name IN ('fn_GetAccountProfile','fn_GetCustomerOrders','fn_GetOpenSupportCases',
               'sp_UpdateProcessingStatus','sp_AssignDeliveryRider','sp_RecordPayment')
ORDER BY name;
GO

/* SECTION - PART F GENERAL ORDER STATUS NOTIFICATION TRIGGER
   Updating any order status automatically creates a notification for its customer.
   Run this section to show the changed-status notification, then roll back the demo. */
BEGIN TRANSACTION;
DECLARE @TriggerDemoOrderID INT=(SELECT TOP (1) orderID FROM dbo.orders ORDER BY orderID);
DECLARE @TriggerDemoStatusID INT=(
    SELECT TOP (1) statusID FROM dbo.status
    WHERE statusID<>(SELECT statusID FROM dbo.orders WHERE orderID=@TriggerDemoOrderID)
    ORDER BY statusID
);
UPDATE dbo.orders SET statusID=@TriggerDemoStatusID WHERE orderID=@TriggerDemoOrderID;
SELECT TOP (1) notificationID,recipientID,category,title,message,relatedID
FROM dbo.notifications
WHERE relatedType='ORDER' AND relatedID=@TriggerDemoOrderID AND title='Order status updated'
ORDER BY notificationID DESC;
ROLLBACK TRANSACTION;
GO
