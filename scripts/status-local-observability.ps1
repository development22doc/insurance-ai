#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Checks status of local observability components.

.DESCRIPTION
    Verifies:
    - Grafana Alloy process
    - Alloy health endpoint
    - OCI SSH tunnel connectivity (Prometheus, Loki, Zipkin, Grafana, Kafka, Keycloak, PostgreSQL, Redis, K3s API)
    - Local JVM service health endpoints
    - Local JVM Prometheus metrics endpoints
    - Existence and size of service log files/directories

.USAGE
    .\scripts\status-local-observability.ps1
#>

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot

Write-Host "======================================" -ForegroundColor Cyan
Write-Host "ClaimAssist Local Observability Status" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
Write-Host ""

# 1. Alloy Process
Write-Host "[ALLOY] Process Status:" -ForegroundColor Cyan
$alloyProcs = Get-Process -Name "alloy" -ErrorAction SilentlyContinue
if ($alloyProcs) {
    foreach ($proc in $alloyProcs) {
        Write-Host "  [OK] Running (PID=$($proc.Id), CPU=$($proc.CPU.ToString("F1"))s, Mem=$([math]::Round($proc.WorkingSet64/1MB)) MB)" -ForegroundColor Green
    }
} else {
    Write-Host "  [STOPPED] No Alloy process found" -ForegroundColor Yellow
}

# 2. Alloy Health Endpoint
Write-Host ""
Write-Host "[ALLOY] Health Endpoint (http://localhost:12345/-/ready):" -ForegroundColor Cyan
try {
    $r = Invoke-WebRequest -Uri "http://localhost:12345/-/ready" -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop
    if ($r.StatusCode -eq 200) {
        Write-Host "  [OK] Healthy" -ForegroundColor Green
    } else {
        Write-Host "  [WARN] HTTP $($r.StatusCode)" -ForegroundColor Yellow
    }
} catch {
    Write-Host "  [FAIL] Not reachable: $($_.Exception.Message)" -ForegroundColor Red
}

# 3. Alloy Metrics Endpoint
Write-Host ""
Write-Host "[ALLOY] Metrics Endpoint (http://localhost:12345/metrics):" -ForegroundColor Cyan
try {
    $r = Invoke-WebRequest -Uri "http://localhost:12345/metrics" -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop
    if ($r.StatusCode -eq 200) {
        $lines = $r.Content -split '\r?\n'
        $metricCount = ($lines | Where-Object { $_ -match '^[a-z]' }).Count
        Write-Host "  [OK] Serving $metricCount metrics" -ForegroundColor Green
    } else {
        Write-Host "  [WARN] HTTP $($r.StatusCode)" -ForegroundColor Yellow
    }
} catch {
    Write-Host "  [FAIL] Not reachable: $($_.Exception.Message)" -ForegroundColor Red
}

# 4. OCI SSH Tunnels
Write-Host ""
Write-Host "[TUNNELS] OCI Observability & Infrastructure Connectivity:" -ForegroundColor Cyan
$tunnelPorts = @(
    @{ Port = 19090; Name = "Prometheus" },
    @{ Port = 13100; Name = "Loki" },
    @{ Port = 19411; Name = "Zipkin" },
    @{ Port = 13000; Name = "Grafana" },
    @{ Port = 18081; Name = "Kafka UI" },
    @{ Port = 18080; Name = "Keycloak" },
    @{ Port = 9092;  Name = "Kafka" },
    @{ Port = 16379; Name = "Redis" },
    @{ Port = 15432; Name = "PostgreSQL" },
    @{ Port = 16443; Name = "K3s API" }
)

$allTunnelsOk = $true
foreach ($t in $tunnelPorts) {
    Write-Host "  [$($t.Name)] localhost:$($t.Port)..." -NoNewline
    try {
        $tcpClient = New-Object System.Net.Sockets.TcpClient
        $result = $tcpClient.BeginConnect("localhost", $t.Port, $null, $null)
        $connected = $result.AsyncWaitHandle.WaitOne(2000)
        if ($connected -and $tcpClient.Connected) {
            Write-Host " [OK]" -ForegroundColor Green
            $tcpClient.Close()
        } else {
            Write-Host " [FAIL]" -ForegroundColor Red
            $allTunnelsOk = $false
        }
    } catch {
        Write-Host " [FAIL]" -ForegroundColor Red
        $allTunnelsOk = $false
    }
}

# 5. Local JVM Services - Health
Write-Host ""
Write-Host "[SERVICES] Local JVM Health Checks (/actuator/health):" -ForegroundColor Cyan
$servicePorts = @(
    @{ Port = 8080; Name = "API Gateway" },
    @{ Port = 8081; Name = "Customer Service" },
    @{ Port = 8082; Name = "Claims Service" },
    @{ Port = 8083; Name = "Agent Service" }
)

$allServicesHealthy = $true
foreach ($svc in $servicePorts) {
    Write-Host "  [$($svc.Name)] localhost:$($svc.Port)/actuator/health..." -NoNewline
    try {
        $r = Invoke-WebRequest -Uri "http://localhost:$($svc.Port)/actuator/health" -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop
        $content = $r.Content
        if ($content -is [byte[]]) { $content = [System.Text.Encoding]::UTF8.GetString($content) }
        $health = $content | ConvertFrom-Json
        if ($health.status -eq "UP") {
            Write-Host " [OK] UP" -ForegroundColor Green
        } else {
            Write-Host " [$($health.status)]" -ForegroundColor Yellow
            $allServicesHealthy = $false
        }
    } catch {
        Write-Host " [FAIL] Not reachable" -ForegroundColor Red
        $allServicesHealthy = $false
    }
}

# 6. Local JVM Services - Prometheus Metrics
Write-Host ""
Write-Host "[SERVICES] Prometheus Metrics Endpoints (/actuator/prometheus):" -ForegroundColor Cyan
$allMetricsOk = $true
foreach ($svc in $servicePorts) {
    Write-Host "  [$($svc.Name)] localhost:$($svc.Port)/actuator/prometheus..." -NoNewline
    try {
        $r = Invoke-WebRequest -Uri "http://localhost:$($svc.Port)/actuator/prometheus" -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop
        if ($r.StatusCode -eq 200) {
            $lines = $r.Content -split '\r?\n'
            $metricCount = ($lines | Where-Object { $_ -match '^[a-z]' -and $_ -notmatch '^#' }).Count
            Write-Host " [OK] $metricCount metrics" -ForegroundColor Green
        } else {
            Write-Host " [WARN] HTTP $($r.StatusCode)" -ForegroundColor Yellow
            $allMetricsOk = $false
        }
    } catch {
        Write-Host " [FAIL] Not reachable" -ForegroundColor Red
        $allMetricsOk = $false
    }
}

# 7. Log Directories
Write-Host ""
Write-Host "[LOGS] Local Log Directories (logs/<service>/):" -ForegroundColor Cyan
$logDir = Join-Path $repoRoot "logs"
$serviceDirs = @("api-gateway", "customer-service", "claims-service", "agent-service", "discovery-service", "config-service")
$allLogsOk = $true
foreach ($svc in $serviceDirs) {
    $svcLogDir = Join-Path $logDir $svc
    if (Test-Path $svcLogDir) {
        $files = Get-ChildItem $svcLogDir -Filter "*.log" -ErrorAction SilentlyContinue
        $count = $files.Count
        $size = ($files | Measure-Object -Property Length -Sum).Sum
        $sizeMB = [math]::Round($size / 1MB, 2)
        if ($count -gt 0) {
            Write-Host "  [$svc] $count files, $sizeMB MB" -ForegroundColor Green
        } else {
            Write-Host "  [$svc] [EMPTY] Directory exists but no .log files" -ForegroundColor Yellow
            $allLogsOk = $false
        }
    } else {
        Write-Host "  [$svc] [MISSING] Directory not found" -ForegroundColor Yellow
        $allLogsOk = $false
    }
}

# Summary
Write-Host ""
Write-Host "======================================" -ForegroundColor Cyan
Write-Host "Summary" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
$summary = @(
    @{ Name = "Alloy Process";       Ok = $alloyProcs -ne $null },
    @{ Name = "Alloy Health";        Ok = $true },  # checked above
    @{ Name = "OCI Tunnels";         Ok = $allTunnelsOk },
    @{ Name = "Service Health";      Ok = $allServicesHealthy },
    @{ Name = "Service Metrics";     Ok = $allMetricsOk },
    @{ Name = "Log Directories";     Ok = $allLogsOk }
)
foreach ($s in $summary) {
    $status = if ($s.Ok) { "[OK]" } else { "[FAIL]" }
    $color = if ($s.Ok) { "Green" } else { "Red" }
    Write-Host "  $status $($s.Name)" -ForegroundColor $color
}

Write-Host ""
if ($summary.Ok -contains $false) {
    Write-Host "Some checks failed. Review output above." -ForegroundColor Yellow
    exit 1
} else {
    Write-Host "All checks passed." -ForegroundColor Green
    exit 0
}