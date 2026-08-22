#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Shows the status of OCI K3s developer tunnels and connectivity.

.DESCRIPTION
    Reports on:
    - SSH tunnel status (localhost ports)
    - TCP connectivity to each dependency
    - Kafka hostname resolution
    - OCI SSH availability

.EXAMPLE
    .\scripts\oci-k8s-status.ps1
#>

$ErrorActionPreference = "Continue"

Write-Host "======================================" -ForegroundColor Cyan
Write-Host "OCI K3s Developer Connectivity Status" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
Write-Host ""

# Tunnel configuration
$tunnels = @(
    @{ LocalPort = 15432; RemoteHost = "claimassist-postgresql"; RemotePort = 5432; Name = "PostgreSQL" },
    @{ LocalPort = 16379; RemoteHost = "claimassist-redis";       RemotePort = 6379; Name = "Redis" },
    @{ LocalPort = 9092;  RemoteHost = "claimassist-kafka";       RemotePort = 9092; Name = "Kafka" },
    @{ LocalPort = 18080; RemoteHost = "claimassist-keycloak";    RemotePort = 8080; Name = "Keycloak" }
)

# Function to check if port is listening
function Test-PortListening {
    param([int]$Port)
    try {
        $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
        return ($null -ne $conn)
    } catch {
        return $false
    }
}

# Function to test TCP connectivity
function Test-TcpConnection {
    param([string]$Host, [int]$Port)
    try {
        $tcpClient = New-Object System.Net.Sockets.TcpClient
        $result = $tcpClient.BeginConnect($Host, $Port, $null, $null)
        $connected = $result.AsyncWaitHandle.WaitOne(2000)
        if ($connected -and $tcpClient.Connected) {
            $tcpClient.Close()
            return $true
        }
    } catch {}
    return $false
}

# Check tunnel status
Write-Host "[TUNNELS]" -ForegroundColor Cyan
$tunnelStatus = @()

foreach ($tunnel in $tunnels) {
    $listening = Test-PortListening $tunnel.LocalPort

    if ($listening) {
        Write-Host "  ✓ $($tunnel.Name) listening on localhost:$($tunnel.LocalPort)" -ForegroundColor Green
        $tunnelStatus += $true
    } else {
        Write-Host "  ✗ $($tunnel.Name) NOT listening on localhost:$($tunnel.LocalPort)" -ForegroundColor Red
        $tunnelStatus += $false
    }
}

Write-Host ""

# Check connectivity
Write-Host "[CONNECTIVITY]" -ForegroundColor Cyan

foreach ($tunnel in $tunnels) {
    $connected = Test-TcpConnection "localhost" $tunnel.LocalPort

    if ($connected) {
        Write-Host "  ✓ $($tunnel.Name) (localhost:$($tunnel.LocalPort)) responding" -ForegroundColor Green
    } else {
        Write-Host "  ✗ $($tunnel.Name) (localhost:$($tunnel.LocalPort)) not responding" -ForegroundColor Yellow
    }
}

Write-Host ""

# Check Kafka hostname resolution
Write-Host "[KAFKA DNS]" -ForegroundColor Cyan
try {
    $resolved = [System.Net.Dns]::GetHostAddresses("claimassist-kafka")
    if ($resolved) {
        foreach ($ip in $resolved) {
            Write-Host "  ✓ claimassist-kafka resolves to $ip" -ForegroundColor Green
        }
    } else {
        Write-Host "  ✗ claimassist-kafka does not resolve" -ForegroundColor Yellow
    }
} catch {
    Write-Host "  ✗ Failed to resolve claimassist-kafka: $_" -ForegroundColor Yellow
}

Write-Host ""

# Show hosts file entry
Write-Host "[HOSTS FILE]" -ForegroundColor Cyan
$hostsPath = "C:\Windows\System32\drivers\etc\hosts"
$kafkaEntry = "127.0.0.1 claimassist-kafka"
$hasEntry = Get-Content $hostsPath -ErrorAction SilentlyContinue | Where-Object { $_ -eq $kafkaEntry }

if ($hasEntry) {
    Write-Host "  ✓ Kafka entry present in hosts file" -ForegroundColor Green
} else {
    Write-Host "  ✗ Kafka entry NOT in hosts file" -ForegroundColor Red
    Write-Host "    Run as Administrator or execute:" -ForegroundColor Yellow
    Write-Host "    .\scripts\oci-k8s-connect.ps1" -ForegroundColor Yellow
}

Write-Host ""

# Check SSH connectivity (if OCI_HOST is set)
Write-Host "[SSH CONNECTION]" -ForegroundColor Cyan
$OciHost = $env:OCI_HOST
$OciUser = $env:OCI_USER
$SshKey = $env:SSH_KEY

if (-not $OciHost) { $OciHost = "oci-vm" }
if (-not $OciUser) { $OciUser = "ubuntu" }
if (-not $SshKey) { $SshKey = "$env:USERPROFILE\.ssh\id_rsa" }

Write-Host "  Configured: $OciUser@$OciHost" -ForegroundColor Cyan

if (Test-Path $SshKey) {
    Write-Host "  SSH key: $SshKey (found)" -ForegroundColor Green

    # Test SSH connectivity (timeout 3 seconds)
    try {
        $result = ssh -i $SshKey -o ConnectTimeout=3 "$OciUser@$OciHost" "echo ok" 2>$null
        if ($result -eq "ok") {
            Write-Host "  ✓ SSH connection successful" -ForegroundColor Green
        } else {
            Write-Host "  ✗ SSH connection failed" -ForegroundColor Red
        }
    } catch {
        Write-Host "  ✗ SSH connection error" -ForegroundColor Red
    }
} else {
    Write-Host "  SSH key: $SshKey (NOT FOUND)" -ForegroundColor Red
}

Write-Host ""

# kubectl check
Write-Host "[KUBECTL]" -ForegroundColor Cyan
if (Get-Command kubectl -ErrorAction SilentlyContinue) {
    Write-Host "  ✓ kubectl found in PATH" -ForegroundColor Green

    # Try to get K3s version
    try {
        $version = kubectl version --client --short 2>$null
        if ($version) {
            Write-Host "  Version: $version" -ForegroundColor Cyan
        }
    } catch {}
} else {
    Write-Host "  ✗ kubectl NOT found in PATH" -ForegroundColor Red
}

Write-Host ""

# Summary
Write-Host "======================================" -ForegroundColor Cyan
Write-Host "Summary" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
Write-Host ""

$listeningCount = ($tunnelStatus | Where-Object { $_ -eq $true } | Measure-Object).Count
Write-Host "Tunnels: $listeningCount / $($tunnels.Count) listening"

if ($listeningCount -eq $tunnels.Count) {
    Write-Host "Status: ✓ READY - All tunnels established" -ForegroundColor Green
} elseif ($listeningCount -gt 0) {
    Write-Host "Status: ⚠ PARTIAL - Some tunnels missing" -ForegroundColor Yellow
    Write-Host "Run: .\scripts\oci-k8s-connect.ps1" -ForegroundColor Yellow
} else {
    Write-Host "Status: ✗ OFFLINE - No tunnels established" -ForegroundColor Red
    Write-Host "Run: .\scripts\oci-k8s-connect.ps1" -ForegroundColor Yellow
}

Write-Host ""

