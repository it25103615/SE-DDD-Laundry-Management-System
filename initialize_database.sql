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
    itemID INTEGER PRIMARY KEY,
    itemName VARCHAR(30)
);
GO

-- =============== Initialize Items Table - End ===============
-- ============================================================

-- ============================================================
-- ============= Initialize Services Table - Start ============

CREATE TABLE services(
    serviceID INTEGER PRIMARY KEY,
    serviceName VARCHAR(30)
);
GO

-- ============== Initialize Services Table - End =============
-- ============================================================

-- ============================================================
-- ========== Initialize ServicePricing Table - Start =========

CREATE TABLE servicePricing(
    serviceID INTEGER,
    itemID INTEGER,
    price FLOAT,

    PRIMARY KEY (itemID, serviceID)
);

-- =========== Initialize ServicePricing Table - End ==========
-- ============================================================

-- ============================================================
-- =============== Initialize User Table - Start ==============

CREATE TABLE users(
    userID INTEGER PRIMARY KEY,
    firstName VARCHAR(50),
    middleName VARCHAR(50),
    lastName VARCHAR(50),
    email VARCHAR(100) UNIQUE,
    password VARCHAR(50),
    phoneNumber CHAR(10),
    type VARCHAR(20),

    CONSTRAINT users_phoneNumber_format CHECK (phoneNumber LIKE '[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]')
);
GO

-- ================ Initialize User Table - End ===============
-- ============================================================

-- ============================================================
-- ============ Initialize Addresses Table - Start ============

CREATE TABLE addresses(
    addressID INTEGER PRIMARY KEY, 
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
    orderID INTEGER PRIMARY KEY,
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
    linePrice FLOAT NOT NULL,

    CONSTRAINT orderLines_orders_fk FOREIGN KEY(orderID)
        REFERENCES orders(orderID),
    CONSTRAINT orderLines_servicePricing_fk FOREIGN KEY(itemID, serviceID)
        REFERENCES servicePricing(itemID, serviceID)
);
GO

-- ============= Initialize OrderLines Table - End ============
-- ============================================================

-- ============================================================
-- ============= Initialize Payments Table - Start ============

CREATE TABLE payments(
    paymentID INTEGER PRIMARY KEY,
    amount FLOAT NOT NULL,
    orderID INTEGER NOT NULL,

    CONSTRAINT payments_orders_fk FOREIGN KEY(orderID)
        REFERENCES orders(orderID)
);
GO

-- ============== Initialize Payments Table - End =============
-- ============================================================

-- ============================================================
-- =============== Initialize Log Table - Start ===============

CREATE TABLE logs(
    logID INTEGER PRIMARY KEY,
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
    feedbackID INTEGER PRIMARY KEY,
    feedback VARCHAR(500),
    userID INTEGER NOT NULL,
    orderID INTEGER,

    CONSTRAINT feedback_users_fk FOREIGN KEY(userID)
        REFERENCES users(userID),
    
    CONSTRAINT feedback_orders_fk FOREIGN KEY(orderID)
        REFERENCES orders(orderID)
);
GO

-- ============== Initialize Feedback Table - End =============
-- ============================================================

-- ============================================================
-- =============== Initialize Chat Table - Start ==============

CREATE TABLE chat(
    chatID INTEGER PRIMARY KEY,
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
    deliverID INTEGER PRIMARY KEY,
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

CREATE TRIGGER trg_delivery_rider_check
ON delivery
AFTER INSERT, UPDATE
AS
BEGIN
    IF EXISTS (
        SELECT 1 FROM inserted i
        JOIN users u ON u.userID = i.pickup_riderID
        WHERE u.type <> 'RIDER'
    )
    OR EXISTS (
        SELECT 1 FROM inserted i
        JOIN users u ON u.userID = i.delivery_riderID
        WHERE u.type <> 'RIDER'
    )
    BEGIN
        RAISERROR('pickup_riderID and delivery_riderID must reference users with type = RIDER', 16, 1);
        ROLLBACK TRANSACTION;
    END
END
GO
-- ============== Initialize Delivery Table - End =============
-- ============================================================

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
    INSERT INTO items(itemID, itemName) VALUES
        (1, 'Everyday Clothing'),
        (2, 'Shirt / Blouse'),
        (3, 'Bed Sheet'),
        (4, 'Trousers / Skirt'),
        (5, 'Bed Linen'),
        (6, 'Two-Piece Suit'),
        (7, 'Everyday Shoes'),
        (8, 'Trainers'),
        (9, 'Premium Material');
    GO

-- ================ Populate Items Table - End ================
-- ============================================================

-- ============================================================
-- ============== Populate Services Table - Start =============

IF OBJECT_ID('dbo.services', 'U') IS NOT NULL
    INSERT INTO services(serviceID, serviceName) VALUES
        (1, 'Wash and Fold'),
        (2, 'Ironing'),
        (3, 'Dry Cleaning'),
        (4, 'Shoe Cleaning');
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
-- ============================================================
-- =============== Populate Users Table - Start ===============

IF OBJECT_ID('dbo.users', 'U') IS NOT NULL
    INSERT INTO users(userID, firstName, middleName, lastName, email, password, phoneNumber, type) VALUES
        (1, 'Nimal', NULL, 'Perera', 'nimal.perera@example.com', 'pass1234', '0711234567', 'CUSTOMER'),
        (2, 'Kamala', NULL, 'Silva', 'kamala.silva@example.com', 'pass1234', '0722345678', 'CUSTOMER'),
        (3, 'Ruwan', 'Chathura', 'Fernando', 'ruwan.fernando@example.com', 'pass1234', '0773456789', 'CUSTOMER'),
        (4, 'Ishara', NULL, 'Jayawardena', 'ishara.jw@example.com', 'pass1234', '0764567890', 'CUSTOMER'),
        (5, 'Saman', NULL, 'Kumara', 'saman.kumara@laundrylink.com', 'staffpass1', '0715678901', 'STAFF'),
        (6, 'Dilani', NULL, 'Wickramasinghe', 'dilani.w@laundrylink.com', 'staffpass2', '0726789012', 'STAFF'),
        (7, 'Chamara', NULL, 'Rajapaksha', 'chamara.r@laundrylink.com', 'riderpass1', '0777890123', 'RIDER'),
        (8, 'Tharindu', NULL, 'Bandara', 'tharindu.b@laundrylink.com', 'riderpass2', '0768901234', 'RIDER'),
        (9, 'Anusha', NULL, 'Gunasekara', 'anusha.g@laundrylink.com', 'adminpass1', '0719012345', 'ADMIN'),
        (10, 'Priyanka', NULL, 'Weerasinghe', 'priyanka.w@example.com', 'pass1234', '0700123456', 'CUSTOMER'),
        (11, 'Nadeeka', NULL, 'Ratnayake', 'nadeeka.r@laundrylink.com', 'managerpass1', '0711122334', 'MANAGER'),
        (12, 'Sanjeewa', NULL, 'Herath', 'sanjeewa.h@laundrylink.com', 'managerpass2', '0722233445', 'MANAGER');
    GO

-- ================ Populate Users Table - End ================
-- ============================================================

-- ============================================================
-- ============= Populate Addresses Table - Start =============

IF OBJECT_ID('dbo.addresses', 'U') IS NOT NULL
    INSERT INTO addresses(addressID, nickname, street, city, state, DeliveryInstructions, isDefault, userID) VALUES
        (1, 'Home', '12 Galle Road', 'Colombo', 'Western', 'Leave with security guard', 1, 1),
        (2, 'Office', '45 Duplication Road', 'Colombo', 'Western', 'Ask for reception', 0, 1),
        (3, 'Home', '78 Kandy Road', 'Kandy', 'Central', 'Gate code 2468', 1, 2),
        (4, 'Home', '23 Negombo Road', 'Negombo', 'Western', 'Ring bell twice', 1, 3),
        (5, 'Home', '9 Havelock Road', 'Colombo', 'Western', 'Call before arriving', 1, 4),
        (6, 'Home', '56 Matara Road', 'Galle', 'Southern', 'Blue gate, back entrance', 1, 10);
    GO

-- ============== Populate Addresses Table - End ==============
-- ============================================================

-- ============================================================
-- =============== Populate Orders Table - Start ==============

IF OBJECT_ID('dbo.orders', 'U') IS NOT NULL
    INSERT INTO orders(orderID, statusID, userID) VALUES
        (1, 15, 1), -- Completed
        (2, 9, 2),  -- Washing
        (3, 3, 3),  -- Awaiting Pickup
        (4, 14, 4), -- Delivered
        (5, 16, 1), -- Payment Failed
        (6, 11, 10); -- Ironing
    GO

-- ================ Populate Orders Table - End ===============
-- ============================================================

-- ============================================================
-- ============= Populate OrderLines Table - Start ============

IF OBJECT_ID('dbo.orderLines', 'U') IS NOT NULL
    INSERT INTO orderLines(orderID, itemID, serviceID, quantity, linePrice) VALUES
        (1, 1, 1, 5, 900.0),   -- 5x Everyday Clothing, Wash and Fold
        (1, 2, 2, 3, 450.0),   -- 3x Shirt/Blouse, Ironing
        (2, 3, 1, 2, 900.0),   -- 2x Bed Sheet, Wash and Fold
        (3, 2, 1, 4, 880.0),   -- 4x Shirt/Blouse, Wash and Fold
        (3, 4, 2, 2, 360.0),   -- 2x Trousers/Skirt, Ironing
        (4, 6, 3, 1, 1500.0),  -- 1x Two-Piece Suit, Dry Cleaning
        (5, 7, 4, 1, 750.0),   -- 1x Everyday Shoes, Shoe Cleaning
        (6, 5, 2, 2, 600.0);   -- 2x Bed Linen, Ironing
    GO

-- ============== Populate OrderLines Table - End =============
-- ============================================================

-- ============================================================
-- ============== Populate Payments Table - Start =============

IF OBJECT_ID('dbo.payments', 'U') IS NOT NULL
    INSERT INTO payments(paymentID, amount, orderID) VALUES
        (1, 1350.0, 1),
        (2, 900.0, 2),
        (3, 1240.0, 3),
        (4, 1500.0, 4),
        (5, 600.0, 6);
    GO

-- =============== Populate Payments Table - End ==============
-- ============================================================

-- ============================================================
-- ================ Populate Logs Table - Start ===============

IF OBJECT_ID('dbo.logs', 'U') IS NOT NULL
    INSERT INTO logs(logID, status_before, status_after, logDate, logTime, orderID) VALUES
        (1, 1, 2, '2026-08-10', '09:00:00', 1),
        (2, 2, 3, '2026-08-10', '09:15:00', 1),
        (3, 3, 14, '2026-08-12', '17:30:00', 1),
        (4, 14, 15, '2026-08-13', '10:00:00', 1),
        (5, 2, 3, '2026-08-20', '11:00:00', 2),
        (6, 3, 9, '2026-08-21', '08:45:00', 2),
        (7, 1, 2, '2026-08-25', '14:00:00', 3),
        (8, 2, 3, '2026-08-25', '14:20:00', 3),
        (9, 12, 13, '2026-08-24', '16:00:00', 4),
        (10, 13, 14, '2026-08-24', '18:30:00', 4),
        (11, 1, 16, '2026-08-26', '09:05:00', 5),
        (12, 3, 9, '2026-08-27', '07:30:00', 6),
        (13, 9, 11, '2026-08-27', '13:00:00', 6);
    GO

-- ================= Populate Logs Table - End ================
-- ============================================================

-- ============================================================
-- ============== Populate Feedback Table - Start =============

IF OBJECT_ID('dbo.feedback', 'U') IS NOT NULL
    INSERT INTO feedback(feedbackID, feedback, userID, orderID) VALUES
        (1, 'One of my shirts came back with a missing button.', 1, 1),
        (2, 'Great service, very fast turnaround!', 2, 2),
        (3, 'Driver was late for pickup by 30 minutes.', 3, 3),
        (4, 'App is easy to use, love the tracking feature.', 4, NULL);
    GO

-- =============== Populate Feedback Table - End ==============
-- ============================================================

-- ============================================================
-- ================ Populate Chat Table - Start ===============

IF OBJECT_ID('dbo.chat', 'U') IS NOT NULL
    INSERT INTO chat(chatID, sentAt, message, userID, feedbackID) VALUES
        (1, '2026-08-13 10:15:00', 'We are sorry about the missing button. A replacement shirt credit has been issued.', 9, 1),
        (2, '2026-08-13 10:20:00', 'Thank you for resolving this so quickly.', 1, 1),
        (3, '2026-08-25 15:00:00', 'Apologies for the delay, we have flagged this with the rider.', 9, 3);
    GO

-- ================= Populate Chat Table - End ================
-- ============================================================

-- ============================================================
-- ============== Populate Delivery Table - Start =============

IF OBJECT_ID('dbo.delivery', 'U') IS NOT NULL
    INSERT INTO delivery(deliverID, orderID, userID, pickup_riderID, delivery_riderID, riderNotes, pickup_scheduled, pickup_actual, delivery_time) VALUES
        (1, 1, 1, 7, 8, 'Left at security desk on delivery', '2026-08-10 09:00:00', '2026-08-10 09:10:00', '2026-08-13 10:00:00'),
        (2, 2, 2, 7, NULL, NULL, '2026-08-20 11:00:00', '2026-08-20 11:05:00', NULL),
        (3, 3, 3, NULL, NULL, NULL, '2026-08-25 14:00:00', NULL, NULL),
        (4, 4, 4, 8, 8, 'Customer requested evening delivery', '2026-08-23 16:00:00', '2026-08-23 16:05:00', '2026-08-24 18:30:00'),
        (5, 6, 10, 7, NULL, 'Awaiting processing before delivery scheduling', '2026-08-27 08:00:00', '2026-08-27 08:10:00', NULL);
    GO

-- =============== Populate Delivery Table - End ==============
-- ============================================================