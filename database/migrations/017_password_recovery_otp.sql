USE laundryLinkDB;
GO
IF OBJECT_ID('dbo.passwordRecoveryCodes', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.passwordRecoveryCodes (
        challengeHash CHAR(64) NOT NULL PRIMARY KEY,
        emailHash CHAR(64) NOT NULL,
        userID INT NULL REFERENCES dbo.users(userID),
        otpHash CHAR(64) NOT NULL,
        attempts INT NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 5),
        createdAt DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        expiresAt DATETIME2 NOT NULL,
        CONSTRAINT CK_passwordRecoveryCodeExpiry CHECK (expiresAt > createdAt)
    );
    CREATE INDEX IX_passwordRecoveryCodeEmail ON dbo.passwordRecoveryCodes(emailHash, createdAt);
END;
GO
