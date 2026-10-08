# Package restructure: layer-first to package-by-feature

Date: 2026-10-02

The Java source tree used to mix two layouts: most features were already in their own package (`orders`, `payment`, `processing`, …), while rider, support, the shared status/log/notification code and part of account sat in layer-first packages (`controller/<module>`, `service/<module>`, `repository/<module>`, `entity/<module>`, `dto/<module>`, `security/<module>`, `exception/<module>`). Everything now follows **package-by-feature**. Use the lookup tables below to find a file that moved.

## What changed and what did not

- 29 main classes and 7 test classes moved into feature packages. 3 of them were also renamed (two classes and one test, all in `account`).
- 30 placeholder `package-info.java` files were deleted, and the top-level layer directories were removed.
- Six new `package-info.java` files describe the feature packages that received code.
- `package` and `import` lines were updated wherever a moved class is used.

Not changed: HTTP routes, request and response JSON, database tables and migrations, the static frontend under `src/main/resources/static`, Spring configuration, and the contents of the feature packages that already existed (`address`, `auth`, `billing`, `common`, `config`, `items`, `orderlines`, `orders`, `payment`, `processing`, `promotion`, `servicepricing`, `services`, `user`) apart from their import lines. No logic was changed: the only edits inside classes are the two renamed type names where they are used.

## File lookup

All paths in this document are relative to the project root. To keep the tables narrow, each section states its base directory once and the table cells hold only the rest of the path: **full path = base + cell**.

### Main sources

Base: `src/main/java/_6/Y2/S1/MTR/_6/LaundryLink/`

#### `account/` — Vipusha V. — user and account management

| Old path | New path | Notes |
|---|---|---|
| `controller/account/AuthController.java` | `account/AuthController.java` |  |
| `controller/account/CustomerDashboardController.java` | `account/CustomerDashboardController.java` |  |
| `controller/account/ProfileController.java` | `account/ProfileController.java` |  |
| `dto/account/ProfileRequests.java` | `account/dto/ProfileRequests.java` |  |
| `dto/account/RegistrationRequest.java` | `account/dto/CustomerRegistrationRequest.java` | **Renamed** from `RegistrationRequest` (the name clashed with the existing `account/dto/RegistrationRequest`) |
| `service/account/AccountService.java` | `account/AccountProfileService.java` | **Renamed** from `AccountService` (the name clashed with the existing `account/AccountService` interface) |

#### `rider/` — Lathurshan S. — pickup and delivery

| Old path | New path | Notes |
|---|---|---|
| `controller/rider/RiderController.java` | `rider/RiderController.java` |  |
| `dto/rider/FailureRequest.java` | `rider/dto/FailureRequest.java` |  |
| `dto/rider/RiderTaskDTO.java` | `rider/dto/RiderTaskDTO.java` |  |
| `repository/rider/RiderRepository.java` | `rider/RiderRepository.java` |  |
| `service/rider/RiderService.java` | `rider/RiderService.java` |  |

#### `support/` — Agksheya B. — reporting, administration and customer support

| Old path | New path | Notes |
|---|---|---|
| `controller/support/SupportController.java` | `support/SupportController.java` |  |
| `dto/support/SupportRequests.java` | `support/dto/SupportRequests.java` |  |
| `exception/support/SupportErrors.java` | `support/SupportErrors.java` |  |
| `repository/support/SupportRepository.java` | `support/SupportRepository.java` |  |
| `security/support/SupportAccess.java` | `support/SupportAccess.java` |  |
| `service/support/AdministrationService.java` | `support/AdministrationService.java` |  |
| `service/support/ReportService.java` | `support/ReportService.java` |  |
| `service/support/SupportService.java` | `support/SupportService.java` |  |

#### `status/` — Shared integration code — order statuses

| Old path | New path | Notes |
|---|---|---|
| `controller/shared/StatusController.java` | `status/StatusController.java` |  |
| `entity/shared/Status.java` | `status/Status.java` |  |
| `repository/shared/StatusRepository.java` | `status/StatusRepository.java` |  |
| `service/shared/StatusService.java` | `status/StatusService.java` |  |

#### `log/` — Shared integration code — order status logs

| Old path | New path | Notes |
|---|---|---|
| `controller/shared/LogController.java` | `log/LogController.java` |  |
| `entity/shared/Log.java` | `log/Log.java` | Now imports `status.Status` (previously same package) |
| `repository/shared/LogRepository.java` | `log/LogRepository.java` |  |
| `service/shared/LogService.java` | `log/LogService.java` |  |

#### `notification/` — Shared integration code — notification inbox

| Old path | New path | Notes |
|---|---|---|
| `controller/shared/NotificationController.java` | `notification/NotificationController.java` |  |
| `service/shared/NotificationService.java` | `notification/NotificationService.java` |  |

### Tests

Base: `src/test/java/_6/Y2/S1/MTR/_6/LaundryLink/`

#### `account/` — Vipusha V. — user and account management

| Old path | New path | Notes |
|---|---|---|
| `dto/account/ProfilePasswordTest.java` | `account/dto/ProfilePasswordTest.java` |  |
| `dto/account/RegistrationRequestTest.java` | `account/dto/CustomerRegistrationRequestTest.java` | **Renamed** from `RegistrationRequestTest`, following its class |

#### `rider/` — Lathurshan S. — pickup and delivery

| Old path | New path | Notes |
|---|---|---|
| `service/rider/RiderServiceTest.java` | `rider/RiderServiceTest.java` |  |

#### `support/` — Agksheya B. — reporting, administration and customer support

| Old path | New path | Notes |
|---|---|---|
| `controller/support/SupportControllerTest.java` | `support/SupportControllerTest.java` |  |
| `security/support/SupportAccessTest.java` | `support/SupportAccessTest.java` |  |
| `service/support/AdministrationServiceTest.java` | `support/AdministrationServiceTest.java` |  |
| `service/support/SupportServiceTest.java` | `support/SupportServiceTest.java` |  |

## Package lookup (for fixing imports)

Package names are relative to the base package `_6.Y2.S1.MTR._6.LaundryLink`.

| Old package | New package | Classes |
|---|---|---|
| `controller.account` | `account` | `AuthController`, `CustomerDashboardController`, `ProfileController` |
| `controller.rider` | `rider` | `RiderController` |
| `controller.shared` | `log` | `LogController` |
| `controller.shared` | `notification` | `NotificationController` |
| `controller.shared` | `status` | `StatusController` |
| `controller.support` | `support` | `SupportController` |
| `dto.account` | `account.dto` | `ProfileRequests`, RegistrationRequest → **CustomerRegistrationRequest** |
| `dto.rider` | `rider.dto` | `FailureRequest`, `RiderTaskDTO` |
| `dto.support` | `support.dto` | `SupportRequests` |
| `entity.shared` | `log` | `Log` |
| `entity.shared` | `status` | `Status` |
| `exception.support` | `support` | `SupportErrors` |
| `repository.rider` | `rider` | `RiderRepository` |
| `repository.shared` | `log` | `LogRepository` |
| `repository.shared` | `status` | `StatusRepository` |
| `repository.support` | `support` | `SupportRepository` |
| `security.support` | `support` | `SupportAccess` |
| `service.account` | `account` | AccountService → **AccountProfileService** |
| `service.rider` | `rider` | `RiderService` |
| `service.shared` | `log` | `LogService` |
| `service.shared` | `notification` | `NotificationService` |
| `service.shared` | `status` | `StatusService` |
| `service.support` | `support` | `AdministrationService`, `ReportService`, `SupportService` |

Static and nested imports follow the same mapping, for example `dto.support.SupportRequests.*` → `support.dto.SupportRequests.*` and `security.support.SupportAccess.Actor` → `support.SupportAccess.Actor`.

## New files

Base: `src/main/java/_6/Y2/S1/MTR/_6/LaundryLink/`

- `account/package-info.java`
- `rider/package-info.java`
- `support/package-info.java`
- `status/package-info.java`
- `log/package-info.java`
- `notification/package-info.java`

Outside that base: `Documentation/Package-Restructure.md` (this file).

## Deleted files and directories

The layer-first placeholders only held a comment ("Add module classes here as they are implemented") and no code.

Base: `src/main/java/_6/Y2/S1/MTR/_6/LaundryLink/`

| Layer directory | Deleted `package-info.java` in |
|---|---|
| `controller/` | `controller/account/`, `controller/order/`, `controller/payment/`, `controller/processing/`, `controller/rider/`, `controller/support/` |
| `dto/` | `dto/account/`, `dto/order/`, `dto/payment/`, `dto/processing/`, `dto/rider/`, `dto/support/` |
| `entity/` | `entity/account/`, `entity/order/`, `entity/payment/`, `entity/processing/`, `entity/rider/`, `entity/support/` |
| `repository/` | `repository/account/`, `repository/order/`, `repository/payment/`, `repository/processing/`, `repository/rider/`, `repository/support/` |
| `service/` | `service/account/`, `service/order/`, `service/payment/`, `service/processing/`, `service/rider/`, `service/support/` |

Removed directories under that base: `controller/`, `service/`, `repository/`, `entity/`, `dto/`, `security/`, `exception/`.

Removed directories under `src/test/java/_6/Y2/S1/MTR/_6/LaundryLink/`: `controller/`, `service/`, `dto/`, `security/`.

## Files edited in place

These files did not move; only their `import` lines changed (and the two documents were rewritten).

Main sources — base: `src/main/java/_6/Y2/S1/MTR/_6/LaundryLink/`

- `orders/Order.java`
- `orders/OrderService.java`
- `payment/PaymentService.java`
- `processing/ProcessingIssueService.java`

Tests — base: `src/test/java/_6/Y2/S1/MTR/_6/LaundryLink/`

- `LaundryLinkApplicationTests.java`
- `orders/OrderServiceTest.java`
- `payment/PaymentServiceTest.java`
- `processing/ProcessingIssueServiceTest.java`

Documents:

- `Documentation/Project-Structure.md`
- `README.md`

## Merging a branch that still uses the old layout

1. Merge or rebase as usual. Git follows most of the moves as renames; a file you changed that also moved may show up as a conflict at its old path.
2. For any class your branch adds under an old layer package, move it to the feature package from the tables above and update its `package` line.
3. Fix imports using the package lookup. Remember the two renames: `AccountService` (the JDBC class) is now `AccountProfileService`, and the `dto.account` `RegistrationRequest` is now `CustomerRegistrationRequest`. The `account/AccountService` interface and `account/dto/RegistrationRequest` are different, unchanged types.
4. Do not leave a second copy of a class at its old location: it would register a duplicate Spring bean or entity.
5. Run `mvn clean test` (or `./mvnw clean test`). The `clean` matters, because stale classes in `target/` are otherwise still discovered.

## Verification

`./mvnw clean test` was run before the restructure and again after it, against the local SQL Server database:

| Run | Test classes | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|---|
| Before | 21 | 179 | 0 | 0 | 0 |
| After | 21 | 179 | 0 | 0 | 0 |

`LaundryLinkApplicationTests` and `ProcessingIntegrationTest` start the full Spring context, so they also confirm that every bean, entity and repository is still discovered from its new package. Both need SQL Server on `localhost:1433`.
