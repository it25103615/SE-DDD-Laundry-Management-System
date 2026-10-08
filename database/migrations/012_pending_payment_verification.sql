/*
    Migration 012 - Pending payment verification
    Customer-submitted payments must wait for manager/owner verification before
    becoming paid. Reuses dbo.payments.paymentStatus instead of adding a duplicate
    status field.
*/
USE laundryLinkDB;
GO

SET XACT_ABORT ON;
SET NOCOUNT ON;

BEGIN TRY
    BEGIN TRANSACTION;

    IF COL_LENGTH('dbo.payments', 'paymentStatus') IS NULL
        ALTER TABLE dbo.payments ADD paymentStatus VARCHAR(20) NOT NULL CONSTRAINT df_payments_status DEFAULT 'PENDING';

    IF EXISTS (SELECT 1 FROM sys.default_constraints WHERE name = 'df_payments_status')
        ALTER TABLE dbo.payments DROP CONSTRAINT df_payments_status;

    ALTER TABLE dbo.payments ADD CONSTRAINT df_payments_status DEFAULT 'PENDING' FOR paymentStatus;

    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'ck_payments_status')
        EXEC(N'ALTER TABLE dbo.payments WITH CHECK ADD CONSTRAINT ck_payments_status
            CHECK (paymentStatus IN (''PENDING'',''PAID'',''VERIFIED'',''REJECTED'',''REFUNDED''))');

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
GO

CREATE OR ALTER TRIGGER dbo.trg_payment_notifications
ON dbo.payments AFTER INSERT, UPDATE AS
BEGIN
    SET NOCOUNT ON;
    INSERT dbo.notifications(recipientID,category,title,message,link,relatedType,relatedID)
    SELECT o.userID,'PAYMENT','Payment accepted',CONCAT('Payment for order #',i.orderID,' was ',LOWER(i.paymentStatus),'.'),
           CONCAT('/html/customer/receipt.html?orderID=',i.orderID,'&paymentID=',i.paymentID),'PAYMENT',i.paymentID
    FROM inserted i JOIN orders o ON o.orderID=i.orderID LEFT JOIN deleted d ON d.paymentID=i.paymentID
    WHERE i.paymentStatus IN('PAID','VERIFIED') AND (d.paymentID IS NULL OR COALESCE(d.paymentStatus,'')<>i.paymentStatus);
END;
GO
