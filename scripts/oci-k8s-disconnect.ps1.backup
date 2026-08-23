#!/usr/bin/env pwsh

param(
    [string]$OciHost = $env:OCI_HOST,
    [string]$OciUser = $env:OCI_USER
)

$ErrorActionPreference = "Continue"

if (-not $OciHost) { $OciHost = "oci-vm" }
if (-not $OciUser) { $OciUser = "ubuntu" }

Write-Host "======================================" -ForegroundColor Cyan
Write-Host "OCI K3s Developer Disconnection" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
Write-Host ""

$ports = @(15432, 16379, 9092, 18080, 16443)

$portNames = @{
    15432 = "PostgreSQL"
    16379 = "Redis"
    9092  = "Kafka"
    18080 = "Keycloak"
    16443 = "K3s-API"
}

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
