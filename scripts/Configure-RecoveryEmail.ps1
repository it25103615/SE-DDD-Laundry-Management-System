param([string]$SenderEmail)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
if (!$SenderEmail) { $SenderEmail = Read-Host 'Gmail address LaundryLink will SEND from (recipient is the registered user)' }
$SenderEmail = $SenderEmail.Trim()
if ($SenderEmail -notmatch '^[A-Za-z0-9._%+-]+@gmail\.com$') { throw 'Enter a Gmail sender address.' }
$taskSecret = Read-Host 'Google app password (hidden; not your regular Gmail password)' -AsSecureString
$taskPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($taskSecret)
try {
    $taskAppPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($taskPointer) -replace '\s', ''
    if ($taskAppPassword -notmatch '^[A-Za-z]{16}$') { throw 'Enter the 16-letter Google app password.' }
    $taskConfigPath = Join-Path $taskRoot 'src/main/resources/application-local.properties'
    $taskLines = if (Test-Path -LiteralPath $taskConfigPath) { @(Get-Content -LiteralPath $taskConfigPath) } else { @() }
    $taskLines = @($taskLines | Where-Object { $_ -notmatch '^\s*(spring\.mail\.|laundrylink\.recovery\.from\s*=)' })
    $taskLines += @('', '# Gmail OTP delivery (local credentials; this file is ignored by Git).',
        'spring.mail.host=smtp.gmail.com', 'spring.mail.port=587',
        "spring.mail.username=$SenderEmail", "spring.mail.password=$taskAppPassword",
        'spring.mail.properties.mail.smtp.auth=true',
        'spring.mail.properties.mail.smtp.starttls.enable=true',
        'spring.mail.properties.mail.smtp.starttls.required=true',
        "laundrylink.recovery.from=$SenderEmail")
    [IO.File]::WriteAllLines($taskConfigPath, $taskLines, (New-Object Text.UTF8Encoding($false)))
    Write-Output 'Gmail sender configured locally. Restart LaundryLink, then request a code for a registered user email.'
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($taskPointer)
    $taskAppPassword = $null
    $taskSecret.Dispose()
}
