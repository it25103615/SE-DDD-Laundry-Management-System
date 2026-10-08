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
# Existing database: applies migrations 003 and 004 first (all migrations are safe to re-run).
# Both cases then run every other file in database/migrations in name order, starting at 005.
# The files are read from the folder, so a new migration only needs to be added there as
# NNN_description.sql; this script does not need to change.
# The sample data (-SampleData) is loaded just before 005, so 005 and later migrations can add
# their test orders after it and fill in columns on the rows it creates.
# Migrations 001 and 002 are never run here: they predate this script and every existing
# database already has them.
# Rules for a new migration: it must be safe to re-run (this script runs all of them every
# time), and its number must not be used by another file.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
# The sample data is loaded just before the first migration with this number or higher.
$firstMigrationAfterSampleData = 5

# Read the migration files in name order and check their names before touching the database.
$migrations = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'database/migrations') -Filter '*.sql' | Sort-Object Name)
$usedNumbers = @{}
foreach ($migration in $migrations) {
    if ($migration.Name -notmatch '^(\d{3})_') {
        throw "Migration file '$($migration.Name)' must be named NNN_description.sql (for example 016_new_table.sql)."
    }
    $number = $Matches[1]
    # Two files with the same number would run in an order nobody chose, so stop here instead.
    if ($usedNumbers.ContainsKey($number)) {
        throw "Migrations '$($usedNumbers[$number])' and '$($migration.Name)' both use number $number. Rename one of them."
    }
    $usedNumbers[$number] = $migration.Name
}

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
        # The fresh schema already contains everything up to 004, so start after them.
        $firstMigration = $firstMigrationAfterSampleData
    } else {
        Write-Output 'Existing laundryLinkDB found; applying migrations from 003 onward. Existing records are preserved.'
        $firstMigration = 3
    }
    # Some migrations have no USE line, so make sure they run in the right database.
    $connection.ChangeDatabase('laundryLinkDB')

    $sampleDataLoaded = $false
    # Loads the sample data once, and only when -SampleData was given.
    function Add-SampleData {
        if ($SampleData -and !$script:sampleDataLoaded) {
            Invoke-SqlFile $command 'database/ddd_assignment2_sample_data.sql'
            Write-Output 'Sample data loaded (demo accounts and orders #1-#5).'
            $script:sampleDataLoaded = $true
        }
    }

    foreach ($migration in $migrations) {
        $number = $migration.Name.Substring(0, 3)
        if ([int]$number -lt $firstMigration) { continue }
        # The sample data goes in after 003/004 and before 005 and everything later.
        if ([int]$number -ge $firstMigrationAfterSampleData) { Add-SampleData }
        Invoke-SqlFile $command "database/migrations/$($migration.Name)"
        # Describe the migration from its file name, e.g. 011_refunds.sql -> "refunds".
        $description = $migration.BaseName.Substring(4).Replace('_', ' ')
        Write-Output "Migration $number applied ($description)."
    }
    # Still load the sample data if there was no migration from 005 onward to trigger it.
    Add-SampleData

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
