param([string]$Server='lpc:.\SQLEXPRESS',[switch]$Apply,[switch]$RemoveDemoAccounts)
$ErrorActionPreference='Stop'
$taskRoot=Split-Path $PSScriptRoot -Parent
$connection=New-Object System.Data.SqlClient.SqlConnection "Server=$Server;Database=laundryLinkDB;Integrated Security=true;TrustServerCertificate=true;Connect Timeout=10"
$transaction=$null
try {
 $connection.Open()
 $transaction=$connection.BeginTransaction([System.Data.IsolationLevel]::Serializable)
 $command=$connection.CreateCommand(); $command.Transaction=$transaction; $command.CommandTimeout=60
 $scope=@"
DECLARE @DemoUsers TABLE(id INT PRIMARY KEY);
INSERT @DemoUsers SELECT userID FROM dbo.users WHERE email LIKE '%@assignment.laundrylink.lk'
 OR email IN ('anna@customer.com','ravi@rider.com','sam@staff.com','cathy@csm.com','maya@manager.com','oliver@owner.com');
DECLARE @DemoOrders TABLE(id INT PRIMARY KEY);
INSERT @DemoOrders SELECT orderID FROM dbo.orders WHERE userID IN(SELECT id FROM @DemoUsers);
DECLARE @DemoCases TABLE(id INT PRIMARY KEY);
INSERT @DemoCases SELECT feedbackID FROM dbo.feedback WHERE userID IN(SELECT id FROM @DemoUsers) OR orderID IN(SELECT id FROM @DemoOrders);
"@
 $command.CommandText=$scope+"SELECT (SELECT COUNT(*) FROM @DemoUsers) AS demoAccounts,(SELECT COUNT(*) FROM @DemoOrders) AS demoOrders,(SELECT COUNT(*) FROM @DemoCases) AS demoCases,(SELECT COUNT(*) FROM users WHERE userID NOT IN(SELECT id FROM @DemoUsers)) AS preservedAccounts;"
 $reader=$command.ExecuteReader()
 if($reader.Read()){Write-Output "Demo accounts: $($reader['demoAccounts']); orders: $($reader['demoOrders']); cases: $($reader['demoCases']); registered accounts preserved: $($reader['preservedAccounts'])."}; $reader.Close()
 if(!$Apply){$transaction.Rollback();$transaction=$null;return}
 $command.CommandText="SELECT QUOTENAME(s.name)+'.'+QUOTENAME(t.name) FROM sys.tables t JOIN sys.schemas s ON s.schema_id=t.schema_id WHERE t.is_ms_shipped=0 ORDER BY s.name,t.name"
 $reader=$command.ExecuteReader();$tableNames=@();while($reader.Read()){$tableNames+=$reader.GetString(0)};$reader.Close()
 $snapshot=New-Object System.Data.DataSet 'LaundryLinkBeforeDemoCleanup'
 foreach($tableName in $tableNames){$command.CommandText="SELECT * FROM $tableName";$adapter=New-Object System.Data.SqlClient.SqlDataAdapter $command;try{$null=$adapter.Fill($snapshot,$tableName)}finally{$adapter.Dispose()}}
 $backupDirectory=Join-Path $taskRoot 'backups/database';New-Item -ItemType Directory -Force -Path $backupDirectory | Out-Null
 $backupPath=Join-Path $backupDirectory ('before-cleanup-'+[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss')+'.xml')
 $snapshot.WriteXml($backupPath,[System.Data.XmlWriteMode]::WriteSchema)
 if(!(Test-Path -LiteralPath $backupPath) -or (Get-Item -LiteralPath $backupPath).Length -eq 0){throw 'Backup failed; cleanup cancelled.'}
 $command.CommandText=$scope+@"
SET XACT_ABORT ON;
DELETE FROM chat WHERE feedbackID IN(SELECT id FROM @DemoCases);
DELETE FROM support_activity WHERE feedbackID IN(SELECT id FROM @DemoCases);
DELETE FROM notifications WHERE recipientID IN(SELECT id FROM @DemoUsers)
 OR (relatedType='SUPPORT' AND relatedID IN(SELECT id FROM @DemoCases)) OR (relatedType='ORDER' AND relatedID IN(SELECT id FROM @DemoOrders));
DELETE FROM feedback WHERE feedbackID IN(SELECT id FROM @DemoCases);
DELETE FROM refunds WHERE paymentID IN(SELECT paymentID FROM payments WHERE orderID IN(SELECT id FROM @DemoOrders));
DELETE FROM payments WHERE orderID IN(SELECT id FROM @DemoOrders);
DELETE FROM qualityChecks WHERE orderID IN(SELECT id FROM @DemoOrders);
DELETE FROM receivedItems WHERE orderLineID IN(SELECT orderLineID FROM orderLines WHERE orderID IN(SELECT id FROM @DemoOrders));
DELETE FROM logs WHERE orderID IN(SELECT id FROM @DemoOrders);
DELETE FROM orderLines WHERE orderID IN(SELECT id FROM @DemoOrders);
DELETE FROM delivery WHERE orderID IN(SELECT id FROM @DemoOrders);
DELETE FROM orders WHERE orderID IN(SELECT id FROM @DemoOrders);
DELETE FROM addresses WHERE userID IN(SELECT id FROM @DemoUsers);
DELETE FROM passwordResetTokens WHERE userID IN(SELECT id FROM @DemoUsers);
"@
 $command.CommandText+="`nDELETE FROM users WHERE userID IN(SELECT id FROM @DemoUsers) AND email NOT IN ('cathy@csm.com','maya@manager.com','ravi@rider.com','sam@staff.com','oliver@owner.com');"
 if($RemoveDemoAccounts){$command.CommandText+="`nDELETE FROM users WHERE userID IN(SELECT id FROM @DemoUsers);"}
 $null=$command.ExecuteNonQuery();$transaction.Commit();$transaction=$null
 Write-Output "Demo records removed. Backup: $backupPath"
 Write-Output $(if($RemoveDemoAccounts){'Demo accounts removed; registered accounts preserved.'}else{'Only the five role login accounts retained; other demo accounts removed.'})
}catch{if($transaction){$transaction.Rollback();$transaction=$null};throw}finally{$connection.Dispose()}
