param([string]$Server = 'lpc:.\SQLEXPRESS')

# Removes seeded users and transactional records while preserving the database
# schema and reference catalog (statuses, items, services and service prices).
$ErrorActionPreference = 'Stop'
$connection = New-Object System.Data.SqlClient.SqlConnection "Server=$Server;Database=laundryLinkDB;Integrated Security=true;TrustServerCertificate=true;Connect Timeout=10"
try {
    $connection.Open()
    $command = $connection.CreateCommand()
    $command.CommandText = @"
SET XACT_ABORT ON;
BEGIN TRANSACTION;
DELETE FROM chat;
DELETE FROM support_activity;
DELETE FROM feedback;
DELETE FROM payments;
DELETE FROM logs;
DELETE FROM orderLines;
DELETE FROM delivery;
DELETE FROM orders;
DELETE FROM addresses;
DELETE FROM system_settings;
DELETE FROM users;
DBCC CHECKIDENT ('chat', RESEED, 0);
DBCC CHECKIDENT ('support_activity', RESEED, 0);
DBCC CHECKIDENT ('feedback', RESEED, 0);
DBCC CHECKIDENT ('payments', RESEED, 0);
DBCC CHECKIDENT ('logs', RESEED, 0);
DBCC CHECKIDENT ('orderLines', RESEED, 0);
DBCC CHECKIDENT ('delivery', RESEED, 0);
DBCC CHECKIDENT ('orders', RESEED, 0);
DBCC CHECKIDENT ('addresses', RESEED, 0);
DBCC CHECKIDENT ('system_settings', RESEED, 0);
DBCC CHECKIDENT ('users', RESEED, 0);
COMMIT;
"@
    $command.ExecuteNonQuery() | Out-Null
    Write-Output 'Seeded users and transactional records removed. Reference catalog preserved.'
} finally {
    $connection.Dispose()
}
