USE laundryLinkDB;
GO

/* ASSIGNMENT PART E — SIX MODULE ROUTINE SCREENSHOTS
   Run each numbered section separately. Keep that section's SQL and Results grid
   visible together in the screenshot. The three procedure demos roll back changes. */

/* SCREENSHOT 1/6 — ACCOUNT FUNCTION
   Select this section through its GO and execute. */
DECLARE @DemoAccountID INT=(SELECT TOP (1) userID FROM dbo.users WHERE type='CSM' ORDER BY userID);
SELECT * FROM dbo.fn_GetAccountProfile(@DemoAccountID);
GO

/* SCREENSHOT 2/6 — ORDER FUNCTION
   Select this section through its GO and execute. */
DECLARE @DemoCustomerID INT=(SELECT TOP (1) userID FROM dbo.users WHERE type='CUSTOMER' ORDER BY userID);
SELECT * FROM dbo.fn_GetCustomerOrders(@DemoCustomerID);
GO

/* SCREENSHOT 3/6 — PROCESSING PROCEDURE
   Select this section through its GO and execute; rollback restores order and log. */
BEGIN TRANSACTION;
DECLARE @DemoOrderID INT=(SELECT TOP (1) orderID FROM dbo.orders ORDER BY orderID);
DECLARE @CurrentStatusID INT=(SELECT statusID FROM dbo.orders WHERE orderID=@DemoOrderID);
DECLARE @NewStatusID INT=(SELECT TOP (1) statusID FROM dbo.status WHERE statusID<>@CurrentStatusID ORDER BY statusID);
EXEC dbo.sp_UpdateProcessingStatus @OrderID=@DemoOrderID,@NewStatusID=@NewStatusID;
SELECT TOP (1) * FROM dbo.logs WHERE orderID=@DemoOrderID ORDER BY logID DESC;
ROLLBACK TRANSACTION;
GO

/* SCREENSHOT 4/6 — RIDER PROCEDURE
   Select this section through its GO and execute; rollback restores delivery. */
BEGIN TRANSACTION;
DECLARE @DemoDeliveryID INT=(SELECT TOP (1) deliverID FROM dbo.delivery ORDER BY deliverID);
DECLARE @DemoRiderID INT=(SELECT TOP (1) userID FROM dbo.users WHERE type='RIDER' ORDER BY userID);
EXEC dbo.sp_AssignDeliveryRider @DeliveryID=@DemoDeliveryID,@RiderID=@DemoRiderID;
ROLLBACK TRANSACTION;
GO

/* SCREENSHOT 5/6 — PAYMENT PROCEDURE
   Select this section through its GO and execute; rollback removes payment and notification. */
BEGIN TRANSACTION;
DECLARE @DemoPaymentOrderID INT=(SELECT TOP (1) orderID FROM dbo.orders ORDER BY orderID);
EXEC dbo.sp_RecordPayment @OrderID=@DemoPaymentOrderID,@Amount=1.00;
ROLLBACK TRANSACTION;
GO

/* SCREENSHOT 6/6 — SUPPORT / CSM FUNCTION
   Select this section through GO and execute; it lists open cases for CSM review. */
SELECT * FROM dbo.fn_GetOpenSupportCases();
GO

/* PART F — GENERAL ORDER STATUS NOTIFICATION TRIGGER
   Changing an order status automatically adds a notification for its customer.
   Select this section through GO and run it; the transaction rolls back both changes. */
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
