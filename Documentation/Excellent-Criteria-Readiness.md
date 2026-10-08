# Excellent-column readiness: final presentation and viva

Audit date: 8 October 2026. Scope: the running Spring Boot application in `src/main`, not the old standalone frontend copies. The supplied screenshots cover the 70-mark demonstration/viva section only. The 30-mark final-report rubric was not supplied. This document is evidence and a release checklist, not a guarantee of marks.

## Criteria and evidence

| Criterion | Excellent target | Current evidence | Remaining acceptance gate |
|---|---|---|---|
| UI and usability (10) | Complete, consistent, professional, usable screens | Shared navigation/styles; 51 HTML pages checked; no missing literal local links. Manager dashboard displays database reports. Shared fake-save/button notifications removed. | Inspect every role at desktop and mobile widths, keyboard navigation, empty/loading/error states. Email password recovery is implemented; SMTP configuration and mailbox receipt still need verification. |
| CRUD and validation (12) | Accurate create/read/update/delete and strong input validation across relevant modules | Account, address, order, processing, rider, payment, promotion and support test classes exist. `SupportRequests` has server constraints; registration checks email, phone, password and confirmation. | Execute the UI CRUD matrix below against SQL Server. A passing mocked test does not prove UI persistence. |
| DB connectivity (5) | Reliable storage/retrieval through UI with no demo errors | JDBC/JPA repositories; real-database startup and processing integration tests. | Record the live test result and complete a browser create-refresh-read check. |
| Integration/stability (10) | Smooth navigation and correct cross-feature data flow | Shared order/status/log/notification tables; processing transition tests; no JavaScript syntax errors across 26 files. | Rehearse one order across customer, rider, staff, payment and support with the same order ID. Check role access and notifications. |
| Design patterns (5) | At least TWO relevant implemented patterns, justified with references | MVC separation and Repository pattern documented below. | Each member must explain their use in their own feature; confirm the lecturer accepts these architectural patterns for this criterion. |
| Teamwork/demo (8) | Clear handovers, equal participation, confident delivery within time | Six feature owners already recorded in `Project-Structure.md`; proposed handovers below. | Assign speakers and actual timings, rehearse with a stopwatch; these are human delivery criteria. |
| Individual understanding (15) | Direct, confident answers and full understanding of owned code | Code-reading and viva prompts below. | Every member answers without reading a script and explains a request through all layers. The screenshot's scoring text says 9–10 despite a 15-mark heading; clarify with the lecturer. |
| Oral communication (5) | Clear, professional language, pace and engagement | Speaker preparation guide below. | Rehearse aloud, remove filler words, use accurate terms, and stop within allocated time. |

## Two implemented design patterns

### 1. MVC separation adapted to a REST frontend

View: `src/main/resources/static/html/customer/feedback.html` and `static/js/support/cases.js` display and submit case information.
Controller: `support/SupportController.java` maps HTTP methods and validates request bodies using `@Valid`.
Model/business layer: `support/SupportService.java` applies workflow, access and transaction rules; `SupportRepository.java` accesses persistent records.
Other features follow the same separation, for example orders and processing. The frontend view is rendered in the browser; this is not server-side template rendering.
Benefit: UI changes do not require moving SQL into the page, and rules can be tested without browser automation. Explain the concrete POST-case request through these layers.

### 2. Repository pattern

`billing/BillingRepository.java` defines the data operations used by billing. `BillingJdbcRepository.java` implements them with SQL. `BillingService.java` receives the repository through its constructor and calculates totals/discounts independently of JDBC details. `BillingServiceTest.java` verifies billing with controlled repository results.
`user/UserRepository.java`, `orders/OrderRepository.java` and `address/AddressRepository.java` provide the corresponding JPA repository abstractions. Support and processing also keep database access in repository classes.
Benefit: persistence concerns stay separate from business rules; tests can isolate calculations and failures. Explain the interface, implementation, constructor injection and the test double using actual code.
Do not claim Strategy, Factory or a full State-object pattern merely because code uses an enum or switch. `ProcessingTransitions` is a workflow rule table; explain its allowed transitions accurately.

## UI persistence and validation matrix

Use disposable records in a demo database. Record role, record ID, action, observed result, database/read-after-refresh evidence and pass/fail for each row. Do not delete historical business data just to demonstrate a delete button; demonstrate cancellation, deactivation or soft deletion where that is the domain rule.

| Module | Valid demonstration | Invalid/boundary demonstration |
|---|---|---|
| Accounts/addresses | Register, login, read/update profile; create/edit/delete own address; reload | Duplicate email, malformed email, non-10-digit phone, weak/mismatched password; another customer's address |
| Orders | Create item lines, read details, edit permitted order, cancel eligible order; reload | Zero/negative quantity, missing address, stale or noneditable order, another customer's order |
| Processing | Receive correct quantities, advance wash/dry-clean route, QC fail/rework then pack | Skip a stage, incorrect received quantity, wrong route, pack before QC |
| Rider | Assign task, pickup, delivery, failure with reason; inspect customer status | Wrong rider/task, repeat completion, missing failure reason |
| Payments/promotions | Create/read/edit authorised payment, promotion CRUD, apply eligible discount; read invoice | Negative/overpayment, invalid/expired discount, duplicate submission, unauthorised payment edit |
| Support/admin | Customer create/read/edit/delete own case; CSM assign/reply/resolve; manager update service/price and staff status | Blank/oversize message, rating outside 1–5, stale version, invalid role/price, customer reads another case |
| Dashboards | Change one source record, reload/refresh relevant dashboard and verify count/status | Empty database, lost session, failed API; show an error instead of fabricated success |

## Integrated demo and handovers

Use one order ID throughout; record it before the rehearsal. Suggested order, based on the existing ownership guide:

1. Vipusha: authenticate, validate a rejected input, profile/address persistence. Handover: the customer is ready to book.
2. Monessha: place an order and show saved details, item quantities and pickup information. Announce order ID.
3. Lathurshan: show the assigned pickup and progress it to the shop. Handover: order ID is now ready for processing.
4. Perera: receive items, follow the correct route, show QC and packing; hand back for delivery.
5. Shathurshigah: show persisted billing/payment and promotion rules at the appropriate point in the workflow. Explain totals using actual order values.
6. Agksheya: show a customer support case, assignment/reply/resolution, service administration, and manager/owner database reports. Refresh after a real record change.

Adjust the sequence to the application's actual payment requirements and lecturer's time limit. Give each member equal speaking time. Each handover names the record ID, completed step and next role. Use separate browser sessions for roles; do not leave another member blocked by logging out their shared session.

## Viva preparation

Each member should demonstrate and answer:

- Which page and script call my endpoint? Which controller, service, repository and table does it use?
- What is checked in the browser, DTO, business service, access layer and database? Why are browser checks insufficient?
- How is the current account resolved? How is record ownership enforced? What happens with a wrong role?
- What happens if the database write fails? Where is the transaction boundary? Is the UI success message shown only after success?
- Why do we cancel/deactivate/soft-delete some records? What happens to related history?
- Which two patterns are implemented, where are they used, and what benefit does each provide?
- Which test proves my main rule? Which boundary or failure does it exercise? Which tests require the real database?
- How does my feature change another module's data, status or notification?

Answer with the rule, one concrete example, and the relevant code. Say when a capability is unfinished. Avoid claims of real-time push: the manager dashboard polls every 30 seconds and has a manual refresh.

## Known gaps and final gate

- Email recovery now has real POST endpoints, expiring hashed tokens and single-use reset. Supply SMTP configuration and verify receipt using `Email-Password-Recovery.md`; no live mailbox delivery has been verified.
- Literal link/syntax checks do not verify generated navigation, permissions, visual consistency or API behaviour. Complete a browser rehearsal for every role.
- Full report assessment needs the missing final-report rubric and the final report itself.
- Database connectivity and the complete live demo must pass on the machine used for assessment. Do not reuse older test reports as current evidence.

Commands: `node scripts/Audit-Frontend.cjs`; installed Maven `mvn test`. Java is configured as 25 in pom.xml; the current local JDK is 26. The Surefire configuration starts Mockito as a Java agent so the suite does not depend on dynamic attachment.


## Verified results on 8 October 2026

- Final clean full Maven run: 292 tests, zero failures, zero errors, zero skipped. This includes real SQL Server password reset/expiry/reuse checks, processing receiving and issues, QC/rework/packing, order-status history, rider assignment and support assignment/chat/resolution for all seven staff role variants.
- Frontend audit: 53 HTML pages, 28 JavaScript files, zero missing literal local links, JavaScript syntax errors or shared-controller DOM contract errors.
- Browser check: Manager demo login reached the dashboard, loaded 5 actual orders and LKR 4,530 recorded payments, and manual refresh updated its timestamp.
- Applied missing processing table definitions from migration 005 without its sample-data section, migration 008 for status history, and migration 012 for password recovery. Existing records/history were preserved.
- Fixed feedback inserts to use OUTPUT INTO, allowing support-case creation when SQL Server feedback triggers are enabled.
- Full visual/CRUD browser rehearsal and SMTP mailbox delivery are still acceptance gates. Automated passing tests alone do not certify every Excellent-column requirement or guarantee a mark.
