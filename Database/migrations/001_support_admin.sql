-- Run once against the existing LaundryLink database. Safe to run again.
-- This migration preserves existing feedback, orders, users and chat records.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF COL_LENGTH('feedback', 'caseType') IS NULL
    ALTER TABLE feedback ADD caseType VARCHAR(20) NOT NULL CONSTRAINT df_feedback_type DEFAULT 'Feedback';
IF COL_LENGTH('feedback', 'subject') IS NULL
    ALTER TABLE feedback ADD subject NVARCHAR(100) NOT NULL CONSTRAINT df_feedback_subject DEFAULT 'Existing customer feedback';
IF COL_LENGTH('feedback', 'rating') IS NULL
    ALTER TABLE feedback ADD rating INT NULL CONSTRAINT ck_feedback_rating CHECK(rating BETWEEN 1 AND 5);
IF COL_LENGTH('feedback', 'caseStatus') IS NULL
    ALTER TABLE feedback ADD caseStatus VARCHAR(20) NOT NULL CONSTRAINT df_feedback_status DEFAULT 'New';
IF COL_LENGTH('feedback', 'priority') IS NULL
    ALTER TABLE feedback ADD priority VARCHAR(10) NOT NULL CONSTRAINT df_feedback_priority DEFAULT 'Normal';
IF COL_LENGTH('feedback', 'assigneeID') IS NULL
    ALTER TABLE feedback ADD assigneeID INT NULL REFERENCES users(userID);
-- Historical submission dates are unknown; leave them NULL rather than inventing dates.
IF COL_LENGTH('feedback', 'createdAt') IS NULL
    ALTER TABLE feedback ADD createdAt DATETIME2 NULL CONSTRAINT df_feedback_created DEFAULT SYSDATETIME();
IF COL_LENGTH('feedback', 'updatedAt') IS NULL
    ALTER TABLE feedback ADD updatedAt DATETIME2 NULL CONSTRAINT df_feedback_updated DEFAULT SYSDATETIME();
IF COL_LENGTH('feedback', 'version') IS NULL
    ALTER TABLE feedback ADD version INT NOT NULL CONSTRAINT df_feedback_version DEFAULT 0;
IF COL_LENGTH('feedback', 'deleted') IS NULL
    ALTER TABLE feedback ADD deleted BIT NOT NULL CONSTRAINT df_feedback_deleted DEFAULT 0;
IF OBJECT_ID('support_activity', 'U') IS NULL
    CREATE TABLE support_activity(
        activityID INT IDENTITY PRIMARY KEY,
        feedbackID INT NULL REFERENCES feedback(feedbackID),
        actorID INT NOT NULL REFERENCES users(userID),
        action NVARCHAR(40) NOT NULL,
        details NVARCHAR(1000) NOT NULL,
        createdAt DATETIME2 NOT NULL DEFAULT SYSDATETIME()
    );
COMMIT;
