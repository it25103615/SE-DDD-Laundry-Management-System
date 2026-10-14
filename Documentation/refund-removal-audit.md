# Refund removal audit — 14 October 2026

## Scope and dependencies identified before removal

- Customer: `static/js/customer/payment-flow.js` generated the request dialog, history action and refund status; `static/html/customer/receipt.html` contained the request button and status fields. These were part of shared payment/receipt pages, not standalone refund pages.
- Management: `static/js/admin/payment-management.js` and `static/html/admin/owner/payment_detail.html` provided refund detail rows, approval/rejection controls and API calls. `payments_billing.html` included a refund status filter. Manager, Owner and Admin use these shared finance pages.
- Backend: `PaymentController` exposed POST `/api/payments/{paymentID}/refund-request`, `/api/payments/management/{paymentID}/refund/approve`, and `/api/payments/management/{paymentID}/refund/reject`. `PaymentService` owned requests, decisions, validation, notifications and refund enrichment of payment history, receipts and management records.
- Refund-only classes: `Refund`, `RefundRepository`, `RefundRequest`, `RefundResponse`, `RefundStatus`. No other module referenced these classes. Shared payment classes and `PaymentStatus` must remain.
- Shared response DTOs: `PaymentHistoryResponse`, `PaymentReceiptResponse`, `PaymentRecordResponse` included refund fields. Removed those fields while retaining all payment/billing fields.
- Security: no dedicated refund permissions existed. Management paths inherited finance role restrictions; request ownership was checked in the service. Removing controller mappings removes the feature for every role, independently of frontend visibility or permission rules.
- Notifications: refund decisions created PAYMENT notifications with `relatedType=REFUND`. The shared inbox now excludes these historical notifications from both the list and unread count; stored notifications are not deleted.
- No additional deployed refund navigation, dashboard cards, standalone pages, CSS, dependencies or integrations were found. The nested older project has no refund backend. Two archived standalone UI mockups mention refunds; these are documentation outside the served static resources and are retained as historical design artifacts.
- `PaymentServiceTest` contained 16 refund-feature tests and refund mock helpers. Removed those obsolete tests/helpers, retained payment tests, and added retired-route and legacy-record regression coverage.

## SQL Server safety

A read-only live metadata audit confirmed:

| Constraint | Source | Target |
| --- | --- | --- |
| fk_refunds_payments | refunds.paymentID | payments.paymentID |
| fk_refunds_requested_users | refunds.requestedBy | users.userID |
| fk_refunds_users | refunds.processedBy | users.userID |

No foreign keys reference refunds from another table. No visible SQL module definitions (including triggers, procedures and functions) reference refund text. The audit did not retrieve customer or refund record contents.

Migration `011_refunds.sql` defines the table, unique payment constraint, amount/status/reason/processing-time checks, defaults and status/request-time index. Migration `012_pending_payment_verification.sql` and the initializer allow historical `REFUNDED` payment status. All migrations and database objects remain unchanged. No migrations, initialization, cleanup or data-changing SQL were run.

The removed JPA entity/repository means the application no longer queries or writes the refunds table. `PaymentStatus.REFUNDED` and existing terminal-status checks remain solely for compatibility with stored payment records; no application operation creates that state. Historical payment receipts and statuses stay accurate, and an old refunded payment cannot be resubmitted or approved.

The existing foreign keys continue to protect historical refunds: deleting a referenced payment/user can be rejected by SQL Server. Constraints were not removed to bypass this protection.

Existing maintenance tools remain unchanged and were NOT executed: `initialize_database.sql` drops/recreates tables (including refunds), and `scripts/Clear-DemoData.ps1 -Apply` deletes demo refunds and related records. These are destructive maintenance tools, not part of this removal or application runtime; do not use them to deploy this change. `Initialize-SupportDatabase.ps1` retains a historical migration-name example.

## Deployment and validation

Rebuild and restart the application to retire routes in an already-running server. Use a clean build for release packaging so obsolete compiled entity classes cannot remain in an incremental build's output. No database migration is needed.

Validation uses database-free tests and JavaScript syntax checks. Live integration tests that write payment/order records are deliberately excluded. The route regression tests send requests for all former paths as Customer, Manager, Owner, Admin, Staff and CSM and require 404 even without security filters. Existing payment tests cover billing amounts, payment submission, verification/rejection, history, receipts and cancelled-order protections; billing, promotion and order tests cover the adjacent workflows.

Results: `mvnw.cmd test -Dtest=Payment*Test,RefundRemovalTest,Billing*Test,Promotion*Test,DiscountStrategyTest,Order*Test` passed all 136 tests (zero failures/errors/skips). `node --check` passed for both modified JavaScript files, and `git diff --check` passed. Five obsolete refund `.class` files left by incremental compilation were removed from `target/classes`. No browser end-to-end or live database-write tests were run.
