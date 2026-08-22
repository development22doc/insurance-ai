<#
Phase 3B — Live vs Rendered Drift Analysis
Run this script on the Windows PowerShell machine where the OCI SSH tunnel and kubeconfig exist.
It will:
 - fetch the 4 live Deployments into workspace files
 - extract key fields from the live YAMLs and from the rendered manifest
 - produce a human-readable drift report at: PHASE_3B_LIVE_DRIFT_REPORT.txt

Usage (PowerShell):
  Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass;
  cd "D:\Mayur\claimsassist\insurance-ai-platform";
  .\scripts\phase3b-drift-analysis.ps1

Notes:
 - All kubectl and helm commands are run with the kubeconfig at $env:USERPROFILE\.kube\claimassist-oci-dev.yaml
 - This script does NOT modify the cluster. It only reads resources.
 - The script assumes the rendered manifest is at: rendered-deployment-validation.yaml
#>

$ErrorActionPreference = 'Stop'
$workspace = Split-Path -Parent $MyInvocation.MyCommand.Path | Split-Path -Parent
Set-Location $workspace

# Ensure all Out-File calls produce UTF8 output. Without this, some Out-File calls
# on Windows PowerShell default to UTF-16 and create files that look like only the
# first lines are readable in UTF-8 viewers. This forces consistent UTF8 encoding.
$PSDefaultParameterValues['Out-File:Encoding'] = 'utf8'

$kubeconfig = "$env:USERPROFILE\.kube\claimassist-oci-dev.yaml"
$namespace = 'claimassist-dev'
$services = @('api-gateway','customer-service','claims-service','agent-service')

Write-Host "Using kubeconfig: $kubeconfig"
Write-Host "Namespace: $namespace"

# Fetch live deployments (wrap executions so kubectl failures don't abort the whole run)
foreach ($svc in $services) {
    $out = Join-Path $workspace ("live-deployment-claimassist-$svc.yaml")
    Write-Host "Fetching live deployment for $svc -> $out"
    try {
        & kubectl --kubeconfig $kubeconfig -n $namespace get deployment claimassist-$svc -o yaml 2>&1 | Tee-Object -FilePath $out | Out-Null
    } catch {
        # Record the failure into the file (so later steps can report it) and continue
        "FAILED TO COLLECT: kubectl get deployment claimassist-$svc -n $namespace" | Out-File $out
        "ERROR: $($_.Exception.Message)" | Out-File $out -Append
        Write-Host "Warning: fetching deployment claimassist-$svc failed: $($_.Exception.Message)"
        continue
    }
}

# Helper: extract a block from the rendered file that contains the deployment for a service
function Get-RenderedBlock {
    param($serviceName)
    $rendered = Join-Path $workspace 'rendered-deployment-validation.yaml'
    if (-not (Test-Path $rendered)) { Throw "Rendered file not found: $rendered" }
    $content = Get-Content $rendered -Raw -ErrorAction Stop
    # split by YAML document separator '---' (may have Windows line endings)
    $docs = $content -split "(?m)^---\s*$"
    foreach ($d in $docs) {
        if ($d -match "name:\s*claimassist-$serviceName\b") { return $d }
    }
    return $null
}

# Helper: simple extraction of keys from a YAML text block (not a full YAML parser)
function Extract-FieldsFromBlock {
    param($text)
    $lines = $text -split "\r?\n"
    $result = @{}

    # image
    $m = ($lines | Select-String -Pattern "\bimage:\s*(\S+)" -AllMatches)
    if ($m) { $result['image'] = $m.Matches[0].Groups[1].Value }

    # imagePullPolicy
    $m = ($lines | Select-String -Pattern "\bimagePullPolicy:\s*(\S+)" -AllMatches)
    if ($m) { $result['imagePullPolicy'] = $m.Matches[0].Groups[1].Value }

    # imagePullSecrets (collect names under the nearest imagePullSecrets: block)
    $ips = @()
    for ($i=0; $i -lt $lines.Length; $i++) {
        if ($lines[$i] -match "^\s*imagePullSecrets:\s*$") {
            # collect next up to 10 lines for '- name:'
            for ($j=1; $j -le 10; $j++) {
                if ($i+$j -ge $lines.Length) { break }
                if ($lines[$i+$j] -match "-\s*name:\s*(\S+)") { $ips += $Matches[1] }
            }
        }
    }
    $result['imagePullSecrets'] = ($ips -join ', ')

    # serviceAccountName
    $m = ($lines | Select-String -Pattern "^\s*serviceAccountName:\s*(\S+)" -AllMatches)
    if ($m) { $result['serviceAccountName'] = $m.Matches[0].Groups[1].Value }

    # volumeMounts: find mount for config
    $vmName = ''
    for ($i=0; $i -lt $lines.Length; $i++) {
        if ($lines[$i] -match "^\s*volumeMounts:\s*$") {
            for ($j=1; $j -le 10; $j++) {
                if ($i+$j -ge $lines.Length) { break }
                if ($lines[$i+$j] -match "-\s*name:\s*(\S+)") { $vmName = $Matches[1]; break }
            }
        }
    }
    $result['volumeMounts_config_name'] = $vmName
    # volumeMounts mountPath and readOnly
    $mountPath = '' ; $readOnly = ''
    for ($i=0; $i -lt $lines.Length; $i++) {
        if ($lines[$i] -match "-\s*name:\s*config") {
            for ($j=1; $j -le 6; $j++) {
                if ($i+$j -ge $lines.Length) { break }
                if ($lines[$i+$j] -match "mountPath:\s*(\S+)") { $mountPath = $Matches[1] }
                if ($lines[$i+$j] -match "readOnly:\s*(\S+)") { $readOnly = $Matches[1] }
            }
        }
    }
    $result['volumeMounts_config_mountPath'] = $mountPath
    $result['volumeMounts_config_readOnly'] = $readOnly

    # volumes (pod-level) : find 'volumes:' then find name: config and configMap.name
    $volName=''; $cmName=''
    for ($i=0; $i -lt $lines.Length; $i++) {
        if ($lines[$i] -match "^\s*volumes:\s*$") {
            for ($j=1; $j -le 20; $j++) {
                if ($i+$j -ge $lines.Length) { break }
                if ($lines[$i+$j] -match "-\s*name:\s*(\S+)") { $volName = $Matches[1] }
                if ($lines[$i+$j] -match "configMap:\s*$") {
                    # look for subsequent name: line
                    for ($k=1; $k -le 6; $k++) {
                        if ($i+$j+$k -ge $lines.Length) { break }
                        if ($lines[$i+$j+$k] -match "name:\s*(\S+)") { $cmName = $Matches[1]; break }
                    }
                }
                if ($volName -and $cmName) { break }
            }
        }
    }
    $result['volumes_config_name'] = $volName
    $result['volumes_config_configMap_name'] = $cmName

    # probes: presence of readiness/liveness + path
    $readinessPath=''; $livenessPath='';
    for ($i=0; $i -lt $lines.Length; $i++) {
        if ($lines[$i] -match "^\s*readinessProbe:\s*$") {
            for ($j=1; $j -le 10; $j++) {
                if ($i+$j -ge $lines.Length) { break }
                if ($lines[$i+$j] -match "path:\s*(\S+)") { $readinessPath = $Matches[1]; break }
            }
        }
        if ($lines[$i] -match "^\s*livenessProbe:\s*$") {
            for ($j=1; $j -le 10; $j++) {
                if ($i+$j -ge $lines.Length) { break }
                if ($lines[$i+$j] -match "path:\s*(\S+)") { $livenessPath = $Matches[1]; break }
            }
        }
    }
    $result['readinessProbe_path'] = $readinessPath
    $result['livenessProbe_path'] = $livenessPath

    # securityContext in container - runAsUser/runAsGroup
    $runAsUser=''; $runAsGroup=''; $runAsNonRoot=''
    for ($i=0; $i -lt $lines.Length; $i++) {
        if ($lines[$i] -match "^\s*securityContext:\s*$") {
            for ($j=1; $j -le 8; $j++) {
                if ($i+$j -ge $lines.Length) { break }
                if ($lines[$i+$j] -match "runAsUser:\s*(\S+)") { $runAsUser=$Matches[1] }
                if ($lines[$i+$j] -match "runAsGroup:\s*(\S+)") { $runAsGroup=$Matches[1] }
                if ($lines[$i+$j] -match "runAsNonRoot:\s*(\S+)") { $runAsNonRoot=$Matches[1] }
            }
        }
    }
    $result['security_runAsUser'] = $runAsUser
    $result['security_runAsGroup'] = $runAsGroup
    $result['security_runAsNonRoot'] = $runAsNonRoot

    # specific env vars: search for each by name and capture value or valueFrom reference
    $envNames = @('SPRING_PROFILES_ACTIVE','SPRING_CONFIG_IMPORT','SPRING_CLOUD_CONFIG_ENABLED','SPRING_CONFIG_ADDITIONAL_LOCATIONS','SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWKSETURI','SPRING_DATASOURCE_URL','SPRING_DATA_REDIS_HOST','SPRING_DATA_REDIS_PORT','SPRING_KAFKA_BOOTSTRAP_SERVERS','KEYCLOAK_ISSUER_URI')
    foreach ($ename in $envNames) {
        $val = ''
        for ($i=0; $i -lt $lines.Length; $i++) {
            if ($lines[$i] -match "-\s*name:\s*$ename\b") {
                # look ahead for value: or valueFrom
                for ($j=1; $j -le 6; $j++) {
                    if ($i+$j -ge $lines.Length) { break }
                    # Use a single-quoted regex to avoid PowerShell double-quote interpolation
                    if ($lines[$i+$j] -match 'value:\s*"?([^\"]*)"?\s*$') { $val = $Matches[1]; break }
                    if ($lines[$i+$j] -match "valueFrom:\s*") {
                        # capture secretKeyRef name/key if present
                        for ($k=1; $k -le 6; $k++) {
                            if ($i+$j+$k -ge $lines.Length) { break }
                            if ($lines[$i+$j+$k] -match "secretKeyRef:\s*") { continue }
                            if ($lines[$i+$j+$k] -match "name:\s*(\S+)") { $val = "valueFromSecret:" + $Matches[1]; break }
                        }
                        break
                    }
                }
            }
        }
        $result[$ename] = $val
    }

    return $result
}

# Helper: run external command with timeout and capture stdout/stderr safely
function Run-ExternalCommand {
    param(
        [string]$exe,
        [string]$args,
        [int]$timeoutSec = 30
    )
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $exe
    $psi.Arguments = $args
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $proc = New-Object System.Diagnostics.Process
    $proc.StartInfo = $psi
    $started = $proc.Start()
    if (-not $started) { return @{ Error = 'failed to start process' } }
    $finished = $proc.WaitForExit($timeoutSec * 1000)
    if ($finished) {
        $out = $proc.StandardOutput.ReadToEnd()
        $err = $proc.StandardError.ReadToEnd()
        return @{ StdOut = $out; StdErr = $err; ExitCode = $proc.ExitCode }
    } else {
        try { $proc.Kill() } catch {}
        return @{ Timeout = $true }
    }
}

# Build report
$reportPath = Join-Path $workspace 'PHASE_3B_LIVE_DRIFT_REPORT.txt'
"PHASE 3B LIVE DRIFT REPORT" | Out-File $reportPath -Encoding utf8
"Generated: $(Get-Date -Format o)" | Out-File $reportPath -Append -Encoding utf8
"" | Out-File $reportPath -Append

# (OCI/cluster/helm checks are performed later after per-service comparisons to avoid blocking the core data collection)


foreach ($svc in $services) {
    "Service: $svc" | Out-File $reportPath -Append
    "--------------------" | Out-File $reportPath -Append

    $liveFile = Join-Path $workspace ("live-deployment-claimassist-$svc.yaml")
    if (-not (Test-Path $liveFile)) {
        "Live deployment file missing: $liveFile. Have you run the fetch steps?" | Out-File $reportPath -Append
        continue
    }
    try {
        $renderedBlock = Get-RenderedBlock $svc
        if (-not $renderedBlock) { "Rendered block for claimassist-$svc not found in rendered-deployment-validation.yaml" | Out-File $reportPath -Append; continue }
    } catch {
        "FAILED TO COLLECT: rendered block for claimassist-$svc" | Out-File $reportPath -Append
        "ERROR: $($_.Exception.Message)" | Out-File $reportPath -Append
        continue
    }

    $liveText = Get-Content $liveFile -Raw
    $liveFields = Extract-FieldsFromBlock $liveText
    $renderedFields = Extract-FieldsFromBlock $renderedBlock

    $keysToCheck = @('image','imagePullPolicy','imagePullSecrets','SPRING_PROFILES_ACTIVE','SPRING_CONFIG_IMPORT','SPRING_CLOUD_CONFIG_ENABLED','SPRING_CONFIG_ADDITIONAL_LOCATIONS','SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWKSETURI','SPRING_DATASOURCE_URL','SPRING_DATA_REDIS_HOST','SPRING_DATA_REDIS_PORT','SPRING_KAFKA_BOOTSTRAP_SERVERS','KEYCLOAK_ISSUER_URI','volumeMounts_config_name','volumeMounts_config_mountPath','volumeMounts_config_readOnly','volumes_config_name','volumes_config_configMap_name','serviceAccountName','readinessProbe_path','livenessProbe_path','security_runAsUser','security_runAsGroup','security_runAsNonRoot')

    foreach ($k in $keysToCheck) {
        $lv = $liveFields[$k]
        if ($lv -eq $null) { $lv = '(missing)' }
        $rv = $renderedFields[$k]
        if ($rv -eq $null) { $rv = '(missing)' }
        $status = if ($lv -eq $rv) { 'SAME' } else { 'DIFFER' }
        "{0,-50} Live: {1,-30} Rendered: {2,-30} => {3}" -f $k, $lv, $rv, $status | Out-File $reportPath -Append
    }
    "" | Out-File $reportPath -Append
}

# Registry secret check
"Registry secret check" | Out-File $reportPath -Append
$secretTypeFile = Join-Path $workspace 'kubectl-secret-claimassist-registry-credentials-type.txt'
if (Test-Path $secretTypeFile) {
    $type = Get-Content $secretTypeFile -Raw
    "Type: $type" | Out-File $reportPath -Append
    if ($type -match 'kubernetes.io/dockerconfigjson') { "Registry secret type OK" | Out-File $reportPath -Append } else { "Registry secret type NOT OK" | Out-File $reportPath -Append }
} else {
    "Secret type file not present. Run the secret check step to fetch it." | Out-File $reportPath -Append
}

# Pods, ReplicaSets, Events (fetch if not already present)
$podsFile = Join-Path $workspace 'kubectl-pods.txt'
if (-not (Test-Path $podsFile)) { & kubectl --kubeconfig $kubeconfig -n $namespace get pods -o wide 2>&1 | Tee-Object $podsFile | Out-Null }
Get-Content $podsFile | Out-File $reportPath -Append

$rsFile = Join-Path $workspace 'kubectl-rs.txt'
if (-not (Test-Path $rsFile)) { & kubectl --kubeconfig $kubeconfig -n $namespace get rs -o wide 2>&1 | Tee-Object $rsFile | Out-Null }
"" | Out-File $reportPath -Append
Get-Content $rsFile | Out-File $reportPath -Append

$eventsFile = Join-Path $workspace 'kubectl-events.txt'
if (-not (Test-Path $eventsFile)) { & kubectl --kubeconfig $kubeconfig -n $namespace get events --sort-by=.lastTimestamp 2>&1 | Tee-Object $eventsFile | Out-Null }
"" | Out-File $reportPath -Append
Get-Content $eventsFile | Out-File $reportPath -Append

Write-Host "Report generated: $reportPath"
Write-Host "Please paste or attach the report content if you want me to analyze further or produce the formatted PHASE 3B LIVE DRIFT REPORT."

# --- OCI connectivity and cluster checks (run after the core collection to avoid blocking) ---
"" | Out-File $reportPath -Append
"--- OCI & Cluster checks ---" | Out-File $reportPath -Append
try {
    $pingOk = Test-Connection -ComputerName '144.24.116.166' -Count 1 -Quiet
    "Ping success: $pingOk" | Out-File $reportPath -Append
} catch {
    "FAILED TO COLLECT: Test-Connection 144.24.116.166" | Out-File $reportPath -Append
    "ERROR: $($_.Exception.Message)" | Out-File $reportPath -Append
}

"kubectl cluster-info" | Out-File $reportPath -Append
$res = Run-ExternalCommand 'kubectl' "--kubeconfig `"$kubeconfig`" cluster-info" 20
if ($res.Timeout) { "FAILED TO COLLECT: kubectl cluster-info (timeout)" | Out-File $reportPath -Append }
elseif ($res.Error) { "FAILED TO COLLECT: kubectl cluster-info ($($res.Error))" | Out-File $reportPath -Append }
else { ($res.StdOut + $res.StdErr) | Out-File $reportPath -Append }

"Helm status for claimassist-dev" | Out-File $reportPath -Append
$res = Run-ExternalCommand 'helm' "status claimassist-dev -n $namespace" 30
if ($res.Timeout) { "FAILED TO COLLECT: helm status (timeout)" | Out-File $reportPath -Append }
elseif ($res.Error) { "FAILED TO COLLECT: helm status ($($res.Error))" | Out-File $reportPath -Append }
else { ($res.StdOut + $res.StdErr) | Out-File $reportPath -Append }

"Helm history for claimassist-dev" | Out-File $reportPath -Append
$res = Run-ExternalCommand 'helm' "history claimassist-dev -n $namespace" 30
if ($res.Timeout) { "FAILED TO COLLECT: helm history (timeout)" | Out-File $reportPath -Append }
elseif ($res.Error) { "FAILED TO COLLECT: helm history ($($res.Error))" | Out-File $reportPath -Append }
else { ($res.StdOut + $res.StdErr) | Out-File $reportPath -Append }

