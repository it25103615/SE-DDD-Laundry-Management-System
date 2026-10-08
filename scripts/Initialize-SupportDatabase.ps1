param(
    [string]$Server = 'lpc:.\SQLEXPRESS',
    # Optional SQL Server login (e.g. a Docker container: -Server 'localhost,1433' -SqlUser sa -SqlPassword '...').
    # When omitted, Windows (integrated) authentication is used as before.
    [string]$SqlUser,
    [string]$SqlPassword,
    # Also load database/ddd_assignment2_sample_data.sql (demo accounts and orders #1-#5)
    # before migration 005, so the laundry processing test orders are created too.
    [switch]$SampleData
)
# Fresh database: creates the complete current schema (initialize_database.sql).
# Existing database: applies migrations 003, 004, 005, 006, 007, 008, 009, 010, 011 and 012 (all safe to re-run).
# Migration 005 runs after the sample data: it creates the laundry processing tables when they
# are missing and, once the sample data exists, adds the processing test orders after it.
# Migration 006 runs next: it adds delivery.addressID when missing and fills it in for
# delivery rows that have no address yet.
# Migration 007 runs next: it adds orders.instructions and orders.preferences when missing.
# Migration 008 runs next: it creates the trigger that writes the order status history
# (dbo.logs) and takes that job away from dbo.sp_UpdateProcessingStatus.
# Migration 009 runs next: it adds the payment method, reference, status and processed-at
# columns to dbo.payments when missing.
# Migration 010 runs next: it fixes the payment notification's receipt link.
# Migration 011 creates the refunds table; 012 adds email recovery tokens.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent

if ($SqlUser) {
    $authentication = "User ID=$SqlUser;Password=$SqlPassword"
} else {
    $authentication = 'Integrated Security=true'
}
$connection = New-Object System.Data.SqlClient.SqlConnection "Server=$Server;Database=master;$authentication;TrustServerCertificate=true;Connect Timeout=10"
# Show PRINT messages from the scripts (e.g. migration 005 explaining why it skipped the test orders).
$connection.add_InfoMessage({ param($sender, $event) Write-Output $event.Message })

# Runs one .sql file batch by batch, splitting on lines that contain only GO (like sqlcmd does).
function Invoke-SqlFile([System.Data.SqlClient.SqlCommand]$command, [string]$relativePath) {
    $sql = Get-Content -Raw -LiteralPath (Join-Path $projectRoot $relativePath)
    # Processing examples are opt-in, just like the other sample records.
    if ($relativePath -eq 'database/migrations/005_processing.sql' -and !$SampleData) {
        $sampleSection = $sql.IndexOf('PART 2 - TEST DATA')
        if ($sampleSection -ge 0) { $sql = $sql.Substring(0, $sql.LastIndexOf('/*', $sampleSection)) }
    }
    foreach ($batch in [regex]::Split($sql, '(?im)^\s*GO\s*\r?$')) {
        if ($batch.Trim()) { $command.CommandText = $batch; $command.ExecuteNonQuery() | Out-Null }
    }
}

try {
    $connection.Open()
    $command = $connection.CreateCommand()
    $command.CommandTimeout = 120
    $command.CommandText = "SELECT DB_ID('laundryLinkDB')"
    $databaseId = $command.ExecuteScalar()
    if ($databaseId -is [DBNull]) {
        Invoke-SqlFile $command 'initialize_database.sql'
        Write-Output 'Created laundryLinkDB with the complete current schema (including the laundry processing tables).'
    } else {
        Write-Output 'Existing laundryLinkDB found; applying migrations 003, 004 and 005.'
        $connection.ChangeDatabase('laundryLinkDB')
        Invoke-SqlFile $command 'database/migrations/003_ddd_assignment2_refinement.sql'
        Write-Output 'Migration 003 applied; existing records preserved.'
        # 004 replaces sp_UpdateOrderStatus with sp_UpdateProcessingStatus, which the processing module calls.
        Invoke-SqlFile $command 'database/migrations/004_ddd_assignment2_module_routines.sql'
        Write-Output 'Migration 004 applied (module routines).'
    }

    if ($SampleData) {
        Invoke-SqlFile $command 'database/ddd_assignment2_sample_data.sql'
        Write-Output 'Sample data loaded (demo accounts and orders #1-#5).'
    }

    # 005: processing tables (skipped when present) and the processing test orders.
    Invoke-SqlFile $command 'database/migrations/005_processing.sql'
    Write-Output 'Migration 005 applied (laundry processing).'

    # 006: delivery.addressID (added when missing). Runs after the sample data and 005 so the
    # delivery rows they create are given the customer's default address.
    Invoke-SqlFile $command 'database/migrations/006_delivery_address.sql'
    Write-Output 'Migration 006 applied (delivery address).'

    # 007: orders.instructions and orders.preferences (added when missing). The application
    # will not start without them, because Hibernate checks the columns at startup.
    Invoke-SqlFile $command 'database/migrations/007_order_instructions.sql'
    Write-Output 'Migration 007 applied (order instructions).'

    # 008: the order status log trigger. It runs after the sample data and 005, which insert
    # their orders and log rows directly, so their history is not written a second time.
    Invoke-SqlFile $command 'database/migrations/008_order_status_log_trigger.sql'
    Write-Output 'Migration 008 applied (order status log trigger).'

    # 009: payment method, transaction reference, status and processed-at columns on payments
    # (added when missing). Like 007, the application will not start without them, because
    # Hibernate checks the columns at startup. Existing payment rows stay valid.
    Invoke-SqlFile $command 'database/migrations/009_payment_data_alignment.sql'
    Write-Output 'Migration 009 applied (payment data alignment).'

    # 010: the payment notification trigger now links to receipt.html?orderID=..&paymentID=..
    # (the receipt page needs both), and existing payment notifications are rewritten to match.
    Invoke-SqlFile $command 'database/migrations/010_notification_links.sql'
    Write-Output 'Migration 010 applied (notification links).'

    # 011: the refunds table (created when missing). The application will not start without it,
    # because Hibernate checks the tables at startup. Safe to re-run.
    Invoke-SqlFile $command 'database/migrations/011_refunds.sql'
    Write-Output 'Migration 011 applied (refunds).'
    Invoke-SqlFile $command 'database/migrations/012_password_recovery.sql'
    Write-Output 'Migration 012 applied (email password recovery).'
    Invoke-SqlFile $command 'database/migrations/013_support_case_topics.sql'
    Write-Output 'Migration 013 applied (support case topics).'

    # Report which orders the laundry processing test cases (TC-LP01 to LP10) should use.
    $connection.ChangeDatabase('laundryLinkDB')
    $command.CommandText = @"
SELECT o.orderID, s.statusLabel
FROM orders o JOIN status s ON s.statusID = o.statusID
JOIN users u ON u.userID = o.userID
WHERE u.email = 'priya.fernando@assignment.laundrylink.lk'
ORDER BY o.orderID
"@
    $reader = $command.ExecuteReader()
    try {
        while ($reader.Read()) { Write-Output ("Processing test order #{0}: {1}" -f $reader.GetInt32(0), $reader.GetString(1)) }
    } finally { $reader.Close() }
} finally { $connection.Dispose() }
