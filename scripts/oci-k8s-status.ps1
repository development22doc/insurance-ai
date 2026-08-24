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
    - K3s cluster status (via explicit kubeconfig)

.EXAMPLE
    .\scripts\oci-k8s-status.ps1
#>

$ErrorActionPreference = "Continue"

Write-Host "======================================" -ForegroundColor Cyan
Write-Host "OCI K3s Developer Connectivity Status" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
Write-Host ""

# Kubeconfig path - always explicit, never relies on shell KUBECONFIG
$KubeConfigPath = "$env:USERPROFILE\.kube\claimassist-oci-dev.yaml"

# Resolve kubectl dynamically
$kubectlCmd = Get-Command kubectl.exe -ErrorAction SilentlyContinue
if ($kubectlCmd) { $Kubectl = $kubectlCmd.Source } else { $Kubectl = $null }

# Tunnel configuration - matches oci-k8s-connect.ps1
$tunnels = @(
    @{ LocalPort = 15432; Name = "PostgreSQL" },
    @{ LocalPort = 16379; Name = "Redis" },
    @{ LocalPort = 9092;  Name = "Kafka" },
    @{ LocalPort = 18080; Name = "Keycloak" },
    @{ LocalPort = 18081; Name = "Kafka-UI" },
    @{ LocalPort = 13000; Name = "Grafana" },
    @{ LocalPort = 19090; Name = "Prometheus" },
    @{ LocalPort = 19411; Name = "Zipkin" },
    @{ LocalPort = 13100; Name = "Loki" },
    @{ LocalPort = 16443; Name = "K3s-API" }
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
    param([string]$TargetHost, [int]$Port)
    try {
        $tcpClient = New-Object System.Net.Sockets.TcpClient
        $result = $tcpClient.BeginConnect($TargetHost, $Port, $null, $null)
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
        Write-Host "  [OK] $($tunnel.Name) listening on localhost:$($tunnel.LocalPort)" -ForegroundColor Green
        $tunnelStatus += $true
    } else {
        Write-Host "  [FAIL] $($tunnel.Name) NOT listening on localhost:$($tunnel.LocalPort)" -ForegroundColor Red
        $tunnelStatus += $false
    }
}

Write-Host ""

# Check connectivity
Write-Host "[CONNECTIVITY]" -ForegroundColor Cyan

foreach ($tunnel in $tunnels) {
    $connected = Test-TcpConnection "localhost" $tunnel.LocalPort

    if ($connected) {
        Write-Host "  [OK] $($tunnel.Name) (localhost:$($tunnel.LocalPort)) responding" -ForegroundColor Green
    } else {
        Write-Host "  [FAIL] $($tunnel.Name) (localhost:$($tunnel.LocalPort)) not responding" -ForegroundColor Yellow
    }
}

Write-Host ""

# Check Kafka hostname resolution
Write-Host "[KAFKA DNS]" -ForegroundColor Cyan
try {
    $resolved = [System.Net.Dns]::GetHostAddresses("claimassist-kafka")
    if ($resolved) {
        foreach ($ip in $resolved) {
            Write-Host "  [OK] claimassist-kafka resolves to $ip" -ForegroundColor Green
        }
    } else {
        Write-Host "  [FAIL] claimassist-kafka does not resolve" -ForegroundColor Yellow
    }
} catch {
    Write-Host "  [FAIL] Failed to resolve claimassist-kafka: $_" -ForegroundColor Yellow
}

Write-Host ""

# Show hosts file entry
Write-Host "[HOSTS FILE]" -ForegroundColor Cyan
$hostsPath = "C:\Windows\System32\drivers\etc\hosts"
$kafkaEntry = "127.0.0.1 claimassist-kafka"
$hasEntry = Get-Content $hostsPath -ErrorAction SilentlyContinue | Where-Object { $_ -eq $kafkaEntry }

if ($hasEntry) {
    Write-Host "  [OK] Kafka entry present in hosts file" -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Kafka entry NOT in hosts file" -ForegroundColor Red
    Write-Host "    Run as Administrator or execute:" -ForegroundColor Yellow
    Write-Host "    .\scripts\oci-k8s-connect.ps1" -ForegroundColor Yellow
}

Write-Host ""

# Check SSH connectivity
Write-Host "[SSH CONNECTION]" -ForegroundColor Cyan
$OciHost = $env:OCI_HOST
$OciUser = $env:OCI_USER
$SshKey = $env:SSH_KEY

if (-not $OciHost) { $OciHost = "144.24.116.166" }
if (-not $OciUser) { $OciUser = "opc" }
if (-not $SshKey) { $SshKey = "$env:USERPROFILE\.ssh\claimassist-oci-dev" }

Write-Host "  Configured: $OciUser@$OciHost" -ForegroundColor Cyan

if (Test-Path $SshKey) {
    Write-Host "  SSH key: $SshKey (found)" -ForegroundColor Green

    try {
        $result = ssh -i $SshKey -o ConnectTimeout=3 "$OciUser@$OciHost" "echo ok" 2>$null
        if ($result -eq "ok") {
            Write-Host "  [OK] SSH connection successful" -ForegroundColor Green
        } else {
            Write-Host "  [FAIL] SSH connection failed" -ForegroundColor Red
        }
    } catch {
        Write-Host "  [FAIL] SSH connection error" -ForegroundColor Red
    }
} else {
    Write-Host "  SSH key: $SshKey (NOT FOUND)" -ForegroundColor Red
}

Write-Host ""

# kubectl check with explicit kubeconfig
Write-Host "[KUBECTL]" -ForegroundColor Cyan
if ($Kubectl) {
    Write-Host "  [OK] kubectl found: $Kubectl" -ForegroundColor Green

    if (Test-Path $KubeConfigPath) {
        Write-Host "  Kubeconfig: $KubeConfigPath (found)" -ForegroundColor Green

        try {
            $version = & $Kubectl --kubeconfig $KubeConfigPath version --client --short 2>$null
            if ($version) {
                Write-Host "  Version: $version" -ForegroundColor Cyan
            }

            # Try to get K3s nodes
            $nodes = & $Kubectl --kubeconfig $KubeConfigPath get nodes --no-headers 2>$null
            if ($nodes) {
                Write-Host "  [OK] K3s cluster reachable" -ForegroundColor Green
                $nodes | ForEach-Object { Write-Host "    $_" -ForegroundColor DarkGray }
            } else {
                Write-Host "  [WARN] K3s cluster not reachable via kubeconfig" -ForegroundColor Yellow
            }
        } catch {
            Write-Host "  [FAIL] kubectl error with explicit kubeconfig: $_" -ForegroundColor Red
        }
    } else {
        Write-Host "  Kubeconfig: $KubeConfigPath (NOT FOUND)" -ForegroundColor Yellow
        Write-Host "    Run: .\scripts\oci-k8s-connect.ps1" -ForegroundColor Yellow
    }
} else {
    Write-Host "  [FAIL] kubectl NOT found in PATH" -ForegroundColor Red
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
    Write-Host "Status: [OK] READY - All tunnels established" -ForegroundColor Green
} elseif ($listeningCount -gt 0) {
    Write-Host "Status: [WARN] PARTIAL - Some tunnels missing" -ForegroundColor Yellow
    Write-Host "Run: .\scripts\oci-k8s-connect.ps1" -ForegroundColor Yellow
} else {
    Write-Host "Status: [FAIL] OFFLINE - No tunnels established" -ForegroundColor Red
    Write-Host "Run: .\scripts\oci-k8s-connect.ps1" -ForegroundColor Yellow
}

Write-Host ""