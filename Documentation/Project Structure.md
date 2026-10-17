# Spring Boot structure and member ownership

The project uses **package-by-feature**. Everything that implements a feature — controller, service, repository, entity and request/response types — lives together in that feature's package. There are no top-level `controller`, `service`, `repository`, `entity` or `dto` packages. Keep class names descriptive of the feature rather than using member names.

Base Java package: `_6.Y2.S1.MTR._6.LaundryLink`.
Base source directory: `src/main/java/_6/Y2/S1/MTR/_6/LaundryLink`.

This replaced the earlier layer-first layout (`controller/<module>`, `service/<module>`, …).

## Repository layout

```text
src/main/java/.../LaundryLink/    Feature packages (see below) and LaundryLinkApplication
src/main/resources/
  application.properties          Shared configuration; imports the ignored application-local.properties
  static/                         Browser frontend served as-is
    index.html                    Public landing page
    html/<area>/                  Pages per area: public, auth, account, customer, staff, rider, support, admin/{manager,owner,customer-service-manager}
    js/, css/                     Scripts and styles, grouped by the same areas; global-pre.js / global-post.js and global.css are shared
    images/                       Static images
    error/404.html                Spring Boot error page
src/test/java/.../LaundryLink/    Tests, mirroring the feature packages
Database/
  initialize_database.sql         Destructive full-schema initializer for a fresh database
  migrations/                     Numbered, additive migrations (001_… to 017_…)
  DDD - Assignment 2 SQL/         DDD assignment queries, sample data and demo scripts
  create_*_orders.sql             Demo order data
scripts/                          PowerShell helpers (database setup, demo-data cleanup, recovery email, local start) and Audit-Frontend.cjs
Documentation/                    Project guides, design-pattern write-ups, ER diagrams (ER Diagram/V1–V4) and viva material
pom.xml, mvnw, mvnw.cmd, .mvn/    Maven build and wrapper
```

## Member destinations

| Feature packages                                              | Member                  | Responsibility                                                      |
| ------------------------------------------------------------- | ----------------------- | ------------------------------------------------------------------- |
| `account`, `address`, `user`, `auth`                          | Vipusha V.              | User and account management                                         |
| `orders`, `orderlines`, `items`, `services`, `servicepricing` | Monessha S.             | Order and reservation management                                    |
| `processing`                                                  | Perera G.A.T.L.         | Laundry processing                                                  |
| `rider`                                                       | Lathurshan S.           | Pickup and delivery                                                 |
| `payment`, `billing`, `promotion`                             | Shathurshigah R.        | Payments, billing and promotions                                    |
| `support`                                                     | Agksheya B.             | Reporting, administration and customer support                      |
| `status`, `log`, `notification`                               | Shared integration code | Order statuses, status logs and the cross-module notification inbox |
| `common`, `config`                                            | Shared                  | API exception types; Spring and security configuration              |

These destinations follow the responsibility map in the project briefing.

## What belongs in a feature package

| Kind of class                                                                             | Where it goes                                                                   | Existing example                   |
| ----------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------- | ---------------------------------- |
| `@RestController` classes; map HTTP requests to service calls and validate request bodies | `<feature>/`                                                                    | `support/SupportController.java`   |
| Business rules and transactions                                                           | `<feature>/`                                                                    | `support/SupportService.java`      |
| JPA repositories or JDBC/native database access                                           | `<feature>/`                                                                    | `rider/RiderRepository.java`       |
| `@Entity` classes representing stored domain records                                      | `<feature>/`                                                                    | `log/Log.java`                     |
| Request and response types, including input constraints                                   | `<feature>/dto/` (or beside the other classes, as `payment` and `promotion` do) | `support/dto/SupportRequests.java` |
| Identity resolution and access checks for the feature                                     | `<feature>/`                                                                    | `support/SupportAccess.java`       |
| Controller advice and API error handling for the feature                                  | `<feature>/`                                                                    | `support/SupportErrors.java`       |
| Spring configuration and bean declarations                                                | `config/`                                                                       | `config/SecurityConfig.java`       |

Cross-module notifications live in `notification/NotificationController.java` and `notification/NotificationService.java`. Feature services can publish notifications through this shared service, while database triggers cover direct order, payment and delivery writes from other modules.

Create model classes only when the implementation needs them. Support uses JDBC over existing tables, so it has no entity classes. Rider also queries existing shared tables directly.

Each feature package may carry a `package-info.java` describing what it covers and who owns it, for example `processing/package-info.java`.

## Example: where Agksheya's files go

```text
support/SupportController.java
support/SupportService.java
support/AdministrationService.java
support/ReportService.java
support/SupportRepository.java
support/SupportAccess.java
support/SupportErrors.java
support/dto/SupportRequests.java
```

For another member, use the same pattern, for example `orders/OrderController.java`, `orders/OrderService.java`, and `orders/OrderRepository.java`.

The Java `package` declaration must match the file location. For example:

```java
package _6.Y2.S1.MTR._6.LaundryLink.orders;
```

## Resources and tests

- HTML, JavaScript and CSS remain in `src/main/resources/static`. The existing browser paths are preserved so navigation does not break. The pages are static frontend assets calling REST controllers, not server-rendered templates.
- Spring Boot error pages live in `src/main/resources/static/error`, named by status code (`error/404.html`). Spring Boot serves them at the URL that failed, so they use absolute asset paths such as `/css/global.css`. API requests still receive the JSON error body.
- Application configuration remains in `src/main/resources`; machine-specific credentials go in ignored `application-local.properties`.
- Additive SQL migrations live in `Database/migrations`. Do not rerun the destructive sample initializer (`Database/initialize_database.sql`) against an existing database.
- Tests mirror the class package under `src/test/java`, such as `support/SupportServiceTest.java`. Keeping a test in the same package as its class lets it use package-private members.
- `LaundryLinkApplication` stays in the base package so Spring Boot can discover nested controllers, services, repositories and entities automatically.

## Moving work from another branch

When merging a branch that still uses the layer-first packages, move any newly added classes into the appropriate feature package and update their `package` declarations and imports, using the member table above to pick the package. Do not keep a second copy of an existing controller or entity in its old package: that can cause duplicate Spring beans or entity mappings. HTTP routes and database table names did not change as part of the reorganization.

Run a **clean** build after a package move so stale compiled classes in `target` cannot be discovered:

```powershell
.\mvnw.cmd clean test
```

The Maven wrapper (`mvnw` / `mvnw.cmd`) needs `JAVA_HOME` pointing at a Java 26 JDK; an installed `mvn` works the same way.

Full application startup and the `@SpringBootTest` classes depend on a SQL Server `laundryLinkDB` database. `scripts/Initialize-SupportDatabase.ps1` creates it on a fresh server or applies the migrations to an existing one. Those classes are `LaundryLinkApplicationTests`, `processing/ProcessingIntegrationTest`, `account/PasswordRecoveryIntegrationTest`, `account/PasswordOtpIntegrationTest`, `support/SupportAssignmentIntegrationTest` and `support/RiderAssignmentIntegrationTest`. To run only tests that need no database, filter by class and exclude the integration tests, for example `.\mvnw.cmd clean test "-Dtest=Support*Test,!*IntegrationTest"`.
