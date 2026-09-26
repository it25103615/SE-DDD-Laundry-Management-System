/*
    LaundryLink - IT2140 DDD Assignment Part 02 refinements
    Safe migration for an existing SQL Server database.

    The preflight block deliberately fails before changing the schema when
    existing data is incompatible. The obsolete, unused system_settings table
    is removed; no application business rows are deleted or rewritten.
*/
USE laundryLinkDB;
GO

SET XACT_ABORT ON;
SET NOCOUNT ON;

BEGIN TRY
    BEGIN TRANSACTION;

    IF COL_LENGTH('users', 'active') IS NULL
        ALTER TABLE users ADD active BIT NOT NULL CONSTRAINT df_users_active DEFAULT 1;
    IF COL_LENGTH('users', 'createdAt') IS NULL
        ALTER TABLE users ADD createdAt DATETIME2 NOT NULL CONSTRAINT df_users_created DEFAULT SYSDATETIME();
    IF COL_LENGTH('users', 'updatedAt') IS NULL
        ALTER TABLE users ADD updatedAt DATETIME2 NOT NULL CONSTRAINT df_users_updated DEFAULT SYSDATETIME();
    IF COL_LENGTH('users', 'version') IS NULL
        ALTER TABLE users ADD version INT NOT NULL CONSTRAINT df_users_version DEFAULT 0;
    IF COL_LENGTH('services', 'description') IS NULL
        ALTER TABLE services ADD description NVARCHAR(250) NOT NULL CONSTRAINT df_services_description DEFAULT 'Laundry service';
    IF COL_LENGTH('services', 'turnaroundHours') IS NULL
        ALTER TABLE services ADD turnaroundHours INT NOT NULL CONSTRAINT df_services_turnaround DEFAULT 48;
    IF COL_LENGTH('services', 'active') IS NULL
        ALTER TABLE services ADD active BIT NOT NULL CONSTRAINT df_services_active DEFAULT 1;
    IF COL_LENGTH('services', 'version') IS NULL
        ALTER TABLE services ADD version INT NOT NULL CONSTRAINT df_services_version DEFAULT 0;
    IF COL_LENGTH('payments', 'paymentStatus') IS NULL
        ALTER TABLE payments ADD paymentStatus VARCHAR(20) NOT NULL CONSTRAINT df_payments_status DEFAULT 'PAID';
    IF COL_LENGTH('payments', 'processedAt') IS NULL
        ALTER TABLE payments ADD processedAt DATETIME2 NOT NULL CONSTRAINT df_payments_processed DEFAULT SYSDATETIME();
    IF OBJECT_ID('dbo.notifications', 'U') IS NULL
    BEGIN
        CREATE TABLE dbo.notifications(
            notificationID BIGINT IDENTITY PRIMARY KEY,
            recipientID INT NOT NULL REFERENCES users(userID),
            category VARCHAR(30) NOT NULL,
            title NVARCHAR(100) NOT NULL,
            message NVARCHAR(300) NOT NULL,
            link VARCHAR(300) NOT NULL,
            relatedType VARCHAR(30) NULL,
            relatedID INT NULL,
            isRead BIT NOT NULL DEFAULT 0,
            createdAt DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
            readAt DATETIME2 NULL
        );
        CREATE INDEX ix_notifications_recipient ON dbo.notifications(recipientID,isRead,createdAt DESC);
    END;

    /* Preflight: prove that all conversions and new constraints are safe. */
    IF EXISTS (
        SELECT 1 FROM users
        WHERE type IS NULL OR UPPER(LTRIM(RTRIM(type))) NOT IN
              ('CUSTOMER','RIDER','STAFF','MANAGER','OWNER','ADMIN','CSM','CUSTOMER_SERVICE_MANAGER')
    )
        THROW 51000, 'Migration stopped: users contains an unsupported role. Review the rows before retrying.', 1;

    IF EXISTS (
        SELECT 1
        FROM servicePricing sp
        LEFT JOIN services s ON s.serviceID = sp.serviceID
        LEFT JOIN items i ON i.itemID = sp.itemID
        WHERE s.serviceID IS NULL OR i.itemID IS NULL
    )
        THROW 51001, 'Migration stopped: servicePricing contains orphaned service or item references.', 1;

    IF EXISTS (SELECT 1 FROM orderLines WHERE quantity <= 0)
        THROW 51002, 'Migration stopped: orderLines contains a non-positive quantity.', 1;

    IF EXISTS (SELECT 1 FROM servicePricing WHERE price IS NULL)
        THROW 51005, 'Migration stopped: servicePricing contains a NULL price.', 1;

    IF EXISTS (SELECT 1 FROM servicePricing WHERE price < 0)
       OR EXISTS (SELECT 1 FROM orderLines WHERE linePrice < 0)
       OR EXISTS (SELECT 1 FROM payments WHERE amount < 0)
        THROW 51003, 'Migration stopped: a monetary column contains a negative value.', 1;

    IF EXISTS (SELECT 1 FROM servicePricing WHERE price > 99999999.99 OR ABS(price - ROUND(price, 2)) > 0.000001)
       OR EXISTS (SELECT 1 FROM orderLines WHERE linePrice > 99999999.99 OR ABS(linePrice - ROUND(linePrice, 2)) > 0.000001)
       OR EXISTS (SELECT 1 FROM payments WHERE amount > 99999999.99 OR ABS(amount - ROUND(amount, 2)) > 0.000001)
        THROW 51004, 'Migration stopped: a monetary value cannot be represented exactly as DECIMAL(10,2).', 1;

    /* Normalize supported role spelling before enforcing the discriminator. */
    UPDATE users SET type = UPPER(LTRIM(RTRIM(type)))
    WHERE type <> UPPER(LTRIM(RTRIM(type)));

    /* The longest supported compatibility alias exceeds the original VARCHAR(20). */
    ALTER TABLE users ALTER COLUMN type VARCHAR(30) NOT NULL;

    /* FLOAT is approximate and is unsuitable for stored currency. */
    IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID('servicePricing') AND name='price' AND system_type_id=62)
        ALTER TABLE servicePricing ALTER COLUMN price DECIMAL(10,2) NOT NULL;
    IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID('orderLines') AND name='linePrice' AND system_type_id=62)
        ALTER TABLE orderLines ALTER COLUMN linePrice DECIMAL(10,2) NOT NULL;
    IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID('payments') AND name='amount' AND system_type_id=62)
        ALTER TABLE payments ALTER COLUMN amount DECIMAL(10,2) NOT NULL;

    /* Restore the two parent links omitted from the original junction table. */
    IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name='servicePricing_services_fk')
        ALTER TABLE servicePricing WITH CHECK ADD CONSTRAINT servicePricing_services_fk
            FOREIGN KEY(serviceID) REFERENCES services(serviceID);
    IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name='servicePricing_items_fk')
        ALTER TABLE servicePricing WITH CHECK ADD CONSTRAINT servicePricing_items_fk
            FOREIGN KEY(itemID) REFERENCES items(itemID);

    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name='ck_orderLines_quantity_positive')
        ALTER TABLE orderLines WITH CHECK ADD CONSTRAINT ck_orderLines_quantity_positive CHECK(quantity > 0);
    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name='ck_servicePricing_price_nonnegative')
        ALTER TABLE servicePricing WITH CHECK ADD CONSTRAINT ck_servicePricing_price_nonnegative CHECK(price >= 0);
    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name='ck_orderLines_linePrice_nonnegative')
        ALTER TABLE orderLines WITH CHECK ADD CONSTRAINT ck_orderLines_linePrice_nonnegative CHECK(linePrice >= 0);
    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name='ck_payments_amount_nonnegative')
        ALTER TABLE payments WITH CHECK ADD CONSTRAINT ck_payments_amount_nonnegative CHECK(amount >= 0);
    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name='ck_users_type')
        ALTER TABLE users WITH CHECK ADD CONSTRAINT ck_users_type CHECK
            (type IN ('CUSTOMER','RIDER','STAFF','MANAGER','OWNER','ADMIN','CSM','CUSTOMER_SERVICE_MANAGER'));
    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name='ck_services_turnaround')
        EXEC('ALTER TABLE services WITH CHECK ADD CONSTRAINT ck_services_turnaround CHECK(turnaroundHours BETWEEN 1 AND 720)');

    IF OBJECT_ID('dbo.system_settings', 'U') IS NOT NULL
        DROP TABLE dbo.system_settings;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
GO

/* One procedure owns the status update and its audit log as one transaction. */
CREATE OR ALTER PROCEDURE dbo.sp_UpdateOrderStatus
    @OrderID INT,
    @NewStatusID INT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    BEGIN TRY
        BEGIN TRANSACTION;

        IF NOT EXISTS (SELECT 1 FROM dbo.orders WHERE orderID=@OrderID)
            THROW 51010, 'The specified order does not exist.', 1;
        IF NOT EXISTS (SELECT 1 FROM dbo.status WHERE statusID=@NewStatusID)
            THROW 51011, 'The specified status does not exist.', 1;

        DECLARE @PreviousStatusID INT;
        SELECT @PreviousStatusID=statusID
        FROM dbo.orders WITH (UPDLOCK, HOLDLOCK)
        WHERE orderID=@OrderID;

        IF @PreviousStatusID=@NewStatusID
            THROW 51012, 'The order already has the requested status.', 1;

        UPDATE dbo.orders SET statusID=@NewStatusID WHERE orderID=@OrderID;
        INSERT INTO dbo.logs(status_before,status_after,logDate,logTime,orderID)
        VALUES(@PreviousStatusID,@NewStatusID,CONVERT(date,SYSDATETIME()),CONVERT(time,SYSDATETIME()),@OrderID);

        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

CREATE OR ALTER TRIGGER dbo.trg_order_notifications
ON dbo.orders AFTER INSERT, UPDATE AS
BEGIN
    SET NOCOUNT ON;
    INSERT dbo.notifications(recipientID,category,title,message,link,relatedType,relatedID)
    SELECT i.userID,'ORDER','Order placed',CONCAT('Order #',i.orderID,' was placed successfully.'),CONCAT('/html/customer/order_details.html?orderId=',i.orderID),'ORDER',i.orderID
    FROM inserted i LEFT JOIN deleted d ON d.orderID=i.orderID WHERE d.orderID IS NULL;
    INSERT dbo.notifications(recipientID,category,title,message,link,relatedType,relatedID)
    SELECT i.userID,'ORDER','Order status updated',CONCAT('Order #',i.orderID,' is now ',COALESCE(s.statusLabel,'updated'),'.'),CONCAT('/html/customer/order_details.html?orderId=',i.orderID),'ORDER',i.orderID
    FROM inserted i JOIN deleted d ON d.orderID=i.orderID LEFT JOIN status s ON s.statusID=i.statusID
    WHERE COALESCE(i.statusID,-1)<>COALESCE(d.statusID,-1);
END;
GO

CREATE OR ALTER TRIGGER dbo.trg_payment_notifications
ON dbo.payments AFTER INSERT, UPDATE AS
BEGIN
    SET NOCOUNT ON;
    INSERT dbo.notifications(recipientID,category,title,message,link,relatedType,relatedID)
    SELECT o.userID,'PAYMENT','Payment accepted',CONCAT('Payment for order #',i.orderID,' was ',LOWER(i.paymentStatus),'.'),CONCAT('/html/customer/receipt.html?orderId=',i.orderID),'PAYMENT',i.paymentID
    FROM inserted i JOIN orders o ON o.orderID=i.orderID LEFT JOIN deleted d ON d.paymentID=i.paymentID
    WHERE i.paymentStatus IN('PAID','VERIFIED') AND (d.paymentID IS NULL OR COALESCE(d.paymentStatus,'')<>i.paymentStatus);
END;
GO

CREATE OR ALTER TRIGGER dbo.trg_delivery_assignment_notifications
ON dbo.delivery AFTER INSERT, UPDATE AS
BEGIN
    SET NOCOUNT ON;
    INSERT dbo.notifications(recipientID,category,title,message,link,relatedType,relatedID)
    SELECT i.pickup_riderID,'ASSIGNMENT','Pickup assigned',CONCAT('You were assigned pickup task #',i.deliverID,' for order #',i.orderID,'.'),CONCAT('/html/rider/task_list.html?taskId=',i.deliverID),'DELIVERY',i.deliverID
    FROM inserted i LEFT JOIN deleted d ON d.deliverID=i.deliverID WHERE i.pickup_riderID IS NOT NULL AND COALESCE(d.pickup_riderID,-1)<>i.pickup_riderID;
    INSERT dbo.notifications(recipientID,category,title,message,link,relatedType,relatedID)
    SELECT i.delivery_riderID,'ASSIGNMENT','Delivery assigned',CONCAT('You were assigned delivery task #',i.deliverID,' for order #',i.orderID,'.'),CONCAT('/html/rider/task_list.html?taskId=',i.deliverID),'DELIVERY',i.deliverID
    FROM inserted i LEFT JOIN deleted d ON d.deliverID=i.deliverID WHERE i.delivery_riderID IS NOT NULL AND COALESCE(d.delivery_riderID,-1)<>i.delivery_riderID;
    INSERT dbo.notifications(recipientID,category,title,message,link,relatedType,relatedID)
    SELECT o.userID,'DELIVERY','Rider assigned',CONCAT('A rider was assigned for order #',i.orderID,'.'),CONCAT('/html/customer/order_details.html?orderId=',i.orderID),'ORDER',i.orderID
    FROM inserted i JOIN orders o ON o.orderID=i.orderID LEFT JOIN deleted d ON d.deliverID=i.deliverID
    WHERE (i.pickup_riderID IS NOT NULL AND COALESCE(d.pickup_riderID,-1)<>i.pickup_riderID) OR (i.delivery_riderID IS NOT NULL AND COALESCE(d.delivery_riderID,-1)<>i.delivery_riderID);
END;
GO

/* Multi-row-safe subtype rule for both pickup and delivery assignments. */
CREATE OR ALTER TRIGGER dbo.trg_delivery_rider_check
ON dbo.delivery
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    IF EXISTS (
        SELECT 1 FROM inserted i
        LEFT JOIN dbo.users u ON u.userID=i.pickup_riderID
        WHERE i.pickup_riderID IS NOT NULL AND (u.userID IS NULL OR u.type <> 'RIDER')
    ) OR EXISTS (
        SELECT 1 FROM inserted i
        LEFT JOIN dbo.users u ON u.userID=i.delivery_riderID
        WHERE i.delivery_riderID IS NOT NULL AND (u.userID IS NULL OR u.type <> 'RIDER')
    )
        THROW 51020, 'Pickup and delivery riders must reference users whose type is RIDER.', 1;
END;
GO
