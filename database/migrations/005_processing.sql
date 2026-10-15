/*
    Migration 005 - Laundry processing module
    ------------------------------------------------------------------
    Adds the two tables the processing module (staff pages under
    /html/staff and the /api/processing endpoints) needs, then seeds the
    orders used by the laundry processing test cases (TC-LP01 to TC-LP10).

    Safe to run more than once:
      * each table is created only when it does not exist yet;
      * the test orders are created only once (see PART 2).

    Run order for an existing database: 003 -> 004 -> 005.
    A fresh install (initialize_database.sql) already contains the tables,
    so running this script afterwards only adds the test orders.
*/
USE laundryLinkDB;
GO

/* ================================================================
   PART 1 - TABLES
   ================================================================ */

/* receivedItems: what staff actually counted for each order line when the
   bags arrived at the shop (TC-LP01 to LP04). One row per order line; a
   re-count replaces the row. Kept separate from orderLines, which belongs
   to the orders module. */
IF OBJECT_ID('dbo.receivedItems', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.receivedItems(
        orderLineID      INT          NOT NULL CONSTRAINT pk_receivedItems PRIMARY KEY,
        receivedQuantity INT          NOT NULL,
        itemCondition    VARCHAR(20)  NOT NULL CONSTRAINT df_receivedItems_condition DEFAULT 'As expected',
        receivedBy       INT          NOT NULL,
        receivedAt       DATETIME2    NOT NULL CONSTRAINT df_receivedItems_receivedAt DEFAULT SYSDATETIME(),

        CONSTRAINT fk_receivedItems_orderLines FOREIGN KEY(orderLineID) REFERENCES dbo.orderLines(orderLineID),
        CONSTRAINT fk_receivedItems_users FOREIGN KEY(receivedBy) REFERENCES dbo.users(userID),
        -- check to make sure a received quantity below 1 can never be stored.
        CONSTRAINT ck_receivedItems_quantity CHECK(receivedQuantity >= 1),
        -- Same options as the condition drop-down on receive_items.html.
        CONSTRAINT ck_receivedItems_condition CHECK(itemCondition IN ('As expected', 'Stained', 'Damaged'))
    );
END;
GO

/* Issues staff report during processing (TC-LP06) need no table: each one is
   a support case in feedback, with caseType = the issue type ('Damaged item',
   'Existing stain', 'Missing item' or 'Item count mismatch'), subject = the
   item, userID = the order's customer, and the reporting staff member recorded
   in support_activity. */

/* qualityChecks: the quality check done after Ironing.
   The newest row for an order is its current check. A failed check must
   name the stage the order goes back to; a passed check must not. */
IF OBJECT_ID('dbo.qualityChecks', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.qualityChecks(
        checkID        INT IDENTITY(1, 1) CONSTRAINT pk_qualityChecks PRIMARY KEY,
        orderID        INT          NOT NULL,
        result         VARCHAR(10)  NOT NULL,
        reworkStatusID INT          NULL,
        packed         BIT          NOT NULL CONSTRAINT df_qualityChecks_packed DEFAULT 0,
        notes          VARCHAR(250) NULL,
        checkedBy      INT          NOT NULL,
        checkedAt      DATETIME2    NOT NULL CONSTRAINT df_qualityChecks_checkedAt DEFAULT SYSDATETIME(),

        CONSTRAINT fk_qualityChecks_orders FOREIGN KEY(orderID) REFERENCES dbo.orders(orderID),
        CONSTRAINT fk_qualityChecks_status FOREIGN KEY(reworkStatusID) REFERENCES dbo.status(statusID),
        CONSTRAINT fk_qualityChecks_users FOREIGN KEY(checkedBy) REFERENCES dbo.users(userID),
        CONSTRAINT ck_qualityChecks_result CHECK(result IN ('Passed', 'Failed')),
        -- Rework stages: 9 Washing, 10 Drying, 11 Ironing, 19 Dry Clean.
        CONSTRAINT ck_qualityChecks_rework CHECK(
            (result = 'Failed' AND reworkStatusID IN (9, 10, 11, 19))
            OR (result = 'Passed' AND reworkStatusID IS NULL))
    );
END;
GO

/* ================================================================
   PART 2 - TEST DATA FOR THE PROCESSING TEST CASES
   ================================================================
   Other modules' tests rely on the sample orders #1-#5 from
   database/ddd_assignment2_sample_data.sql, so this part only runs AFTER
   that script has been loaded and adds NEW orders at the end:

     In Shop order    (TC-LP01 to LP06)  Shirt / Blouse x4, Trousers / Skirt x2,
                                          customer instruction "Ring bell twice"
     Washing order    (TC-LP07 to LP10)  Shirt / Blouse x3, wash route
     Dry clean order  (route check)      Two-Piece Suit x1, at Verifying Items

   On a fresh database loaded with the sample data these become orders #6,
   #7 and #8. The final SELECT prints the real IDs.
*/
SET XACT_ABORT ON;
SET NOCOUNT ON;

IF NOT EXISTS (SELECT 1 FROM dbo.users WHERE email = 'ayesha.fernando@assignment.laundrylink.lk')
BEGIN
    PRINT 'Sample data not loaded yet - skipping processing test orders. Run database/ddd_assignment2_sample_data.sql, then run this migration again.';
END
ELSE
BEGIN TRY
    BEGIN TRANSACTION;

    -- Look up catalogue IDs by name: IDs differ between fresh and upgraded databases.
    DECLARE @Wash  INT = (SELECT TOP(1) serviceID FROM dbo.services WHERE serviceName = 'Wash and Fold');
    DECLARE @Iron  INT = (SELECT TOP(1) serviceID FROM dbo.services WHERE serviceName = 'Ironing');
    DECLARE @Dry   INT = (SELECT TOP(1) serviceID FROM dbo.services WHERE serviceName = 'Dry Cleaning');
    DECLARE @Shirt    INT = (SELECT TOP(1) itemID FROM dbo.items WHERE itemName = 'Shirt / Blouse');
    DECLARE @Trousers INT = (SELECT TOP(1) itemID FROM dbo.items WHERE itemName = 'Trousers / Skirt');
    DECLARE @Suit     INT = (SELECT TOP(1) itemID FROM dbo.items WHERE itemName = 'Two-Piece Suit');

    -- Make sure every item/service price the test orders use exists.
    MERGE dbo.servicePricing AS target
    USING (VALUES
        (@Iron, @Shirt,    CAST(150.00  AS DECIMAL(10,2))),
        (@Iron, @Trousers, CAST(180.00  AS DECIMAL(10,2))),
        (@Wash, @Shirt,    CAST(220.00  AS DECIMAL(10,2))),
        (@Dry,  @Suit,     CAST(1500.00 AS DECIMAL(10,2)))
    ) AS source(serviceID, itemID, price)
       ON target.serviceID = source.serviceID AND target.itemID = source.itemID
    WHEN NOT MATCHED THEN INSERT(serviceID, itemID, price) VALUES(source.serviceID, source.itemID, source.price);

    -- A dedicated customer, so the test instruction does not change anyone else's address.
    -- Password: LaundryLink1! (same BCrypt hash as the other assignment accounts).
    IF NOT EXISTS (SELECT 1 FROM dbo.users WHERE email = 'priya.fernando@assignment.laundrylink.lk')
        INSERT dbo.users(firstName, lastName, email, password, phoneNumber, type)
        VALUES('Priya', 'Fernando', 'priya.fernando@assignment.laundrylink.lk',
               '$2a$10$JubJfTSRWCO3oTOBnrayR.hcBzzgjHMFAcXUpGvDT2/vAwCiB5M7O', '0712345620', 'CUSTOMER');
    DECLARE @Customer INT = (SELECT userID FROM dbo.users WHERE email = 'priya.fernando@assignment.laundrylink.lk');

    -- TC-LP05 reads the delivery instruction from the customer's default address.
    IF NOT EXISTS (SELECT 1 FROM dbo.addresses WHERE userID = @Customer AND nickname = 'Home')
        INSERT dbo.addresses(nickname, street, city, state, DeliveryInstructions, isDefault, userID)
        VALUES('Home', '5 Flower Road', 'Colombo 07', 'Western', 'Ring bell twice', 1, @Customer);

    -- Create the three orders only once: the test runs move their statuses,
    -- so "does this customer already have orders" is the rerun check.
    IF NOT EXISTS (SELECT 1 FROM dbo.orders WHERE userID = @Customer)
    BEGIN
        DECLARE @InShop INT, @Washing INT, @DryClean INT;
        -- Arrival log times are set relative to the server clock (90, 60 and 30 minutes ago),
        -- so they always come before anything the tests do, whatever the server's time zone.
        DECLARE @Arrived90 DATETIME2 = DATEADD(MINUTE, -90, SYSDATETIME());
        DECLARE @Arrived60 DATETIME2 = DATEADD(MINUTE, -60, SYSDATETIME());
        DECLARE @Arrived30 DATETIME2 = DATEADD(MINUTE, -30, SYSDATETIME());

        INSERT dbo.orders(statusID, userID) VALUES(7, @Customer);   -- 7 In Shop
        SET @InShop = CONVERT(INT, SCOPE_IDENTITY());
        INSERT dbo.orderLines(orderID, itemID, serviceID, quantity, linePrice) VALUES
            (@InShop, @Shirt,    @Iron, 4, 600.00),
            (@InShop, @Trousers, @Iron, 2, 360.00);
        INSERT dbo.logs(status_before, status_after, logDate, logTime, orderID)
            VALUES(6, 7, CONVERT(DATE, @Arrived90), CONVERT(TIME(0), @Arrived90), @InShop);

        INSERT dbo.orders(statusID, userID) VALUES(9, @Customer);   -- 9 Washing
        SET @Washing = CONVERT(INT, SCOPE_IDENTITY());
        INSERT dbo.orderLines(orderID, itemID, serviceID, quantity, linePrice) VALUES
            (@Washing, @Shirt, @Wash, 3, 660.00);
        INSERT dbo.logs(status_before, status_after, logDate, logTime, orderID)
            VALUES(8, 9, CONVERT(DATE, @Arrived60), CONVERT(TIME(0), @Arrived60), @Washing);

        INSERT dbo.orders(statusID, userID) VALUES(8, @Customer);   -- 8 Verifying Items
        SET @DryClean = CONVERT(INT, SCOPE_IDENTITY());
        INSERT dbo.orderLines(orderID, itemID, serviceID, quantity, linePrice) VALUES
            (@DryClean, @Suit, @Dry, 1, 1500.00);
        INSERT dbo.logs(status_before, status_after, logDate, logTime, orderID)
            VALUES(7, 8, CONVERT(DATE, @Arrived30), CONVERT(TIME(0), @Arrived30), @DryClean);

        -- The Washing and Dry clean orders are already past receiving, so record their counts
        -- (as the sample staff member) like the Receive Items page would have.
        DECLARE @Staff INT = (SELECT TOP(1) userID FROM dbo.users WHERE email = 'sam@staff.com');
        IF @Staff IS NOT NULL
            INSERT dbo.receivedItems(orderLineID, receivedQuantity, itemCondition, receivedBy)
            SELECT orderLineID, quantity, 'As expected', @Staff
            FROM dbo.orderLines WHERE orderID IN (@Washing, @DryClean);
    END;

    COMMIT TRANSACTION;

    -- Show which order IDs the test cases should use.
    SELECT o.orderID, s.statusLabel,
           CASE o.statusID WHEN 7 THEN 'TC-LP01 to LP06' WHEN 9 THEN 'TC-LP07 to LP10' ELSE 'Dry clean route' END AS usedBy
    FROM dbo.orders o JOIN dbo.status s ON s.statusID = o.statusID
    WHERE o.userID = @Customer
    ORDER BY o.orderID;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
GO
