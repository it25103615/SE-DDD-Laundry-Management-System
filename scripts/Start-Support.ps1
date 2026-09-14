param([int]$Port = 8080)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $projectRoot
if (-not $env:JAVA_HOME) {
    $jdk = Get-ChildItem -LiteralPath "$env:USERPROFILE/.jdks" -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -match '26' } | Select-Object -First 1
    if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
}
$mavenCommand = Get-Command mvn.cmd -ErrorAction SilentlyContinue
if ($mavenCommand) { $mavenPath = $mavenCommand.Source }
else {
    $mavenPath = Get-ChildItem -Path "$env:ProgramFiles/JetBrains/*/plugins/maven-plugin/lib/maven3/bin/mvn.cmd" -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $env:JAVA_HOME -or -not $mavenPath) { throw 'Install Java 26 and Maven, or set JAVA_HOME and add mvn.cmd to PATH.' }
& $mavenPath '-B' 'spring-boot:run' "-Dspring-boot.run.arguments=--server.port=$Port"
exit $LASTEXITCODE
