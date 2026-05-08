$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
$metricsDir = Join-Path $repoRoot "Native\metrics"
$exePath = Join-Path $repoRoot "Native\build\system_metrics.exe"
$pidFile = Join-Path $env:TEMP "wasp-metrics-loop.pid"

if (-not (Test-Path $exePath)) {
    throw "Missing metrics executable: $exePath. Build Native first."
}

if (-not (Test-Path $metricsDir)) {
    New-Item -ItemType Directory -Path $metricsDir -Force | Out-Null
}

function Test-ProcessRunning([int]$processId) {
    try {
        Get-Process -Id $processId -ErrorAction Stop | Out-Null
        return $true
    } catch {
        return $false
    }
}

if (Test-Path $pidFile) {
    $raw = Get-Content $pidFile -ErrorAction SilentlyContinue | Select-Object -First 1
    $existingPid = $raw -as [int]
    if ($null -ne $existingPid -and (Test-ProcessRunning $existingPid)) {
        Write-Host "Metrics loop is already running (PID $existingPid)."
        exit 0
    }
    Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
}

$proc = Start-Process $exePath `
    -ArgumentList @("loop") `
    -WorkingDirectory $metricsDir `
    -WindowStyle Hidden `
    -PassThru

Set-Content -Path $pidFile -Value $proc.Id -Encoding ASCII
Write-Host "Started metrics loop (PID $($proc.Id))."
Write-Host "Writing to: $metricsDir\system_metrics_output.json"
