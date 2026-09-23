USE laundryLinkDB;
GO

/* QUERY 1 - SIMPLE SELECT
   Lists orders that have not reached Completed status. Useful for the daily work queue. */
SELECT orderID, userID, statusID
FROM dbo.orders
WHERE statusID <> 15
ORDER BY orderID;

/* QUERY 2 - JOIN
   Combines orders, customers and status descriptions into readable order information. */
SELECT o.orderID,
       CONCAT(u.firstName, ' ', u.lastName) AS customerName,
       u.email,
       s.statusLabel
FROM dbo.orders o
JOIN dbo.users u ON u.userID=o.userID
JOIN dbo.status s ON s.statusID=o.statusID
ORDER BY o.orderID;

/* QUERY 3 - AGGREGATION
   Summarizes recorded payment values. DECIMAL gives an exact financial total. */
SELECT COUNT(*) AS paymentCount,
       SUM(amount) AS totalRecordedPayments,
       AVG(amount) AS averagePayment,
       MIN(amount) AS smallestPayment,
       MAX(amount) AS largestPayment
FROM dbo.payments;

/* QUERY 4 - GROUP BY AND HAVING
   Shows services appearing on at least two order lines, highlighting popular services. */
SELECT s.serviceID, s.serviceName,
       COUNT(*) AS orderLineCount,
       SUM(ol.quantity) AS totalItems
FROM dbo.services s
JOIN dbo.orderLines ol ON ol.serviceID=s.serviceID
GROUP BY s.serviceID, s.serviceName
HAVING COUNT(*) >= 2
ORDER BY orderLineCount DESC;

/* QUERY 5 - SUBQUERY
   Finds customers whose total recorded payments exceed the average customer total. */
SELECT u.userID,
       CONCAT(u.firstName, ' ', u.lastName) AS customerName,
       SUM(p.amount) AS customerPaymentTotal
FROM dbo.users u
JOIN dbo.orders o ON o.userID=u.userID
JOIN dbo.payments p ON p.orderID=o.orderID
WHERE u.type='CUSTOMER'
GROUP BY u.userID, u.firstName, u.lastName
HAVING SUM(p.amount) > (
    SELECT AVG(customerTotal)
    FROM (
        SELECT SUM(p2.amount) AS customerTotal
        FROM dbo.orders o2
        JOIN dbo.payments p2 ON p2.orderID=o2.orderID
        GROUP BY o2.userID
    ) totals
)
ORDER BY customerPaymentTotal DESC;

/* OPTIONAL QUERY 6 - composite pricing lookup for the service catalogue. */
SELECT s.serviceName, i.itemName, sp.price
FROM dbo.servicePricing sp
JOIN dbo.services s ON s.serviceID=sp.serviceID
JOIN dbo.items i ON i.itemID=sp.itemID
ORDER BY s.serviceName, i.itemName;
GO
