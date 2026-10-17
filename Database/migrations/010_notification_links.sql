/*
    Migration 010 - Payment notification links
    ------------------------------------------------------------------
    The "Payment accepted" notification linked to
        /html/customer/receipt.html?orderId=<order>
    but the receipt page needs both ?orderID=<order> and ?paymentID=<payment>
    (payment-flow.js), so clicking the notification showed "Receipt unavailable".

    After this migration:
      * dbo.trg_payment_notifications links to
        /html/customer/receipt.html?orderID=<order>&paymentID=<payment>;
      * existing payment notifications are rewritten to the same form.

    Order notification links (order_details.html?orderId=...) are not changed:
    the order pages now accept ?orderId= and look up the signed-in customer.

    Safe to run more than once (CREATE OR ALTER; the UPDATE only touches rows
    that still have the old link).

    Run order for an existing database:
    003 -> 004 -> 005 -> 006 -> 007 -> 008 -> 009 -> 010.
    A fresh install (initialize_database.sql) already has the new trigger.
*/
USE laundryLinkDB;
GO

CREATE OR ALTER TRIGGER dbo.trg_payment_notifications
ON dbo.payments AFTER INSERT, UPDATE AS
BEGIN
    SET NOCOUNT ON;
    INSERT dbo.notifications(recipientID,category,title,message,link,relatedType,relatedID)
    SELECT o.userID,'PAYMENT','Payment accepted',CONCAT('Payment for order #',i.orderID,' was ',LOWER(i.paymentStatus),'.'),
           CONCAT('/html/customer/receipt.html?orderID=',i.orderID,'&paymentID=',i.paymentID),'PAYMENT',i.paymentID
    FROM inserted i JOIN orders o ON o.orderID=i.orderID LEFT JOIN deleted d ON d.paymentID=i.paymentID
    WHERE i.paymentStatus IN('PAID','VERIFIED') AND (d.paymentID IS NULL OR COALESCE(d.paymentStatus,'')<>i.paymentStatus);
END;
GO

/* Fix notifications that already exist. relatedID holds the paymentID, which gives the order. */
UPDATE n
SET n.link = CONCAT('/html/customer/receipt.html?orderID=', p.orderID, '&paymentID=', p.paymentID)
FROM dbo.notifications n
JOIN dbo.payments p ON p.paymentID = n.relatedID
WHERE n.category = 'PAYMENT'
  AND n.link LIKE '%receipt.html?orderId=%';
GO
