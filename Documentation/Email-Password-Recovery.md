# Email OTP password recovery

The recovery flow sends a six-digit code to the active account's registered email address. The application needs one sender mailbox; recipients do not supply their own mail credentials.

## Gmail sender setup

1. Enable Google 2-Step Verification for a Gmail sender account and create an app password: https://support.google.com/accounts/answer/185833
2. From the project folder, run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Configure-RecoveryEmail.ps1
```

The script prompts for the sender address and hides the app password while typing. It configures smtp.gmail.com, port 587, and required STARTTLS in the ignored application-local.properties file, preserving database configuration. The app password is stored in this local file; keep the file private and out of Git. Do not use your regular Gmail password.

3. Restart LaundryLink. On Forgot password, enter an existing user's email address. Retrieve the emailed code, verify it, then choose a new password. Never send app passwords or OTPs in chat.

Existing environment-variable configuration remains supported: LAUNDRYLINK_SMTP_HOST, LAUNDRYLINK_SMTP_PORT, LAUNDRYLINK_SMTP_USERNAME, LAUNDRYLINK_SMTP_PASSWORD, and LAUNDRYLINK_SMTP_FROM. If SMTP_FROM is omitted, the sender defaults to SMTP_USERNAME.

## Database and behavior

Migration 017_password_recovery_otp.sql adds passwordRecoveryCodes. The migration is additive and is applied to the current database. Initialize-SupportDatabase.ps1 discovers it automatically for other installations. Existing passwordResetTokens stores the final reset authorization after OTP verification.

Codes expire after 10 minutes, are limited to five incorrect attempts, and can be used once. Requests have a 60-second cooldown for known and unknown email addresses alike. Only digests are stored; the code digest is bound to a cryptographically random browser challenge. The challenge and final reset authorization remain in browser memory, not localStorage. Reloading during recovery requires requesting a new code. Resending replaces the previous code and invalidates previously issued reset authorization. Password changes use BCrypt and consume the reset authorization.

Unknown and inactive accounts receive the same request message but cannot verify or change a password. Missing SMTP configuration and delivery failures return an explicit error. SMTP failures roll back issuance. No code is returned by the API or logged.

## Validation

Automated tests use a mocked mail transport; OTP issuance, wrong-attempt limits, expiry, single use, password hashing, and unknown-account cooldown are tested against SQL Server with transaction rollback. Real inbox delivery must be checked after configuring the sender. SMTP acceptance cannot guarantee inbox placement; check spam too.

Report start dates are capped at today in Asia/Colombo and checked again in ReportService. Future start dates are rejected even if a caller bypasses the browser. Existing end-date ordering and open-ended ranges remain supported.
