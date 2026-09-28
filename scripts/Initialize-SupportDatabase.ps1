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
# Existing database: applies migrations 003, 004 and 005 (all safe to re-run).
# Migration 005 always runs last: it creates the laundry processing tables when they are
# missing and, once the sample data exists, adds the processing test orders after it.
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
