# Viva code guide — Reporting, Administration and Customer Support

Your documented major component is `support/` (Agksheya B.). The application is organised by feature: each feature groups its controller, service, repository and request/response types. Search **VIVA** in backend files to find the explanatory comments. This update adds comments and documentation only; it does not change business behaviour.

## Explain the Spring Boot structure

| Term                       | Meaning in this project                                                                                                                             | Example                                                                 |
| -------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------- |
| Server                     | The running Java application handles HTTP requests on port 8080. Spring Boot starts embedded Tomcat. SQL Server separately stores the data.         | `LaundryLinkApplication.java`, `application.properties`                 |
| Controller                 | Receives a URL/request, identifies the actor, validates request shape and delegates. Returns JSON/status to the browser.                            | `support/SupportController.java`, `/api/support/cases`                  |
| Service                    | Implements business rules and coordinates database operations. A service is a Java object, not the web server.                                      | `support/SupportService.java`                                           |
| Repository                 | Persistence boundary: queries and stores data. This project uses both Spring Data JPA and explicit JDBC.                                            | `support/SupportRepository.java`, `orders/OrderRepository.java`         |
| DTO                        | Data Transfer Object: the request/response shape between layers. Records and ordinary classes both serve as DTOs. A DTO is not necessarily a table. | `support/dto/SupportRequests.java`                                      |
| Entity                     | A Java object mapped to a database table with `@Entity`, `@Id` and relationships.                                                                   | `user/User.java`, `orders/Order.java`                                   |
| Configuration              | Sets up framework beans, role access, login and CSRF.                                                                                               | `config/SecurityConfig.java`                                            |
| Advice / exception handler | Converts failures into responses the browser understands.                                                                                           | `support/SupportErrors.java`, `common/ApiExceptionHandler.java`         |
| Dependency injection       | Spring constructs objects and supplies dependencies through constructors.                                                                           | `SupportService(SupportRepository, SupportAccess, NotificationService)` |
| Transaction                | Database operations succeed or roll back together.                                                                                                  | `@Transactional` on support writes                                      |
| Validation                 | `@Valid` activates DTO constraints; services check ownership and business rules.                                                                    | `@NotBlank`, `@Size`, status transition checks                          |
| Frontend                   | HTML gives the structure; CSS styles it; JavaScript sends API requests and renders responses.                                                       | `static/html`, `static/css`, `static/js`                                |

**A request's journey:** HTML button → JavaScript event handler → `fetch` API request → security/session checks → controller → service → repository/JDBC or JPA → SQL Server → JSON response → JavaScript updates the screen. HTML does not directly update database tables.

`services/Service.java` is the laundry service catalogue **entity**. `ServiceService.java` is its business service. The shared word does not make these the same kind of class.

## OOP concepts: show these exact examples

| Concept                   | Actual code to show                                                                                                                                          | What to say                                                                                                                                                                       |
| ------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Encapsulation             | Private fields in `User`, `Order`, `SupportService`, `SupportRepository`                                                                                     | State/dependencies belong to the object. Public methods expose operations. Lombok generates some getters/setters; private fields alone do not guarantee every business invariant. |
| Abstraction               | `ProcessingRoute`, `DiscountStrategy`, `BillingRepository`, `PromotionRepository`                                                                            | The interface describes what a collaborator can do without exposing its implementation.                                                                                           |
| Inheritance               | `UserRepository extends JpaRepository`; custom exceptions extending standard exceptions                                                                      | Repository interfaces inherit repository operations. Exception subclasses inherit exception behaviour.                                                                            |
| Interface implementation  | `WashRoute implements ProcessingRoute`, `PercentageDiscountStrategy implements DiscountStrategy`, `DatabaseUserDetailsService implements UserDetailsService` | The concrete class supplies the contract's behaviour. This is interface implementation, not a custom domain superclass hierarchy.                                                 |
| Runtime polymorphism      | `ProcessingRoute.nextStage/canAdvance` used by processing; `DiscountType.strategy()` invoked by `PromotionService`                                           | The same interface call executes the selected implementation. Different implementations give different behaviour.                                                                 |
| Composition / association | `SupportService` holds repository/access/notification references; `DiscountType` holds a strategy                                                            | Objects collaborate through has-a relationships. Injected shared beans are dependencies, not proof of strict UML lifetime composition.                                            |
| Method overloading        | `SupportService.cases(...)` overloads; `CaseInput` constructors                                                                                              | Same name with different parameter lists, selected at compile time.                                                                                                               |
| Method overriding         | `@Override` methods in route/discount/repository implementations                                                                                             | The concrete type provides the interface method implementation.                                                                                                                   |

**Do not claim:** Customer, Manager, Rider and Staff are Java subclasses of User. They are database role values and authorisation checks in this implementation. JPA relationships are associations, not inheritance. An enum holding statuses does not by itself implement the State pattern.

## Design patterns and architectural conventions

1. **Strategy — processing:** `processing/ProcessingRoute.java` is the contract; `WashRoute`, `DryCleanRoute`, `ShoeCleanRoute`, `IroningRoute` are implementations; `ProcessingTransitions.routeFor()` selects one; `ProcessingService` uses it. Benefit: route algorithms vary without putting all steps into one large conditional service. The selector is not a full GoF Factory Method implementation.
2. **Strategy — discounts:** `promotion/DiscountStrategy.java`, `PercentageDiscountStrategy.java`, `FixedAmountDiscountStrategy.java`, `DiscountType.java`, `PromotionService.java`. The enum holds the appropriate strategy and the service invokes its calculation. Benefit: percentage and fixed discounts use one contract.
3. **Repository — persistence boundary:** Your major component's `SupportRepository` wraps JDBC operations; `BillingRepository`/`BillingJdbcRepository` illustrate interface and implementation; Spring Data interfaces extend `JpaRepository`. Benefit: database concerns are separated from HTTP endpoints and business orchestration. In support, SQL statements are often assembled in the service and executed by the repository, so do not describe it as a fully SQL-free domain layer.
4. **Layered architecture / MVC-style separation:** Controller → service → repository, with static frontend pages and JSON responses. The project is a REST backend plus HTML/JS frontend, not server-rendered Thymeleaf MVC views.
5. **Dependency injection:** Constructor injection supplies collaborators. Spring beans normally have singleton scope; there is no need to claim a handwritten Singleton design pattern.

Database triggers react to changes and polling refreshes the UI. Describe these accurately as database event handling and polling; they are not a custom Java Observer class implementation.

## Your backend file map

| File under `support/`            | Responsibility / when to change it                                                                                                                   |
| -------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| `SupportController.java`         | API routes and controller request binding. Add/change endpoint URL here.                                                                             |
| `SupportService.java`            | Case lifecycle, ownership, assignment, message rules, feedback's one-response rule, customer satisfaction. Change business behaviour here.           |
| `SupportRepository.java`         | JDBC operations, shared case SELECT and audit insertion. Change persistence helper/read columns here.                                                |
| `SupportAccess.java`             | Authenticated Actor and role capabilities. Change support permission decisions here.                                                                 |
| `dto/SupportRequests.java`       | Input fields and Bean Validation. Change the contract here and update the frontend payload too.                                                      |
| `ReportService.java`             | Live reports, order cohort/date/service filters, report totals and alerts. Change report calculation/query here.                                     |
| `AdministrationService.java`     | Service/price/staff CRUD, account hashing and validation. Change administration rules here.                                                          |
| `RiderAssignmentController.java` | HTTP controller **and** package-private `RiderAssignmentService` in the same file. Manager assignment locks rows and updates delivery/order records. |
| `SupportErrors.java`             | Support error-to-HTTP response mapping.                                                                                                              |
| `package-info.java`              | Component ownership and package description.                                                                                                         |

Shared dependencies: `notification/NotificationService`, `config/SecurityConfig`, `account/AccountSessionGuard`, and `auth/DatabaseUserDetailsService`. Order, payment, processing and rider features belong to other system modules but integrate with yours.

## Your pages and their JavaScript/backend connections

Paths below are relative to `src/main/resources/static/`.

| Page                                                                                | Browser code                                           | Backend / purpose                                                                                                              |
| ----------------------------------------------------------------------------------- | ------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------ |
| `html/customer/feedback.html`                                                       | `js/support/cases.js`, `common.js`                     | Support entry: enquiry, complaint or Feedback selection; own requests and My feedback. Calls SupportController/SupportService. |
| `html/customer/reviews.html`                                                        | Redirect only                                          | Old feedback address redirects to Support; no separate customer menu.                                                          |
| `html/admin/customer-service-manager/complaints.html`, `html/support/cases.html`    | `cases.js`                                             | CSM triage/assignment; other staff's assigned cases; conversations and resolution. CSM alone sees history.                     |
| `html/support/feedback.html`                                                        | `cases.js` in feedback-only mode                       | CSM feedback list and one response, without case assignment/status journey.                                                    |
| `html/admin/customer-service-manager/dashboard.html`, `html/support/dashboard.html` | `js/support/overview.js`                               | Live case overview and links into queue; scoped by actor.                                                                      |
| `html/admin/customer-service-manager/orders.html`                                   | `js/support/orders.js`                                 | Order lookup/history for customer assistance; ReportService.                                                                   |
| `html/admin/manager/dashboard.html`                                                 | `js/manager/dashboard.js` calls `/api/support/reports` | Live business totals/alerts and refresh; ReportService.                                                                        |
| `html/admin/owner/reports.html`, `dashboard.html`, `administration_overview.html`   | `js/support/reports.js`                                | Generate selected report, charts and CSV; ReportService. CSV is built from the returned JSON in the browser.                   |
| `html/admin/manager/service_catalog.html`                                           | Inspect `data-page` and `common.js` handlers           | Catalogue/pricing → AdministrationService.                                                                                     |
| `html/admin/manager/staff_accounts.html`                                            | `common.js` administration handlers                    | Staff account create/edit/deactivate → AdministrationService.                                                                  |
| `html/admin/manager/assign_riders.html`                                             | `js/manager/rider-assignments.js` sends PUT assignment | RiderAssignmentController and same-file RiderAssignmentService.                                                                |
| `html/admin/owner/payments_billing.html`, `payment_detail.html`                     | `js/admin/payment-management.js`                       | PaymentService, billing and management repository. Shared operational integration, not the support service.                    |

For any page, its `<script src=...>` tags are the authoritative browser controller map. `global-pre.js` builds role navigation and the notification bell. `support.css` styles support/report panels. `common.js` includes both shared API helpers and several administration page handlers.

## The exact write/status locations to show

**Support case creation:** `cases.js` case-form submit → POST `/api/support/cases` → `SupportController.create` → `SupportService.create` → INSERT `feedback`; audit INSERT `support_activity`; CSM notification INSERT `notifications`.

**Assignment/progress/resolution:** handle-form or Handle this myself → PATCH `/cases/{id}` → `SupportService.handle`. Look for `UPDATE feedback SET caseStatus=?, priority=?, assigneeID=?`. `TRANSITIONS` and `allowed()` define legal moves; optimistic `version` avoids silently overwriting a newer update. Customer messages live in `chat`, not `support_activity`.

**Reply:** POST `/cases/{id}/messages` → `SupportService.message` → case version update + INSERT `chat` + audit and recipient notification. Closed support requests reject messages. Feedback rejects customer follow-up and permits one CSM response only.

**Customer confirmation:** POST `/cases/{id}/satisfaction` → `SupportService.satisfaction`. Customer ownership and latest staff reply are checked. Yes sets `Closed`; No sets `In Review` or `Reopened`. The response is recorded and support is notified. Frontend Yes closes the panel; No keeps the reply form available.

**Administration:** service/staff/pricing forms in `common.js` → `/administration/...` → `AdministrationService.createService/updateService/setPrice/createStaff/updateStaff` → services/servicePricing/users writes. Staff passwords are hashed; account update versions and permissions are checked.

**Order status flow, across modules:**

| Step                                                                       | Where to show                                                                                           |
| -------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------- |
| New order → Unconfirmed                                                    | `orders/OrderService.createOrder` (look for initial status and save)                                    |
| Payment approval → Payment Verified → Awaiting Pickup                      | `payment/PaymentService.verifyPaymentDecision`; `PaymentManagementJdbcRepository.updateOrderStatus`     |
| Manager pickup/delivery assignment → 4 or 13                               | `support/RiderAssignmentController.java`, inner `RiderAssignmentService.assign`                         |
| Rider pickup → shop handover (6 → 7)                                       | `rider/RiderRepository.pickupDeliveredToShop` and status update helpers                                 |
| Staff receipt (7 → 8), cleaning route, quality inspection (21), ready (12) | `processing/ProcessingService`, `ProcessingRoute` implementations, `ProcessingRepository.updateStatus`  |
| Rider delivery → Delivered                                                 | `rider/RiderService` and `RiderRepository` delivery/status methods                                      |
| History and automatic notification on order changes                        | SQL `dbo.trg_order_status_log`, `dbo.trg_order_notifications`; migrations and `initialize_database.sql` |

Support statuses and order statuses are different fields: `feedback.caseStatus` vs `orders.statusID`. Do not confuse closing a complaint with changing its laundry order to Delivered.

## If the examiner asks for a change

- Change label/layout/colour: HTML/CSS. Change click/payload/rendering: the page's JS.
- Add a request field: DTO + JS payload + validation; add storage column through a migration if it must persist.
- Change who can assign: SupportAccess + SupportService checks; frontend visibility alone is not security.
- Change allowed support transitions: TRANSITIONS/allowed, handle/satisfaction and the frontend status options.
- Change feedback rules: SupportService.message/edit and cases.js feedback view.
- Change report totals/filter basis: ReportService SQL; chart appearance only: reports.js/support.css.
- Change notification recipients/link: NotificationService caller or relevant SQL trigger; bell display: global-pre.js.
- Add a cleaning route: ProcessingRoute implementation, route selection and matching staff timeline.
- Change payment-release status: PaymentService verification + repository update; do not change support case code.

## Short answers you can say aloud

“My component is reporting, administration and customer support. Controllers expose APIs, services enforce business rules, and repositories access SQL Server. The frontend sends JSON and renders the result.”

“I show encapsulation with private fields, abstraction with interfaces, inheritance through repository/exception types, and polymorphism through processing and discount strategies. Roles are values, not User subclasses.”

“Case assignment is saved in feedback.assigneeID; replies are in chat; audit events are in support_activity. Case status updates and customer satisfaction are in SupportService. Laundry order status updates belong to payment, processing and rider modules.”

See **Viva-Backend-File-Index.md** for every backend file and **Viva-Page-Index.md** for every HTML page's actual script references.
