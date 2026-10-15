/*
    Migration 007 - Order instructions
    ------------------------------------------------------------------
    Lets each order remember what the customer asked for on the
    "Instructions" step of the new-order form. Until now the ticked
    preferences and the note were kept only in the browser and were lost
    when the order was placed, so staff always saw "No instructions".

    Safe to run more than once: each column is added only when missing.
    Nothing is backfilled: orders placed before this migration keep NULL
    in both columns, which the application shows as "None".

    Run order for an existing database: 003 -> 004 -> 005 -> 006 -> 007.
    A fresh install (initialize_database.sql) already has both columns, so
    running this script afterwards changes nothing.
*/
USE laundryLinkDB;
GO

/* orders.instructions: the customer's free-text note for the laundry team.
   NULL means the customer wrote nothing. */
IF COL_LENGTH('dbo.orders', 'instructions') IS NULL
BEGIN
    ALTER TABLE dbo.orders ADD instructions VARCHAR(500) NULL;
END;
GO

/* orders.preferences: the ticked options as a comma-separated list of codes,
   for example 'fragrance-free,hangers'. The allowed codes are listed in
   OrderPreference.java. NULL means nothing was ticked. */
IF COL_LENGTH('dbo.orders', 'preferences') IS NULL
BEGIN
    ALTER TABLE dbo.orders ADD preferences VARCHAR(100) NULL;
END;
GO
