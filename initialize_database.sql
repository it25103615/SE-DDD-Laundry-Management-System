-- ============================================================
-- ======= Create Database if it does not exist - Start =======
IF DB_ID('laundryLinkDB') IS NOT NULL
BEGIN
    PRINT 'Database exists. Using Database';
END
ELSE
BEGIN
    PRINT 'Database does not exist. Creating database';
    CREATE DATABASE laundryLinkDB;
END
GO
-- ======== Create Database if it does not exist - End ========
-- ============================================================

-- ============================================================
-- ============= Use the Project Database - Start =============
USE laundryLinkDB;
GO
-- ============== Use the Project Database - End ==============
-- ============================================================

-- ============================================================
-- =============== Drop Existing Tables - Start ===============

IF OBJECT_ID('dbo.trg_delivery_rider_check', 'TR') IS NOT NULL
    DROP TRIGGER dbo.trg_delivery_rider_check;
IF OBJECT_ID('dbo.sp_UpdateOrderStatus', 'P') IS NOT NULL
    DROP PROCEDURE dbo.sp_UpdateOrderStatus;
IF OBJECT_ID('dbo.support_activity', 'U') IS NOT NULL
    DROP TABLE dbo.support_activity;
IF OBJECT_ID('dbo.notifications', 'U') IS NOT NULL
    DROP TABLE dbo.notifications;
IF OBJECT_ID('dbo.delivery', 'U') IS NOT NULL
    DROP TABLE dbo.delivery;
IF OBJECT_ID('dbo.chat', 'U') IS NOT NULL
    DROP TABLE dbo.chat;
IF OBJECT_ID('dbo.feedback', 'U') IS NOT NULL
    DROP TABLE dbo.feedback;
IF OBJECT_ID('dbo.payments', 'U') IS NOT NULL
    DROP TABLE dbo.payments;
IF OBJECT_ID('dbo.logs', 'U') IS NOT NULL
    DROP TABLE dbo.logs;
IF OBJECT_ID('dbo.orderLines', 'U') IS NOT NULL
    DROP TABLE dbo.orderLines
IF OBJECT_ID('dbo.orders', 'U') IS NOT NULL
    DROP TABLE dbo.orders;
IF OBJECT_ID('dbo.addresses', 'U') IS NOT NULL
    DROP TABLE dbo.addresses;
IF OBJECT_ID('dbo.status', 'U') IS NOT NULL
    DROP TABLE dbo.status;
IF OBJECT_ID('dbo.items', 'U') IS NOT NULL
    DROP TABLE dbo.items;
IF OBJECT_ID('dbo.services', 'U') IS NOT NULL
    DROP TABLE dbo.services;
IF OBJECT_ID('dbo.servicePricing', 'U') IS NOT NULL
    DROP TABLE dbo.servicePricing;
IF OBJECT_ID('dbo.users', 'U') IS NOT NULL
    DROP TABLE dbo.users;

GO
-- ================ Drop Existing Tables - END ================
-- ============================================================

-- ============================================================
-- ============== Initialize Status Table - Start =============

CREATE TABLE status(
    statusID INTEGER PRIMARY KEY,
    statusLabel VARCHAR(20)
);
GO

-- =============== Initialize Status Table - End ==============
-- ============================================================

-- ============================================================
-- ============== Initialize Items Table - Start ==============

CREATE TABLE items(
    itemID INTEGER IDENTITY(1, 1) PRIMARY KEY,
    itemName VARCHAR(30)
);
GO

-- =============== Initialize Items Table - End ===============
-- ============================================================

-- ============================================================
-- ============= Initialize Services Table - Start ============

CREATE TABLE services(
    serviceID INTEGER IDENTITY(1, 1) PRIMARY KEY,
    serviceName VARCHAR(30) NOT NULL,
    description NVARCHAR(250) NOT NULL DEFAULT 'Laundry service',
    turnaroundHours INT NOT NULL DEFAULT 48 CHECK(turnaroundHours BETWEEN 1 AND 720),
    active BIT NOT NULL DEFAULT 1,
    version INT NOT NULL DEFAULT 0
);
GO

-- ============== Initialize Services Table - End =============
-- ============================================================

-- ============================================================
-- ========== Initialize ServicePricing Table - Start =========

CREATE TABLE servicePricing(
    serviceID INTEGER,
    itemID INTEGER,
    price DECIMAL(10,2) NOT NULL,

    PRIMARY KEY (itemID, serviceID),
    CONSTRAINT servicePricing_services_fk FOREIGN KEY(serviceID) REFERENCES services(serviceID),
    CONSTRAINT servicePricing_items_fk FOREIGN KEY(itemID) REFERENCES items(itemID),
    CONSTRAINT ck_servicePricing_price_nonnegative CHECK(price >= 0)
);

-- =========== Initialize ServicePricing Table - End ==========
-- ============================================================

-- ============================================================
-- =============== Initialize User Table - Start ==============

CREATE TABLE users(
    userID INTEGER IDENTITY(1, 1) PRIMARY KEY,
    firstName VARCHAR(50),
    middleName VARCHAR(50),
    lastName VARCHAR(50),
    email VARCHAR(100) UNIQUE,
    password VARCHAR(100) NULL,
    phoneNumber CHAR(10),
    type VARCHAR(30) NOT NULL,
    active BIT NOT NULL DEFAULT 1,
    createdAt DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    updatedAt DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    version INT NOT NULL DEFAULT 0,

    CONSTRAINT users_phoneNumber_format CHECK (phoneNumber LIKE '[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]'),
    CONSTRAINT ck_users_type CHECK(type IN ('CUSTOMER','RIDER','STAFF','MANAGER','OWNER','ADMIN','CSM','CUSTOMER_SERVICE_MANAGER'))
);
GO

-- ================ Initialize User Table - End ===============
-- ============================================================

-- ============================================================
-- ============ Initialize Addresses Table - Start ============

CREATE TABLE addresses(
    addressID INTEGER IDENTITY(1, 1) PRIMARY KEY, 
    nickname VARCHAR(50), 
    street VARCHAR(100), 
    city VARCHAR(30),
    state VARCHAR(30), 
    DeliveryInstructions VARCHAR(250), 
    isDefault BIT NOT NULL DEFAULT 0, 
    userID INTEGER,

    CONSTRAINT address_user_fk FOREIGN KEY(userID)
        REFERENCES users(userID)
);
GO

-- ============= Initialize Addresses Table - End =============
-- ============================================================

-- ============================================================
-- ============== Initialize Orders Table - Start =============

CREATE TABLE orders(
    orderID INTEGER IDENTITY(1, 1) PRIMARY KEY,
    statusID INTEGER,
    userID INTEGER,

    CONSTRAINT order_status_fk FOREIGN KEY(statusID)
        REFERENCES status(statusID),

    CONSTRAINT order_users_fk FOREIGN KEY(userID)
        REFERENCES users(userID)
);
GO

-- =============== Initialize Orders Table - End ==============
-- ============================================================

-- ============================================================
-- ============ Initialize OrderLines Table - Start ===========

CREATE TABLE orderLines(
    orderLineID INTEGER IDENTITY(1,1) PRIMARY KEY,
    orderID INTEGER NOT NULL,
    itemID INTEGER NOT NULL,
    serviceID INTEGER NOT NULL,
    quantity INTEGER NOT NULL,
    linePrice DECIMAL(10,2) NOT NULL,

    CONSTRAINT orderLines_orders_fk FOREIGN KEY(orderID)
        REFERENCES orders(orderID),
    CONSTRAINT orderLines_servicePricing_fk FOREIGN KEY(itemID, serviceID)
        REFERENCES servicePricing(itemID, serviceID),
    CONSTRAINT ck_orderLines_quantity_positive CHECK(quantity > 0),
    CONSTRAINT ck_orderLines_linePrice_nonnegative CHECK(linePrice >= 0)
);
GO

-- ============= Initialize OrderLines Table - End ============
-- ============================================================

-- ============================================================
-- ============= Initialize Payments Table - Start ============

CREATE TABLE payments(
    paymentID INTEGER IDENTITY(1, 1) PRIMARY KEY,
    amount DECIMAL(10,2) NOT NULL,
    orderID INTEGER NOT NULL,
    paymentStatus VARCHAR(20) NOT NULL DEFAULT 'PAID',
    processedAt DATETIME2 NOT NULL DEFAULT SYSDATETIME(),

    CONSTRAINT payments_orders_fk FOREIGN KEY(orderID)
        REFERENCES orders(orderID),
    CONSTRAINT ck_payments_amount_nonnegative CHECK(amount >= 0)
);
GO

-- ============== Initialize Payments Table - End =============
-- ============================================================

-- ============================================================
-- =============== Initialize Log Table - Start ===============

CREATE TABLE logs(
    logID INTEGER IDENTITY(1, 1) PRIMARY KEY,
    status_before INTEGER,
    status_after INTEGER,
    logDate DATE,
    logTime TIME,
    orderID INTEGER,

    CONSTRAINT logs_orders_fk FOREIGN KEY(orderID)
        REFERENCES orders(orderID),

    CONSTRAINT logs_status_before_fk FOREIGN KEY(status_before)
        REFERENCES status(statusID),

    CONSTRAINT logs_status_after_fk FOREIGN KEY(status_after)
        REFERENCES status(statusID)
);
GO

-- ================ Initialize Log Table - End ================
-- ============================================================

-- ============================================================
-- ============= Initialize Feedback Table - Start ============

CREATE TABLE feedback(
    feedbackID INTEGER IDENTITY(1, 1) PRIMARY KEY,
    feedback VARCHAR(500),
    userID INTEGER NOT NULL,
    orderID INTEGER,
    caseType VARCHAR(20) NOT NULL CONSTRAINT df_feedback_type DEFAULT 'Feedback',
    subject NVARCHAR(100) NOT NULL CONSTRAINT df_feedback_subject DEFAULT 'Customer feedback',
    rating INT NULL CONSTRAINT ck_feedback_rating CHECK(rating BETWEEN 1 AND 5),
    caseStatus VARCHAR(20) NOT NULL CONSTRAINT df_feedback_status DEFAULT 'New',
    priority VARCHAR(10) NOT NULL CONSTRAINT df_feedback_priority DEFAULT 'Normal',
    assigneeID INT NULL,
    createdAt DATETIME2 NULL CONSTRAINT df_feedback_created DEFAULT SYSDATETIME(),
    updatedAt DATETIME2 NULL CONSTRAINT df_feedback_updated DEFAULT SYSDATETIME(),
    version INT NOT NULL CONSTRAINT df_feedback_version DEFAULT 0,
    deleted BIT NOT NULL CONSTRAINT df_feedback_deleted DEFAULT 0,

    CONSTRAINT feedback_users_fk FOREIGN KEY(userID)
        REFERENCES users(userID),
    
    CONSTRAINT feedback_orders_fk FOREIGN KEY(orderID)
        REFERENCES orders(orderID),
    CONSTRAINT feedback_assignee_users_fk FOREIGN KEY(assigneeID)
        REFERENCES users(userID)
);
GO

-- ============== Initialize Feedback Table - End =============
-- ============================================================

-- ============================================================
-- =============== Initialize Chat Table - Start ==============

CREATE TABLE chat(
    chatID INTEGER IDENTITY(1, 1) PRIMARY KEY,
    sentAt DATETIME DEFAULT GETDATE(),
    message VARCHAR(250),
    userID INTEGER NOT NULL,
    feedbackID INTEGER,

    CONSTRAINT chat_users_fk FOREIGN KEY(userID)
        REFERENCES users(userID),

    CONSTRAINT chat_feedback_fk FOREIGN KEY(feedbackID)
        REFERENCES feedback(feedbackID)
);
GO

-- ================ Initialize Chat Table - End ===============
-- ============================================================

-- ============================================================
-- ============= Initialize Delivery Table - Start ============

CREATE TABLE delivery(
    deliverID INTEGER IDENTITY(1, 1) PRIMARY KEY,
    orderID INTEGER,
    userID INTEGER,
    pickup_riderID INTEGER,
    delivery_riderID INTEGER,
    riderNotes VARCHAR(250),
    pickup_scheduled DATETIME,
    pickup_actual DATETIME,
    delivery_time DATETIME,

    CONSTRAINT delivery_orders_fk FOREIGN KEY(orderID)
        REFERENCES orders(orderID),
        
    CONSTRAINT delivery_users_fk FOREIGN KEY(userID)
        REFERENCES users(userID),

    CONSTRAINT delivery_pickup_rider_users_fk FOREIGN KEY(pickup_riderID)
        REFERENCES users(userID),

    CONSTRAINT delivery_delivery_rider_users_fk FOREIGN KEY(delivery_riderID)
        REFERENCES users(userID)
);
GO

CREATE OR ALTER TRIGGER dbo.trg_delivery_rider_check
ON delivery
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM inserted i
        LEFT JOIN users u ON u.userID = i.pickup_riderID
        WHERE i.pickup_riderID IS NOT NULL AND (u.userID IS NULL OR u.type <> 'RIDER')
    )
    OR EXISTS (
        SELECT 1 FROM inserted i
        LEFT JOIN users u ON u.userID = i.delivery_riderID
        WHERE i.delivery_riderID IS NOT NULL AND (u.userID IS NULL OR u.type <> 'RIDER')
    )
        THROW 51020, 'Pickup and delivery riders must reference users whose type is RIDER.', 1;
END
GO
-- ============== Initialize Delivery Table - End =============
/*
    LaundryLink master database initializer
    FRESH INSTALLATIONS ONLY - this script drops and recreates project tables.
    It directly creates the complete IT2140 Assignment Part 02 schema.
    Never run it against an existing database that contains data.
*/
-- ============================================================

-- Support audit and administration tables used by the current backend.
CREATE TABLE support_activity(
    activityID INT IDENTITY PRIMARY KEY,
    feedbackID INT NULL REFERENCES feedback(feedbackID),
    actorID INT NOT NULL REFERENCES users(userID),
    action NVARCHAR(40) NOT NULL,
    details NVARCHAR(1000) NOT NULL,
    createdAt DATETIME2 NOT NULL DEFAULT SYSDATETIME()
);
GO

CREATE TABLE notifications(
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
CREATE INDEX ix_notifications_recipient ON notifications(recipientID,isRead,createdAt DESC);
GO

CREATE OR ALTER TRIGGER dbo.trg_order_notifications
ON dbo.orders AFTER INSERT, UPDATE AS
BEGIN
    SET NOCOUNT ON;
    INSERT dbo.notifications(recipientID,category,title,message,link,relatedType,relatedID)
    SELECT i.userID,'ORDER','Order placed',CONCAT('Order #',i.orderID,' was placed successfully.'),
           CONCAT('/html/customer/order_details.html?orderId=',i.orderID),'ORDER',i.orderID
    FROM inserted i LEFT JOIN deleted d ON d.orderID=i.orderID WHERE d.orderID IS NULL;
    INSERT dbo.notifications(recipientID,category,title,message,link,relatedType,relatedID)
    SELECT i.userID,'ORDER','Order status updated',CONCAT('Order #',i.orderID,' is now ',COALESCE(s.statusLabel,'updated'),'.'),
           CONCAT('/html/customer/order_details.html?orderId=',i.orderID),'ORDER',i.orderID
    FROM inserted i JOIN deleted d ON d.orderID=i.orderID LEFT JOIN status s ON s.statusID=i.statusID
    WHERE COALESCE(i.statusID,-1)<>COALESCE(d.statusID,-1);
END;
GO

CREATE OR ALTER TRIGGER dbo.trg_payment_notifications
ON dbo.payments AFTER INSERT, UPDATE AS
BEGIN
    SET NOCOUNT ON;
    INSERT dbo.notifications(recipientID,category,title,message,link,relatedType,relatedID)
    SELECT o.userID,'PAYMENT','Payment accepted',CONCAT('Payment for order #',i.orderID,' was ',LOWER(i.paymentStatus),'.'),
           CONCAT('/html/customer/receipt.html?orderId=',i.orderID),'PAYMENT',i.paymentID
    FROM inserted i JOIN orders o ON o.orderID=i.orderID LEFT JOIN deleted d ON d.paymentID=i.paymentID
    WHERE i.paymentStatus IN('PAID','VERIFIED') AND (d.paymentID IS NULL OR COALESCE(d.paymentStatus,'')<>i.paymentStatus);
END;
GO

CREATE OR ALTER TRIGGER dbo.trg_delivery_assignment_notifications
ON dbo.delivery AFTER INSERT, UPDATE AS
BEGIN
    SET NOCOUNT ON;
    INSERT dbo.notifications(recipientID,category,title,message,link,relatedType,relatedID)
    SELECT i.pickup_riderID,'ASSIGNMENT','Pickup assigned',CONCAT('You were assigned pickup task #',i.deliverID,' for order #',i.orderID,'.'),
           CONCAT('/html/rider/task_list.html?taskId=',i.deliverID),'DELIVERY',i.deliverID
    FROM inserted i LEFT JOIN deleted d ON d.deliverID=i.deliverID
    WHERE i.pickup_riderID IS NOT NULL AND COALESCE(d.pickup_riderID,-1)<>i.pickup_riderID;
    INSERT dbo.notifications(recipientID,category,title,message,link,relatedType,relatedID)
    SELECT i.delivery_riderID,'ASSIGNMENT','Delivery assigned',CONCAT('You were assigned delivery task #',i.deliverID,' for order #',i.orderID,'.'),
           CONCAT('/html/rider/task_list.html?taskId=',i.deliverID),'DELIVERY',i.deliverID
    FROM inserted i LEFT JOIN deleted d ON d.deliverID=i.deliverID
    WHERE i.delivery_riderID IS NOT NULL AND COALESCE(d.delivery_riderID,-1)<>i.delivery_riderID;
    INSERT dbo.notifications(recipientID,category,title,message,link,relatedType,relatedID)
    SELECT o.userID,'DELIVERY','Rider assigned',CONCAT('A rider was assigned for order #',i.orderID,'.'),
           CONCAT('/html/customer/order_details.html?orderId=',i.orderID),'ORDER',i.orderID
    FROM inserted i JOIN orders o ON o.orderID=i.orderID LEFT JOIN deleted d ON d.deliverID=i.deliverID
    WHERE (i.pickup_riderID IS NOT NULL AND COALESCE(d.pickup_riderID,-1)<>i.pickup_riderID)
       OR (i.delivery_riderID IS NOT NULL AND COALESCE(d.delivery_riderID,-1)<>i.delivery_riderID);
END;
GO

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
        SELECT @PreviousStatusID=statusID FROM dbo.orders WITH (UPDLOCK,HOLDLOCK) WHERE orderID=@OrderID;
        IF @PreviousStatusID=@NewStatusID
            THROW 51012, 'The order already has the requested status.', 1;
        UPDATE dbo.orders SET statusID=@NewStatusID WHERE orderID=@OrderID;
        INSERT dbo.logs(status_before,status_after,logDate,logTime,orderID)
        VALUES(@PreviousStatusID,@NewStatusID,CONVERT(date,SYSDATETIME()),CONVERT(time,SYSDATETIME()),@OrderID);
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO

-- ============================================================
-- ============================================================
-- ============================================================
-- ============================================================
-- ============================================================

-- ADD DATA TO THE DATABASE

-- ============================================================
-- ============================================================
-- ============================================================
-- ============================================================
-- ============================================================

-- ============================================================
-- =============== Populate Status Table - Start ==============
IF OBJECT_ID('dbo.status', 'U') IS NOT NULL
    INSERT INTO status(statusID, statusLabel) VALUES
        (1, 'Unconfirmed'),
        (2, 'Payment Verified'),
        (3, 'Awaiting Pickup'),
        (4, 'En Route To Pickup'),
        (5, 'Picked Up'),
        (6, 'En Route To Shop'),
        (7, 'In Shop'),
        (8, 'Verifying Items'),
        (9, 'Washing'),
        (10, 'Drying'),
        (11, 'Ironing'),
        (12, 'Awaiting Delivery'),
        (13, 'En Route To Delivery'),
        (14, 'Delivered'),
        (15, 'Completed'),
        (16, 'Payment Failed'),
        (17, 'Pickup Failed'),
        (18, 'Delivery Failed'),
        (19, 'Dry Clean');
    GO
-- ================ Populate Status Table - End ===============
-- ============================================================

-- ============================================================
-- =============== Populate Items Table - Start ===============

IF OBJECT_ID('dbo.items', 'U') IS NOT NULL
    INSERT INTO items(itemName) VALUES
        ('Everyday Clothing'),
        ('Shirt / Blouse'),
        ('Bed Sheet'),
        ('Trousers / Skirt'),
        ('Bed Linen'),
        ('Two-Piece Suit'),
        ('Everyday Shoes'),
        ('Trainers'),
        ('Premium Material');
    GO

-- ================ Populate Items Table - End ================
-- ============================================================

-- ============================================================
-- ============== Populate Services Table - Start =============

IF OBJECT_ID('dbo.services', 'U') IS NOT NULL
    INSERT INTO services(serviceName) VALUES
        ('Wash and Fold'),
        ('Ironing'),
        ('Dry Cleaning'),
        ('Shoe Cleaning');
    GO

-- =============== Populate Services Table - End ==============
-- ============================================================

-- ============================================================
-- =========== Populate ServicePricing Table - Start ==========

IF OBJECT_ID('dbo.servicePricing', 'U') IS NOT NULL
    INSERT INTO servicePricing(serviceID, itemID, price) VALUES
        (1, 1, 180.0), --Washing and folding + Everyday clothing
        (1, 2, 220.0), --Washing and folding + Shirt / Blouse
        (1, 3, 450.0), --Washing and folding + Bed sheet
        (2, 2, 150.0), --Ironing + Shirt / Blouse
        (2, 4, 180.0), --Ironing + Trousers / skirt
        (2, 5, 300.0), --Ironing + Bed Linen
        (3, 2, 450.0), --Dry Cleaning + Shirt / Blouse
        (3, 4, 550.0), --Dry Cleaning + Trousers / Skirt
        (3, 6, 1500.0), --Dry Cleaning + Two-piece suit
        (4, 7, 750.0), --Shoe Cleaning + Everyday shoes
        (4, 8, 900.0), --Shoe Cleaning + Trainers
        (4, 9, 1200.0); --Shoe Cleaning + Premium Material
    GO

-- ============ Populate ServicePricing Table - End ===========
-- ============================================================

-- Fresh installations intentionally stop after essential reference catalog data.
-- Assignment/demo users and transactional records are kept separately in:
-- database/ddd_assignment2_sample_data.sql
