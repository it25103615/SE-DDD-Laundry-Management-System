USE laundryLinkDB;
GO
IF OBJECT_ID('dbo.sp_UpdateOrderStatus','P') IS NOT NULL DROP PROCEDURE dbo.sp_UpdateOrderStatus;
IF OBJECT_ID('dbo.sp_GetAccountProfile','P') IS NOT NULL DROP PROCEDURE dbo.sp_GetAccountProfile;
IF OBJECT_ID('dbo.sp_GetCustomerOrders','P') IS NOT NULL DROP PROCEDURE dbo.sp_GetCustomerOrders;
IF OBJECT_ID('dbo.sp_UpdateProcessingStatus','P') IS NOT NULL DROP PROCEDURE dbo.sp_UpdateProcessingStatus;
IF OBJECT_ID('dbo.sp_AssignDeliveryRider','P') IS NOT NULL DROP PROCEDURE dbo.sp_AssignDeliveryRider;
IF OBJECT_ID('dbo.sp_RecordPayment','P') IS NOT NULL DROP PROCEDURE dbo.sp_RecordPayment;
IF OBJECT_ID('dbo.sp_UpdateSupportCase','P') IS NOT NULL DROP PROCEDURE dbo.sp_UpdateSupportCase;
IF OBJECT_ID('dbo.sp_ResolveSupportCase','P') IS NOT NULL DROP PROCEDURE dbo.sp_ResolveSupportCase;
GO

/* PART E 1/6 — ACCOUNT FUNCTION
   Returns a customer's or staff member's safe profile (no password).
   For the screenshot, run demo section 1 in ddd_assignment2_procedure_trigger_demo.sql.
*/
CREATE OR ALTER FUNCTION dbo.fn_GetAccountProfile(@UserID INT)
RETURNS TABLE
AS
RETURN
(
    SELECT userID, firstName, middleName, lastName, email, phoneNumber, type, active, createdAt
    FROM dbo.users
    WHERE userID=@UserID
);
GO

/* PART E 2/6 — ORDER FUNCTION
   Returns one customer's orders, statuses, line counts, and totals.
   For the screenshot, run demo section 2 in ddd_assignment2_procedure_trigger_demo.sql.
*/
CREATE OR ALTER FUNCTION dbo.fn_GetCustomerOrders(@CustomerID INT)
RETURNS TABLE
AS
RETURN
(
    SELECT o.orderID, o.statusID, s.statusLabel, COUNT(ol.orderLineID) AS lineCount,
           COALESCE(SUM(ol.linePrice),0) AS orderTotal
    FROM dbo.orders o
    JOIN dbo.status s ON s.statusID=o.statusID
    LEFT JOIN dbo.orderLines ol ON ol.orderID=o.orderID
    WHERE o.userID=@CustomerID
    GROUP BY o.orderID, o.statusID, s.statusLabel
);
GO

/* PART E 3/6 — PROCESSING PROCEDURE
   Changes an order's workflow status and records the transition in dbo.logs.
   The log row is written by the dbo.trg_order_status_log trigger (migration 008), which the
   UPDATE below fires; the procedure does not insert it itself, or each step would be logged
   twice. Run migration 008 after this one so the trigger exists.
   For the screenshot, run demo section 3 in ddd_assignment2_procedure_trigger_demo.sql.
*/
CREATE OR ALTER PROCEDURE dbo.sp_UpdateProcessingStatus
    @OrderID INT,
    @NewStatusID INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
        IF NOT EXISTS (SELECT 1 FROM dbo.orders WHERE orderID=@OrderID)
            THROW 51110, 'The specified order does not exist.', 1;
        IF NOT EXISTS (SELECT 1 FROM dbo.status WHERE statusID=@NewStatusID)
            THROW 51111, 'The specified status does not exist.', 1;
        DECLARE @PreviousStatusID INT;
        SELECT @PreviousStatusID=statusID FROM dbo.orders WITH (UPDLOCK,HOLDLOCK) WHERE orderID=@OrderID;
        IF @PreviousStatusID=@NewStatusID
            THROW 51112, 'The order already has the requested processing status.', 1;
        UPDATE dbo.orders SET statusID=@NewStatusID WHERE orderID=@OrderID;
        COMMIT TRANSACTION;
        SELECT @OrderID AS orderID, @PreviousStatusID AS statusBefore, @NewStatusID AS statusAfter;
    END TRY
    BEGIN CATCH
        IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

/* PART E 4/6 — RIDER PROCEDURE
   Assigns a valid RIDER account to a delivery; the trigger also checks the role.
   For the screenshot, run demo section 4 in ddd_assignment2_procedure_trigger_demo.sql.
*/
CREATE OR ALTER PROCEDURE dbo.sp_AssignDeliveryRider
    @DeliveryID INT,
    @RiderID INT
AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS (SELECT 1 FROM dbo.delivery WHERE deliverID=@DeliveryID)
        THROW 51120, 'The specified delivery does not exist.', 1;
    IF NOT EXISTS (SELECT 1 FROM dbo.users WHERE userID=@RiderID AND UPPER(type)='RIDER')
        THROW 51121, 'The selected user is not a rider.', 1;
    UPDATE dbo.delivery SET delivery_riderID=@RiderID WHERE deliverID=@DeliveryID;
    SELECT deliverID, orderID, delivery_riderID FROM dbo.delivery WHERE deliverID=@DeliveryID;
END;
GO

/* PART E 5/6 — PAYMENT PROCEDURE
   Records a positive payment against an existing order.
   For the screenshot, run demo section 5 in ddd_assignment2_procedure_trigger_demo.sql.
*/
CREATE OR ALTER PROCEDURE dbo.sp_RecordPayment
    @OrderID INT,
    @Amount DECIMAL(10,2)
AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS (SELECT 1 FROM dbo.orders WHERE orderID=@OrderID)
        THROW 51130, 'The specified order does not exist.', 1;
    IF @Amount IS NULL OR @Amount<=0
        THROW 51131, 'Payment amount must be greater than zero.', 1;
    INSERT dbo.payments(amount,orderID) VALUES(@Amount,@OrderID);
    DECLARE @PaymentID INT=CONVERT(INT,SCOPE_IDENTITY());
    SELECT paymentID, orderID, amount, paymentStatus, processedAt
    FROM dbo.payments WHERE paymentID=@PaymentID;
END;
GO

/* PART E 6/6 — SUPPORT / CSM FUNCTION
   Lists unresolved cases for the CSM support-work dashboard.
   For the screenshot, run demo section 6 in ddd_assignment2_procedure_trigger_demo.sql.
*/
CREATE OR ALTER FUNCTION dbo.fn_GetOpenSupportCases()
RETURNS TABLE
AS
RETURN
(
    SELECT feedbackID, subject, caseType, priority, caseStatus, createdAt, assigneeID
    FROM dbo.feedback
    WHERE deleted=0 AND caseStatus NOT IN ('Resolved','Closed')
);
GO
