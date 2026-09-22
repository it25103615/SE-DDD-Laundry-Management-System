param([string]$Server = 'lpc:.\SQLEXPRESS')
# Creates the complete current schema when absent; upgrades an existing database with migration 003 only.
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
        Write-Output 'Created laundryLinkDB with the complete current schema; no migrations are required.'
    } else {
        Write-Output 'Existing laundryLinkDB found; applying only migration 003.'
        $connection.ChangeDatabase('laundryLinkDB')
        $refinementSql = Get-Content -Raw -LiteralPath (Join-Path $projectRoot 'database/migrations/003_ddd_assignment2_refinement.sql')
        foreach ($batch in [regex]::Split($refinementSql, '(?im)^\s*GO\s*\r?$')) {
            if ($batch.Trim()) { $command.CommandText=$batch; $command.ExecuteNonQuery() | Out-Null }
        }
        Write-Output 'Migration 003 applied; existing records preserved.'
    }
} finally { $connection.Dispose() }
