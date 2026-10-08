# Assignment Part 02 — SSMS Screenshot Checklist

Use SQL Server Management Studio and connect to the existing `laundryLinkDB`. Back up important development data first. Do **not** run `initialize_database.sql` on the existing database.

Automated execution from the Codex sandbox was blocked by Windows integrated authentication (`Failed to generate SSPI context`). The commands are therefore prepared for manual execution under the normal Windows account. The consolidated, section-by-section SQL is in `database/ddd_assignment2_screenshot_commands.sql`.

## A. Safe setup

1. Open `database/migrations/003_ddd_assignment2_refinement.sql`.
2. Execute it once. Screenshot the successful Messages pane. If a preflight error appears, stop and correct/review the named data issue rather than bypassing it.
3. Execute `database/migrations/004_ddd_assignment2_module_routines.sql` to install the six Part E routines.
4. Open and execute `database/ddd_assignment2_sample_data.sql`.
5. Screenshot the final table-count result. Confirm every count is at least 5.

## B. Schema evidence

5. In Object Explorer, expand `laundryLinkDB → Tables` and screenshot all 15 tables.
6. Screenshot `fn_GetAccountProfile`, `fn_GetCustomerOrders`, and `fn_GetOpenSupportCases` under Programmability → Functions, and the three module procedures under Programmability → Stored Procedures.
7. Expand `Tables → dbo.orders → Triggers` and locate `dbo.trg_order_notifications` for Part F evidence.
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
SELECT TOP (5) * FROM dbo.notifications;
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
14. In `database/ddd_assignment2_procedure_trigger_demo.sql`, run each **SCREENSHOT 1/6** through **SCREENSHOT 6/6** section separately, selecting through its `GO` line.
15. Capture each routine's labeled definition from `database/migrations/004_ddd_assignment2_module_routines.sql` as code evidence, then capture its matching demo call and Results grid as execution evidence.
16. The three procedure demo sections use their own transactions and roll back their changes.

## F. Trigger

17. Capture the labeled `dbo.trg_order_notifications` definition from `database/migrations/003_ddd_assignment2_refinement.sql` as code evidence.
18. In `database/ddd_assignment2_procedure_trigger_demo.sql`, run the **PART F — GENERAL ORDER STATUS NOTIFICATION TRIGGER** section.
19. Capture the `UPDATE dbo.orders` statement and the resulting **Order status updated** notification in one screenshot. The transaction rolls back the order and notification changes.

## G. Report assembly

20. Label screenshots with figure numbers and short captions.
21. Put DDL/schema evidence before DML/query evidence.
22. Explain that `trg_order_notifications` runs after order inserts or updates and sends a customer notification when the order status changes.
23. Do not show connection strings, passwords, or local credential files in screenshots.
