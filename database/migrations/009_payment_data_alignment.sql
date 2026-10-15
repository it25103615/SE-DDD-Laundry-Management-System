/*
    LaundryLink - payment data alignment.
    Adds storage for the payment method chosen by the customer and the
    application-generated payment reference. Existing payment rows remain valid.
*/
USE laundryLinkDB;
GO

SET XACT_ABORT ON;
SET NOCOUNT ON;

BEGIN TRY
    BEGIN TRANSACTION;

    IF COL_LENGTH('dbo.payments', 'paymentMethod') IS NULL
        ALTER TABLE dbo.payments ADD paymentMethod VARCHAR(20) NULL;

    IF COL_LENGTH('dbo.payments', 'transactionReference') IS NULL
        ALTER TABLE dbo.payments ADD transactionReference VARCHAR(50) NULL;

    IF COL_LENGTH('dbo.payments', 'paymentStatus') IS NULL
        ALTER TABLE dbo.payments ADD paymentStatus VARCHAR(20) NOT NULL CONSTRAINT df_payments_status DEFAULT 'PENDING';

    IF COL_LENGTH('dbo.payments', 'processedAt') IS NULL
        ALTER TABLE dbo.payments ADD processedAt DATETIME2 NOT NULL CONSTRAINT df_payments_processed DEFAULT SYSDATETIME();

    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name='ck_payments_method')
        EXEC(N'ALTER TABLE dbo.payments WITH CHECK ADD CONSTRAINT ck_payments_method
            CHECK (paymentMethod IS NULL OR paymentMethod IN (''CARD'',''CASH''))');

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ux_payments_transactionReference' AND object_id=OBJECT_ID('dbo.payments'))
        EXEC(N'CREATE UNIQUE INDEX ux_payments_transactionReference
            ON dbo.payments(transactionReference)
            WHERE transactionReference IS NOT NULL');

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
GO
