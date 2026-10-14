# Viva demonstration check — 8 October 2026

Verified: full Maven suite (356 tests, no failures/errors/skips); frontend audit (55 HTML pages, 28 JavaScript files, no broken literal local links, syntax errors or missing controller elements); live customer/CSM/manager/staff/rider/owner login, main page, context and notification inbox.

Database integration tests cover processing, quality checking/packing, rider assignment, payment recovery and support assignment, messages, permissions and resolution. Temporary test records roll back. Five support practice orders #128–132 remain Delivered.

## Rehearsal order

1. Create a fresh customer order with saved address, pickup window, item/service and instructions; submit payment.
2. Manager reviews payment and approves it; order becomes Awaiting Pickup. Assign pickup rider.
3. Rider collects and records shop handover; staff sees In Shop.
4. Staff receives/counts items, follows the appropriate cleaning route, performs quality inspection, packs and marks ready.
5. Manager assigns delivery rider; rider completes delivery. Show customer order history and updated business reports/charts.
6. Customer submits an enquiry or complaint linked to an order. CSM assigns the appropriate person; verify notification and recipient's assigned cases.
7. Recipient replies; customer sees reply. Choose No to continue chatting; after another staff reply, choose Yes to close the panel. Reopen the record and confirm the conversation is read-only.
8. Submit Feedback from the Support message-type dropdown. CSM replies once; customer reads the response without a reply form. No assignment, resolution or continued feedback chat.
9. Generate a report with dates/service; inspect charts and download CSV.

## Limits

- SMTP is not configured in this environment. Do not promise delivered password-reset emails until SMTP credentials and actual mailbox receipt are verified.
- Automated checks and live page/API checks do not prove every visual interaction was manually rehearsed. Complete the sequence above before presenting.
- Keep the backend running at http://localhost:8080 and SQL Server available. Refresh browser tabs after updates.
- Latest workflow changes and readiness test updates are local and not yet committed/pushed.
