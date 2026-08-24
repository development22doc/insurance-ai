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
        - Kafka UI:    localhost:18081 -> kafka-ui:8080
        - Grafana:     localhost:13000 -> grafana-service:3000
        - Prometheus:  localhost:19090 -> prometheus-service:9090
        - Zipkin:      localhost:19411 -> zipkin-service:9411
        - Loki:        localhost:13100 -> loki-service:3100

    Also manages the Windows hosts file entry for Kafka hostname resolution.
    Generates local kubeconfig for OCI K3s access.

    Prerequisites:
    - SSH access to OCI VM configured (e.g., ~/.ssh/claimassist-oci-dev)
    - kubectl configured to access OCI K3s
    - Administrator privileges (for hosts file modification)

.PARAMETER OciHost
    OCI VM hostname or IP. Defaults to OCI_HOST env var or '144.24.116.166'

.PARAMETER OciUser
    SSH username for OCI VM. Defaults to OCI_USER env var or 'opc'

.PARAMETER SshKey
    Path to SSH private key. Defaults to SSH_KEY env var or ~/.ssh/claimassist-oci-dev

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

# Defaults with resolution priority: parameter > env var > repository default
if (-not $OciHost) { $OciHost = "144.24.116.166" }
if (-not $OciUser) { $OciUser = "opc" }
if (-not $SshKey) { $SshKey = "$env:USERPROFILE\.ssh\claimassist-oci-dev" }

# Kubeconfig path - always explicit, never relies on shell KUBECONFIG
$KubeConfigPath = "$env:USERPROFILE\.kube\claimassist-oci-dev.yaml"

# Resolve kubectl dynamically
$Kubectl = (Get-Command kubectl.exe -ErrorAction Stop).Source

# OCI-side Kubernetes port-forwards.
# These run on the OCI VM and expose K3s services on OCI localhost.
$ociPortForwards = @(
    @{ Name = "PostgreSQL";   Namespace = "claimassist-dev";        LocalPort = 15432; Service = "claimassist-postgresql"; RemotePort = 5432 },
    @{ Name = "Redis";        Namespace = "claimassist-dev";        LocalPort = 16379; Service = "claimassist-redis";      RemotePort = 6379 },
    @{ Name = "Kafka";        Namespace = "claimassist-dev";        LocalPort = 9092;  Service = "claimassist-kafka";      RemotePort = 9092 },
    @{ Name = "Keycloak";     Namespace = "claimassist-dev";        LocalPort = 18080; Service = "claimassist-keycloak";   RemotePort = 8080 },
    @{ Name = "Kafka-UI";     Namespace = "claimassist-observability"; LocalPort = 18081; Service = "kafka-ui";             RemotePort = 8080 },
    @{ Name = "Grafana";      Namespace = "claimassist-observability"; LocalPort = 13000; Service = "grafana-service";      RemotePort = 3000 },
    @{ Name = "Prometheus";   Namespace = "claimassist-observability"; LocalPort = 19090; Service = "prometheus-service";   RemotePort = 9090 },
    @{ Name = "Zipkin";       Namespace = "claimassist-observability"; LocalPort = 19411; Service = "zipkin-service";       RemotePort = 9411 },
    @{ Name = "Loki";         Namespace = "claimassist-observability"; LocalPort = 13100; Service = "loki-service";         RemotePort = 3100 }
)

function Invoke-OciCommand {
    param([string]$Command)

    & ssh `
        -i $SshKey `
        -o IdentitiesOnly=yes `
        -o BatchMode=yes `
        -o ConnectTimeout=10 `
        "$OciUser@$OciHost" `
        $Command
}

function Test-OciPort {
    param([int]$Port)

    $result = Invoke-OciCommand "ss -lnt | grep ':$Port ' || true"
    return [bool]$result
}

Write-Host ""
Write-Host "[OCI-PORT-FORWARDS] Ensuring K3s dependency forwards..." -ForegroundColor Cyan

foreach ($pf in $ociPortForwards) {

    Write-Host "[$($pf.Name)] Checking OCI port $($pf.LocalPort)..."

    if (Test-OciPort $pf.LocalPort) {
        Write-Host "[$($pf.Name)] [OK] OCI port $($pf.LocalPort) already listening" -ForegroundColor Green
        continue
    }

    Write-Host "[$($pf.Name)] [START] OCI 127.0.0.1:$($pf.LocalPort) -> svc/$($pf.Service):$($pf.RemotePort)" -ForegroundColor Cyan

    $remoteCommand = "nohup kubectl -n $($pf.Namespace) port-forward svc/$($pf.Service) $($pf.LocalPort):$($pf.RemotePort) --address 127.0.0.1 >/tmp/claimassist-portforward-$($pf.LocalPort).log 2>&1 </dev/null &"

    Invoke-OciCommand $remoteCommand | Out-Null

    $ready = $false

    for ($attempt = 1; $attempt -le 20; $attempt++) {
        Start-Sleep -Milliseconds 500

        if (Test-OciPort $pf.LocalPort) {
            $ready = $true
            break
        }
    }

    if (-not $ready) {
        Write-Host "[$($pf.Name)] [ERROR] OCI port-forward failed" -ForegroundColor Red

        Invoke-OciCommand "cat /tmp/claimassist-portforward-$($pf.LocalPort).log 2>/dev/null || true"

        throw "OCI port-forward failed for $($pf.Name)."
    }

    Write-Host "[$($pf.Name)] [OK] OCI port-forward established" -ForegroundColor Green
}

Write-Host "[OCI-PORT-FORWARDS] All dependency forwards ready." -ForegroundColor Green
Write-Host ""
# Tunnel configuration
$tunnels = @(
    @{ LocalPort = 15432; RemoteHost = "127.0.0.1"; RemotePort = 15432; Name = "PostgreSQL" },
    @{ LocalPort = 16379; RemoteHost = "127.0.0.1";       RemotePort = 16379; Name = "Redis" },
    @{ LocalPort = 9092;  RemoteHost = "127.0.0.1";       RemotePort = 9092; Name = "Kafka" },
    @{ LocalPort = 18080; RemoteHost = "127.0.0.1";    RemotePort = 18080; Name = "Keycloak" },
    @{ LocalPort = 18081; RemoteHost = "127.0.0.1";    RemotePort = 18081; Name = "Kafka-UI" },
    @{ LocalPort = 13000; RemoteHost = "127.0.0.1";    RemotePort = 13000; Name = "Grafana" },
    @{ LocalPort = 19090; RemoteHost = "127.0.0.1";    RemotePort = 19090; Name = "Prometheus" },
    @{ LocalPort = 19411; RemoteHost = "127.0.0.1";    RemotePort = 19411; Name = "Zipkin" },
    @{ LocalPort = 13100; RemoteHost = "127.0.0.1";    RemotePort = 13100; Name = "Loki" },
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
    Write-Host "        Expected: ~/.ssh/claimassist-oci-dev" -ForegroundColor Yellow
    exit 1
}

if (-not (Get-Command kubectl.exe -ErrorAction SilentlyContinue)) {
    Write-Host "[ERROR] kubectl not found in PATH" -ForegroundColor Red
    exit 1
}

Write-Host "[OK] SSH key: $SshKey" -ForegroundColor Green
Write-Host "[OK] kubectl found: $Kubectl" -ForegroundColor Green
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

# Function to establish tunnel with improved readiness polling
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

    $logDir = "$env:USERPROFILE\.kube\ssh-logs"
    if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir | Out-Null }
    $logPath = Join-Path $logDir ("ssh-tunnel-$($Name)-$LocalPort.log")

    $forwardSpec = "127.0.0.1:${LocalPort}:${RemoteHost}:${RemotePort}"
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

    $outPath = Join-Path $logDir ("ssh-tunnel-$($Name)-$LocalPort.out")
    $errPath = $logPath

    try {
        $proc = Start-Process -FilePath "ssh.exe" -ArgumentList $sshArgsArray -WindowStyle Hidden -PassThru -RedirectStandardOutput $outPath -RedirectStandardError $errPath

        # Improved readiness polling: retry every 500ms for up to 10 seconds
        $ready = $false
        for ($attempt = 1; $attempt -le 20; $attempt++) {
            Start-Sleep -Milliseconds 500
            if (Test-PortInUse $LocalPort) {
                $ready = $true
                break
            }
        }

        if ($ready) {
            Write-Host "[$Name] [OK] Tunnel established (pid=$($proc.Id)), out=$outPath err=$errPath" -ForegroundColor Green
            return $true
        } else {
            Write-Host "[$Name] [ERROR] Failed to establish tunnel; see $errPath" -ForegroundColor Red
            if (Test-Path $errPath) {
                Get-Content $errPath -Tail 20 | ForEach-Object { Write-Host "  $_" -ForegroundColor DarkGray }
            }
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

# Fetch remote k3s kubeconfig and write local developer kubeconfig
$apiTunnel = $tunnels | Where-Object { $_.Name -eq 'K3s-API' }
if ($apiTunnel -and (Test-PortInUse $apiTunnel.LocalPort)) {
    Write-Host "[KUBE] Fetching remote k3s kubeconfig and generating local kubeconfig..." -ForegroundColor Cyan

    try {
        $remoteKube = & ssh -i $SshKey "$OciUser@$OciHost" "sudo cat /etc/rancher/k3s/k3s.yaml" 2>$null

        if (-not $remoteKube) {
            Write-Host "[KUBE] [WARN] Could not fetch remote k3s kubeconfig" -ForegroundColor Yellow
        } else {
            $ca = [regex]::Match($remoteKube, 'certificate-authority-data:\s*(\S+)').Groups[1].Value
            $clientCert = [regex]::Match($remoteKube, 'client-certificate-data:\s*(\S+)').Groups[1].Value
            $clientKey = [regex]::Match($remoteKube, 'client-key-data:\s*(\S+)').Groups[1].Value

            if (-not $ca -or -not $clientCert -or -not $clientKey) {
                Write-Host "[KUBE] [WARN] Remote kubeconfig missing embedded certs/keys" -ForegroundColor Yellow
            } else {
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

                $kubeDir = Split-Path $KubeConfigPath -Parent
                if (-not (Test-Path $kubeDir)) { New-Item -ItemType Directory -Path $kubeDir | Out-Null }

                $localKube | Out-File -FilePath $KubeConfigPath -Encoding ascii -Force
                Write-Host "[KUBE] [OK] Local kubeconfig written: $KubeConfigPath" -ForegroundColor Green
            }
        }
    } catch {
        Write-Host "[KUBE] [ERROR] Error while generating local kubeconfig: $_" -ForegroundColor Red
    }
} else {
    Write-Host "[KUBE] K3s API tunnel not active; skipping kubeconfig generation" -ForegroundColor Yellow
}

# Manage Kafka hostname in hosts file
Write-Host "[HOSTS] Managing Kafka hostname resolution..." -ForegroundColor Cyan

$hostsPath = "C:\Windows\System32\drivers\etc\hosts"
$kafkaEntry = "127.0.0.1 claimassist-kafka"
$marker = "# CLAIMASSIST-OCI-K8S-DEV"

try {
    $currentUser = [System.Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object System.Security.Principal.WindowsPrincipal($currentUser)
    $isAdmin = $principal.IsInRole([System.Security.Principal.WindowsBuiltInRole]::Administrator)

    if (-not $isAdmin) {
        Write-Host "[WARN] Not running as Administrator - cannot modify hosts file" -ForegroundColor Yellow
        Write-Host "       Run as Administrator to enable:" -ForegroundColor Yellow
        Write-Host "       Start-Process powershell -Verb RunAs -ArgumentList '-File $PSCommandPath'" -ForegroundColor Yellow
    } else {
        $hostsContent = Get-Content $hostsPath -ErrorAction SilentlyContinue
        $kafkaLineExists = $hostsContent | Where-Object { $_ -eq $kafkaEntry }

        if ($kafkaLineExists) {
            Write-Host "[HOSTS] [OK] Kafka entry already present" -ForegroundColor Green
        } else {
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
Write-Host "Local endpoints:" -ForegroundColor Cyan
Write-Host "  PostgreSQL:  localhost:15432" -ForegroundColor Yellow
Write-Host "  Redis:       localhost:16379" -ForegroundColor Yellow
Write-Host "  Kafka:       localhost:9092" -ForegroundColor Yellow
Write-Host "  Keycloak:    localhost:18080" -ForegroundColor Yellow
Write-Host "  Kafka UI:    localhost:18081" -ForegroundColor Yellow
Write-Host "  Grafana:     localhost:13000" -ForegroundColor Yellow
Write-Host "  Prometheus:  localhost:19090" -ForegroundColor Yellow
Write-Host "  Zipkin:      localhost:19411" -ForegroundColor Yellow
Write-Host "  Loki:        localhost:13100" -ForegroundColor Yellow
Write-Host "  K3s API:     localhost:16443" -ForegroundColor Yellow
Write-Host ""
Write-Host "Kubeconfig: $KubeConfigPath" -ForegroundColor Cyan
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