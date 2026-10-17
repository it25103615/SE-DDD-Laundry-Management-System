USE laundryLinkDB;
GO
IF OBJECT_ID('dbo.passwordResetTokens', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.passwordResetTokens (
        tokenHash CHAR(64) NOT NULL PRIMARY KEY,
        userID INT NOT NULL REFERENCES dbo.users(userID),
        createdAt DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        expiresAt DATETIME2 NOT NULL,
        CONSTRAINT CK_passwordResetExpiry CHECK (expiresAt > createdAt)
    );
    CREATE INDEX IX_passwordResetUser ON dbo.passwordResetTokens(userID);
END;
GO
