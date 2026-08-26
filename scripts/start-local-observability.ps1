#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Starts Grafana Alloy for local IntelliJ observability collection.

.DESCRIPTION
    Launches Grafana Alloy as a local telemetry collector that:
    - Scrapes Prometheus metrics from local JVM services (ports 8080-8083)
    - Collects structured JSON logs from local log files (logs/<service>/)
    - Forwards metrics to OCI Prometheus (via SSH tunnel localhost:19090)
    - Forwards logs to OCI Loki (via SSH tunnel localhost:13100)
    - Traces are sent directly by applications to Zipkin (localhost:19411)

    Prerequisites:
    - Grafana Alloy installed on Windows (https://grafana.com/docs/alloy/latest/set-up/install/windows/)
    - OCI SSH tunnels established (run scripts/oci-k8s-connect.ps1 first)
    - Local JVM services running (run scripts/start-services-local.ps1)

.USAGE
    .\scripts\start-local-observability.ps1

.PARAMETER AlloyPath
    Path to Alloy executable. Defaults to 'alloy' in PATH.

.PARAMETER ConfigPath
    Path to Alloy config file. Defaults to infrastructure/monitoring/alloy/config.alloy

.EXAMPLE
    .\scripts\start-local-observability.ps1

.EXAMPLE
    .\scripts\start-local-observability.ps1 -AlloyPath "C:\Program Files\Grafana Alloy\alloy.exe"
#>

param(
    [string]$AlloyPath = "alloy",
    [string]$ConfigPath = "infrastructure/monitoring/alloy/config.alloy"
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$configFile = Join-Path $repoRoot $ConfigPath
$logDir = Join-Path $repoRoot "logs"
$alloyLogDir = Join-Path $logDir "alloy"
New-Item -ItemType Directory -Force -Path $alloyLogDir | Out-Null

Write-Host "======================================" -ForegroundColor Cyan
Write-Host "ClaimAssist Local Observability Startup" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
Write-Host ""

# Check if Alloy is available
Write-Host "[PREREQ] Checking Grafana Alloy installation..." -ForegroundColor Yellow
$alloyCmd = Get-Command $AlloyPath -ErrorAction SilentlyContinue
if (-not $alloyCmd) {
    Write-Host "[ERROR] Grafana Alloy not found at: $AlloyPath" -ForegroundColor Red
    Write-Host "        Install from: https://grafana.com/docs/alloy/latest/set-up/install/windows/" -ForegroundColor Yellow
    Write-Host "        Or add to PATH and re-run." -ForegroundColor Yellow
    exit 1
}
Write-Host "[OK] Alloy found: $($alloyCmd.Source)" -ForegroundColor Green

# Check config file
if (-not (Test-Path $configFile)) {
    Write-Host "[ERROR] Alloy config not found: $configFile" -ForegroundColor Red
    exit 1
}
Write-Host "[OK] Config file: $configFile" -ForegroundColor Green

# Check OCI tunnels
Write-Host "[TUNNELS] Verifying OCI observability tunnels..." -ForegroundColor Yellow
$tunnelPorts = @(
    @{ Port = 19090; Name = "Prometheus" },
    @{ Port = 13100; Name = "Loki" },
    @{ Port = 19411; Name = "Zipkin" }
)

$allTunnelsOk = $true
foreach ($t in $tunnelPorts) {
    Write-Host "  [$($t.Name)] Checking localhost:$($t.Port)..." -NoNewline
    try {
        $tcpClient = New-Object System.Net.Sockets.TcpClient
        $result = $tcpClient.BeginConnect("localhost", $t.Port, $null, $null)
        $connected = $result.AsyncWaitHandle.WaitOne(3000)
        if ($connected -and $tcpClient.Connected) {
            Write-Host " [OK]" -ForegroundColor Green
            $tcpClient.Close()
        } else {
            Write-Host " [WARN] Not reachable (tunnels may not be up)" -ForegroundColor Yellow
            # Don't fail - Alloy will retry
        }
    } catch {
        Write-Host " [WARN] Connection failed (tunnels may not be up)" -ForegroundColor Yellow
    }
}

# Create log directories for services
$serviceLogDirs = @("api-gateway", "customer-service", "claims-service", "agent-service", "discovery-service", "config-service")
foreach ($svc in $serviceLogDirs) {
    $svcLogDir = Join-Path $logDir $svc
    if (-not (Test-Path $svcLogDir)) {
        New-Item -ItemType Directory -Force -Path $svcLogDir | Out-Null
    }
}

# Start Alloy
Write-Host ""
Write-Host "[START] Launching Grafana Alloy..." -ForegroundColor Cyan

$alloyLogFile = Join-Path $alloyLogDir "alloy.log"
$alloyErrFile = Join-Path $alloyLogDir "alloy.err.log"

$proc = Start-Process -FilePath $alloyCmd.Source -ArgumentList "run", $configFile `
    -WorkingDirectory $repoRoot `
    -RedirectStandardOutput $alloyLogFile `
    -RedirectStandardError $alloyErrFile `
    -PassThru -WindowStyle Hidden

Write-Host "[OK] Alloy started with PID=$($proc.Id)" -ForegroundColor Green
Write-Host "     Logs: $alloyLogFile" -ForegroundColor Gray
Write-Host "     Errors: $alloyErrFile" -ForegroundColor Gray

# Wait a moment for Alloy to start
Start-Sleep -Seconds 3

# Check if Alloy is still running
if ($proc.HasExited) {
    Write-Host "[ERROR] Alloy process exited unexpectedly (code=$($proc.ExitCode))" -ForegroundColor Red
    Write-Host "--- Alloy stdout ---"
    Get-Content $alloyLogFile -Tail 50
    Write-Host "--- Alloy stderr ---"
    Get-Content $alloyErrFile -Tail 50
    exit 1
}

# Test Alloy health endpoint
Write-Host "[HEALTH] Checking Alloy health endpoint (http://localhost:12345/-/ready)..." -NoNewline
$alloyHealthy = $false
for ($i = 1; $i -le 10; $i++) {
    try {
        $r = Invoke-WebRequest -Uri "http://localhost:12345/-/ready" -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
        if ($r.StatusCode -eq 200) {
            $alloyHealthy = $true
            break
        }
    } catch {}
    Start-Sleep -Seconds 1
}

if ($alloyHealthy) {
    Write-Host " [OK]" -ForegroundColor Green
} else {
    Write-Host " [WARN] Health check timeout (Alloy may still be starting)" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "======================================" -ForegroundColor Cyan
Write-Host "Local Observability Running" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Alloy Endpoints:" -ForegroundColor Cyan
Write-Host "  Health:     http://localhost:12345/-/ready" -ForegroundColor Yellow
Write-Host "  Metrics:    http://localhost:12345/metrics" -ForegroundColor Yellow
Write-Host "  Pprof:      http://localhost:12345/debug/pprof/" -ForegroundColor Yellow
Write-Host ""
Write-Host "Forwarding to OCI (via SSH tunnels):" -ForegroundColor Cyan
Write-Host "  Metrics -> Prometheus: localhost:19090/api/v1/write" -ForegroundColor Yellow
Write-Host "  Logs    -> Loki:       localhost:13100/loki/api/v1/push" -ForegroundColor Yellow
Write-Host "  Traces  -> Zipkin:     localhost:19411/api/v2/spans (direct from apps)" -ForegroundColor Yellow
Write-Host ""
Write-Host "Grafana (via tunnel): http://localhost:13000" -ForegroundColor Yellow
Write-Host ""
Write-Host "To stop: Press Ctrl+C or run .\scripts\stop-local-observability.ps1" -ForegroundColor Cyan
Write-Host ""

# Keep script running and monitor Alloy process
Write-Host "Monitoring Alloy process (PID=$($proc.Id))... Press Ctrl+C to stop." -ForegroundColor Cyan
try {
    while (-not $proc.HasExited) {
        Start-Sleep -Seconds 5
    }
    Write-Host "[WARN] Alloy process exited (code=$($proc.ExitCode))" -ForegroundColor Yellow
} catch {
    # Ctrl+C pressed
}

# Cleanup on exit
Write-Host "[SHUTDOWN] Stopping Alloy..." -ForegroundColor Yellow
if (-not $proc.HasExited) {
    Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
}
Write-Host "[SHUTDOWN] Alloy stopped" -ForegroundColor Green
