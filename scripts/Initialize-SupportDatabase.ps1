param([string]$Server = 'lpc:.\SQLEXPRESS')
# Creates the project database when absent and applies all application migrations.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$connection = New-Object System.Data.SqlClient.SqlConnection "Server=$Server;Database=master;Integrated Security=true;TrustServerCertificate=true;Connect Timeout=10"
try {
    $connection.Open()
    $command = $connection.CreateCommand()
    $command.CommandText = "SELECT DB_ID('laundryLinkDB')"
    $databaseId = $command.ExecuteScalar()
    if ($databaseId -is [DBNull]) {
        $sql = Get-Content -Raw -LiteralPath (Join-Path $projectRoot 'initialize_database.sql')
        foreach ($batch in [regex]::Split($sql, '(?im)^\s*GO\s*\r?$')) {
            if ($batch.Trim()) { $command.CommandText=$batch; $command.ExecuteNonQuery() | Out-Null }
        }
        Write-Output 'Created laundryLinkDB.'
    } else { Write-Output 'Existing laundryLinkDB found.' }
    $connection.ChangeDatabase('laundryLinkDB')
    $command.CommandText = Get-Content -Raw -LiteralPath (Join-Path $projectRoot 'database/migrations/001_support_admin.sql')
    $command.ExecuteNonQuery() | Out-Null
    $accountSql = Get-Content -Raw -LiteralPath (Join-Path $projectRoot 'database/migrations/002_account_password_hash.sql')
    foreach ($batch in [regex]::Split($accountSql, '(?im)^\s*GO\s*\r?$')) {
        if ($batch.Trim()) { $command.CommandText=$batch; $command.ExecuteNonQuery() | Out-Null }
    }
    Write-Output 'Application migrations applied; existing records preserved.'
} finally { $connection.Dispose() }
