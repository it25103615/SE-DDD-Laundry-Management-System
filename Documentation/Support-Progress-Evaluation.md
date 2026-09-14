# Reporting, Administration & Support — progress evaluation

Owner: Agksheya B. Branch: agksheya/reporting-admin-support.

## Requirements checked

- SE2030_Group Project Specification.pdf, pages 4 and 14–17: 75% project progress; demonstration scores UI (10), CRUD (15), database connection (10), validation (5), teamwork (5), and communication (5). The design document is a separate 50 marks.
- LaundryLink - Project breifing.pdf, pages 32–36: complaints, feedback/ratings, administration, reports, performance monitoring, and notifications/communications.
- SE Report.pdf, FR6.1–FR6.8; Final Report Lundry Link .pdf, page 12 identifies this module as Agksheya's.

This is a feature-based progress slice, not a certified completion percentage. Team integration, runtime verification and your understanding must support the evaluation claim.

## Implemented slice

| Requirement | Implementation |
|---|---|
| Complaint and feedback create/read/update/delete | Customer form, own-case list, edit and delete while New; database-backed endpoints |
| Ratings | Feedback requires a rating from 1 to 5; complaints/questions cannot carry ratings |
| Case management | Search, type/status filters, pagination, assignment, priority and controlled status progression |
| Case history and communications | Timestamped actor history and replies saved in existing chat table |
| System administration | Settings create/read/update/delete with manager authorization and audit entries |
| Operational / financial / customer reports | Date/service filters, status breakdown, line values, recorded payment totals, top 100 customer summary, CSV export |
| Business indicators | Database-backed order/customer/value/delivery totals and all-time support/rating summary |
| Support order lookup | Paginated orders and shared status-log history |

## Start locally

1. Use Java 26, Maven, and SQL Server. The repository wrapper lacks its .mvn configuration; `scripts/Start-Support.ps1` can use Maven bundled with IntelliJ.
2. For an existing database, run `database/migrations/001_support_admin.sql` in that database. Do not rerun the destructive initializer on existing data.
3. For a missing database, `scripts/Initialize-SupportDatabase.ps1` creates the schema and reference catalog, then applies the application migrations. It uses your Windows SQL Express login.
4. Configure your SQL Server connection in ignored `src/main/resources/application-local.properties` (URL, username and password). The shared default expects localhost:1433; TCP must be enabled for JDBC.
5. Run `powershell -File scripts/Start-Support.ps1`. Open http://localhost:8080/html/customer/feedback.html.
6. Create a fresh customer account through `/html/auth/register.html`, then log in through `/html/auth/login.html`. The database contains no seeded user accounts.
7. Spring Security creates the session and SupportAccess resolves the authenticated email and database role. The sample passwords are plain text and must be migrated to BCrypt by the account module before deployment.

## Two-to-three-minute individual demonstration

1. Customer Nimal: create a Complaint, choose owned order #1, enter subject/message. Show the saved case and refresh to demonstrate persistence.
2. Edit the subject while the case is New. Show the edit in history.
3. Create a second temporary case, delete it, and refresh to show it is gone. Delete is a soft deletion; its history is retained for accountability.
4. Submit Feedback with rating 5. Show that a missing rating or blank message is rejected.
5. Switch to Admin/support. Search for the first complaint, assign Anusha, move to In Review with a note, reply, then resolve with a resolution note. Explain why skipping directly from New to Resolved is rejected.
6. Switch back to Nimal: read the reply and case history. Switch to Kamala: Nimal's cases are not listed.
7. Manager: open Settings. Create `shop.contact_phone`, edit its value, delete it, and show all three audit records. These are operational information records, not live pricing/security rules.
8. Open Reports. Use all-time data, then August 2026 dates to match the original seed. Show filtered totals, order status breakdown and CSV export.

## Important metric definitions

- Order value is SUM(orderLines.linePrice). The existing schema stores a total per line, so do not multiply by quantity again.
- Recorded payments are SUM(payments.amount). This table has no date or transaction-status field; this is not a statement of verified revenue received within a period.
- Date filters use the earliest status log for each order. Orders without dates appear only in all-time reports.
- A service filter selects orders containing that service; payment and service totals cover those entire orders.
- Delivered/completed means status 14 or 15. An on-time percentage cannot be calculated because delivery deadlines are not stored.
- Support figures are explicitly all-time. Unknown imported feedback dates and ratings are left unknown.

## Architecture to explain in the viva

Java classes are grouped by layer and module. See [Project-Structure.md](Project-Structure.md) for the six-member ownership map and exact source locations.

Browser form → SupportController (HTTP and bean validation) → SupportService / SettingsService / ReportService (business rules) → SupportRepository (parameterized SQL) → SQL Server.

SupportAccess obtains the current actor and checks role/ownership. Customer requests never accept a customer ID in their body. The order must belong to that actor. Write methods are transactional, so a failed audit write rolls back the corresponding change. Version checks prevent stale browser forms from overwriting newer changes. The repository pattern separates database access from business rules; constructor injection keeps services testable.

Case lifecycle: New → Assigned → In Review → Resolved → Closed; Resolved/Closed may be Reopened, then assigned/reviewed again. Every staff update requires a note. New cases can be edited/deleted; handled cases retain their history. Replies to closed cases are rejected until reopening.

## Remaining work for final delivery

- Integrate real authentication/CSRF/session behavior with the account module. The shared project currently permits requests globally; demo identities are for local evaluation only.
- Implement event-driven order/payment/pickup/delivery notifications. Case conversations already persist, but automated notifications and email delivery are not implemented.
- Agree which settings other modules will consume; the CRUD screen currently maintains operational information.
- Add authoritative order/payment dates and delivery deadlines upstream before adding calendar revenue and on-time KPIs.
- Complete team integration, the design document (sprint summaries, use cases, class/activity diagrams and ethics), and a full group rehearsal.
- Review and understand each change before presenting it; the specification explicitly evaluates individual understanding.

## Verification

Verified on 14 September 2026:

- Java 26 compile and 33 module tests passed (0 failures, 0 errors): access checks, HTTP input validation, lifecycle transitions, create/edit/delete/reply business behavior and stale-version protection.
- All six support JavaScript files passed Node syntax checks; eight module HTML pages passed local-link and duplicate-ID checks.
- Reports page layout inspected in the browser. This was a static preview, not a live API/persistence demonstration.
- laundryLinkDB was created in local SQL Express on 14 September 2026 with user approval. Verified 12 users, 6 orders, 4 feedback records, both support tables and all 10 added feedback columns. Local TCP is now enabled on 127.0.0.1 and ::1 port 1433. JDBC Windows authentication successfully connected, and the full suite of 34 tests (including Spring Boot context startup with SQL Server) passed. The complete browser CRUD walkthrough remains to be verified.

See `support-structure-tests.log` for the latest run. The full-application context test now passes against SQL Server; see `database-startup-tests.log`. The remaining module tests use mocks and do not prove every SQL query or CRUD workflow. Run the integration checklist above against SQL Server after setup before describing the slice as demonstration-ready.



## Local connection fixed

SQL Express now accepts localhost TCP connections on port 1433. Its Windows-only authentication mode is preserved. The ignored `application-local.properties` uses `integratedSecurity=true`; the shared configuration imports that file optionally. Microsoft's signed `mssql-jdbc_auth-13.4.0.x64.dll` is installed in the project root and ignored by Git. Keep IntelliJ's working directory at the project root so Java can find this library. No existing SQL login passwords were changed.

Microsoft reference: https://learn.microsoft.com/en-us/sql/connect/jdbc/building-the-connection-url
