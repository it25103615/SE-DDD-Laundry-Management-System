# Assignment Part 02 — SSMS Screenshot Checklist

Use SQL Server Management Studio and connect to the existing `laundryLinkDB`. Back up important development data first. Do **not** run `initialize_database.sql` on the existing database.

Automated execution from the Codex sandbox was blocked by Windows integrated authentication (`Failed to generate SSPI context`). The commands are therefore prepared for manual execution under the normal Windows account. The consolidated, section-by-section SQL is in `database/ddd_assignment2_screenshot_commands.sql`.

## A. Safe setup

1. Open `database/migrations/003_ddd_assignment2_refinement.sql`.
2. Execute it once. Screenshot the successful Messages pane. If a preflight error appears, stop and correct/review the named data issue rather than bypassing it.
3. Open and execute `database/ddd_assignment2_sample_data.sql`.
4. Screenshot the final table-count result. Confirm every count is at least 5.

## B. Schema evidence

5. In Object Explorer, expand `laundryLinkDB → Tables` and screenshot all 15 tables.
6. Expand `Programmability → Stored Procedures` and screenshot `dbo.sp_UpdateOrderStatus`.
7. Expand `Tables → dbo.delivery → Triggers` and screenshot `dbo.trg_delivery_rider_check`.
8. In a query window run the following and screenshot the result:

```sql
EXEC sp_help 'dbo.servicePricing';
EXEC sp_help 'dbo.orderLines';
EXEC sp_help 'dbo.users';
```

Capture the composite key, foreign keys, decimal types and check constraints.

## C. Five records from every table

9. Run and screenshot each result grid. Use `TOP (5)` so screenshots remain readable.

```sql
SELECT TOP (5) * FROM dbo.status;
SELECT TOP (5) * FROM dbo.items;
SELECT TOP (5) * FROM dbo.services;
SELECT TOP (5) * FROM dbo.servicePricing;
SELECT TOP (5) * FROM dbo.users;
SELECT TOP (5) * FROM dbo.addresses;
SELECT TOP (5) * FROM dbo.orders;
SELECT TOP (5) * FROM dbo.orderLines;
SELECT TOP (5) * FROM dbo.payments;
SELECT TOP (5) * FROM dbo.logs;
SELECT TOP (5) * FROM dbo.feedback;
SELECT TOP (5) * FROM dbo.chat;
SELECT TOP (5) * FROM dbo.delivery;
SELECT TOP (5) * FROM dbo.support_activity;
SELECT TOP (5) * FROM dbo.system_settings;
```

## D. Required SQL queries

10. Open `database/ddd_assignment2_queries.sql`.
11. Execute each labelled query separately and take one screenshot containing both its SQL and output:

   1. Simple SELECT
   2. JOIN
   3. Aggregation
   4. GROUP BY with HAVING
   5. Subquery

12. Optionally screenshot Query 6 as extra evidence of the composite pricing relationship.

## E. Stored procedure

13. Open `database/ddd_assignment2_procedure_trigger_demo.sql`.
14. Run only the **PROCEDURE DEMONSTRATION** batch.
15. Screenshot the `EXEC dbo.sp_UpdateOrderStatus` statement and both result grids: the temporarily changed order and newest log.
16. The outer demonstration transaction rolls the order and log changes back after the result grids are produced.

## F. Trigger

17. Run **CASE 1** separately. Screenshot the valid rider assignment result. The transaction rolls back afterward.
18. Run **CASE 2** separately. Screenshot error number `51020` and the explanatory error message. The transaction rolls back automatically.
19. Run this final confirmation and screenshot that delivery data remains valid:

```sql
SELECT d.deliverID, d.pickup_riderID, p.type AS pickupRole,
       d.delivery_riderID, r.type AS deliveryRole
FROM dbo.delivery d
LEFT JOIN dbo.users p ON p.userID=d.pickup_riderID
LEFT JOIN dbo.users r ON r.userID=d.delivery_riderID;
```

## G. Report assembly

20. Label screenshots with figure numbers and short captions.
21. Put DDL/schema evidence before DML/query evidence.
22. Include both successful and rejected trigger cases.
23. Do not show connection strings, passwords, or local credential files in screenshots.
