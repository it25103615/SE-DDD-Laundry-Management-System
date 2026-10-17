/*
    Migration 016 - Order creation log row
    ------------------------------------------------------------------
    Records when an order was made, so orders can be searched by date
    (the manager's Order lookup page).

    dbo.orders has no date column. Until now the first dbo.logs row of an
    order was written at its first status change, so an order that was
    still "Unconfirmed" had no date at all.

    After this migration:
      * PART 1 - dbo.trg_order_status_log also fires on INSERT and writes a
        "creation row" for every new order: status_before is NULL (the
        order had no status before it existed), status_after is the status
        it was created with, and logDate / logTime are the moment it was
        placed. Status changes are logged exactly as before.
      * PART 2 - every order made before this migration gets a creation row
        too. Its real placement time was never stored, so the row is a best
        guess: the date and time of the order's earliest log row; without
        one, its scheduled pickup when that is already in the past;
        otherwise the time this migration runs.

    The date an order was made is therefore the logDate of its creation
    row, which is also its earliest log row.

    Safe to run more than once: CREATE OR ALTER replaces the trigger, and
    PART 2 skips every order that already has a creation row.

    Run order for an existing database:
    003 -> 004 -> 005 -> 006 -> 007 -> 008 -> ... -> 015 -> 016.
    A fresh install (initialize_database.sql) already has this trigger.
*/
USE laundryLinkDB;
GO

/* PART 1 - the status history trigger, now for new orders as well.

   "inserted" holds the rows as they are now and "deleted" as they were
   before. A new order has no row in "deleted", so the LEFT JOIN leaves d
   empty for it: the WHERE keeps it (d.orderID IS NULL) and d.statusID
   gives the NULL status_before of a creation row. For an UPDATE both rows
   exist and the row is logged only when statusID actually changed, as in
   migration 008. */
CREATE OR ALTER TRIGGER dbo.trg_order_status_log
ON dbo.orders AFTER INSERT, UPDATE AS
BEGIN
    SET NOCOUNT ON;
    -- Read the clock once so the date and the time belong to the same moment.
    DECLARE @Now DATETIME2 = SYSDATETIME();
    INSERT dbo.logs(status_before,status_after,logDate,logTime,orderID)
    SELECT d.statusID, i.statusID, CONVERT(date,@Now), CONVERT(time,@Now), i.orderID
    FROM inserted i LEFT JOIN deleted d ON d.orderID=i.orderID
    WHERE d.orderID IS NULL OR COALESCE(i.statusID,-1)<>COALESCE(d.statusID,-1);
END;
GO

/* PART 2 - creation rows for the orders that already exist.

   firstLog is the order's earliest log row. The status the order had
   before that first change is the status it was created with, so it
   becomes status_after of the creation row; an order with no log rows is
   still at the status it was created with. */
DECLARE @Now DATETIME2 = SYSDATETIME();

INSERT dbo.logs(status_before,status_after,logDate,logTime,orderID)
SELECT NULL,
       COALESCE(firstLog.status_before, o.statusID),
       COALESCE(firstLog.logDate, CONVERT(date,pickup.scheduled), CONVERT(date,@Now)),
       CASE WHEN firstLog.logDate IS NOT NULL THEN firstLog.logTime
            WHEN pickup.scheduled IS NOT NULL THEN CONVERT(time,pickup.scheduled)
            ELSE CONVERT(time,@Now) END,
       o.orderID
FROM dbo.orders o
OUTER APPLY (SELECT TOP (1) l.status_before, l.logDate, l.logTime
             FROM dbo.logs l WHERE l.orderID=o.orderID
             ORDER BY l.logDate, l.logTime, l.logID) firstLog
OUTER APPLY (SELECT MIN(d.pickup_scheduled) AS scheduled
             FROM dbo.delivery d WHERE d.orderID=o.orderID AND d.pickup_scheduled<@Now) pickup
WHERE NOT EXISTS (SELECT 1 FROM dbo.logs c WHERE c.orderID=o.orderID AND c.status_before IS NULL);

PRINT CONCAT('Creation rows added for existing orders: ', @@ROWCOUNT);
GO
