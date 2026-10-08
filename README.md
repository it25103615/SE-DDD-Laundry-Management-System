# LaundryLink

Spring Boot / Java 26 laundry management application.

## Project layout

```text
src/main/java/_6/Y2/S1/MTR/_6/LaundryLink/
  LaundryLinkApplication.java
  account/  address/  user/  auth/                            User and account management
  orders/  orderlines/  items/  services/  servicepricing/    Order and reservation management
  processing/                                                 Laundry processing
  rider/                                                      Pickup and delivery
  payment/  billing/  promotion/                              Payments, billing and promotions
  support/                                                    Reporting, administration and support
  status/  log/  notification/                                Shared statuses, status logs and inbox
  common/                                                     Shared API exception types
  config/                                                     Spring and security configuration
src/main/resources/
  application.properties
  static/        HTML, CSS and JavaScript
src/test/java/    Tests mirroring the Java package layout
database/       Additive database migrations
scripts/        Local setup and startup scripts
Documentation/  Project and evaluation guides
```

The Java code is organised **package-by-feature**: each feature package holds its own controller, service, repository and entity classes, with request and response types either beside them or in a `dto` sub-package. There are no top-level `controller`, `service`, `repository`, `entity` or `dto` packages.

See [the team structure guide](Documentation/Project-Structure.md) for ownership and where to add files. If you are looking for a file that used to live under one of the old layer packages, use the lookup table in [the package restructure record](Documentation/Package-Restructure.md).

## Run and evaluate

Use Java 26 and Maven. Configure the SQL Server connection using an ignored `src/main/resources/application-local.properties` file. The application requires a running database and the support migration.

```powershell
# Uses Maven from PATH or the IntelliJ installation:
powershell -File scripts/Start-Support.ps1

# Run module tests without a database:
mvn clean test "-Dtest=Support*Test"
```

The checked-in Maven wrapper is currently missing its `.mvn` configuration, so use an installed Maven distribution. See [the support evaluation guide](Documentation/Support-Progress-Evaluation.md) for database setup, demo accounts, CRUD walkthrough and remaining integration work.


### Login and local Windows database authentication

This machine uses Windows authentication through the ignored `application-local.properties`, which is optionally imported by the shared configuration. Keep IntelliJ's working directory set to the project root; the matching Microsoft JDBC authentication DLL is located there and ignored by Git. Other machines need their own local SQL connection configuration. Full startup testing against local SQL Express now passes (34 tests total); see `database-startup-tests.log`.

The web login now creates a normal Spring Security session using records from the `users` table. Support APIs require that session and enforce the database role. The supplied sample records still contain plain-text passwords; the account module must migrate them to BCrypt before deployment.
