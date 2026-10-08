$ErrorActionPreference='Stop'
$log=Join-Path $PSScriptRoot '../sql-tcp-setup.log'
try {
 $tcpPath='HKLM:/SOFTWARE/Microsoft/Microsoft SQL Server/MSSQL16.SQLEXPRESS/MSSQLServer/SuperSocketNetLib/Tcp'
 $loopbacks=Get-ChildItem -LiteralPath $tcpPath | Where-Object {(Get-ItemProperty -LiteralPath $_.PSPath).IpAddress -in @('127.0.0.1','::1')}
 if(-not $loopbacks){throw 'No loopback SQL Server addresses found.'}
 Set-ItemProperty -LiteralPath $tcpPath -Name ListenOnAllIPs -Value 0
 foreach($ip in $loopbacks){
  Set-ItemProperty -LiteralPath $ip.PSPath -Name TcpDynamicPorts -Value ''
  Set-ItemProperty -LiteralPath $ip.PSPath -Name TcpPort -Value '1433'
  Set-ItemProperty -LiteralPath $ip.PSPath -Name Enabled -Value 1
 }
 Set-ItemProperty -LiteralPath $tcpPath -Name Enabled -Value 1
 Restart-Service -Name 'MSSQL$SQLEXPRESS'
 'SUCCESS: SQL Express restarted with localhost TCP port 1433.' | Set-Content -LiteralPath $log
} catch {
 ('FAILED: '+$_.Exception.Message) | Set-Content -LiteralPath $log
 exit 1
}
