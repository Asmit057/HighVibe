<#
  Stops the services started by start-services.ps1 (and their child processes,
  including running preview dev servers). Infrastructure keeps running: docker compose stop
#>
$root = Split-Path -Parent $PSScriptRoot
$pidFile = "$root\runtime\services.pids"
if (-not (Test-Path $pidFile)) { Write-Host 'Nothing to stop.'; exit 0 }

Get-Content $pidFile | Where-Object { $_ -match '^\d+$' } | ForEach-Object {
    # /T also ends child processes (npm -> node, preview dev servers)
    taskkill /PID $_ /T /F 2>$null | Out-Null
}
Remove-Item $pidFile -Force
Write-Host 'HighVibe services stopped.'
