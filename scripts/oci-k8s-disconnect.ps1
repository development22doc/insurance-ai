#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Disconnects OCI K3s developer tunnels and cleans up.

.DESCRIPTION
    Stops SSH tunnel processes and removes the Kafka hostname entry from
    the Windows hosts file.

.EXAMPLE
    .\scripts\oci-k8s-disconnect.ps1
#>

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

# Tunnel ports to close
$ports = @(15432, 16379, 9092, 18080)
$portNames = @{ 15432 = "PostgreSQL"; 16379 = "Redis"; 9092 = "Kafka"; 18080 = "Keycloak" }

Write-Host "[TUNNELS] Stopping SSH tunnels..." -ForegroundColor Cyan

foreach ($port in $ports) {
    $name = $portNames[$port]
    Write-Host "  [$name] Checking port $port..." -NoNewline

    try {
        $conn = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
        if ($null -ne $conn) {
            $proc = Get-Process -Id $conn.OwningProcess -ErrorAction SilentlyContinue
            if ($null -ne $proc) {
                Stop-Process -Force -Id $proc.Id -ErrorAction SilentlyContinue
                Write-Host " stopped ($($proc.ProcessName))" -ForegroundColor Green
            } else {
                Write-Host " port in use but process not found" -ForegroundColor Yellow
            }
        } else {
            Write-Host " not listening" -ForegroundColor Cyan
        }
    } catch {
        Write-Host " error: $_" -ForegroundColor Yellow
    }
}

Write-Host ""

# Remove Kafka hosts entry
Write-Host "[HOSTS] Cleaning up Kafka hostname..." -ForegroundColor Cyan

$hostsPath = "C:\Windows\System32\drivers\etc\hosts"
$kafkaEntry = "127.0.0.1 claimassist-kafka"
$marker = "# CLAIMASSIST-OCI-K8S-DEV"

try {
    # Check if we have admin rights
    $currentUser = [System.Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object System.Security.Principal.WindowsPrincipal($currentUser)
    $isAdmin = $principal.IsInRole([System.Security.Principal.WindowsBuiltInRole]::Administrator)

    if (-not $isAdmin) {
        Write-Host "  [WARN] Not running as Administrator - cannot modify hosts file" -ForegroundColor Yellow
    } else {
        # Read current hosts file
        $hostsContent = @(Get-Content $hostsPath -ErrorAction SilentlyContinue)

        # Find and remove entries
        $newContent = @()
        $removed = $false
        $i = 0

        while ($i -lt $hostsContent.Count) {
            $line = $hostsContent[$i]

            # Skip marker comment and the next line (entry)
            if ($line -match [regex]::Escape($marker)) {
                Write-Host "  Removing marker comment" -ForegroundColor Green
                $removed = $true
                # Skip the marker and next line (the entry)
                $i++
                if ($i -lt $hostsContent.Count) {
                    Write-Host "  Removing Kafka entry: $($hostsContent[$i])" -ForegroundColor Green
                    $i++
                }
            } else {
                $newContent += $line
                $i++
            }
        }

        if ($removed) {
            # Write back the filtered content
            $newContent | Set-Content $hostsPath -Encoding UTF8 -ErrorAction Stop
            Write-Host "  ✓ Hosts file cleaned" -ForegroundColor Green
        } else {
            Write-Host "  No marked entries found" -ForegroundColor Cyan
        }
    }
} catch {
    Write-Host "  [WARN] Could not modify hosts file: $_" -ForegroundColor Yellow
}

Write-Host ""

# List any remaining SSH processes connected to OCI host
Write-Host "[SSH PROCESSES]" -ForegroundColor Cyan
try {
    $sshProcs = Get-Process ssh -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -match [regex]::Escape($OciHost) }
    if ($sshProcs) {
        Write-Host "  Found $($sshProcs.Count) SSH process(es) to $OciHost" -ForegroundColor Cyan
        foreach ($proc in $sshProcs) {
            Write-Host "    PID $($proc.Id): $($proc.ProcessName)" -ForegroundColor Cyan
        }
        Write-Host "  Note: These were not terminated to preserve manual SSH sessions" -ForegroundColor Cyan
    } else {
        Write-Host "  No SSH processes found for $OciHost" -ForegroundColor Cyan
    }
} catch {
    Write-Host "  Could not enumerate SSH processes" -ForegroundColor Yellow
}

Write-Host ""

Write-Host "======================================" -ForegroundColor Cyan
Write-Host "Disconnection Complete" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "To reconnect:" -ForegroundColor Cyan
Write-Host "  .\scripts\oci-k8s-connect.ps1" -ForegroundColor Yellow
Write-Host ""

