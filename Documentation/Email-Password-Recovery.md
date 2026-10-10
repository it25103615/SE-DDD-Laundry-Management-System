# Email password recovery setup

The recovery feature is implemented in `account/PasswordRecoveryController.java`, `PasswordRecoveryService.java` and `static/js/auth/password-recovery.js`. Migration `database/migrations/013_password_recovery.sql` adds reset-token storage and is included in `scripts/Initialize-SupportDatabase.ps1`. Migration 013 has been applied to the current local database.

Configure the following environment variables for the Spring Boot process, or their corresponding properties in the ignored `application-local.properties` file. Keep credentials out of Git and screenshots.

| Environment variable | Purpose |
|---|---|
| `LAUNDRYLINK_SMTP_HOST` | SMTP server supplied by your email provider |
| `LAUNDRYLINK_SMTP_PORT` | Provider's STARTTLS SMTP port; default 587 |
| `LAUNDRYLINK_SMTP_USERNAME` | SMTP login |
| `LAUNDRYLINK_SMTP_PASSWORD` | SMTP password or provider app password |
| `LAUNDRYLINK_SMTP_FROM` | Verified sender email address |
| `LAUNDRYLINK_BASE_URL` | Application origin reachable by the recipient; default `http://localhost:8080` |
| `LAUNDRYLINK_SMTP_AUTH` | Default true |
| `LAUNDRYLINK_SMTP_STARTTLS` | Default true; TLS is required when enabled |

Restart Spring Boot after configuration. Use the provider's SMTP instructions; the default uses STARTTLS, not implicit SSL on port 465. If your provider requires implicit SSL, set `spring.mail.properties.mail.smtp.ssl.enable=true` and use its specified port/configuration in your local properties.

Demo acceptance steps:

1. Use a test account whose mailbox you control. Open Forgot password from login and submit its email.
2. Check the received email. The UI uses the same generic response for unknown and existing accounts, preventing a direct account-existence lookup.
3. Open the emailed link, choose a strong matching password, and confirm the success message. Log in with the new password.
4. Reopen the old link: it must fail. Request another link and check the 15-minute expiry. Repeated requests for the same account are limited to one per minute.
5. Check invalid email, weak password, mismatched confirmation and missing token. Fields must show meaningful errors and the database must stay unchanged.

The reset token is 32 cryptographically random bytes encoded as URL-safe text. Only its SHA-256 digest is stored. Reset links carry the token in a URL fragment so it is not sent in the page URL request; the script clears the fragment and posts the token in the API body. An active account and a nonexpired token are required. The transaction updates a BCrypt password hash and removes all reset tokens for that user. SMTP failure rolls back token issuance and produces a real error. Outbound SMTP is never contacted by the automated recovery tests.

Configured SMTP delivery is an external acceptance gate. Unit tests mock the mail sender; integration tests verify hash changes, expiry, inactive-account rejection and single use against SQL Server with rollback. No claim of successful email delivery is made until a test message is received.
