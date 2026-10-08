USE laundryLinkDB;
GO

SET XACT_ABORT ON;
SET NOCOUNT ON;

BEGIN TRY
    BEGIN TRANSACTION;

    IF OBJECT_ID('dbo.refunds', 'U') IS NULL
    BEGIN
        CREATE TABLE dbo.refunds (
            refundID INT IDENTITY(1,1) PRIMARY KEY,
            paymentID INT NOT NULL,
            refundAmount DECIMAL(10,2) NOT NULL,
            refundReason VARCHAR(255) NOT NULL,
            refundStatus VARCHAR(20) NOT NULL CONSTRAINT df_refunds_status DEFAULT 'REQUESTED',
            requestedAt DATETIME2 NOT NULL CONSTRAINT df_refunds_requestedAt DEFAULT SYSDATETIME(),
            processedAt DATETIME2 NULL,
            refundedAt DATETIME2 NULL,
            requestedBy INT NULL,
            processedBy INT NULL,
            CONSTRAINT fk_refunds_payments FOREIGN KEY (paymentID) REFERENCES dbo.payments(paymentID),
            CONSTRAINT fk_refunds_requested_users FOREIGN KEY (requestedBy) REFERENCES dbo.users(userID),
            CONSTRAINT fk_refunds_users FOREIGN KEY (processedBy) REFERENCES dbo.users(userID),
            CONSTRAINT uq_refunds_payment UNIQUE (paymentID),
            CONSTRAINT ck_refunds_amount_positive CHECK (refundAmount > 0),
            CONSTRAINT ck_refunds_status CHECK (refundStatus IN ('REQUESTED','REFUNDED','REJECTED')),
            CONSTRAINT ck_refunds_processed_status CHECK (
                (refundStatus = 'REQUESTED' AND processedAt IS NULL AND refundedAt IS NULL)
                OR (refundStatus = 'REFUNDED' AND processedAt IS NOT NULL AND refundedAt IS NOT NULL)
                OR (refundStatus = 'REJECTED' AND processedAt IS NOT NULL AND refundedAt IS NULL)
            ),
            CONSTRAINT ck_refunds_reason_not_blank CHECK (LEN(LTRIM(RTRIM(refundReason))) > 0)
        );
    END;

    IF NOT EXISTS (
        SELECT 1
        FROM sys.indexes
        WHERE name = 'ix_refunds_status_requestedAt'
          AND object_id = OBJECT_ID('dbo.refunds')
    )
    BEGIN
        CREATE INDEX ix_refunds_status_requestedAt ON dbo.refunds(refundStatus, requestedAt DESC, refundID DESC);
    END;

    COMMIT;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK;
    THROW;
END CATCH;
GO
