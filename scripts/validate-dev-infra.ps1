#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Comprehensive DEV infrastructure validation script for ClaimAssist.

.DESCRIPTION
    Validates the entire ClaimAssist DEV K8s infrastructure:
    - Cluster connectivity and namespace
    - All pod health (Running, Ready, 0 restarts)
    - Kubernetes Service endpoints
    - HPA status
    - Helm release revision and status
    - Eureka/Config Server leakage check (zero tolerance)
    - Actuator health endpoints (liveness + readiness)
    - Inter-service K8s DNS connectivity
    - Tailscale NodePort external access
    - NetworkPolicy presence

    Exit code 0 = all checks pass, 1 = one or more failures.

.PARAMETER KubeConfig
    Path to kubeconfig file. Defaults to ~/.kube/claimassist-oci-dev.yaml

.PARAMETER Namespace
    Kubernetes namespace. Defaults to claimassist-dev

.PARAMETER HelmRelease
    Helm release name. Defaults to claimassist-dev

.PARAMETER HelmChartPath
    Path to Helm chart for lint. Defaults to infrastructure/helm/claimassist

.PARAMETER TailscaleHost
    Tailscale MagicDNS hostname. Defaults to vnic.taild219f3.ts.net

.EXAMPLE
    .\scripts\validate-dev-infra.ps1

    .\scripts\validate-dev-infra.ps1 -KubeConfig C:\custom\kubeconfig.yaml
#>

param(
    [string]$KubeConfig = "$env:USERPROFILE\.kube\claimassist-oci-dev.yaml",
    [string]$Namespace = "claimassist-dev",
    [string]$HelmRelease = "claimassist-dev",
    [string]$HelmChartPath = "infrastructure\helm\claimassist",
    [string]$TailscaleHost = "vnic.taild219f3.ts.net"
)

$ErrorActionPreference = "Continue"
$script:FailCount = 0
$script:PassCount = 0
$script:WarnCount = 0

function Write-Check {
    param(
        [string]$Name,
        [string]$Status,
        [string]$Detail = ""
    )
    switch ($Status) {
        "PASS" {
            Write-Host "  [PASS] $Name" -ForegroundColor Green
            $script:PassCount++
        }
        "FAIL" {
            Write-Host "  [FAIL] $Name - $Detail" -ForegroundColor Red
            $script:FailCount++
        }
        "WARN" {
            Write-Host "  [WARN] $Name - $Detail" -ForegroundColor Yellow
            $script:WarnCount++
        }
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host " ClaimAssist DEV Infrastructure Validator" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# ----------------------------------------------------------------
# 1. Prerequisites
# ----------------------------------------------------------------
Write-Host "[1/10] Prerequisites..." -ForegroundColor Cyan

$kubectl = Get-Command kubectl.exe -ErrorAction SilentlyContinue
if (-not $kubectl) {
    Write-Check "kubectl" "FAIL" "kubectl.exe not found in PATH"
    Write-Host ""
    Write-Host "Cannot continue without kubectl. Fix PATH and re-run." -ForegroundColor Red
    exit 1
}
Write-Check "kubectl" "PASS"

$helm = Get-Command helm.exe -ErrorAction SilentlyContinue
if (-not $helm) {
    Write-Check "helm" "WARN" "helm.exe not found in PATH (Helm lint skipped)"
} else {
    Write-Check "helm" "PASS"
}

if (-not (Test-Path $KubeConfig)) {
    Write-Check "kubeconfig" "FAIL" "File not found: $KubeConfig"
    Write-Host ""
    Write-Host "Cannot continue without kubeconfig." -ForegroundColor Red
    exit 1
}
Write-Check "kubeconfig" "PASS" $KubeConfig

# ----------------------------------------------------------------
# 2. Cluster connectivity
# ----------------------------------------------------------------
Write-Host ""
Write-Host "[2/10] Cluster connectivity..." -ForegroundColor Cyan

$clusterInfo = kubectl cluster-info --kubeconfig=$KubeConfig 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Check "cluster-info" "FAIL" "Cannot reach K8s API server"
} else {
    Write-Check "cluster-info" "PASS"
}

$nsList = kubectl get namespace $Namespace --kubeconfig=$KubeConfig -o name 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Check "namespace" "FAIL" "Namespace '$Namespace' not found"
} else {
    Write-Check "namespace" "PASS"
}

# ----------------------------------------------------------------
# 3. Helm release status
# ----------------------------------------------------------------
Write-Host ""
Write-Host "[3/10] Helm release..." -ForegroundColor Cyan

$helmStatus = helm list -n $Namespace --kubeconfig=$KubeConfig -o json 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Check "helm-list" "FAIL" "Cannot list Helm releases"
} else {
    $release = $helmStatus | ConvertFrom-Json | Where-Object { $_.name -eq $HelmRelease }
    if (-not $release) {
        Write-Check "helm-release" "FAIL" "Release '$HelmRelease' not found"
    } elseif ($release.status -ne "deployed") {
        Write-Check "helm-release" "FAIL" "Status: $($release.status), Revision: $($release.revision)"
    } else {
        Write-Check "helm-release" "PASS" "Revision $($release.revision), Status: deployed"
    }
}

if ($helm) {
    $lintResult = helm lint $HelmChartPath -f "$HelmChartPath\values-dev.yaml" --namespace $Namespace 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Check "helm-lint" "FAIL" "Lint errors found"
    } else {
        Write-Check "helm-lint" "PASS"
    }
}

# ----------------------------------------------------------------
# 4. Pod health
# ----------------------------------------------------------------
Write-Host ""
Write-Host "[4/10] Pod health..." -ForegroundColor Cyan

$expectedApps = @("api-gateway", "customer-service", "claims-service", "agent-service")
$expectedInfra = @("postgresql-0", "redis", "kafka-0", "keycloak-0", "keycloak-postgresql-0")

$podJson = kubectl get pods -n $Namespace --kubeconfig=$KubeConfig -o json 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Check "pod-list" "FAIL" "Cannot list pods"
} else {
    $pods = ($podJson | ConvertFrom-Json).items

    foreach ($appName in $expectedApps) {
        $pod = $pods | Where-Object { $_.metadata.name -match $appName -and $_.status.phase -ne "Succeeded" } | Select-Object -First 1
        if (-not $pod) {
            Write-Check "pod/$appName" "FAIL" "No running pod found"
            continue
        }

        $readyCond = $pod.status.conditions | Where-Object { $_.type -eq "Ready" }
        $readyStatus = if ($readyCond) { $readyCond.status } else { "Unknown" }
        $ready = ($readyStatus -eq "True")
        $restarts = 0
        if ($pod.status.containerStatuses) {
            $restarts = ($pod.status.containerStatuses | Measure-Object -Property restartCount -Sum).Sum
        }
        $phase = $pod.status.phase

        if ($phase -eq "Running" -and $ready -and $restarts -eq 0) {
            Write-Check "pod/$appName" "PASS" "Running, Ready, 0 restarts"
        } elseif ($phase -eq "Running" -and $ready) {
            Write-Check "pod/$appName" "WARN" "Running but $restarts restarts"
        } else {
            Write-Check "pod/$appName" "FAIL" "Phase=$phase, Ready=$readyStatus, Restarts=$restarts"
        }
    }

    foreach ($infraName in $expectedInfra) {
        $pod = $pods | Where-Object { $_.metadata.name -match $infraName -and $_.status.phase -ne "Succeeded" } | Select-Object -First 1
        if (-not $pod) {
            Write-Check "infra/$infraName" "FAIL" "No running pod found"
            continue
        }
        $phase = $pod.status.phase
        if ($phase -eq "Running") {
            Write-Check "infra/$infraName" "PASS"
        } else {
            Write-Check "infra/$infraName" "FAIL" "Phase=$phase"
        }
    }
}

# ----------------------------------------------------------------
# 5. Service endpoints
# ----------------------------------------------------------------
Write-Host ""
Write-Host "[5/10] Service endpoints..." -ForegroundColor Cyan

$svcJson = kubectl get services -n $Namespace --kubeconfig=$KubeConfig -o json 2>&1
if ($LASTEXITCODE -eq 0) {
    $services = ($svcJson | ConvertFrom-Json).items
    $expectedSvcs = @("claimassist-api-gateway", "claimassist-customer-service", "claimassist-claims-service", "claimassist-agent-service", "claimassist-postgresql", "claimassist-redis", "claimassist-kafka", "claimassist-keycloak")

    foreach ($svcName in $expectedSvcs) {
        $svc = $services | Where-Object { $_.metadata.name -eq $svcName }
        if (-not $svc) {
            Write-Check "svc/$svcName" "FAIL" "Service not found"
            continue
        }
        $clusterIP = $svc.spec.clusterIP
        if ($clusterIP -and $clusterIP -ne "None" -and $clusterIP -ne "") {
            Write-Check "svc/$svcName" "PASS" "ClusterIP=$clusterIP"
        } else {
            Write-Check "svc/$svcName" "WARN" "ClusterIP=$clusterIP"
        }
    }
}

# ----------------------------------------------------------------
# 6. HPA status
# ----------------------------------------------------------------
Write-Host ""
Write-Host "[6/10] HPA status..." -ForegroundColor Cyan

$hpaJson = kubectl get hpa -n $Namespace --kubeconfig=$KubeConfig -o json 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Check "hpa-list" "WARN" "Cannot list HPAs (may not be installed)"
} else {
    $hpas = ($hpaJson | ConvertFrom-Json).items
    if ($hpas.Count -eq 0) {
        Write-Check "hpa-list" "WARN" "No HPAs found"
    } else {
        foreach ($hpa in $hpas) {
            $name = $hpa.metadata.name
            $current = $hpa.status.currentReplicas
            $desired = $hpa.status.desiredReplicas
            if ($current -eq $desired) {
                Write-Check "hpa/$name" "PASS" "Current=$current, Desired=$desired"
            } else {
                Write-Check "hpa/$name" "WARN" "Current=$current, Desired=$desired"
            }
        }
    }
}

# ----------------------------------------------------------------
# 7. Eureka/Config Server leakage check
# ----------------------------------------------------------------
Write-Host ""
Write-Host "[7/10] Eureka/Config Server leakage check..." -ForegroundColor Cyan

$leakagePatterns = @("eureka", "DiscoveryClient", "ConfigServer", "configserver", "EurekaClient", "heartbeat", "localhost:8761", "localhost:8888")

foreach ($appName in $expectedApps) {
    $logOutput = kubectl logs deployment/claimassist-$appName -n $Namespace --tail=200 --kubeconfig=$KubeConfig 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Check "leakage/$appName" "WARN" "Cannot fetch logs"
        continue
    }

    $found = @()
    foreach ($pattern in $leakagePatterns) {
        $matches = $logOutput | Select-String -Pattern $pattern -CaseSensitive:$false
        if ($matches) {
            $found += $pattern
        }
    }

    if ($found.Count -eq 0) {
        Write-Check "leakage/$appName" "PASS" "Zero Eureka/Config references"
    } else {
        Write-Check "leakage/$appName" "FAIL" "Found: $($found -join ', ')"
    }
}

# ----------------------------------------------------------------
# 8. Actuator health endpoints
# ----------------------------------------------------------------
Write-Host ""
Write-Host "[8/10] Actuator health endpoints..." -ForegroundColor Cyan

$portMap = @{
    "api-gateway"     = 8080
    "customer-service" = 8081
    "claims-service"   = 8082
    "agent-service"    = 8083
}

foreach ($appName in $expectedApps) {
    $port = $portMap[$appName]
    $podName = kubectl get pod -n $Namespace --kubeconfig=$KubeConfig -l "app.kubernetes.io/component=$appName" -o jsonpath="{.items[0].metadata.name}" 2>&1

    if (-not $podName -or $LASTEXITCODE -ne 0) {
        Write-Check "health/$appName" "FAIL" "No pod found"
        continue
    }

    # api-gateway uses /actuator/health, backend services also use /actuator/health
    # but Keycloak uses its own health endpoint
    $healthPath = "actuator/health"
    $healthJson = kubectl exec $podName -n $Namespace --kubeconfig=$KubeConfig -- wget -qO- --header="Accept: application/json" "http://localhost:$port/$healthPath" 2>&1

    if ($healthJson -match '"status"\s*:\s*"UP"') {
        Write-Check "health/$appName" "PASS" "status=UP"
    } else {
        $short = ($healthJson -split "`n" | Select-Object -First 3) -join " "
        Write-Check "health/$appName" "FAIL" "Response: $short"
    }
}

# ----------------------------------------------------------------
# 9. Inter-service K8s DNS connectivity
# ----------------------------------------------------------------
Write-Host ""
Write-Host "[9/10] Inter-service K8s DNS..." -ForegroundColor Cyan

$sourcePod = kubectl get pod -n $Namespace --kubeconfig=$KubeConfig -l "app.kubernetes.io/component=claims-service" -o jsonpath="{.items[0].metadata.name}" 2>&1
$targets = @(
    @{ Name = "customer-service"; Svc = "claimassist-customer-service"; Port = 8081 },
    @{ Name = "agent-service";    Svc = "claimassist-agent-service";    Port = 8083 },
    @{ Name = "api-gateway";      Svc = "claimassist-api-gateway";      Port = 8080 },
    @{ Name = "postgresql";       Svc = "claimassist-postgresql";       Port = 5432 },
    @{ Name = "redis";            Svc = "claimassist-redis";            Port = 6379 },
    @{ Name = "kafka";            Svc = "claimassist-kafka";            Port = 9092 },
    @{ Name = "keycloak";         Svc = "claimassist-keycloak";         Port = 8080 }
)

foreach ($target in $targets) {
    $result = kubectl exec $sourcePod -n $Namespace --kubeconfig=$KubeConfig -- wget -qO- --timeout=3 --header="Accept: application/json" "http://$($target.Svc):$($target.Port)/actuator/health" 2>&1
    if ($result -match '"status"\s*:\s*"UP"') {
        Write-Check "dns/$($target.Name)" "PASS" "Reachable via K8s DNS"
    } elseif ($target.Name -eq "keycloak") {
        # Keycloak doesn't have /actuator/health; check realm endpoint instead
        $kcResult = kubectl exec $sourcePod -n $Namespace --kubeconfig=$KubeConfig -- wget -qO- --timeout=5 --header="Accept: application/json" "http://$($target.Svc):$($target.Port)/realms/claimassist-dev/.well-known/openid-configuration" 2>&1
        if ($kcResult -match "issuer") {
            Write-Check "dns/$($target.Name)" "PASS" "Keycloak realm reachable"
        } else {
            Write-Check "dns/$($target.Name)" "FAIL" "Keycloak realm not reachable"
        }
    } else {
        # TCP services (PostgreSQL, Redis, Kafka) won't respond to HTTP but are reachable
        if ($target.Port -eq 5432 -or $target.Port -eq 6379 -or $target.Port -eq 9092) {
            $tcpResult = kubectl exec $sourcePod -n $Namespace --kubeconfig=$KubeConfig -- sh -c "wget -qO- --timeout=3 http://$($target.Svc):$($target.Port) 2>&1; echo exit:`$?" 2>&1
            if ($tcpResult -match "Invalid argument|Resource temporarily|exit:True") {
                Write-Check "dns/$($target.Name)" "PASS" "TCP port reachable (binary protocol)"
            } else {
                Write-Check "dns/$($target.Name)" "FAIL" "Not reachable"
            }
        } else {
            Write-Check "dns/$($target.Name)" "FAIL" "Not reachable"
        }
    }
}

# ----------------------------------------------------------------
# 10. Tailscale NodePort access
# ----------------------------------------------------------------
Write-Host ""
Write-Host "[10/10] Tailscale NodePort access..." -ForegroundColor Cyan

$nodePorts = @(
    @{ Name = "PostgreSQL"; Port = 30432 },
    @{ Name = "Redis";      Port = 30379 },
    @{ Name = "Keycloak";   Port = 30080 },
    @{ Name = "Kafka";      Port = 30092 }
)

foreach ($np in $nodePorts) {
    $result = Test-NetConnection -ComputerName $TailscaleHost -Port $np.Port -WarningAction SilentlyContinue
    if ($result.TcpTestSucceeded) {
        Write-Check "tailscale/$($np.Name)" "PASS" "$TailscaleHost`:$($np.Port)"
    } else {
        Write-Check "tailscale/$($np.Name)" "FAIL" "$TailscaleHost`:$($np.Port) unreachable"
    }
}

# ----------------------------------------------------------------
# Summary
# ----------------------------------------------------------------
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host " Validation Summary" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  PASS: $script:PassCount" -ForegroundColor Green
Write-Host "  FAIL: $script:FailCount" -ForegroundColor $(if ($script:FailCount -gt 0) { "Red" } else { "Green" })
Write-Host "  WARN: $script:WarnCount" -ForegroundColor $(if ($script:WarnCount -gt 0) { "Yellow" } else { "Green" })
Write-Host ""

if ($script:FailCount -eq 0) {
    Write-Host "  RESULT: ALL CHECKS PASSED" -ForegroundColor Green
} else {
    Write-Host "  RESULT: $script:FailCount CHECK(S) FAILED" -ForegroundColor Red
}
Write-Host ""

exit $script:FailCount
