# Button and support workflow verification

Verified on 8 October 2026 against the local SQL Server database.

## Fixed failures

- Customer Addresses now uses the saved-address API and the existing add/edit/default/delete controller instead of static example cards.
- Manager rider assignment now lists real waiting pickup/delivery tasks and active riders. Assignment updates the selected rider and order status transactionally, making the task available in the rider's work queue. Missing delivery address schema was migrated.
- Customer feedback now includes every element needed by the shared case controller, including detail, chat, history and resolution panels.
- The home-page Track order control navigates to the customer's real orders. The old customer profile page redirects to the working shared account profile.
- Fabricated generic success handlers were removed. Success appears only after the actual API operation.
- Support assignment now includes all active ADMIN, OWNER, MANAGER, CSM (including the legacy CUSTOMER_SERVICE_MANAGER alias), STAFF and RIDER users. Customers and inactive users cannot be assigned.
- Shared support pages are accessible to every staff role. Managers, laundry staff and riders only see cases assigned to them. Coordinators retain oversight and reassignment permissions.
- Assignees receive an in-app notification linking directly to the case. Both parties can read and send customer/order-linked messages. Resolution requires a note and valid workflow transition; case history and customer notifications are retained.
- Support dashboard, case queue and open conversations refresh every 15 seconds while visible. Draft replies and case edits pause refresh and are preserved if entered while a request is in flight. The shared notification bell checks every 30 seconds. These are polling updates, not WebSocket push.

## Evidence

- Clean full Maven suite: 292 tests, zero failures, errors or skipped tests.
- Seven real database role variants test assignment eligibility, recipient notifications, shared-page access, assigned queue, linked order, two-way messages, reply notification and resolution. Database fixtures roll back.
- Rider assignment tests cover pickup/delivery transitions, repeat assignment, inactive/non-rider rejection and manager/customer permissions.
- Frontend audit covers 53 HTML pages and 28 JavaScript files, checking local literal links, syntax and required shared-controller DOM elements.
- Browser checks covered manager dashboard refresh; assignment listing and missing-rider validation; service/staff form Edit and Clear; processing list/grid toggle; customer case Search/Clear, details/chat/history/close and required empty-reply validation; customer address dialog; and expanded support assignee options.
- Final rider browser check reached Assigned cases, its shared support dashboard and the database-scoped case queue without an access-denied redirect. With no cases assigned to that rider, counts were zero and the empty state loaded correctly.

## Acceptance limits

Automated coverage and selected browser flows do not prove every rendered button was manually exercised. Rehearse all role workflows before assessment. Email recovery is implemented but mailbox delivery requires configured SMTP and a real receipt test. Team delivery, individual viva performance and the unprovided report rubric cannot be certified through code checks.

Run `node scripts/Audit-Frontend.cjs` and Maven `clean test` when the temporary verification server is stopped (Windows locks its log under target while running).
