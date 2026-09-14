# LaundryLink

Spring Boot / Java 26 laundry management application.

## Project layout

```text
src/main/java/_6/Y2/S1/MTR/_6/LaundryLink/
  LaundryLinkApplication.java
  controller/    HTTP endpoints, grouped by module
  service/       Business rules, grouped by module
  repository/    Database access, grouped by module
  entity/        Database models, grouped by module
  dto/           Request and response objects, grouped by module
  config/        Spring and security configuration
  security/      Identity and access checks
  exception/     API exception handlers
src/main/resources/
  application.properties
  static/        HTML, CSS and JavaScript
src/test/java/    Tests mirroring the Java package layout
database/       Additive database migrations
scripts/        Local setup and startup scripts
Documentation/  Project and evaluation guides
```

Each main layer contains module packages for `account`, `order`, `processing`, `rider`, `payment`, and `support`. Common order statuses and logs use `shared` packages. Package documentation reserves locations for work that has not yet been merged; it does not mean those modules are implemented.

See [the team structure guide](Documentation/Project-Structure.md) for ownership and where to add files.

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
