/* Persistent support-testing fixtures. Password: LaundryLink1!
   Runs atomically; repeated runs retain the same five accounts and orders.
   Records the completed lifecycle directly in SQL; this does not test the UI actions. */
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF NOT EXISTS(SELECT 1 FROM status WHERE statusID=21)
 INSERT status(statusID,statusLabel) VALUES(21,'Quality Inspection');
DECLARE @staff int=(SELECT TOP 1 userID FROM users WHERE type='STAFF' AND active=1 ORDER BY userID),
 @rider int=(SELECT TOP 1 userID FROM users WHERE type='RIDER' AND active=1 ORDER BY userID);
IF @staff IS NULL OR @rider IS NULL THROW 51000,'Active staff and rider accounts are required.',1;
DECLARE @fixtures TABLE(n int,firstName varchar(50),lastName varchar(50),email varchar(100),item varchar(100),service varchar(100),quantity int);
INSERT @fixtures VALUES
 (1,'Nethmi','Perera','nethmi@support-test.example.com','Shirt / Blouse','Wash and Fold',3),
 (2,'Kavish','Silva','kavish@support-test.example.com','Everyday Clothing','Wash and Fold',4),
 (3,'Amaya','Fernando','amaya@support-test.example.com','Bed Sheet','Wash and Fold',2),
 (4,'Ruwan','Dias','ruwan@support-test.example.com','Two-Piece Suit','Dry Cleaning',1),
 (5,'Fathima','Hassan','fathima@support-test.example.com','Trousers / Skirt','Ironing',2);
DECLARE @n int,@first varchar(50),@last varchar(50),@email varchar(100),@item varchar(100),@service varchar(100),@quantity int;
DECLARE fixtures CURSOR LOCAL FAST_FORWARD FOR SELECT * FROM @fixtures;
OPEN fixtures;
FETCH NEXT FROM fixtures INTO @n,@first,@last,@email,@item,@service,@quantity;
WHILE @@FETCH_STATUS=0 BEGIN
 DECLARE @user int=NULL,@order int=NULL,@address int=NULL,@itemID int=NULL,@serviceID int=NULL,@price decimal(10,2)=NULL;
 SELECT @user=userID FROM users WHERE email=@email;
 IF @user IS NULL BEGIN
  INSERT users(firstName,lastName,email,password,phoneNumber,type,active)
  VALUES(@first,@last,@email,'$2a$10$JubJfTSRWCO3oTOBnrayR.hcBzzgjHMFAcXUpGvDT2/vAwCiB5M7O',CONCAT('077900010',@n),'CUSTOMER',1);
  SET @user=SCOPE_IDENTITY();
 END;
 SELECT @order=orderID FROM orders WHERE userID=@user AND instructions='Support workflow practice order';
 IF @order IS NULL BEGIN
  SELECT @itemID=i.itemID,@serviceID=s.serviceID,@price=p.price FROM servicePricing p
   JOIN items i ON i.itemID=p.itemID JOIN services s ON s.serviceID=p.serviceID
   WHERE i.itemName=@item AND s.serviceName=@service;
  IF @price IS NULL THROW 51001,'Required catalogue pricing is missing.',1;
  INSERT addresses(nickname,street,city,state,DeliveryInstructions,isDefault,userID)
   VALUES('Home',CONCAT(20+@n,' Lake Road'),'Colombo','Western','Call on arrival',1,@user);
  SET @address=SCOPE_IDENTITY();
  INSERT orders(statusID,userID,instructions,preferences) VALUES(1,@user,'Support workflow practice order','');
  SET @order=SCOPE_IDENTITY();
  INSERT orderLines(orderID,itemID,serviceID,quantity,linePrice) VALUES(@order,@itemID,@serviceID,@quantity,@price*@quantity);
  INSERT payments(amount,orderID,paymentStatus,paymentMethod,transactionReference,processedAt)
   VALUES(@price*@quantity,@order,'VERIFIED','CARD',CONCAT('SUPPORT-TEST-',@order),SYSDATETIME());
  UPDATE orders SET statusID=2 WHERE orderID=@order;
  UPDATE orders SET statusID=3 WHERE orderID=@order;
  INSERT delivery(orderID,userID,pickup_riderID,delivery_riderID,addressID,pickup_scheduled,pickup_actual,delivery_time,riderNotes)
   VALUES(@order,@user,@rider,@rider,@address,SYSDATETIME(),SYSDATETIME(),SYSDATETIME(),'Support workflow practice: collected and delivered');
  UPDATE orders SET statusID=4 WHERE orderID=@order;
  UPDATE orders SET statusID=5 WHERE orderID=@order;
  UPDATE orders SET statusID=6 WHERE orderID=@order;
  UPDATE orders SET statusID=7 WHERE orderID=@order;
  INSERT receivedItems(orderLineID,receivedQuantity,itemCondition,receivedBy)
   SELECT orderLineID,quantity,'As expected',@staff FROM orderLines WHERE orderID=@order;
  UPDATE orders SET statusID=8 WHERE orderID=@order;
  IF @service='Dry Cleaning' UPDATE orders SET statusID=19 WHERE orderID=@order;
  ELSE IF @service='Wash and Fold' BEGIN
   UPDATE orders SET statusID=9 WHERE orderID=@order;
   UPDATE orders SET statusID=10 WHERE orderID=@order;
  END;
  UPDATE orders SET statusID=11 WHERE orderID=@order;
  UPDATE orders SET statusID=21 WHERE orderID=@order;
  INSERT qualityChecks(orderID,result,packed,notes,checkedBy) VALUES(@order,'Passed',1,'Items checked and packed for support workflow practice',@staff);
  UPDATE orders SET statusID=12 WHERE orderID=@order;
  UPDATE orders SET statusID=13 WHERE orderID=@order;
  UPDATE orders SET statusID=14 WHERE orderID=@order;
 END;
 FETCH NEXT FROM fixtures INTO @n,@first,@last,@email,@item,@service,@quantity;
END;
CLOSE fixtures;
DEALLOCATE fixtures;
COMMIT;
SELECT u.email,o.orderID,s.statusLabel,p.amount,p.paymentStatus
 FROM users u JOIN orders o ON o.userID=u.userID JOIN status s ON s.statusID=o.statusID
 JOIN payments p ON p.orderID=o.orderID WHERE u.email LIKE '%@support-test.example.com' ORDER BY o.orderID;
