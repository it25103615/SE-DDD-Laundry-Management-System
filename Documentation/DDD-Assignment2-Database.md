# LaundryLink Database — IT2140 Assignment Part 02

## 1. Final relational schema

`PK` means primary key, `FK` foreign key, `UK` candidate/unique key, `NN` NOT NULL.

```text
STATUS(statusID PK, statusLabel)

ITEMS(itemID PK, itemName)

SERVICES(
    serviceID PK, serviceName NN,
    description NN, turnaroundHours NN CHECK 1..720,
    active NN DEFAULT 1, version NN DEFAULT 0
)

SERVICE_PRICING(
    itemID PK/FK → ITEMS(itemID),
    serviceID PK/FK → SERVICES(serviceID),
    price NN CHECK price >= 0
)

USERS(
    userID PK,
    firstName, middleName, lastName,
    email UK,
    password,
    phoneNumber CHECK ten digits,
    type VARCHAR(30) NN CHECK valid application role,
    active NN DEFAULT 1,
    createdAt NN, updatedAt NN,
    version NN DEFAULT 0
)

ADDRESSES(
    addressID PK,
    nickname, street, city, state, DeliveryInstructions,
    isDefault NN DEFAULT 0,
    userID FK → USERS(userID)
)

ORDERS(
    orderID PK,
    statusID FK → STATUS(statusID),
    userID FK → USERS(userID)
)

ORDER_LINES(
    orderLineID PK,
    orderID NN FK → ORDERS(orderID),
    itemID NN,
    serviceID NN,
    quantity NN CHECK quantity > 0,
    linePrice NN CHECK linePrice >= 0,
    (itemID, serviceID) FK → SERVICE_PRICING(itemID, serviceID)
)

PAYMENTS(
    paymentID PK,
    amount NN CHECK amount >= 0,
    orderID NN FK → ORDERS(orderID),
    paymentStatus NN DEFAULT 'PAID',
    processedAt
)

LOGS(
    logID PK,
    status_before FK → STATUS(statusID),
    status_after FK → STATUS(statusID),
    logDate, logTime,
    orderID FK → ORDERS(orderID)
)

FEEDBACK(
    feedbackID PK,
    feedback,
    userID NN FK → USERS(userID),
    orderID FK → ORDERS(orderID),
    caseType NN DEFAULT 'Feedback',
    subject NN,
    rating CHECK 1 <= rating <= 5,
    caseStatus NN DEFAULT 'New',
    priority NN DEFAULT 'Normal',
    assigneeID FK → USERS(userID),
    createdAt DEFAULT current date/time,
    updatedAt DEFAULT current date/time,
    version NN DEFAULT 0,
    deleted NN DEFAULT 0
)

CHAT(
    chatID PK,
    sentAt DEFAULT current date/time,
    message,
    userID NN FK → USERS(userID),
    feedbackID FK → FEEDBACK(feedbackID)
)

DELIVERY(
    deliverID PK,
    orderID FK → ORDERS(orderID),
    userID FK → USERS(userID),
    pickup_riderID FK → USERS(userID),
    delivery_riderID FK → USERS(userID),
    riderNotes, pickup_scheduled, pickup_actual, delivery_time
)

SUPPORT_ACTIVITY(
    activityID PK,
    feedbackID FK → FEEDBACK(feedbackID),
    actorID NN FK → USERS(userID),
    action NN, details NN,
    createdAt NN DEFAULT current date/time
)

NOTIFICATIONS(
    notificationID PK,
    recipientID NN FK → USERS(userID),
    category NN, title NN, message NN, link,
    relatedType, relatedID,
    isRead NN DEFAULT 0,
    createdAt NN DEFAULT current date/time,
    readAt
)

```

All generated numeric keys use SQL Server `IDENTITY`, except `statusID`, whose values are a controlled workflow catalogue. `servicePricing` uses the natural composite key `(itemID, serviceID)` because one price belongs to one exact item/service combination.

## 2. Relationships

- One user may own many addresses, orders, feedback cases, chat messages and delivery records.
- One status may describe many current orders and many before/after log entries.
- One order may have many order lines, payments, logs, feedback cases and delivery records under the physical constraints.
- Items and services form a many-to-many relationship resolved by `servicePricing`.
- Orders use priced item/service combinations through `orderLines`.
- One feedback case may contain many chat messages and support activity records.
- One user may receive many notifications, each with its own read state and optional destination link.
- Pickup and delivery riders are user records restricted to the `RIDER` subtype by a trigger.

The schema does not claim strict one-to-one cardinality for payment or delivery because `orderID` is not unique in those tables. It also does not enforce one default address per user. Adding those restrictions without agreed business rules could reject legitimate existing records.

## 3. ISA mapping

The Part 01 conceptual EER represented `Customer`, `Rider`, `Staff`, `Manager`, and `Owner` as subtypes of `User`. Part 02 maps this hierarchy using **single-relation ISA mapping**:

```text
USERS(..., type)
```

This is appropriate because the current subtypes do not introduce significant subtype-specific attributes. Common identity and login data is stored once, avoiding empty subtype tables and extra joins. The discriminator is constrained to every role recognized by the application: `CUSTOMER`, `RIDER`, `STAFF`, `MANAGER`, `OWNER`, `ADMIN`, `CSM`, and `CUSTOMER_SERVICE_MANAGER`. `ADMIN`, `CSM`, and `CUSTOMER_SERVICE_MANAGER` are retained as compatibility aliases because active security code recognizes them.

## 4. Refinements from Part 01/current implementation

These are explicit Part 02 refinements; they were not all present in the original implementation:

1. Added the missing `servicePricing → services` and `servicePricing → items` foreign keys.
2. Changed currency fields from approximate `FLOAT` to exact `DECIMAL(10,2)`.
3. Added positive-quantity and non-negative-money checks.
4. Added a role discriminator check without changing the single-table ISA strategy.
5. Added the support-case columns and support audit table to the fresh-install DDL, matching the backend already in this project.
6. Added account active/audit/version fields and service description/turnaround/active/version fields for operational administration.
7. Replaced the rider trigger body with a multi-row-safe, null-safe implementation using `THROW`.
8. Added `sp_UpdateOrderStatus`, which updates an order and inserts its status log atomically.
9. Added persistent user notifications and payment processing fields. Database triggers publish order, payment and rider-assignment notifications even when the owning module writes directly to its table.

Migration `003_ddd_assignment2_refinement.sql` checks for unknown roles, orphaned pricing rows, invalid quantities, null/negative prices, negative payments, and money values that cannot be represented by `DECIMAL(10,2)`. Any problem rolls back the transaction. It also removes the unused `system_settings` table.

## 5. DDL and migration strategy

- **Fresh installation:** run `initialize_database.sql` only. It directly creates all 15 final tables, constraints, the procedure, triggers, and essential reference catalogues. Migrations 001, 002 and 003 are not required afterward.
- **Existing development database:** do not rerun `initialize_database.sql`. For the current database, where migrations 001 and 002 are already applied, run only `database/migrations/003_ddd_assignment2_refinement.sql` after taking a verified backup.
- `001_support_admin.sql` and `002_account_password_hash.sql` are retained as historical schema-evolution records. Their final support-table, feedback-column, and password-column results are already incorporated directly into the master initializer.
- `003_ddd_assignment2_refinement.sql` remains the upgrade path for an existing database; it removes the unused settings table and creates or updates the procedure, notification storage and triggers.

The project does not use Flyway or Liquibase. Migration execution is manual or through `scripts/Initialize-SupportDatabase.ps1`. Therefore the operator must retain evidence of which scripts were executed.

## 6. Sample data

`database/ddd_assignment2_sample_data.sql` inserts realistic customers, riders, staff, manager and owner records; addresses; orders and lines; payments; logs; cases; messages; deliveries; and activities. Notifications are produced by the table triggers as those records are created. The script uses natural-key checks and never deletes existing records, and its final count query includes all 15 tables.

The assignment identities are usable demo accounts with the shared demo password `LaundryLink1!`, stored only as a BCrypt hash. Six additional role-specific demo identities use the easy-to-demonstrate `name@role.com` / `Name1234` convention. The sample script upgrades existing seeded rows when their password is still `NULL`. In normal use, customers register themselves, while an authenticated Owner or Manager creates Staff, Rider, CSM, or Manager accounts through the management-login screen. That screen never permits creation of another Owner; the seeded Owner is the bootstrap account.

## 7. Required queries

The executable queries are in `database/ddd_assignment2_queries.sql`.

1. **Simple SELECT:** pending orders filtered by status.
2. **JOIN:** order, customer and readable status data.
3. **Aggregation:** count, sum, average, minimum and maximum payment values.
4. **GROUP BY/HAVING:** services appearing on at least two order lines.
5. **Subquery:** customers whose payment total exceeds the average customer total.
6. Optional catalogue join: item/service prices.

## 8. Stored procedure

`dbo.sp_UpdateOrderStatus(@OrderID, @NewStatusID)` validates both keys, locks and reads the current status, rejects a no-op, updates the order, and inserts a corresponding log row. `TRY/CATCH`, `XACT_ABORT`, and one transaction guarantee that the update and audit entry either both succeed or both roll back.

## 9. Trigger

`dbo.trg_delivery_rider_check` runs after inserts and updates to `delivery`. It examines the full `inserted` pseudo-table, so multi-row statements are supported. Null rider assignments remain valid, while any non-null pickup or delivery rider must reference a `users` row with type `RIDER`.

`trg_order_notifications`, `trg_payment_notifications`, and `trg_delivery_assignment_notifications` create inbox records for customer order changes, accepted payments, and rider assignments. Because SQL Server does not allow a direct `OUTPUT` result set from a table with enabled triggers, new inserts that need the generated identity should use `SCOPE_IDENTITY()` or `OUTPUT ... INTO`.

## 10. Assumptions and remaining differences

- The current application, not the conceptual EER, requires `ADMIN`, `CSM`, and `CUSTOMER_SERVICE_MANAGER` aliases.
- Support cases and audit history are retained because they are active SE-project tables even if absent from the first EER.
- There are no separate subtype tables; this is an intentional ISA mapping, not a missing relation.
- `delivery.userID` duplicates the customer available through `orders.userID`; it is retained for compatibility.
- `feedback` represents complaints, questions and ratings in one relation because the current support module uses this structure.
- The physical database is broader than the Java ORM: only Status and Log currently have JPA entities; most access uses JDBC/native SQL.
- Order and delivery date/detail attributes remain limited because adding them would redesign application workflows beyond this assignment refinement. Payments now record processing status and time so accepted-payment notifications have an explicit trigger condition.

## 11. Execution verification status — 22 September 2026

### Verified by execution

- A read-only connection to `Agksheya-PC\SQLEXPRESS` / `laundryLinkDB` succeeded under the normal Windows identity.
- The current schema expects 15 tables: the former unused settings table was removed and the active notifications table was added.
- A checksum full backup was created and passed `RESTORE VERIFYONLY`: `laundryLinkDB_before_DDD_Assignment2_20260922_123219.bak`.
- Migration 003 completed successfully in all four batches.
- Physical metadata verification confirmed `DECIMAL(10,2)` money columns, `users.type VARCHAR(30)`, enabled/trusted pricing foreign keys, enabled/trusted validation checks, and enabled procedure/trigger objects.
- The Spring Boot context test connected to SQL Server, completed Hibernate schema validation, and passed: 1 test, 0 failures, 0 errors.
- The database-independent Java test selection also passed: 43 tests, 0 failures, 0 errors.

### Verified by static inspection

- Migration 003 contains transactional preflight checks, both missing `servicePricing` foreign keys, exact-money conversions, quantity/money checks, role validation, the stored procedure, and the revised trigger.
- The sample script contains non-deleting, transaction-protected data and a final row-count query covering all 15 relations.
- The five required query forms and the rollback-safe procedure/trigger demonstrations are present.
- The fresh-install initializer represents the refined schema, but it was not executed because it is destructive.

### Assignment evidence executed — 22 September 2026

- The assignment sample-data script completed successfully. Every one of the 15 tables now has at least five rows; verified counts range from 5 to 26.
- All five required query forms completed successfully: simple `SELECT`, multi-table `JOIN`, aggregation, `GROUP BY/HAVING`, and subquery.
- The stored-procedure demonstration changed order 1 from status 15 to status 1 and created log 6 inside an outer transaction. The outer rollback restored status 15 and the original log count.
- The trigger accepted a valid `RIDER` assignment and rejected a non-rider assignment with SQL Server error 51020. Both demonstrations preserved the original delivery row.
- The verified outputs and explanations are included in `Documentation/2026-Y2-S1-MTR-26_Assignment01_Part02.pdf`.
