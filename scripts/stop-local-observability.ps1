#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Stops Grafana Alloy local observability collector.

.DESCRIPTION
    Finds and stops the running Alloy process started by start-local-observability.ps1.

.USAGE
    .\scripts\stop-local-observability.ps1
#>

$ErrorActionPreference = "Stop"

Write-Host "======================================" -ForegroundColor Cyan
Write-Host "Stopping Local Observability (Alloy)" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
Write-Host ""

# Find Alloy processes
$alloyProcs = Get-Process -Name "alloy" -ErrorAction SilentlyContinue
if (-not $alloyProcs) {
    Write-Host "[INFO] No Alloy processes found" -ForegroundColor Yellow
    exit 0
}

foreach ($proc in $alloyProcs) {
    Write-Host "Stopping Alloy (PID=$($proc.Id))..." -ForegroundColor Yellow
    try {
        Stop-Process -Id $proc.Id -Force -ErrorAction Stop
        Write-Host "[OK] Stopped PID=$($proc.Id)" -ForegroundColor Green
    } catch {
        Write-Host "[ERROR] Failed to stop PID=$($proc.Id): $_" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "[OK] All Alloy processes stopped" -ForegroundColor Green
