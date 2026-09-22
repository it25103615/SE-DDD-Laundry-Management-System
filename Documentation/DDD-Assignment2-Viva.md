# LaundryLink Database — Concise Viva Guide

## Core design

- `users` stores common identity/login data. `type` implements the User ISA hierarchy in one relation.
- `addresses` stores multiple delivery locations per user.
- `orders` identifies a customer's laundry request and current status.
- `orderLines` is required because an order can contain several item/service combinations and quantities.
- `items` describes garments or laundry item categories.
- `services` describes work such as washing, ironing and dry cleaning.
- `servicePricing` resolves the many-to-many Item–Service relationship and stores the price of each valid combination.
- `payments` records amounts against orders.
- `status` is the controlled workflow catalogue.
- `logs` records status transitions over time.
- `delivery` stores pickup/delivery riders and timestamps.
- `feedback` stores complaints, questions and ratings as support cases.
- `chat` stores messages belonging to a support case.
- `support_activity` is an audit trail of actions on cases/settings.
- `system_settings` stores manager-maintained operational settings.

## Keys and relationships to remember

- Most tables use an integer identity PK.
- `servicePricing` has composite PK `(itemID, serviceID)` because that pair uniquely identifies one price.
- `orderLines` references both `orders` and the composite pricing key.
- `orders.userID` identifies the customer; `orders.statusID` identifies the current state.
- Rider columns reference `users`, and the trigger enforces the Rider subtype.
- `users.email` and `system_settings.settingKey` are unique candidate keys.

## Why one users table?

The EER has User subtypes, but they currently have no independent attributes. Single-relation ISA mapping stores common attributes once and uses `type` as a discriminator. It avoids extra joins and empty subtype tables. A CHECK constraint restricts valid roles, while aliases used by the active application are retained for compatibility.

## Why DECIMAL instead of FLOAT?

`FLOAT` is approximate binary arithmetic and can introduce rounding artifacts. `DECIMAL(10,2)` stores exact values with two decimal places, which is appropriate for prices and payments.

## Five query concepts

1. `SELECT ... WHERE`: filters pending orders.
2. `JOIN`: combines orders, users and statuses.
3. `COUNT/SUM/AVG/MIN/MAX`: summarizes payments.
4. `GROUP BY ... HAVING`: groups service usage and filters groups after aggregation.
5. Subquery: calculates average customer totals, then returns customers above that average.

`WHERE` filters rows before grouping; `HAVING` filters groups after aggregation.

## Procedure flow

`sp_UpdateOrderStatus`:

1. Starts a transaction.
2. Validates the order and new status.
3. Locks and reads the current status.
4. Rejects an unchanged status.
5. Updates the order.
6. Inserts the before/after transition in `logs`.
7. Commits; `CATCH` rolls back and rethrows on error.

Example syntax:

```sql
EXEC dbo.sp_UpdateOrderStatus @OrderID=1, @NewStatusID=3;
```

## Trigger flow

`trg_delivery_rider_check` runs automatically after an insert or update on `delivery`. It checks every row in the `inserted` pseudo-table. Non-null rider IDs must belong to a user whose type is `RIDER`; otherwise `THROW` cancels the statement.

A procedure is called explicitly and can accept parameters. A trigger fires automatically because a table event occurred.

## Constraints worth explaining

- PK: uniquely identifies a row and disallows null.
- FK: prevents references to nonexistent parents.
- UNIQUE: protects email and setting natural keys.
- CHECK: validates roles, phone format, rating range, positive quantity and non-negative money.
- DEFAULT: supplies flags, timestamps, status text and version values when omitted.
- NOT NULL: makes required attributes mandatory.

## Likely SQL to type

```sql
SELECT * FROM dbo.orders WHERE statusID<>15;

SELECT o.orderID,u.email,s.statusLabel
FROM dbo.orders o
JOIN dbo.users u ON u.userID=o.userID
JOIN dbo.status s ON s.statusID=o.statusID;

SELECT serviceID,COUNT(*) AS uses
FROM dbo.orderLines
GROUP BY serviceID
HAVING COUNT(*)>=2;

SELECT * FROM dbo.users
WHERE userID IN (SELECT userID FROM dbo.orders);
```

Be ready to state that support tables and role aliases are Part 02/current-application refinements, not concepts that were silently present in the original EER.
