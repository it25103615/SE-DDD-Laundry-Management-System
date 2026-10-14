-- Safe to re-run; historical cases remain General unless they are known laundry issues.
IF COL_LENGTH('dbo.feedback','topic') IS NULL
    ALTER TABLE dbo.feedback ADD topic VARCHAR(30) NOT NULL
        CONSTRAINT df_feedback_topic DEFAULT 'General' WITH VALUES;
GO
UPDATE dbo.feedback SET topic='Laundry & items'
WHERE topic='General' AND caseType IN ('Damaged item','Existing stain','Missing item','Item count mismatch');
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name='ck_feedback_topic')
    ALTER TABLE dbo.feedback ADD CONSTRAINT ck_feedback_topic
        CHECK (topic IN ('General','Payments & billing','Laundry & items','Pickup & delivery','Account & booking'));
GO
