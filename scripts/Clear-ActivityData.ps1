param([string]$Server = 'lpc:.\SQLEXPRESS', [switch]$Apply)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
$taskConnection = New-Object System.Data.SqlClient.SqlConnection "Server=$Server;Database=laundryLinkDB;Integrated Security=true;TrustServerCertificate=true;Connect Timeout=10"
$taskTransaction = $null
# Child records are deleted before their parents; accounts and catalogue tables are never deleted.
$taskActivityTables = @('passwordRecoveryCodes','chat','support_activity','notifications','feedback','refunds','payments','qualityChecks','receivedItems','logs','orderLines','delivery','orderPromotions','orders')
$taskRetainedTables = @('users','addresses','items','services','servicePricing','status','promotions','passwordResetTokens')
try {
    $taskConnection.Open()
    $taskTransaction = $taskConnection.BeginTransaction([System.Data.IsolationLevel]::Serializable)
    $taskCommand = $taskConnection.CreateCommand()
    $taskCommand.Transaction = $taskTransaction
    $taskCommand.CommandTimeout = 120
    $taskCommand.CommandText = "SELECT t.name FROM sys.tables t JOIN sys.schemas s ON s.schema_id=t.schema_id WHERE t.is_ms_shipped=0 AND s.name='dbo' ORDER BY t.name"
    $taskReader = $taskCommand.ExecuteReader()
    $taskTables = @()
    while ($taskReader.Read()) { $taskTables += $taskReader.GetString(0) }
    $taskReader.Close()
    foreach ($taskTable in $taskTables) {
        if ($taskTable -notin ($taskActivityTables + $taskRetainedTables)) {
            throw "Unrecognised table '$taskTable'; inspect dependencies before clearing activity."
        }
    }
    $taskSnapshot = New-Object System.Data.DataSet 'LaundryLinkBeforeActivityCleanup'
    foreach ($taskTable in $taskTables) {
        $taskCommand.CommandText = "SELECT * FROM dbo.[$taskTable]"
        $taskAdapter = New-Object System.Data.SqlClient.SqlDataAdapter $taskCommand
        try { $null = $taskAdapter.Fill($taskSnapshot, $taskTable) }
        finally { $taskAdapter.Dispose() }
        Write-Output "$taskTable : $($taskSnapshot.Tables[$taskTable].Rows.Count)"
    }
    if (!$Apply) { $taskTransaction.Rollback(); $taskTransaction = $null; return }
    $taskBackupDirectory = Join-Path $taskRoot 'backups/database'
    $null = New-Item -ItemType Directory -Force -Path $taskBackupDirectory
    $taskBackupPath = Join-Path $taskBackupDirectory ('before-activity-cleanup-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss') + '.xml')
    $taskSnapshot.WriteXml($taskBackupPath, [System.Data.XmlWriteMode]::WriteSchema)
    # Read back the snapshot before deletion; an incomplete export cancels the transaction.
    $taskCheck = New-Object System.Data.DataSet
    $null = $taskCheck.ReadXml($taskBackupPath)
    foreach ($taskTable in $taskTables) {
        if ($taskCheck.Tables[$taskTable].Rows.Count -ne $taskSnapshot.Tables[$taskTable].Rows.Count) {
            throw "Backup verification failed for $taskTable."
        }
    }
    $taskCommand.CommandText = 'SET XACT_ABORT ON;'
    $null = $taskCommand.ExecuteNonQuery()
    foreach ($taskTable in $taskActivityTables) {
        if ($taskTable -notin $taskTables) { continue }
        $taskCommand.CommandText = "DELETE FROM dbo.[$taskTable]"
        $null = $taskCommand.ExecuteNonQuery()
    }
    foreach ($taskTable in $taskActivityTables) {
        if ($taskTable -notin $taskTables) { continue }
        $taskCommand.CommandText = "SELECT COUNT(*) FROM dbo.[$taskTable]"
        if ($taskCommand.ExecuteScalar() -ne 0) { throw "Activity remains in $taskTable; rolling back." }
    }
    foreach ($taskTable in $taskRetainedTables) {
        # Compare every retained value, not just account counts, before committing.
        $taskCommand.CommandText = "SELECT * FROM dbo.[$taskTable]"
        $taskAfter = New-Object System.Data.DataSet
        $taskAdapter = New-Object System.Data.SqlClient.SqlDataAdapter $taskCommand
        try { $null = $taskAdapter.Fill($taskAfter, $taskTable) }
        finally { $taskAdapter.Dispose() }
        $taskBefore = New-Object System.Data.DataSet
        $null = $taskBefore.Tables.Add($taskSnapshot.Tables[$taskTable].Copy())
        if ($taskAfter.GetXml() -ne $taskBefore.GetXml()) { throw "$taskTable changed; rolling back." }
    }
    $taskTransaction.Commit()
    $taskTransaction = $null
    Write-Output 'All activity cleared. Users, addresses, catalogue, pricing, statuses, promotions and recovery tokens verified unchanged.'
    Write-Output "Snapshot: $taskBackupPath"
} catch {
    if ($taskTransaction) { $taskTransaction.Rollback(); $taskTransaction = $null }
    throw
} finally { $taskConnection.Dispose() }
