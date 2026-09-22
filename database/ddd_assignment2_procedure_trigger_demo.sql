USE laundryLinkDB;
GO

/* PROCEDURE DEMONSTRATION
   Choose an existing order and a different existing status before executing.
   An outer transaction shows both results and then rolls them back, so the
   demonstration does not retain changes in an existing database. */
BEGIN TRANSACTION;
DECLARE @DemoOrderID INT=(SELECT TOP (1) orderID FROM dbo.orders ORDER BY orderID);
DECLARE @CurrentStatusID INT=(SELECT statusID FROM dbo.orders WHERE orderID=@DemoOrderID);
DECLARE @DemoNewStatusID INT=(SELECT TOP (1) statusID FROM dbo.status WHERE statusID<>@CurrentStatusID ORDER BY statusID);

EXEC dbo.sp_UpdateOrderStatus @OrderID=@DemoOrderID, @NewStatusID=@DemoNewStatusID;

SELECT o.orderID, o.statusID, s.statusLabel
FROM dbo.orders o JOIN dbo.status s ON s.statusID=o.statusID
WHERE o.orderID=@DemoOrderID;
SELECT TOP (5) * FROM dbo.logs WHERE orderID=@DemoOrderID ORDER BY logID DESC;
ROLLBACK TRANSACTION;
GO

/* TRIGGER DEMONSTRATION
   Both cases run inside transactions that are rolled back, so demonstration data
   is not retained. Run Case 1 and Case 2 separately in SSMS for clear screenshots. */

/* CASE 1 - a valid RIDER assignment succeeds, then is rolled back. */
BEGIN TRANSACTION;
DECLARE @ValidDeliveryID INT=(SELECT TOP (1) deliverID FROM dbo.delivery ORDER BY deliverID);
DECLARE @RiderID INT=(SELECT TOP (1) userID FROM dbo.users WHERE type='RIDER' ORDER BY userID);
UPDATE dbo.delivery SET pickup_riderID=@RiderID WHERE deliverID=@ValidDeliveryID;
SELECT deliverID,pickup_riderID,delivery_riderID FROM dbo.delivery WHERE deliverID=@ValidDeliveryID;
ROLLBACK TRANSACTION;
GO

/* CASE 2 - a non-RIDER assignment is rejected by trg_delivery_rider_check.
   TRY/CATCH keeps the script controlled and prints the database error. */
BEGIN TRY
    BEGIN TRANSACTION;
    DECLARE @InvalidDeliveryID INT=(SELECT TOP (1) deliverID FROM dbo.delivery ORDER BY deliverID);
    DECLARE @NonRiderID INT=(SELECT TOP (1) userID FROM dbo.users WHERE type<>'RIDER' ORDER BY userID);
    UPDATE dbo.delivery SET pickup_riderID=@NonRiderID WHERE deliverID=@InvalidDeliveryID;
    ROLLBACK TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
    SELECT ERROR_NUMBER() AS errorNumber, ERROR_MESSAGE() AS triggerError;
END CATCH;
GO
