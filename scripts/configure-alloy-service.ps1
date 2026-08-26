#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Configures the Alloy Windows service to use the ClaimAssist project config.

.DESCRIPTION
    Idempotent: inspects the current service ImagePath and only changes it when
    the project config is not already active.  Uses direct registry writes to
    avoid sc.exe quoting issues with paths containing spaces.

    Must run as Administrator (writes to HKLM:\SYSTEM\CurrentControlSet\Services\Alloy).

.EXAMPLE
    .\scripts\configure-alloy-service.ps1

.NOTES
    Idempotent — safe to run multiple times without disrupting a healthy Alloy process.
#>

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$projectConfig = Join-Path $repoRoot "infrastructure\monitoring\alloy\config.alloy"
$storagePath   = "C:\ProgramData\GrafanaLabs\Alloy\data"
$alloyExe      = "C:\Program Files\GrafanaLabs\Alloy\alloy-service-windows-amd64.exe"
$regPath       = "HKLM:\SYSTEM\CurrentControlSet\Services\Alloy"

# ------------------------------------------------------------------
# 1. Pre-flight checks
# ------------------------------------------------------------------
Write-Host "[ALLOY] Checking prerequisites..." -ForegroundColor Cyan

# Admin check
$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Host "[ALLOY] [ERROR] Administrator privileges required to configure Alloy service." -ForegroundColor Red
    Write-Host "" -ForegroundColor Red
    Write-Host "  Run this PowerShell as Administrator:" -ForegroundColor Yellow
    Write-Host "    .\scripts\configure-alloy-service.ps1" -ForegroundColor Yellow
    Write-Host "" -ForegroundColor Yellow
    exit 1
}

# Alloy binary
if (-not (Test-Path $alloyExe)) {
    Write-Host "[ALLOY] [ERROR] Alloy binary not found: $alloyExe" -ForegroundColor Red
    Write-Host "        Install Grafana Alloy first: https://grafana.com/docs/alloy/latest/set-up/install/windows/" -ForegroundColor Yellow
    exit 1
}
Write-Host "[ALLOY] [OK] Alloy installed" -ForegroundColor Green

# Project config file
if (-not (Test-Path $projectConfig)) {
    Write-Host "[ALLOY] [ERROR] Project config not found: $projectConfig" -ForegroundColor Red
    exit 1
}

# Service exists
$svc = Get-Service -Name Alloy -ErrorAction SilentlyContinue
if (-not $svc) {
    Write-Host "[ALLOY] [ERROR] Windows service 'Alloy' not found.  Reinstall Alloy." -ForegroundColor Red
    exit 1
}

# ------------------------------------------------------------------
# 2. Inspect current ImagePath
# ------------------------------------------------------------------
Write-Host "[ALLOY] Inspecting current service configuration..." -ForegroundColor Cyan

$desiredImagePath = "`"$alloyExe`" run `"$projectConfig`" --storage.path=`"$storagePath`""

$currentImagePath = ""
try {
    $currentImagePath = (Get-ItemProperty -Path $regPath -Name ImagePath -ErrorAction Stop).ImagePath
} catch {
    Write-Host "[ALLOY] [WARN] Could not read ImagePath from registry: $_" -ForegroundColor Yellow
}

$hasExe      = $currentImagePath -match "alloy-service-windows-amd64\.exe"
$hasConfig   = $currentImagePath -match [regex]::Escape($projectConfig)
$hasStorage  = $currentImagePath -match "\-\-storage\.path"
$isCorrect   = $hasExe -and $hasConfig -and $hasStorage

if ($isCorrect) {
    Write-Host "[ALLOY] [OK] Project configuration active" -ForegroundColor Green
} else {
    Write-Host "[ALLOY] [INFO] Current ImagePath: $currentImagePath" -ForegroundColor Gray
    Write-Host "[ALLOY] [INFO] Desired  ImagePath: $desiredImagePath" -ForegroundColor Gray
}

# ------------------------------------------------------------------
# 3. Apply configuration if needed (idempotent: skip when already correct)
# ------------------------------------------------------------------
if (-not $isCorrect) {
    Write-Host "[ALLOY] Configuring service (registry write)..." -ForegroundColor Cyan

    # Must stop before changing ImagePath
    if ($svc.Status -ne "Stopped") {
        Write-Host "[ALLOY] Stopping Alloy service..." -ForegroundColor Yellow
        Stop-Service -Name Alloy -Force -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 3

        $svc = Get-Service -Name Alloy -ErrorAction SilentlyContinue
        if ($svc -and $svc.Status -ne "Stopped") {
            Write-Host "[ALLOY] Force-stopping Alloy processes..." -ForegroundColor Yellow
            taskkill /F /IM alloy-windows-amd64.exe 2>$null
            taskkill /F /IM alloy-service-windows-amd64.exe 2>$null
            Start-Sleep -Seconds 3
        }
    }

    # Write via registry — avoids sc.exe quoting issues with paths containing spaces
    try {
        Set-ItemProperty -Path $regPath -Name "ImagePath" -Value $desiredImagePath -ErrorAction Stop
    } catch {
        Write-Host "[ALLOY] [ERROR] Failed to write registry: $_" -ForegroundColor Red
        Write-Host "        Ensure you are running as Administrator." -ForegroundColor Yellow
        exit 1
    }

    # Verify write
    $verifyPath = (Get-ItemProperty -Path $regPath -Name ImagePath -ErrorAction SilentlyContinue).ImagePath
    if ($verifyPath -ne $desiredImagePath) {
        Write-Host "[ALLOY] [ERROR] Registry write verification failed." -ForegroundColor Red
        Write-Host "        Wrote:  $desiredImagePath" -ForegroundColor Gray
        Write-Host "        Read:   $verifyPath" -ForegroundColor Gray
        exit 1
    }
    Write-Host "[ALLOY] [OK] Project configuration active" -ForegroundColor Green

    # Start service
    Write-Host "[ALLOY] Starting Alloy service..." -ForegroundColor Cyan
    Start-Service -Name Alloy -ErrorAction Stop

    # Wait for Running state (up to 30 s)
    $started = $false
    for ($i = 1; $i -le 10; $i++) {
        Start-Sleep -Seconds 3
        $svc = Get-Service -Name Alloy -ErrorAction SilentlyContinue
        if ($svc -and $svc.Status -eq "Running") { $started = $true; break }
    }
    if (-not $started) {
        Write-Host "[ALLOY] [ERROR] Alloy service did not start (status: $($svc.Status))." -ForegroundColor Red
        exit 1
    }
}

# ------------------------------------------------------------------
# 4. Verify service is Running (always, even when idempotent)
# ------------------------------------------------------------------
$svc = Get-Service -Name Alloy
if ($svc.Status -ne "Running") {
    Write-Host "[ALLOY] Alloy service not running — starting..." -ForegroundColor Yellow
    Start-Service -Name Alloy -ErrorAction Stop
    Start-Sleep -Seconds 5
    $svc = Get-Service -Name Alloy
}
Write-Host "[ALLOY] [OK] Alloy service running" -ForegroundColor Green

# ------------------------------------------------------------------
# 5. Verify process uses project config
# ------------------------------------------------------------------
$alloyProcesses = Get-CimInstance Win32_Process |
    Where-Object { $_.Name -match "alloy" } |
    Select-Object ProcessId, Name, CommandLine

$projectConfigFound = $false
foreach ($p in $alloyProcesses) {
    if ($p.CommandLine -and $p.CommandLine -match [regex]::Escape($projectConfig)) {
        $projectConfigFound = $true
    }
}

if ($projectConfigFound) {
    Write-Host "[ALLOY] [OK] Alloy process uses project config" -ForegroundColor Green
} else {
    Write-Host "[ALLOY] [WARN] Could not confirm project config in process command line" -ForegroundColor Yellow
}

# ------------------------------------------------------------------
# 6. Verify health endpoint
# ------------------------------------------------------------------
$healthOk = $false
try {
    $r = Invoke-WebRequest -Uri "http://localhost:12345/-/ready" -UseBasicParsing -TimeoutSec 5
    if ($r.StatusCode -eq 200) {
        $healthOk = $true
        Write-Host "[ALLOY] [OK] Alloy health check passed" -ForegroundColor Green
    }
} catch {
    Write-Host "[ALLOY] [WARN] Alloy health endpoint not reachable: $_" -ForegroundColor Yellow
}

if (-not $healthOk) {
    Write-Host "[ALLOY] Alloy may still be starting — health endpoint not yet responding." -ForegroundColor Yellow
}

Write-Host ""
