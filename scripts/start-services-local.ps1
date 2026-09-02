#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Starts ClaimAssist platform services locally against OCI K3s infrastructure.

.DESCRIPTION
    This script starts the four local JVM services (API Gateway, Customer, Claims, Agent)
    using Maven spring-boot:run with the local-k8s profile. It loads environment
    variables from .env.local-k3s and passes them to child processes.

    All configuration is script-owned - no manual environment setup required.

.USAGE
    .\scripts\start-services-local.ps1 -Profile local-k8s

.PARAMETER Profile
    Profile to use. Only 'local-k8s' is supported for this script.

.EXAMPLE
    .\scripts\start-services-local.ps1 -Profile local-k8s
#>

param(
    [ValidateSet("local-k8s")]
    [string]$Profile = "local-k8s"
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$logDir = Join-Path $repoRoot "logs"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

Write-Host "======================================" -ForegroundColor Cyan
Write-Host "ClaimAssist Local Services Startup" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
Write-Host "Profile: $Profile" -ForegroundColor Cyan
Write-Host ""

# ============================================================
# PREREQUISITE VALIDATION
# ============================================================

Write-Host "[PREREQ] Validating prerequisites..." -ForegroundColor Yellow

# 1. Java
$javaHome = $env:JAVA_HOME
if (-not $javaHome -or -not (Test-Path "$javaHome\bin\java.exe")) {
    Write-Host "[ERROR] JAVA_HOME not set or java.exe not found: $javaHome" -ForegroundColor Red
    exit 1
}
$javaVersionOutput = cmd.exe /c "`"$javaHome\bin\java.exe`" -version 2>&1"
$javaVersion = $javaVersionOutput |
    Where-Object { $_ -match 'version "' } |
    Select-Object -First 1
Write-Host "[OK] Java: $javaVersion" -ForegroundColor Green

# 2. Maven
$mvnCmd = Get-Command mvn.exe -ErrorAction SilentlyContinue
if (-not $mvnCmd) {
    $mvnCmd = Get-Command mvn -ErrorAction SilentlyContinue
}
if (-not $mvnCmd) {
    Write-Host "[ERROR] Maven (mvn) not found in PATH" -ForegroundColor Red
    exit 1
}
$mvnVersionOutput = (& $mvnCmd -version 2>&1 | Out-String)
$mvnVersion = ($mvnVersionOutput -split '\r?\n' |
    Where-Object { $_ -match 'Apache Maven' } |
    Select-Object -First 1)
Write-Host "[OK] Maven: $mvnVersion" -ForegroundColor Green

# 3. kubectl
$kubectlCmd = Get-Command kubectl.exe -ErrorAction SilentlyContinue
if ($kubectlCmd) { $Kubectl = $kubectlCmd.Source } else { $Kubectl = $null }
if (-not $Kubectl) {
    Write-Host "[ERROR] kubectl not found in PATH" -ForegroundColor Red
    exit 1
}
Write-Host "[OK] kubectl: $Kubectl" -ForegroundColor Green

# 4. Environment file
$envFile = Join-Path $repoRoot ".env.local-k3s"
if (-not (Test-Path $envFile)) {
    Write-Host "[ERROR] Environment file not found: $envFile" -ForegroundColor Red
    Write-Host "       Copy .env.local-k3s.example to .env.local-k3s and fill in values" -ForegroundColor Yellow
    exit 1
}
Write-Host "[OK] Environment file: $envFile" -ForegroundColor Green

# 5. Load environment variables from .env.local-k3s into process scope
Write-Host "[ENV] Loading environment from $envFile..." -ForegroundColor Cyan
$envVars = @{}
Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ([string]::IsNullOrWhiteSpace($line)) { return }
    if ($line.StartsWith("#")) { return }
    if ($line -match '^([^=]+)=(.*)$') {
        $name = $matches[1].Trim()
        $value = $matches[2].Trim()
        # Remove optional surrounding quotes
        if ($value.Length -ge 2) {
            if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
                ($value.StartsWith("'") -and $value.EndsWith("'"))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
        }
        $envVars[$name] = $value
        [Environment]::SetEnvironmentVariable($name, $value, "Process")
    }
}

# Validate required variables
$requiredVars = @(
    "SPRING_PROFILES_ACTIVE",
    "SPRING_KAFKA_BOOTSTRAP_SERVERS",
    "SPRING_DATA_REDIS_HOST",
    "SPRING_DATA_REDIS_PORT",
    "POSTGRES_HOST",
    "POSTGRES_PORT",
    "POSTGRES_USER",
    "POSTGRES_PASSWORD",
    "ZIPKIN_ENDPOINT",
    "LOKI_HOST",
    "LOKI_PORT",
    "CLAIMASSIST_DEV_ID"
)

$missingVars = @()
foreach ($var in $requiredVars) {
    if (-not $envVars.ContainsKey($var) -or [string]::IsNullOrWhiteSpace($envVars[$var])) {
        $missingVars += $var
    }
}
if ($missingVars.Count -gt 0) {
    Write-Host "[ERROR] Missing required environment variables:" -ForegroundColor Red
    $missingVars | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    exit 1
}

# Never print password
Write-Host "[OK] All required environment variables loaded" -ForegroundColor Green

# 6. Verify Tailscale infrastructure connectivity
Write-Host "[INFRA] Verifying shared infrastructure via Tailscale..." -ForegroundColor Yellow
$infraEndpoints = @(
    @{ Host = $envVars["POSTGRES_HOST"]; Port = [int]$envVars["POSTGRES_PORT"]; Name = "PostgreSQL" },
    @{ Host = $envVars["SPRING_DATA_REDIS_HOST"]; Port = [int]$envVars["SPRING_DATA_REDIS_PORT"]; Name = "Redis" },
    @{ Host = ($envVars["SPRING_KAFKA_BOOTSTRAP_SERVERS"] -split ":")[0]; Port = [int](($envVars["SPRING_KAFKA_BOOTSTRAP_SERVERS"] -split ":")[1]); Name = "Kafka" }
)

$allInfraOk = $true
foreach ($t in $infraEndpoints) {
    Write-Host "  [$($t.Name)] Checking $($t.Host):$($t.Port)..." -NoNewline
    try {
        $tcpClient = New-Object System.Net.Sockets.TcpClient
        $result = $tcpClient.BeginConnect($t.Host, $t.Port, $null, $null)
        $connected = $result.AsyncWaitHandle.WaitOne(5000)
        if ($connected -and $tcpClient.Connected) {
            Write-Host " [OK]" -ForegroundColor Green
            $tcpClient.Close()
        } else {
            Write-Host " [FAIL]" -ForegroundColor Red
            $allInfraOk = $false
        }
    } catch {
        Write-Host " [FAIL]" -ForegroundColor Red
        $allInfraOk = $false
    }
}

if (-not $allInfraOk) {
    Write-Host ""
    Write-Host "[ERROR] One or more infrastructure endpoints not available." -ForegroundColor Red
    Write-Host "        Ensure the OCI K3s infrastructure is running and accessible via Tailscale." -ForegroundColor Yellow
    Write-Host "        Verify Tailscale is connected and the K3s node is reachable." -ForegroundColor Yellow
    exit 1
}

# 7. Verify local application ports are free
Write-Host "[PORTS] Verifying local application ports are free..." -ForegroundColor Yellow
$appPorts = @(8080, 8081, 8082, 8083)
$portsInUse = @()
foreach ($port in $appPorts) {
    $conn = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if ($conn) {
        $portsInUse += $port
    }
}
if ($portsInUse.Count -gt 0) {
    Write-Host "[ERROR] Ports already in use: $($portsInUse -join ', ')" -ForegroundColor Red
    Write-Host "        Stop existing services or run .\scripts\oci-k8s-disconnect.ps1" -ForegroundColor Yellow
    exit 1
}
Write-Host "[OK] All application ports (8080-8083) are free" -ForegroundColor Green

# ============================================================
# BUILD SERVICES
# ============================================================

Write-Host ""
Write-Host "[BUILD] Building services with Maven..." -ForegroundColor Cyan

$servicesToBuild = @("customer-service", "claims-service", "agent-service", "api-gateway")
$buildSuccess = $true

foreach ($svc in $servicesToBuild) {
    Write-Host "  Building $svc..." -NoNewline
    $buildResult = & $mvnCmd -pl $svc -am -DskipTests clean compile -q
    if ($LASTEXITCODE -eq 0) {
        Write-Host " [OK]" -ForegroundColor Green
    } else {
        Write-Host " [FAIL]" -ForegroundColor Red
        $buildSuccess = $false
    }
}

if (-not $buildSuccess) {
    Write-Host "[ERROR] Build failed. Check output above." -ForegroundColor Red
    exit 1
}

Write-Host "[OK] All services built successfully" -ForegroundColor Green

# ============================================================
# START SERVICES
# ============================================================

Write-Host ""
Write-Host "[START] Launching services with spring-boot:run..." -ForegroundColor Cyan

$services = @(
    @{
        Name = "customer-service"
        Dir  = "customer-service"
        Port = 8081
        Env  = @{
            "SPRING_PROFILES_ACTIVE" = $envVars["SPRING_PROFILES_ACTIVE"]
            "SPRING_KAFKA_BOOTSTRAP_SERVERS" = $envVars["SPRING_KAFKA_BOOTSTRAP_SERVERS"]
            "SPRING_DATA_REDIS_HOST" = $envVars["SPRING_DATA_REDIS_HOST"]
            "SPRING_DATA_REDIS_PORT" = $envVars["SPRING_DATA_REDIS_PORT"]
            "SPRING_DATASOURCE_URL" = "jdbc:postgresql://$($envVars['POSTGRES_HOST']):$($envVars['POSTGRES_PORT'])/claimassist_customer?sslmode=disable"
            "SPRING_DATASOURCE_USERNAME" = $envVars["POSTGRES_USER"]
            "SPRING_DATASOURCE_PASSWORD" = $envVars["POSTGRES_PASSWORD"]
            "KEYCLOAK_ISSUER_URI" = $envVars["KEYCLOAK_ISSUER_URI"]
            "KEYCLOAK_JWKS_URI" = $envVars["KEYCLOAK_JWKS_URI"]
            "SERVICE_TOKEN_URI" = "$($envVars['KEYCLOAK_SERVER_URL'])/realms/$($envVars['KEYCLOAK_REALM'])/protocol/openid-connect/token"
            "SERVICE_CLIENT_ID" = "claimassist-admin-service"
            "SERVICE_CLIENT_SECRET" = "local-dev-admin-client-secret"
            "ZIPKIN_ENDPOINT" = $envVars["ZIPKIN_ENDPOINT"]
            "LOKI_HOST" = $envVars["LOKI_HOST"]
            "LOKI_PORT" = $envVars["LOKI_PORT"]
            "SERVER_PORT" = "8081"
            "CLAIMASSIST_DEV_ID" = $envVars["CLAIMASSIST_DEV_ID"]
            "SPRING_CLOUD_CONFIG_ENABLED" = "false"
        }
    }
    @{
        Name = "claims-service"
        Dir  = "claims-service"
        Port = 8082
        Env  = @{
            "SPRING_PROFILES_ACTIVE" = $envVars["SPRING_PROFILES_ACTIVE"]
            "SPRING_KAFKA_BOOTSTRAP_SERVERS" = $envVars["SPRING_KAFKA_BOOTSTRAP_SERVERS"]
            "SPRING_DATA_REDIS_HOST" = $envVars["SPRING_DATA_REDIS_HOST"]
            "SPRING_DATA_REDIS_PORT" = $envVars["SPRING_DATA_REDIS_PORT"]
            "SPRING_DATASOURCE_URL" = "jdbc:postgresql://$($envVars['POSTGRES_HOST']):$($envVars['POSTGRES_PORT'])/claimassist_claims?serverTimezone=UTC"
            "SPRING_DATASOURCE_USERNAME" = $envVars["POSTGRES_USER"]
            "SPRING_DATASOURCE_PASSWORD" = $envVars["POSTGRES_PASSWORD"]
            "KEYCLOAK_ISSUER_URI" = $envVars["KEYCLOAK_ISSUER_URI"]
            "KEYCLOAK_JWKS_URI" = $envVars["KEYCLOAK_JWKS_URI"]
            "SERVICE_TOKEN_URI" = "$($envVars['KEYCLOAK_SERVER_URL'])/realms/$($envVars['KEYCLOAK_REALM'])/protocol/openid-connect/token"
            "SERVICE_CLIENT_ID" = "claimassist-admin-service"
            "SERVICE_CLIENT_SECRET" = "local-dev-admin-client-secret"
            "ZIPKIN_ENDPOINT" = $envVars["ZIPKIN_ENDPOINT"]
            "LOKI_HOST" = $envVars["LOKI_HOST"]
            "LOKI_PORT" = $envVars["LOKI_PORT"]
            "SERVER_PORT" = "8082"
            "CLAIMASSIST_DEV_ID" = $envVars["CLAIMASSIST_DEV_ID"]
            "SPRING_CLOUD_CONFIG_ENABLED" = "false"
        }
    }
    @{
        Name = "agent-service"
        Dir  = "agent-service"
        Port = 8083
        Env  = @{
            "SPRING_PROFILES_ACTIVE" = $envVars["SPRING_PROFILES_ACTIVE"]
            "SPRING_KAFKA_BOOTSTRAP_SERVERS" = $envVars["SPRING_KAFKA_BOOTSTRAP_SERVERS"]
            "SPRING_DATA_REDIS_HOST" = $envVars["SPRING_DATA_REDIS_HOST"]
            "SPRING_DATA_REDIS_PORT" = $envVars["SPRING_DATA_REDIS_PORT"]
            "SPRING_DATASOURCE_URL" = "jdbc:postgresql://$($envVars['POSTGRES_HOST']):$($envVars['POSTGRES_PORT'])/claimassist_agent?serverTimezone=UTC"
            "SPRING_DATASOURCE_USERNAME" = $envVars["POSTGRES_USER"]
            "SPRING_DATASOURCE_PASSWORD" = $envVars["POSTGRES_PASSWORD"]
            "KEYCLOAK_ISSUER_URI" = $envVars["KEYCLOAK_ISSUER_URI"]
            "KEYCLOAK_JWKS_URI" = $envVars["KEYCLOAK_JWKS_URI"]
            "SERVICE_TOKEN_URI" = "$($envVars['KEYCLOAK_SERVER_URL'])/realms/$($envVars['KEYCLOAK_REALM'])/protocol/openid-connect/token"
            "SERVICE_CLIENT_ID" = "internal-service-client"
            "SERVICE_CLIENT_SECRET" = "internal-service-secret"
            "ZIPKIN_ENDPOINT" = $envVars["ZIPKIN_ENDPOINT"]
            "LOKI_HOST" = $envVars["LOKI_HOST"]
            "LOKI_PORT" = $envVars["LOKI_PORT"]
            "SERVER_PORT" = "8083"
            "CLAIMASSIST_DEV_ID" = $envVars["CLAIMASSIST_DEV_ID"]
            "SPRING_CLOUD_CONFIG_ENABLED" = "false"
        }
    }
    @{
        Name = "api-gateway"
        Dir  = "api-gateway"
        Port = 8080
        Env  = @{
            "SPRING_PROFILES_ACTIVE" = $envVars["SPRING_PROFILES_ACTIVE"]
            "SPRING_KAFKA_BOOTSTRAP_SERVERS" = $envVars["SPRING_KAFKA_BOOTSTRAP_SERVERS"]
            "SPRING_DATA_REDIS_HOST" = $envVars["SPRING_DATA_REDIS_HOST"]
            "SPRING_DATA_REDIS_PORT" = $envVars["SPRING_DATA_REDIS_PORT"]
            "KEYCLOAK_ISSUER_URI" = $envVars["KEYCLOAK_ISSUER_URI"]
            "KEYCLOAK_JWKS_URI" = $envVars["KEYCLOAK_JWKS_URI"]
            "ZIPKIN_ENDPOINT" = $envVars["ZIPKIN_ENDPOINT"]
            "LOKI_HOST" = $envVars["LOKI_HOST"]
            "LOKI_PORT" = $envVars["LOKI_PORT"]
            "SERVER_PORT" = "8080"
            "CLAIMASSIST_DEV_ID" = $envVars["CLAIMASSIST_DEV_ID"]
            "SPRING_CLOUD_CONFIG_ENABLED" = "false"
        }
    }
)

$launchedProcesses = @()

function Start-Service {
    param(
        [string]$Name,
        [string]$Dir,
        [int]$Port,
        [hashtable]$Env
    )

    Write-Host "======================================"
    Write-Host "[$Name] Starting on port $Port..." -ForegroundColor Cyan

    $svcDir = Join-Path $repoRoot $Dir
    $logFile = Join-Path $logDir "$Name.log"
    $errFile = Join-Path $logDir "$Name.err.log"

    # Build environment argument string for Maven
    $envArgs = @()
    foreach ($key in $Env.Keys) {
        $envArgs += "-D$key=$($Env[$key])"
    }

    # Start process with spring-boot:run
    $mavenArgs = @(
        "spring-boot:run",
        "-pl", $Dir,
        "-Dspring-boot.run.profiles=$Profile",
        "-Dspring-boot.run.jvmArguments=-Duser.timezone=Asia/Kolkata"
    ) + $envArgs

    Write-Host "[$Name] Command: mvn $($mavenArgs -join ' ')" -ForegroundColor DarkGray

    $proc = Start-Process -FilePath $mvnCmd.Source -ArgumentList $mavenArgs `
        -WorkingDirectory $repoRoot `
        -RedirectStandardOutput $logFile `
        -RedirectStandardError $errFile `
        -PassThru -WindowStyle Hidden

    $launchedProcesses += @{
        Name = $Name
        Process = $proc
        Port = $Port
        LogFile = $logFile
        ErrFile = $errFile
        Dir = $svcDir
    }

    Write-Host "[$Name] Launched with PID=$($proc.Id)" -ForegroundColor Green
    return $proc
}

# Start services in order (gateway last since it routes to others)
$startOrder = @("customer-service", "claims-service", "agent-service", "api-gateway")
foreach ($svcName in $startOrder) {
    $svc = $services | Where-Object { $_.Name -eq $svcName }
    Start-Service -Name $svc.Name -Dir $svc.Dir -Port $svc.Port -Env $svc.Env
}

# ============================================================
# WAIT FOR HEALTH
# ============================================================

Write-Host ""
Write-Host "[HEALTH] Waiting for services to become healthy..." -ForegroundColor Cyan

function Get-HealthStatus {
    param([int]$port)
    try {
        $r = Invoke-WebRequest -Uri "http://localhost:$port/actuator/health" -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop
        $content = $r.Content
        if ($content -is [byte[]]) {
            $content = [System.Text.Encoding]::UTF8.GetString($content)
        }
        $j = $content | ConvertFrom-Json
        return $j.status
    } catch {
        return $null
    }
}

function Wait-Healthy {
    param([string]$Name, [int]$Port, [int]$TimeoutSec = 180)
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt $TimeoutSec) {
        $status = Get-HealthStatus $Port
        if ($status -eq "UP") { return $true }
        Start-Sleep -Seconds 3
    }
    return $false
}

$allHealthy = $true
foreach ($procInfo in $launchedProcesses) {
    $ok = Wait-Healthy -Name $procInfo.Name -Port $procInfo.Port -TimeoutSec 180
    if ($ok) {
        Write-Host "[$($procInfo.Name)] UP (port $($procInfo.Port), /actuator/health=UP) PID=$($procInfo.Process.Id)" -ForegroundColor Green
    } else {
        Write-Host "[$($procInfo.Name)] FAILED to become healthy within timeout. PID=$($procInfo.Process.Id) port=$($procInfo.Port)" -ForegroundColor Red
        Write-Host "--- last log lines ---"
        if (Test-Path $procInfo.LogFile) { Get-Content $procInfo.LogFile -Tail 40 }
        Write-Host "--- last err lines ---"
        if (Test-Path $procInfo.ErrFile) { Get-Content $procInfo.ErrFile -Tail 40 }
        $allHealthy = $false
    }
}

Write-Host ""
Write-Host "======================================"
Write-Host "Startup Complete" -ForegroundColor Cyan
Write-Host "======================================"

if ($allHealthy) {
    Write-Host "[OK] All services started successfully" -ForegroundColor Green
} else {
    Write-Host "[WARN] Some services failed to start" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Service Endpoints:" -ForegroundColor Cyan
Write-Host "  API Gateway:      http://localhost:8080" -ForegroundColor Yellow
Write-Host "  Customer Service: http://localhost:8081" -ForegroundColor Yellow
Write-Host "  Claims Service:   http://localhost:8082" -ForegroundColor Yellow
Write-Host "  Agent Service:    http://localhost:8083" -ForegroundColor Yellow
Write-Host ""
Write-Host "Health Checks:" -ForegroundColor Cyan
Write-Host "  http://localhost:8080/actuator/health" -ForegroundColor Yellow
Write-Host "  http://localhost:8081/actuator/health" -ForegroundColor Yellow
Write-Host "  http://localhost:8082/actuator/health" -ForegroundColor Yellow
Write-Host "  http://localhost:8083/actuator/health" -ForegroundColor Yellow
Write-Host ""
Write-Host "Observability:" -ForegroundColor Cyan
Write-Host "  Kafka UI:    http://localhost:18081" -ForegroundColor Yellow
Write-Host "  Grafana:     http://localhost:13000" -ForegroundColor Yellow
Write-Host "  Prometheus:  http://localhost:19090" -ForegroundColor Yellow
Write-Host "  Zipkin:      http://localhost:19411" -ForegroundColor Yellow
Write-Host "  Loki:        http://localhost:13100" -ForegroundColor Yellow
Write-Host "  Keycloak:    http://localhost:18080" -ForegroundColor Yellow
Write-Host ""
Write-Host "Process Management:" -ForegroundColor Cyan
Write-Host "  Logs directory: $logDir" -ForegroundColor Yellow
Write-Host "  To stop all services, press Ctrl+C in this window" -ForegroundColor Yellow
Write-Host "  Or run: .\scripts\oci-k8s-disconnect.ps1" -ForegroundColor Yellow
Write-Host ""

# ============================================================
# CLEAN SHUTDOWN HANDLING
# ============================================================

$shutdown = $false

function Stop-AllServices {
    if ($shutdown) { return }
    $shutdown = $true

    Write-Host ""
    Write-Host "[SHUTDOWN] Stopping all services..." -ForegroundColor Yellow

    foreach ($procInfo in $launchedProcesses) {
        if (-not $procInfo.Process.HasExited) {
            Write-Host "  Stopping $($procInfo.Name) (PID=$($procInfo.Process.Id))..." -ForegroundColor Yellow
            try {
                Stop-Process -Id $procInfo.Process.Id -Force -ErrorAction SilentlyContinue
            } catch {}
        }
    }

    Write-Host "[SHUTDOWN] All services stopped" -ForegroundColor Green
}

# Register cleanup on exit
Register-EngineEvent -SourceIdentifier "PowerShell.Exiting" -Action { Stop-AllServices } | Out-Null

# Wait for Ctrl+C or process exit
Write-Host "Press Ctrl+C to stop all services..." -ForegroundColor Cyan
try {
    while (-not $shutdown) {
        Start-Sleep -Seconds 5
        # Check if any process died unexpectedly
        foreach ($procInfo in $launchedProcesses) {
            if ($procInfo.Process.HasExited) {
                Write-Host "[$($procInfo.Name)] Process exited unexpectedly (code=$($procInfo.Process.ExitCode))" -ForegroundColor Red
            }
        }
    }
} catch {
    Stop-AllServices
}
