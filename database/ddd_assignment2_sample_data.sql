/*
    LaundryLink realistic assignment data
    Prerequisites: initialize_database.sql plus migrations 001, 002 and 003.
    This script never deletes data. Natural-key checks make reruns safe for the
    supplied sample records, and all writes are atomic.
*/
USE laundryLinkDB;
GO
SET XACT_ABORT ON;
SET NOCOUNT ON;

BEGIN TRY
    BEGIN TRANSACTION;

    /* Reference data: five or more rows in each catalogue. */
    MERGE dbo.status AS target
    USING (VALUES
        (1,'Unconfirmed'),(2,'Payment Verified'),(3,'Awaiting Pickup'),
        (4,'En Route To Pickup'),(5,'Picked Up'),(6,'En Route To Shop'),
        (7,'In Shop'),(8,'Verifying Items'),(9,'Washing'),(10,'Drying'),
        (11,'Ironing'),(12,'Awaiting Delivery'),(13,'En Route To Delivery'),
        (14,'Delivered'),(15,'Completed'),(16,'Payment Failed'),
        (17,'Pickup Failed'),(18,'Delivery Failed'),(19,'Dry Clean')
    ) AS source(statusID,statusLabel) ON target.statusID=source.statusID
    WHEN NOT MATCHED THEN INSERT(statusID,statusLabel) VALUES(source.statusID,source.statusLabel);

    MERGE dbo.items AS target
    USING (VALUES ('Everyday Clothing'),('Shirt / Blouse'),('Bed Sheet'),
                  ('Trousers / Skirt'),('Two-Piece Suit')) AS source(itemName)
       ON target.itemName=source.itemName
    WHEN NOT MATCHED THEN INSERT(itemName) VALUES(source.itemName);

    MERGE dbo.services AS target
    USING (VALUES ('Wash and Fold'),('Ironing'),('Dry Cleaning'),
                  ('Shoe Cleaning'),('Express Wash')) AS source(serviceName)
       ON target.serviceName=source.serviceName
    WHEN NOT MATCHED THEN INSERT(serviceName) VALUES(source.serviceName);

    DECLARE @Wash INT=(SELECT TOP(1) serviceID FROM dbo.services WHERE serviceName='Wash and Fold');
    DECLARE @Iron INT=(SELECT TOP(1) serviceID FROM dbo.services WHERE serviceName='Ironing');
    DECLARE @Dry INT=(SELECT TOP(1) serviceID FROM dbo.services WHERE serviceName='Dry Cleaning');
    DECLARE @Express INT=(SELECT TOP(1) serviceID FROM dbo.services WHERE serviceName='Express Wash');
    DECLARE @Clothing INT=(SELECT TOP(1) itemID FROM dbo.items WHERE itemName='Everyday Clothing');
    DECLARE @Shirt INT=(SELECT TOP(1) itemID FROM dbo.items WHERE itemName='Shirt / Blouse');
    DECLARE @Sheet INT=(SELECT TOP(1) itemID FROM dbo.items WHERE itemName='Bed Sheet');
    DECLARE @Trousers INT=(SELECT TOP(1) itemID FROM dbo.items WHERE itemName='Trousers / Skirt');
    DECLARE @Suit INT=(SELECT TOP(1) itemID FROM dbo.items WHERE itemName='Two-Piece Suit');

    MERGE dbo.servicePricing AS target
    USING (VALUES
        (@Wash,@Clothing,CAST(180.00 AS DECIMAL(10,2))),
        (@Wash,@Shirt,CAST(220.00 AS DECIMAL(10,2))),
        (@Wash,@Sheet,CAST(450.00 AS DECIMAL(10,2))),
        (@Iron,@Shirt,CAST(150.00 AS DECIMAL(10,2))),
        (@Iron,@Trousers,CAST(180.00 AS DECIMAL(10,2))),
        (@Dry,@Suit,CAST(1500.00 AS DECIMAL(10,2))),
        (@Express,@Clothing,CAST(300.00 AS DECIMAL(10,2)))
    ) AS source(serviceID,itemID,price)
       ON target.serviceID=source.serviceID AND target.itemID=source.itemID
    WHEN NOT MATCHED THEN INSERT(serviceID,itemID,price) VALUES(source.serviceID,source.itemID,source.price);

    /* Single-table ISA samples: subtypes are represented by users.type.
       Assignment accounts use LaundryLink1!. The six simple stakeholder
       demo accounts below use Name1234 as documented in the credentials list. */
    MERGE dbo.users AS target
    USING (VALUES
        ('Ayesha',NULL,'Fernando','ayesha.fernando@assignment.laundrylink.lk','$2a$10$JubJfTSRWCO3oTOBnrayR.hcBzzgjHMFAcXUpGvDT2/vAwCiB5M7O','0712345601','CUSTOMER',0),
        ('Kavindu',NULL,'Perera','kavindu.perera@assignment.laundrylink.lk','$2a$10$JubJfTSRWCO3oTOBnrayR.hcBzzgjHMFAcXUpGvDT2/vAwCiB5M7O','0712345602','CUSTOMER',0),
        ('Nimali',NULL,'Silva','nimali.silva@assignment.laundrylink.lk','$2a$10$JubJfTSRWCO3oTOBnrayR.hcBzzgjHMFAcXUpGvDT2/vAwCiB5M7O','0712345603','CUSTOMER',0),
        ('Ravindu',NULL,'Jayasinghe','ravindu.rider@assignment.laundrylink.lk','$2a$10$JubJfTSRWCO3oTOBnrayR.hcBzzgjHMFAcXUpGvDT2/vAwCiB5M7O','0712345604','RIDER',0),
        ('Fathima',NULL,'Rizwan','fathima.rider@assignment.laundrylink.lk','$2a$10$JubJfTSRWCO3oTOBnrayR.hcBzzgjHMFAcXUpGvDT2/vAwCiB5M7O','0712345605','RIDER',0),
        ('Malith',NULL,'Dias','malith.staff@assignment.laundrylink.lk','$2a$10$JubJfTSRWCO3oTOBnrayR.hcBzzgjHMFAcXUpGvDT2/vAwCiB5M7O','0712345606','STAFF',0),
        ('Shalini',NULL,'De Alwis','shalini.manager@assignment.laundrylink.lk','$2a$10$JubJfTSRWCO3oTOBnrayR.hcBzzgjHMFAcXUpGvDT2/vAwCiB5M7O','0712345607','MANAGER',0),
        ('Dinesh',NULL,'Gunawardena','dinesh.owner@assignment.laundrylink.lk','$2a$10$JubJfTSRWCO3oTOBnrayR.hcBzzgjHMFAcXUpGvDT2/vAwCiB5M7O','0712345608','OWNER',0),
        ('Anna',NULL,'Customer','anna@customer.com','$2a$10$1KDqj6fKQfS7RQVszxPaburFF42/RUfNxwH5iUJTLg7x7GWxGh7ki','0712345611','CUSTOMER',1),
        ('Ravi',NULL,'Rider','ravi@rider.com','$2a$10$Wiex9/zmcn/fCsnL94iswu8/S98o6HzV89TafM5kze7BwVtXHRgVW','0712345612','RIDER',1),
        ('Sam',NULL,'Staff','sam@staff.com','$2a$10$guYOZz33UgK9JsNLLParqO0jXrTkaiCyb3BHDKz/yDbd5pLh/qGou','0712345613','STAFF',1),
        ('Cathy',NULL,'Support','cathy@csm.com','$2a$10$L6PoIgZzuc1BPl30xZ0OLOAq0LSUaupcoE2hxR0QuXC.BSF9t33Qy','0712345614','CSM',1),
        ('Maya',NULL,'Manager','maya@manager.com','$2a$10$WZo0TPpt3L.LuFBemCJ99.UySvNPcKhTLVD5gFOPqjtnHBtSZXJeW','0712345615','MANAGER',1),
        ('Oliver',NULL,'Owner','oliver@owner.com','$2a$10$8VbbFHeF6vxZ157KSmJuHe.eHOh9YFUB28ltKQu2PDPYcH2tpiROy','0712345616','OWNER',1)
    ) AS source(firstName,middleName,lastName,email,password,phoneNumber,type,resetDemoPassword)
       ON target.email=source.email
    WHEN MATCHED AND (target.password IS NULL OR source.resetDemoPassword=1) THEN
      UPDATE SET password=source.password,active=1,type=source.type,updatedAt=SYSDATETIME(),version=version+1
    WHEN NOT MATCHED THEN
      INSERT(firstName,middleName,lastName,email,password,phoneNumber,type)
      VALUES(source.firstName,source.middleName,source.lastName,source.email,source.password,source.phoneNumber,source.type);

    DECLARE @Customer1 INT=(SELECT userID FROM dbo.users WHERE email='ayesha.fernando@assignment.laundrylink.lk');
    DECLARE @Customer2 INT=(SELECT userID FROM dbo.users WHERE email='kavindu.perera@assignment.laundrylink.lk');
    DECLARE @Customer3 INT=(SELECT userID FROM dbo.users WHERE email='nimali.silva@assignment.laundrylink.lk');
    DECLARE @Rider1 INT=(SELECT userID FROM dbo.users WHERE email='ravindu.rider@assignment.laundrylink.lk');
    DECLARE @Rider2 INT=(SELECT userID FROM dbo.users WHERE email='fathima.rider@assignment.laundrylink.lk');
    DECLARE @Staff INT=(SELECT userID FROM dbo.users WHERE email='malith.staff@assignment.laundrylink.lk');
    DECLARE @Manager INT=(SELECT userID FROM dbo.users WHERE email='shalini.manager@assignment.laundrylink.lk');
    DECLARE @Owner INT=(SELECT userID FROM dbo.users WHERE email='dinesh.owner@assignment.laundrylink.lk');
    DECLARE @DemoCustomer INT=(SELECT userID FROM dbo.users WHERE email='anna@customer.com');
    DECLARE @DemoCsm INT=(SELECT userID FROM dbo.users WHERE email='cathy@csm.com');

    IF NOT EXISTS(SELECT 1 FROM dbo.addresses WHERE userID=@Customer1 AND nickname='Home')
      INSERT dbo.addresses(nickname,street,city,state,DeliveryInstructions,isDefault,userID)
      VALUES('Home','18 Temple Road','Colombo 05','Western','Call at the gate',1,@Customer1);
    IF NOT EXISTS(SELECT 1 FROM dbo.addresses WHERE userID=@Customer1 AND nickname='Office')
      INSERT dbo.addresses(nickname,street,city,state,DeliveryInstructions,isDefault,userID)
      VALUES('Office','42 Union Place','Colombo 02','Western','Leave at reception',0,@Customer1);
    IF NOT EXISTS(SELECT 1 FROM dbo.addresses WHERE userID=@Customer2 AND nickname='Home')
      INSERT dbo.addresses(nickname,street,city,state,DeliveryInstructions,isDefault,userID)
      VALUES('Home','76 Lake Drive','Kandy','Central','Ring the front bell',1,@Customer2);
    IF NOT EXISTS(SELECT 1 FROM dbo.addresses WHERE userID=@Customer3 AND nickname='Home')
      INSERT dbo.addresses(nickname,street,city,state,DeliveryInstructions,isDefault,userID)
      VALUES('Home','11 Beach Road','Galle','Southern','Blue gate',1,@Customer3);
    IF NOT EXISTS(SELECT 1 FROM dbo.addresses WHERE userID=@Customer3 AND nickname='Parents')
      INSERT dbo.addresses(nickname,street,city,state,DeliveryInstructions,isDefault,userID)
      VALUES('Parents','29 Main Street','Matara','Southern','Call ten minutes before arrival',0,@Customer3);

    /* Five orders with stable customer/status pairs allow safe reruns. */
    IF NOT EXISTS(SELECT 1 FROM dbo.orders WHERE userID=@Customer1 AND statusID=15) INSERT dbo.orders(statusID,userID) VALUES(15,@Customer1);
    IF NOT EXISTS(SELECT 1 FROM dbo.orders WHERE userID=@Customer1 AND statusID=9)  INSERT dbo.orders(statusID,userID) VALUES(9,@Customer1);
    IF NOT EXISTS(SELECT 1 FROM dbo.orders WHERE userID=@Customer2 AND statusID=3)  INSERT dbo.orders(statusID,userID) VALUES(3,@Customer2);
    IF NOT EXISTS(SELECT 1 FROM dbo.orders WHERE userID=@Customer2 AND statusID=12) INSERT dbo.orders(statusID,userID) VALUES(12,@Customer2);
    IF NOT EXISTS(SELECT 1 FROM dbo.orders WHERE userID=@Customer3 AND statusID=13) INSERT dbo.orders(statusID,userID) VALUES(13,@Customer3);

    DECLARE @Order1 INT=(SELECT TOP(1) orderID FROM dbo.orders WHERE userID=@Customer1 AND statusID=15 ORDER BY orderID);
    DECLARE @Order2 INT=(SELECT TOP(1) orderID FROM dbo.orders WHERE userID=@Customer1 AND statusID=9 ORDER BY orderID);
    DECLARE @Order3 INT=(SELECT TOP(1) orderID FROM dbo.orders WHERE userID=@Customer2 AND statusID=3 ORDER BY orderID);
    DECLARE @Order4 INT=(SELECT TOP(1) orderID FROM dbo.orders WHERE userID=@Customer2 AND statusID=12 ORDER BY orderID);
    DECLARE @Order5 INT=(SELECT TOP(1) orderID FROM dbo.orders WHERE userID=@Customer3 AND statusID=13 ORDER BY orderID);

    IF NOT EXISTS(SELECT 1 FROM dbo.orderLines WHERE orderID=@Order1 AND itemID=@Clothing AND serviceID=@Wash)
      INSERT dbo.orderLines(orderID,itemID,serviceID,quantity,linePrice) VALUES(@Order1,@Clothing,@Wash,4,720.00);
    IF NOT EXISTS(SELECT 1 FROM dbo.orderLines WHERE orderID=@Order2 AND itemID=@Shirt AND serviceID=@Wash)
      INSERT dbo.orderLines(orderID,itemID,serviceID,quantity,linePrice) VALUES(@Order2,@Shirt,@Wash,3,660.00);
    IF NOT EXISTS(SELECT 1 FROM dbo.orderLines WHERE orderID=@Order3 AND itemID=@Sheet AND serviceID=@Wash)
      INSERT dbo.orderLines(orderID,itemID,serviceID,quantity,linePrice) VALUES(@Order3,@Sheet,@Wash,2,900.00);
    IF NOT EXISTS(SELECT 1 FROM dbo.orderLines WHERE orderID=@Order4 AND itemID=@Shirt AND serviceID=@Iron)
      INSERT dbo.orderLines(orderID,itemID,serviceID,quantity,linePrice) VALUES(@Order4,@Shirt,@Iron,5,750.00);
    IF NOT EXISTS(SELECT 1 FROM dbo.orderLines WHERE orderID=@Order5 AND itemID=@Suit AND serviceID=@Dry)
      INSERT dbo.orderLines(orderID,itemID,serviceID,quantity,linePrice) VALUES(@Order5,@Suit,@Dry,1,1500.00);

    IF NOT EXISTS(SELECT 1 FROM dbo.payments WHERE orderID=@Order1 AND amount=720.00) INSERT dbo.payments(amount,orderID) VALUES(720.00,@Order1);
    IF NOT EXISTS(SELECT 1 FROM dbo.payments WHERE orderID=@Order2 AND amount=660.00) INSERT dbo.payments(amount,orderID) VALUES(660.00,@Order2);
    IF NOT EXISTS(SELECT 1 FROM dbo.payments WHERE orderID=@Order3 AND amount=900.00) INSERT dbo.payments(amount,orderID) VALUES(900.00,@Order3);
    IF NOT EXISTS(SELECT 1 FROM dbo.payments WHERE orderID=@Order4 AND amount=750.00) INSERT dbo.payments(amount,orderID) VALUES(750.00,@Order4);
    IF NOT EXISTS(SELECT 1 FROM dbo.payments WHERE orderID=@Order5 AND amount=1500.00) INSERT dbo.payments(amount,orderID) VALUES(1500.00,@Order5);

    IF NOT EXISTS(SELECT 1 FROM dbo.logs WHERE orderID=@Order1 AND status_after=15) INSERT dbo.logs VALUES(14,15,'2026-09-01','16:30',@Order1);
    IF NOT EXISTS(SELECT 1 FROM dbo.logs WHERE orderID=@Order2 AND status_after=9)  INSERT dbo.logs VALUES(8,9,'2026-09-02','10:15',@Order2);
    IF NOT EXISTS(SELECT 1 FROM dbo.logs WHERE orderID=@Order3 AND status_after=3)  INSERT dbo.logs VALUES(2,3,'2026-09-03','08:45',@Order3);
    IF NOT EXISTS(SELECT 1 FROM dbo.logs WHERE orderID=@Order4 AND status_after=12) INSERT dbo.logs VALUES(11,12,'2026-09-04','14:20',@Order4);
    IF NOT EXISTS(SELECT 1 FROM dbo.logs WHERE orderID=@Order5 AND status_after=13) INSERT dbo.logs VALUES(12,13,'2026-09-05','17:10',@Order5);

    /* Five support cases and five related messages. */
    IF NOT EXISTS(SELECT 1 FROM dbo.feedback WHERE subject='Careful handling request') INSERT dbo.feedback(feedback,userID,orderID,caseType,subject,rating,caseStatus,priority,assigneeID) VALUES('Please take extra care with the linen fabric.',@Customer1,@Order1,'Question','Careful handling request',NULL,'New','Normal',@Staff);
    IF NOT EXISTS(SELECT 1 FROM dbo.feedback WHERE subject='Excellent folding') INSERT dbo.feedback(feedback,userID,orderID,caseType,subject,rating,caseStatus,priority,assigneeID) VALUES('The clothes were folded very neatly.',@Customer1,@Order2,'Feedback','Excellent folding',5,'Closed','Low',@Staff);
    IF NOT EXISTS(SELECT 1 FROM dbo.feedback WHERE subject='Pickup time clarification') INSERT dbo.feedback(feedback,userID,orderID,caseType,subject,rating,caseStatus,priority,assigneeID) VALUES('Please confirm the planned pickup time.',@Customer2,@Order3,'Question','Pickup time clarification',NULL,'Assigned','Normal',@Manager);
    IF NOT EXISTS(SELECT 1 FROM dbo.feedback WHERE subject='Ironing crease issue') INSERT dbo.feedback(feedback,userID,orderID,caseType,subject,rating,caseStatus,priority,assigneeID) VALUES('Two shirts have incorrect sleeve creases.',@Customer2,@Order4,'Complaint','Ironing crease issue',NULL,'In Review','High',@Manager);
    IF NOT EXISTS(SELECT 1 FROM dbo.feedback WHERE subject='Helpful rider') INSERT dbo.feedback(feedback,userID,orderID,caseType,subject,rating,caseStatus,priority,assigneeID) VALUES('The delivery rider was polite and helpful.',@Customer3,@Order5,'Feedback','Helpful rider',5,'Resolved','Low',@Staff);
    IF NOT EXISTS(SELECT 1 FROM dbo.feedback WHERE subject='Demo pickup timing question' AND userID=@DemoCustomer) INSERT dbo.feedback(feedback,userID,orderID,caseType,subject,rating,caseStatus,priority,assigneeID) VALUES('Could you confirm the pickup time for my next booking?',@DemoCustomer,NULL,'Question','Demo pickup timing question',NULL,'Assigned','Normal',@DemoCsm);
    IF NOT EXISTS(SELECT 1 FROM dbo.feedback WHERE subject='Demo garment care feedback' AND userID=@DemoCustomer) INSERT dbo.feedback(feedback,userID,orderID,caseType,subject,rating,caseStatus,priority,assigneeID) VALUES('My previous laundry was returned fresh and neatly folded.',@DemoCustomer,NULL,'Feedback','Demo garment care feedback',5,'Resolved','Low',@DemoCsm);

    DECLARE @Feedback1 INT=(SELECT feedbackID FROM dbo.feedback WHERE subject='Careful handling request');
    DECLARE @Feedback2 INT=(SELECT feedbackID FROM dbo.feedback WHERE subject='Excellent folding');
    DECLARE @Feedback3 INT=(SELECT feedbackID FROM dbo.feedback WHERE subject='Pickup time clarification');
    DECLARE @Feedback4 INT=(SELECT feedbackID FROM dbo.feedback WHERE subject='Ironing crease issue');
    DECLARE @Feedback5 INT=(SELECT feedbackID FROM dbo.feedback WHERE subject='Helpful rider');
    DECLARE @DemoFeedback1 INT=(SELECT feedbackID FROM dbo.feedback WHERE subject='Demo pickup timing question' AND userID=@DemoCustomer);
    DECLARE @DemoFeedback2 INT=(SELECT feedbackID FROM dbo.feedback WHERE subject='Demo garment care feedback' AND userID=@DemoCustomer);
    IF NOT EXISTS(SELECT 1 FROM dbo.chat WHERE feedbackID=@Feedback1 AND message='We have added the handling note.') INSERT dbo.chat(message,userID,feedbackID) VALUES('We have added the handling note.',@Staff,@Feedback1);
    IF NOT EXISTS(SELECT 1 FROM dbo.chat WHERE feedbackID=@Feedback2 AND message='Thank you for your feedback.') INSERT dbo.chat(message,userID,feedbackID) VALUES('Thank you for your feedback.',@Staff,@Feedback2);
    IF NOT EXISTS(SELECT 1 FROM dbo.chat WHERE feedbackID=@Feedback3 AND message='Pickup is scheduled for 9 AM.') INSERT dbo.chat(message,userID,feedbackID) VALUES('Pickup is scheduled for 9 AM.',@Manager,@Feedback3);
    IF NOT EXISTS(SELECT 1 FROM dbo.chat WHERE feedbackID=@Feedback4 AND message='We are reviewing the affected shirts.') INSERT dbo.chat(message,userID,feedbackID) VALUES('We are reviewing the affected shirts.',@Manager,@Feedback4);
    IF NOT EXISTS(SELECT 1 FROM dbo.chat WHERE feedbackID=@Feedback5 AND message='We will pass this message to the rider.') INSERT dbo.chat(message,userID,feedbackID) VALUES('We will pass this message to the rider.',@Staff,@Feedback5);
    IF NOT EXISTS(SELECT 1 FROM dbo.chat WHERE feedbackID=@DemoFeedback1 AND message='Your request is assigned and we will confirm the available pickup window shortly.') INSERT dbo.chat(message,userID,feedbackID) VALUES('Your request is assigned and we will confirm the available pickup window shortly.',@DemoCsm,@DemoFeedback1);
    IF NOT EXISTS(SELECT 1 FROM dbo.chat WHERE feedbackID=@DemoFeedback2 AND message='Thank you, Anna. We are happy you enjoyed the service.') INSERT dbo.chat(message,userID,feedbackID) VALUES('Thank you, Anna. We are happy you enjoyed the service.',@DemoCsm,@DemoFeedback2);

    IF NOT EXISTS(SELECT 1 FROM dbo.delivery WHERE orderID=@Order1) INSERT dbo.delivery(orderID,userID,pickup_riderID,delivery_riderID,riderNotes,pickup_scheduled,pickup_actual,delivery_time) VALUES(@Order1,@Customer1,@Rider1,@Rider2,'Completed without issue','2026-08-30 09:00','2026-08-30 09:05','2026-09-01 16:30');
    IF NOT EXISTS(SELECT 1 FROM dbo.delivery WHERE orderID=@Order2) INSERT dbo.delivery(orderID,userID,pickup_riderID,delivery_riderID,riderNotes,pickup_scheduled,pickup_actual,delivery_time) VALUES(@Order2,@Customer1,@Rider1,NULL,'Processing at shop','2026-09-01 08:30','2026-09-01 08:35',NULL);
    IF NOT EXISTS(SELECT 1 FROM dbo.delivery WHERE orderID=@Order3) INSERT dbo.delivery(orderID,userID,pickup_riderID,delivery_riderID,riderNotes,pickup_scheduled,pickup_actual,delivery_time) VALUES(@Order3,@Customer2,NULL,NULL,'Awaiting rider','2026-09-23 09:00',NULL,NULL);
    IF NOT EXISTS(SELECT 1 FROM dbo.delivery WHERE orderID=@Order4) INSERT dbo.delivery(orderID,userID,pickup_riderID,delivery_riderID,riderNotes,pickup_scheduled,pickup_actual,delivery_time) VALUES(@Order4,@Customer2,@Rider2,NULL,'Ready for delivery','2026-09-03 10:00','2026-09-03 10:10',NULL);
    IF NOT EXISTS(SELECT 1 FROM dbo.delivery WHERE orderID=@Order5) INSERT dbo.delivery(orderID,userID,pickup_riderID,delivery_riderID,riderNotes,pickup_scheduled,pickup_actual,delivery_time) VALUES(@Order5,@Customer3,@Rider1,@Rider2,'Customer requested evening delivery','2026-09-04 11:00','2026-09-04 11:08',NULL);

    IF NOT EXISTS(SELECT 1 FROM dbo.support_activity WHERE feedbackID=@Feedback1 AND action='Created') INSERT dbo.support_activity(feedbackID,actorID,action,details) VALUES(@Feedback1,@Customer1,'Created','Customer submitted a handling question.');
    IF NOT EXISTS(SELECT 1 FROM dbo.support_activity WHERE feedbackID=@Feedback2 AND action='Closed') INSERT dbo.support_activity(feedbackID,actorID,action,details) VALUES(@Feedback2,@Staff,'Closed','Positive feedback acknowledged.');
    IF NOT EXISTS(SELECT 1 FROM dbo.support_activity WHERE feedbackID=@Feedback3 AND action='Assigned') INSERT dbo.support_activity(feedbackID,actorID,action,details) VALUES(@Feedback3,@Manager,'Assigned','Manager took ownership of pickup query.');
    IF NOT EXISTS(SELECT 1 FROM dbo.support_activity WHERE feedbackID=@Feedback4 AND action='Reviewed') INSERT dbo.support_activity(feedbackID,actorID,action,details) VALUES(@Feedback4,@Manager,'Reviewed','Garments sent for quality inspection.');
    IF NOT EXISTS(SELECT 1 FROM dbo.support_activity WHERE feedbackID=@Feedback5 AND action='Resolved') INSERT dbo.support_activity(feedbackID,actorID,action,details) VALUES(@Feedback5,@Staff,'Resolved','Compliment recorded for rider team.');

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
GO

/* Evidence check: every final relation should report at least five rows. */
SELECT tableName,recordCount FROM (VALUES
 ('status',(SELECT COUNT(*) FROM dbo.status)),('items',(SELECT COUNT(*) FROM dbo.items)),
 ('services',(SELECT COUNT(*) FROM dbo.services)),('servicePricing',(SELECT COUNT(*) FROM dbo.servicePricing)),
 ('users',(SELECT COUNT(*) FROM dbo.users)),('addresses',(SELECT COUNT(*) FROM dbo.addresses)),
 ('orders',(SELECT COUNT(*) FROM dbo.orders)),('orderLines',(SELECT COUNT(*) FROM dbo.orderLines)),
 ('payments',(SELECT COUNT(*) FROM dbo.payments)),('logs',(SELECT COUNT(*) FROM dbo.logs)),
 ('feedback',(SELECT COUNT(*) FROM dbo.feedback)),('chat',(SELECT COUNT(*) FROM dbo.chat)),
 ('delivery',(SELECT COUNT(*) FROM dbo.delivery)),('support_activity',(SELECT COUNT(*) FROM dbo.support_activity)),
 ('notifications',(SELECT COUNT(*) FROM dbo.notifications))
) counts(tableName,recordCount)
ORDER BY tableName;
