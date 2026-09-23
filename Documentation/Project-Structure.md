# Spring Boot structure and member ownership

The project uses **layer first, then module**. Every controller belongs under `controller/<module>`, every service under `service/<module>`, and so on. Keep class names descriptive of the feature rather than using member names.

Base Java package: `_6.Y2.S1.MTR._6.LaundryLink`.
Base source directory: `src/main/java/_6/Y2/S1/MTR/_6/LaundryLink`.

## Member destinations

| Module folder | Member | Responsibility |
|---|---|---|
| `account` | Vipusha V. | User and account management |
| `order` | Monessha S. | Order and reservation management |
| `processing` | Perera G.A.T.L. | Laundry processing |
| `rider` | Lathurshan S. | Pickup and delivery |
| `payment` | Shathurshigah R. | Payments, billing and promotions |
| `support` | Agksheya B. | Reporting, administration and customer support |
| `shared` | Shared integration code | Existing order statuses and status logs |

These destinations follow the responsibility map in the project briefing. Account, order, processing and payment backend implementations have not yet been merged into this checkout. Their `package-info.java` files document the intended locations without creating placeholder endpoints or beans.

## What belongs in each folder

| Folder | Contents | Existing example |
|---|---|---|
| `controller/<module>` | `@RestController` classes; map HTTP requests to service calls and validate request bodies | `controller/support/SupportController.java` |
| `service/<module>` | Business rules and transactions | `service/support/SupportService.java` |
| `repository/<module>` | JPA repositories or JDBC/native database access | `repository/rider/RiderRepository.java` |
| `entity/<module>` | `@Entity` classes representing stored domain records | `entity/shared/Log.java` |
| `dto/<module>` | Request and response types, including input constraints | `dto/support/SupportRequests.java` |
| `config` or `config/<module>` | Spring configuration and bean declarations | `config/SecurityConfig.java` |
| `security/<module>` | Identity resolution and access checks | `security/support/SupportAccess.java` |
| `exception/<module>` | Controller advice and API error handling | `exception/support/SupportErrors.java` |

Create model classes only when the implementation needs them. Support currently uses JDBC over existing tables, so its `entity` folder is reserved for future typed models. Rider also queries existing shared tables directly.

## Example: where Agksheya's files go

```text
controller/support/SupportController.java
service/support/SupportService.java
service/support/SettingsService.java
service/support/ReportService.java
repository/support/SupportRepository.java
dto/support/SupportRequests.java
security/support/SupportAccess.java
exception/support/SupportErrors.java
```

For another member, use the same pattern, for example `controller/order/OrderController.java`, `service/order/OrderService.java`, and `repository/order/OrderRepository.java`.

The Java `package` declaration must match the file location. For example:

```java
package _6.Y2.S1.MTR._6.LaundryLink.controller.order;
```

## Resources and tests

- HTML, JavaScript and CSS remain in `src/main/resources/static`. The existing browser paths are preserved so navigation does not break. The pages are static frontend assets calling REST controllers, not server-rendered templates.
- Application configuration remains in `src/main/resources`; machine-specific credentials go in ignored `application-local.properties`.
- Additive SQL migrations live in `database/migrations`. Do not rerun the destructive sample initializer against an existing database.
- Tests mirror the class package under `src/test/java`, such as `service/support/SupportServiceTest.java`.
- `LaundryLinkApplication` stays in the base package so Spring Boot can discover nested controllers, services, repositories and entities automatically.

## Moving work from another branch

The existing source moves are:

| Old package | New package |
|---|---|
| `rider.controller` | `controller.rider` |
| `rider.service` | `service.rider` |
| `rider.repository` | `repository.rider` |
| `rider.dto` | `dto.rider` |
| `rider.config` | `config.rider` |
| `Sercurity` | `config` |
| Flat `logs` and `status` packages | Corresponding `controller.shared`, `service.shared`, `repository.shared`, `entity.shared` layers |
| Flat `support` package | The support layer packages shown above |

When merging another member's branch, move any newly added classes into the appropriate destination and update their package declarations and imports. Do not keep a second copy of an existing controller or entity in its old package: that can cause duplicate Spring beans or entity mappings. HTTP routes and database table names have not changed as part of this reorganization.

Run a **clean** build after a package move so stale compiled classes in `target` cannot be discovered:

```powershell
mvn clean test "-Dtest=Support*Test"
```

The restructuring passed this clean build with 33 support tests. Full application startup remains dependent on the SQL Server setup described in the evaluation guide.
