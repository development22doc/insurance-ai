#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Establishes secure SSH tunnels from Windows to OCI K3s dependencies.

.DESCRIPTION
    Sets up SSH port-forwarding tunnels for:
        - PostgreSQL:  localhost:15432 -> claimassist-postgresql:5432
    - Redis:       localhost:16379 -> claimassist-redis:6379
    - Kafka:       localhost:9092  -> claimassist-kafka:9092
    - Keycloak:    localhost:18080 -> claimassist-keycloak:8080

    Also manages the Windows hosts file entry for Kafka hostname resolution.

    Prerequisites:
    - SSH access to OCI VM configured (e.g., ~/.ssh/id_rsa)
    - kubectl configured to access OCI K3s
    - Administrator privileges (for hosts file modification)

.PARAMETER OciHost
    OCI VM hostname or IP. Defaults to OCI_HOST env var or 'oci-vm'

.PARAMETER OciUser
    SSH username for OCI VM. Defaults to OCI_USER env var or 'ubuntu'

.PARAMETER SshKey
    Path to SSH private key. Defaults to SSH_KEY env var or ~/.ssh/id_rsa

.EXAMPLE
    .\scripts\oci-k8s-connect.ps1

    .\scripts\oci-k8s-connect.ps1 -OciHost 1.2.3.4 -OciUser ubuntu
#>

param(
    [string]$OciHost = $env:OCI_HOST,
    [string]$OciUser = $env:OCI_USER,
    [string]$SshKey = $env:SSH_KEY
)

$ErrorActionPreference = "Stop"

# Defaults
if (-not $OciHost) { $OciHost = "oci-vm" }
if (-not $OciUser) { $OciUser = "ubuntu" }
if (-not $SshKey) { $SshKey = "$env:USERPROFILE\.ssh\id_rsa" }

# Tunnel configuration
$tunnels = @(
    @{ LocalPort = 15432; RemoteHost = "claimassist-postgresql"; RemotePort = 5432; Name = "PostgreSQL" },
    @{ LocalPort = 16379; RemoteHost = "claimassist-redis";       RemotePort = 6379; Name = "Redis" },
    @{ LocalPort = 9092;  RemoteHost = "claimassist-kafka";       RemotePort = 9092; Name = "Kafka" },
    @{ LocalPort = 18080; RemoteHost = "claimassist-keycloak";    RemotePort = 8080; Name = "Keycloak" },
    # K3s API server forwarding (remote binds to 127.0.0.1:6443 on the OCI VM)
    # We forward it to an unprivileged local port by default to avoid collisions with local k8s
    @{ LocalPort = 16443; RemoteHost = "127.0.0.1";                 RemotePort = 6443; Name = "K3s-API" }
)

Write-Host "======================================" -ForegroundColor Cyan
Write-Host "OCI K3s Developer Connectivity Setup" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
Write-Host ""

# Validate prerequisites
Write-Host "[PRE-CHECK] Validating prerequisites..." -ForegroundColor Yellow

if (-not (Test-Path $SshKey)) {
    Write-Host "[ERROR] SSH key not found: $SshKey" -ForegroundColor Red
    Write-Host "        Set SSH_KEY env var or use -SshKey parameter" -ForegroundColor Red
    exit 1
}

if (-not (Get-Command kubectl -ErrorAction SilentlyContinue)) {
    Write-Host "[ERROR] kubectl not found in PATH" -ForegroundColor Red
    exit 1
}

Write-Host "[OK] SSH key: $SshKey" -ForegroundColor Green
Write-Host "[OK] kubectl found" -ForegroundColor Green
Write-Host "[OK] OCI Host: $OciHost" -ForegroundColor Green
Write-Host "[OK] OCI User: $OciUser" -ForegroundColor Green
Write-Host ""

# Function to check if port is in use
function Test-PortInUse {
    param([int]$Port)
    try {
        $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
        return ($null -ne $conn)
    } catch {
        return $false
    }
}

# Function to kill process on port
function Stop-ProcessOnPort {
    param([int]$Port)
    try {
        $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
        if ($null -ne $conn) {
            Get-Process -Id $conn.OwningProcess -ErrorAction SilentlyContinue | Stop-Process -Force
            Start-Sleep -Seconds 1
            return $true
        }
    } catch {
        return $false
    }
    return $false
}

# Function to establish tunnel
function Establish-Tunnel {
    param(
        [int]$LocalPort,
        [string]$RemoteHost,
        [int]$RemotePort,
        [string]$Name
    )

    Write-Host "[$Name] Checking tunnel localhost:${LocalPort} -> ${RemoteHost}:${RemotePort}..." -NoNewline

    if (Test-PortInUse $LocalPort) {
        Write-Host " already listening" -ForegroundColor Cyan
        return $true
    }

    Write-Host " establishing..." -ForegroundColor Yellow

    # Build SSH command for port forwarding. Use cmd.exe to run ssh with stdout/stderr redirected
    # so the process can be started in background on Windows and we can capture errors to a log.
    $logDir = "$env:USERPROFILE\.kube\ssh-logs"
    if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir | Out-Null }
    $logPath = Join-Path $logDir ("ssh-tunnel-$($Name)-$LocalPort.log")

    # Use ExitOnForwardFailure and keepalive settings to ensure the tunnel fails fast on problems
    # Build the forward specification separately to avoid PowerShell interpolation ambiguity (e.g. $LocalPort:)
    $forwardSpec = "127.0.0.1:${LocalPort}:${RemoteHost}:${RemotePort}"
    $sshArgs = "-i `"$SshKey`" -o IdentitiesOnly=yes -o ExitOnForwardFailure=yes -o ServerAliveInterval=30 -o ServerAliveCountMax=3 -N -L $forwardSpec $OciUser@$OciHost"

    # Prepare per-tunnel stdout/stderr paths
    $outPath = Join-Path $logDir ("ssh-tunnel-$($Name)-$LocalPort.out")
    $errPath = $logPath

    # Build ssh argument array to avoid any shell parsing/quoting issues
    $sshArgsArray = @(
        "-i", $SshKey,
        "-o", "IdentitiesOnly=yes",
        "-o", "ExitOnForwardFailure=yes",
        "-o", "ServerAliveInterval=30",
        "-o", "ServerAliveCountMax=3",
        "-N",
        "-L", $forwardSpec,
        "$OciUser@$OciHost"
    )

    try {
        # Start ssh.exe directly and redirect stdout/stderr to log files. This avoids cmd.exe argument parsing issues
        $proc = Start-Process -FilePath "ssh.exe" -ArgumentList $sshArgsArray -WindowStyle Hidden -PassThru -RedirectStandardOutput $outPath -RedirectStandardError $errPath
        Start-Sleep -Seconds 2

        # Check whether the local port is listening
        if (Test-PortInUse $LocalPort) {
            Write-Host "[$Name] [OK] Tunnel established (pid=$($proc.Id)), out=$outPath err=$errPath" -ForegroundColor Green
            return $true
        } else {
            Write-Host "[$Name] [ERROR] Failed to establish tunnel; see $errPath" -ForegroundColor Red
            return $false
        }
    } catch {
        Write-Host "[$Name] [ERROR] SSH start failed: $_" -ForegroundColor Red
        return $false
    }
}

# Establish all tunnels
Write-Host "[TUNNELS] Establishing SSH tunnels..." -ForegroundColor Cyan
$allSuccess = $true

foreach ($tunnel in $tunnels) {
    $ok = Establish-Tunnel -LocalPort $tunnel.LocalPort -RemoteHost $tunnel.RemoteHost -RemotePort $tunnel.RemotePort -Name $tunnel.Name
    if (-not $ok) { $allSuccess = $false }
}

if (-not $allSuccess) {
    Write-Host ""
    Write-Host "[ERROR] One or more tunnels failed. Check SSH connectivity:" -ForegroundColor Red
    Write-Host "        ssh -i $SshKey $OciUser@$OciHost" -ForegroundColor Yellow
    exit 1
}

Write-Host ""

# If K3s API tunnel was configured and established, fetch the remote k3s kubeconfig and write a local developer kubeconfig
$apiTunnel = $tunnels | Where-Object { $_.Name -eq 'K3s-API' }
if ($apiTunnel -and (Test-PortInUse $apiTunnel.LocalPort)) {
    Write-Host "[KUBE] Fetching remote k3s kubeconfig and generating local kubeconfig..." -ForegroundColor Cyan

    try {
        # Retrieve remote k3s kubeconfig
        $remoteKube = & ssh -i $SshKey "$OciUser@$OciHost" "sudo cat /etc/rancher/k3s/k3s.yaml" 2>$null

        if (-not $remoteKube) {
            Write-Host "[KUBE] [WARN] Could not fetch remote k3s kubeconfig" -ForegroundColor Yellow
        } else {
            # Extract certs/keys data
            $ca = [regex]::Match($remoteKube, 'certificate-authority-data:\s*(\S+)').Groups[1].Value
            $clientCert = [regex]::Match($remoteKube, 'client-certificate-data:\s*(\S+)').Groups[1].Value
            $clientKey = [regex]::Match($remoteKube, 'client-key-data:\s*(\S+)').Groups[1].Value

            if (-not $ca -or -not $clientCert -or -not $clientKey) {
                Write-Host "[KUBE] [WARN] Remote kubeconfig missing embedded certs/keys" -ForegroundColor Yellow
            } else {
                $localKubePath = "$env:USERPROFILE\.kube\claimassist-oci-dev.yaml"
                $serverUrl = "https://127.0.0.1:$($apiTunnel.LocalPort)"

                $localKubeLines = @(
                    "apiVersion: v1",
                    "clusters:",
                    "- cluster:",
                    "    certificate-authority-data: $ca",
                    "    server: $serverUrl",
                    "  name: claimassist-oci-dev",
                    "contexts:",
                    "- context:",
                    "    cluster: claimassist-oci-dev",
                    "    user: claimassist-oci-dev",
                    "  name: claimassist-oci-dev",
                    "current-context: claimassist-oci-dev",
                    "kind: Config",
                    "users:",
                    "- name: claimassist-oci-dev",
                    "  user:",
                    "    client-certificate-data: $clientCert",
                    "    client-key-data: $clientKey"
                )
                $localKube = $localKubeLines -join "`n"

                # Ensure .kube directory exists
                $kubeDir = Split-Path $localKubePath -Parent
                if (-not (Test-Path $kubeDir)) { New-Item -ItemType Directory -Path $kubeDir | Out-Null }

                $localKube | Out-File -FilePath $localKubePath -Encoding ascii -Force
                Write-Host "[KUBE] [OK] Local kubeconfig written: $localKubePath" -ForegroundColor Green

                Write-Host ""
                Write-Host "Usage examples:" -ForegroundColor Cyan
                $envCmd = '$env:KUBECONFIG="' + $localKubePath + ';$env:USERPROFILE\.kube\config"'
                Write-Host "  # Temporary for this PowerShell session (literal command):" -ForegroundColor Yellow
                Write-Host "  $envCmd" -ForegroundColor Yellow
                Write-Host "  # Or reference explicitly:" -ForegroundColor Yellow
                $cmd1 = 'kubectl --kubeconfig "' + $localKubePath + '" config get-contexts'
                $cmd2 = 'kubectl --kubeconfig "' + $localKubePath + '" get nodes'
                Write-Host "  $cmd1" -ForegroundColor Yellow
                Write-Host "  $cmd2" -ForegroundColor Yellow
            }
        }
    } catch {
        Write-Host "[KUBE] ✗ Error while generating local kubeconfig: $_" -ForegroundColor Red
    }
} else {
    Write-Host "[KUBE] K3s API tunnel not active; skipping kubeconfig generation" -ForegroundColor Yellow
}


# Manage Kafka hostname in hosts file
Write-Host "[HOSTS] Managing Kafka hostname resolution..." -ForegroundColor Cyan

$hostsPath = "C:\Windows\System32\drivers\etc\hosts"
$kafkaEntry = "127.0.0.1 claimassist-kafka"
$marker = "# CLAIMASSIST-OCI-K8S-DEV"

# Check if we have admin rights
try {
    $currentUser = [System.Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object System.Security.Principal.WindowsPrincipal($currentUser)
    $isAdmin = $principal.IsInRole([System.Security.Principal.WindowsBuiltInRole]::Administrator)

    if (-not $isAdmin) {
        Write-Host "[WARN] Not running as Administrator - cannot modify hosts file" -ForegroundColor Yellow
        Write-Host "       Run as Administrator to enable:" -ForegroundColor Yellow
        Write-Host "       Start-Process powershell -Verb RunAs -ArgumentList '-File $PSCommandPath'" -ForegroundColor Yellow
        # Continue anyway - tunnels are working, just hosts file won't update
    } else {
        # Read current hosts file
        $hostsContent = Get-Content $hostsPath -ErrorAction SilentlyContinue

        # Check if entry already exists
        $kafkaLineExists = $hostsContent | Where-Object { $_ -eq $kafkaEntry }

        if ($kafkaLineExists) {
            Write-Host "[HOSTS] [OK] Kafka entry already present" -ForegroundColor Green
        } else {
            # Add marker comment and entry
            $newEntry = "$marker`n$kafkaEntry"
            Add-Content -Path $hostsPath -Value "`n$newEntry" -Encoding UTF8 -ErrorAction Stop
            Write-Host "[HOSTS] [OK] Added Kafka hostname entry" -ForegroundColor Green
        }
    }
} catch {
    Write-Host "[WARN] Could not modify hosts file: $_" -ForegroundColor Yellow
}

Write-Host ""

# Verify connectivity
Write-Host "[VERIFY] Verifying connectivity to K3s dependencies..." -ForegroundColor Cyan

$allConnected = $true

foreach ($tunnel in $tunnels) {
    Write-Host "[$($tunnel.Name)] Testing connection to localhost:$($tunnel.LocalPort)..." -NoNewline
    try {
        $tcpClient = New-Object System.Net.Sockets.TcpClient
        $result = $tcpClient.BeginConnect("localhost", $tunnel.LocalPort, $null, $null)
        $result.AsyncWaitHandle.WaitOne(3000) | Out-Null

        if ($tcpClient.Connected) {
            Write-Host " [OK] Connected" -ForegroundColor Green
            $tcpClient.Close()
        } else {
            Write-Host " [WARN] Connection timeout" -ForegroundColor Yellow
            $allConnected = $false
        }
    } catch {
        Write-Host " [WARN] Connection failed" -ForegroundColor Yellow
        $allConnected = $false
    }
}

Write-Host ""
Write-Host "======================================" -ForegroundColor Cyan
Write-Host "Setup Complete" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
Write-Host ""

if ($allConnected) {
    Write-Host "[OK] All tunnels established and verified" -ForegroundColor Green
} else {
    Write-Host "[WARN] Some connections could not be verified" -ForegroundColor Yellow
    Write-Host "  This may be normal if the K3s services are not yet ready" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "1. Start services with LOCAL-K8S profile:" -ForegroundColor Cyan
Write-Host "   .\scripts\start-services-local.ps1 -Profile local-k8s" -ForegroundColor Yellow
Write-Host ""
Write-Host "2. Check tunnel status anytime:" -ForegroundColor Cyan
Write-Host "   .\scripts\oci-k8s-status.ps1" -ForegroundColor Yellow
Write-Host ""
Write-Host "3. Disconnect tunnels when done:" -ForegroundColor Cyan
Write-Host "   .\scripts\oci-k8s-disconnect.ps1" -ForegroundColor Yellow
Write-Host ""
Write-Host "Environment variables for services:" -ForegroundColor Cyan
Write-Host "   POSTGRES_HOST=localhost" -ForegroundColor Yellow
Write-Host "   POSTGRES_PORT=15432" -ForegroundColor Yellow
Write-Host "   REDIS_HOST=localhost" -ForegroundColor Yellow
Write-Host "   REDIS_PORT=16379" -ForegroundColor Yellow
Write-Host "   KAFKA_BOOTSTRAP_SERVERS=localhost:9092" -ForegroundColor Yellow
Write-Host "   KEYCLOAK_ISSUER_URI=http://localhost:18080/realms/claimassist" -ForegroundColor Yellow
Write-Host ""

