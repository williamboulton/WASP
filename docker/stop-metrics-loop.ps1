$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$pidFile = Join-Path $env:TEMP "wasp-metrics-loop.pid"

if (-not (Test-Path $pidFile)) {
    Write-Host "No PID file found. Metrics loop is likely not running."
    exit 0
}

$raw = Get-Content $pidFile -ErrorAction SilentlyContinue | Select-Object -First 1
$loopPid = $raw -as [int]

if ($null -eq $loopPid) {
    Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
    Write-Host "PID file was invalid. Cleaned up."
    exit 0
}

try {
    Stop-Process -Id $loopPid -Force -ErrorAction Stop
    Write-Host "Stopped metrics loop (PID $loopPid)."
} catch {
    Write-Host "Process $loopPid was not running."
}

Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
