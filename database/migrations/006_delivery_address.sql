/*
    Migration 006 - Delivery address
    ------------------------------------------------------------------
    Lets each order remember which of the customer's saved addresses it is
    collected from and returned to. Until now the delivery table had no
    address at all, so riders were always sent to the customer's default
    address, whatever the customer picked on the schedule step.

    Safe to run more than once:
      * the column and its foreign key are added only when missing;
      * the backfill only touches rows that have no address yet.

    Run order for an existing database: 003 -> 004 -> 005 -> 006.
    A fresh install (initialize_database.sql) already has the column, so
    running this script afterwards only backfills rows without an address
    (for example the orders created by the sample data script).
*/
USE laundryLinkDB;
GO

/* ================================================================
   PART 1 - COLUMN
   ================================================================ */

/* delivery.addressID: the saved address chosen for this order.
   NULL means "use the customer's default address". */
IF COL_LENGTH('dbo.delivery', 'addressID') IS NULL
BEGIN
    ALTER TABLE dbo.delivery ADD addressID INT NULL;
END;
GO

/* The application never removes an address row: when a customer deletes an
   address it only loses its userID (and isDefault becomes 0), so delivery rows
   keep pointing at it. SET NULL is a safety net for a row removed directly in
   the database: the delivery row is kept and its addressID becomes NULL (the
   customer's default address is used again). */
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'delivery_addresses_fk')
BEGIN
    ALTER TABLE dbo.delivery
        ADD CONSTRAINT delivery_addresses_fk FOREIGN KEY(addressID)
            REFERENCES dbo.addresses(addressID) ON DELETE SET NULL;
END;
GO

/* ================================================================
   PART 2 - BACKFILL
   ================================================================ */

/* Existing delivery rows were created before the column existed. Give each
   one the customer's current default address, which is the address riders
   were already being sent to. Rows whose customer has no default address
   stay NULL. */
UPDATE d
SET d.addressID = (
        SELECT TOP 1 a.addressID
        FROM dbo.addresses a
        WHERE a.userID = d.userID AND a.isDefault = 1
        ORDER BY a.addressID DESC)
FROM dbo.delivery d
WHERE d.addressID IS NULL;
GO
