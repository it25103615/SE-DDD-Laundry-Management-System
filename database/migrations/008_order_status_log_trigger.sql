/*
    Migration 008 - Order status log trigger
    ------------------------------------------------------------------
    Makes one database trigger responsible for the order status history.

    Until now a dbo.logs row was written only by the code that remembered
    to do it: the payment module and dbo.sp_UpdateProcessingStatus. The
    rider module changed dbo.orders.statusID directly and wrote nothing,
    so an order's history skipped pickup, delivery and "Completed".

    After this migration:
      * dbo.trg_order_status_log writes one dbo.logs row for every change
        of dbo.orders.statusID, whichever module made the change;
      * dbo.sp_UpdateProcessingStatus no longer inserts the row itself,
        otherwise each processing step would be logged twice.
    The payment module's own log writing is removed in the application
    code at the same time (PaymentService).

    Safe to run more than once (CREATE OR ALTER). Existing log rows are
    not touched and no missing history is backfilled.

    Run order for an existing database:
    003 -> 004 -> 005 -> 006 -> 007 -> 008.
    A fresh install (initialize_database.sql) already has both objects.
*/
USE laundryLinkDB;
GO

/* Writes the status history. Fires after any UPDATE of dbo.orders and adds
   one dbo.logs row for each order whose statusID actually changed.

   - "inserted" holds the rows as they are now, "deleted" as they were
     before, so joining them on orderID gives the status before and after.
   - It handles several orders updated by one statement.
   - An UPDATE that leaves the status the same (for example one that only
     changes the instructions) writes nothing.
   - New orders are not logged: this is an UPDATE trigger, and an order's
     history starts with its first status change, as before.
   - SET NOCOUNT ON keeps the row count the application sees equal to the
     number of orders it updated (the rider module checks that count).
   - The row is written in the same transaction as the UPDATE, so if the
     change is rolled back the log row goes with it. */
CREATE OR ALTER TRIGGER dbo.trg_order_status_log
ON dbo.orders AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    -- Read the clock once so the date and the time belong to the same moment.
    DECLARE @Now DATETIME2 = SYSDATETIME();
    INSERT dbo.logs(status_before,status_after,logDate,logTime,orderID)
    SELECT d.statusID, i.statusID, CONVERT(date,@Now), CONVERT(time,@Now), i.orderID
    FROM inserted i JOIN deleted d ON d.orderID=i.orderID
    WHERE COALESCE(i.statusID,-1)<>COALESCE(d.statusID,-1);
END;
GO

/* Same procedure as before, minus its INSERT into dbo.logs: the UPDATE
   below fires dbo.trg_order_status_log, which writes that row inside this
   procedure's transaction. */
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
