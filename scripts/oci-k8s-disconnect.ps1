#!/usr/bin/env pwsh

param(
    [string]$OciHost = $env:OCI_HOST,
    [string]$OciUser = $env:OCI_USER
)

$ErrorActionPreference = "Continue"

if (-not $OciHost) { $OciHost = "144.24.116.166" }
if (-not $OciUser) { $OciUser = "opc" }

Write-Host "======================================" -ForegroundColor Cyan
Write-Host "OCI K3s Developer Disconnection" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
Write-Host ""

$ports = @(15432, 16379, 9092, 18080, 18081, 13000, 19090, 19411, 13100, 16443)

$portNames = @{
    15432 = "PostgreSQL"
    16379 = "Redis"
    9092  = "Kafka"
    18080 = "Keycloak"
    18081 = "Kafka-UI"
    13000 = "Grafana"
    19090 = "Prometheus"
    19411 = "Zipkin"
    13100 = "Loki"
    16443 = "K3s-API"
}

# ============================================================
# CLAIMASSIST LOCAL JVM SERVICE CLEANUP
# ============================================================

Write-Host ""
Write-Host "[LOCAL-JVM] Stopping ClaimAssist local JVM services..." -ForegroundColor Cyan

$claimAssistPorts = @(
    8080, # API Gateway
    8081, # Customer Service
    8082, # Claims Service
    8083, # Agent Service
    8761, # Discovery Service
    8888  # Config Service
)

foreach ($port in $claimAssistPorts) {

    $listeners = Get-NetTCPConnection `
        -LocalPort $port `
        -State Listen `
        -ErrorAction SilentlyContinue

    foreach ($listener in $listeners) {

        $processId = $listener.OwningProcess

        $process = Get-CimInstance Win32_Process `
            -Filter "ProcessId = $processId" `
            -ErrorAction SilentlyContinue

        if ($process -and $process.Name -eq "java.exe") {

            Write-Host "  [STOP] ClaimAssist Java PID=$processId PORT=$port" -ForegroundColor Yellow

            Stop-Process `
                -Id $processId `
                -Force `
                -ErrorAction SilentlyContinue
        }
    }
}

Start-Sleep -Seconds 1

$remainingJvmPorts = Get-NetTCPConnection `
    -LocalPort 8080,8081,8082,8083,8761,8888 `
    -State Listen `
    -ErrorAction SilentlyContinue

if ($remainingJvmPorts) {
    Write-Host "[LOCAL-JVM] Remaining listeners:" -ForegroundColor Yellow

    $remainingJvmPorts |
        Select-Object LocalPort,OwningProcess |
        Format-Table -AutoSize
}
else {
    Write-Host "[LOCAL-JVM] All ClaimAssist JVM service ports are free." -ForegroundColor Green
}

Write-Host ""
Write-Host "[TUNNELS] Stopping SSH tunnels..." -ForegroundColor Cyan

foreach ($port in $ports) {
    $name = $portNames[$port]

    Write-Host "  [$name] Checking port $port..." -NoNewline

    try {
        $connections = @(Get-NetTCPConnection `
            -LocalPort $port `
            -State Listen `
            -ErrorAction SilentlyContinue)

        if ($connections.Count -gt 0) {
            foreach ($conn in $connections) {
                $pidToStop = $conn.OwningProcess

                if ($pidToStop -and $pidToStop -ne 0) {
                    $proc = Get-Process `
                        -Id $pidToStop `
                        -ErrorAction SilentlyContinue

                    if ($proc) {
                        Stop-Process `
                            -Id $pidToStop `
                            -Force `
                            -ErrorAction SilentlyContinue

                        Write-Host " stopped PID $pidToStop ($($proc.ProcessName))" -ForegroundColor Green
                    }
                }
            }
        }
        else {
            Write-Host " not listening" -ForegroundColor Cyan
        }
    }
    catch {
        Write-Host " error: $($_.Exception.Message)" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host ""
Write-Host ""
Write-Host "[OCI-PORT-FORWARDS] Cleaning up ClaimAssist OCI port-forwards..." -ForegroundColor Cyan

$cleanupOciHost = if ($env:OCI_HOST) { $env:OCI_HOST } else { "144.24.116.166" }
$cleanupOciUser = if ($env:OCI_USER) { $env:OCI_USER } else { "opc" }
$cleanupSshKey = if ($env:SSH_KEY) { $env:SSH_KEY } else { "$env:USERPROFILE\.ssh\claimassist-oci-dev" }

if (-not (Test-Path $cleanupSshKey)) {
    Write-Host "[WARN] SSH key not found: $cleanupSshKey" -ForegroundColor Yellow
}
else {

    $remoteCleanup = @"
for port in 15432 16379 9092 18080 18081 13000 19090 19411 13100; do
    pids=$(ss -lntp 2>/dev/null | grep ":${port} " | sed -n 's/.*pid=\([0-9]*\).*/\1/p' | sort -u)

    for pid in $pids; do
        if [ -n "$pid" ]; then
            echo "[STOP] Port $port -> PID $pid"
            kill "$pid" 2>/dev/null || true
        fi
    done
done

sleep 2

echo "[VERIFY] Remaining dependency listeners:"
ss -lntp 2>/dev/null | grep -E ':15432|:16379|:9092|:18080|:18081|:13000|:19090|:19411|:13100' || true
"@

    & ssh.exe `
        -i $cleanupSshKey `
        -o IdentitiesOnly=yes `
        -o BatchMode=yes `
        -o ConnectTimeout=10 `
        "$cleanupOciUser@$cleanupOciHost" `
        $remoteCleanup

    if ($LASTEXITCODE -eq 0) {
        Write-Host "[OCI-PORT-FORWARDS] Cleanup completed." -ForegroundColor Green
    }
    else {
        Write-Host "[OCI-PORT-FORWARDS] Cleanup SSH returned exit code $LASTEXITCODE." -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "[HOSTS] Cleaning up Kafka hostname..." -ForegroundColor Cyan

$hostsPath = "C:\Windows\System32\drivers\etc\hosts"
$kafkaEntry = "127.0.0.1 claimassist-kafka"
$marker = "# CLAIMASSIST-OCI-K8S-DEV"

try {
    $currentUser = [System.Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object System.Security.Principal.WindowsPrincipal($currentUser)

    $isAdmin = $principal.IsInRole(
        [System.Security.Principal.WindowsBuiltInRole]::Administrator
    )

    if (-not $isAdmin) {
        Write-Host "  [WARN] Not running as Administrator - hosts file not changed" -ForegroundColor Yellow
    }
    else {
        $hostsContent = @(Get-Content $hostsPath -ErrorAction SilentlyContinue)

        $newContent = @()

        foreach ($line in $hostsContent) {
            if ($line -eq $marker -or $line -eq $kafkaEntry) {
                continue
            }

            $newContent += $line
        }

        $newContent | Set-Content `
            -Path $hostsPath `
            -Encoding UTF8 `
            -ErrorAction Stop

        Write-Host "  [OK] Kafka hosts entry cleaned" -ForegroundColor Green
    }
}
catch {
    Write-Host "  [WARN] Could not modify hosts file: $($_.Exception.Message)" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "[SSH PROCESSES] Checking remaining SSH processes..." -ForegroundColor Cyan

try {
    $sshProcs = Get-Process ssh -ErrorAction SilentlyContinue

    if ($sshProcs) {
        Write-Host "  SSH processes currently running: $($sshProcs.Count)" -ForegroundColor Cyan
        Write-Host "  Manual SSH sessions are not terminated." -ForegroundColor Cyan
    }
    else {
        Write-Host "  No SSH processes found." -ForegroundColor Cyan
    }
}
catch {
    Write-Host "  Could not enumerate SSH processes." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "======================================" -ForegroundColor Cyan
Write-Host "Disconnection Complete" -ForegroundColor Green
Write-Host "======================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "To reconnect:" -ForegroundColor Cyan
Write-Host "  .\scripts\oci-k8s-connect.ps1" -ForegroundColor Yellow
Write-Host ""