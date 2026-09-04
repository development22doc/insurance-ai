# Phase 3B — Final OCI server-side validation & live drift collection
# WARNING: This script is READ-ONLY for cluster resources. It will NOT modify the cluster.
# Run this on the Windows PowerShell machine where the SSH tunnel exists (the same machine described in the instructions).
# Usage (from PowerShell):
#   Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass;
#   cd "D:\Mayur\claimsassist\insurance-ai-platform";
#   .\scripts\phase3b-live-validation.ps1

# Variables (edit only if you must change paths/hosts)
$workspace = "D:\Mayur\claimsassist\insurance-ai-platform"
$kubeconfig = "$env:USERPROFILE\\.kube\\claimassist-oci-dev.yaml"
$namespace = "claimassist-dev"
$ociHost = "144.24.116.166"
$ociUser = "opc"
$sshKey = "$env:USERPROFILE\\.ssh\\github-actions-oci-dev"
$k3sLocalPort = 16443

Push-Location $workspace
try {
    Write-Host "Working in $workspace"

    Write-Host "STEP 1: Verify SSH tunnel (Get-NetTCPConnection -LocalPort $k3sLocalPort)"
    Get-NetTCPConnection -LocalPort $k3sLocalPort | Tee-Object -FilePath "$workspace\oci-tunnel-netstat.txt"

    Write-Host "If there is no output above, the tunnel is not active. To start it run the connect command from the instructions."
    Write-Host "You can start the tunnel manually with the provided helper script (uncomment below if you want the script to attempt connecting)."

    # NOTE: By default we do not start the tunnel automatically. If you want this script to start the tunnel, run the connect command below manually.
    # .\scripts\oci-k8s-connect.ps1 -OciHost $ociHost -OciUser $ociUser -SshKey $sshKey

    Write-Host "STEP 1+: Verify kubectl cluster-info and nodes (using provided kubeconfig)"
    & kubectl --kubeconfig $kubeconfig cluster-info 2>&1 | Tee-Object -FilePath "$workspace\kubectl-cluster-info.txt"
    & kubectl --kubeconfig $kubeconfig get nodes -o wide 2>&1 | Tee-Object -FilePath "$workspace\kubectl-nodes.txt"

    Write-Host "STEP 2: Server-side dry-run using rendered-deployment-validation.yaml"
    $rendered = Join-Path $workspace "rendered-deployment-validation.yaml"
    if (-Not (Test-Path $rendered)) {
        Write-Error "Rendered file not found: $rendered. Aborting dry-run step."
    } else {
        & kubectl --kubeconfig $kubeconfig apply --dry-run=server -f $rendered -n $namespace 2>&1 | Tee-Object -FilePath "$workspace\kubectl-dryrun-server-full.txt"
    }

    Write-Host "STEP 3: Fetch live Deployments (yaml)"
    $deployments = @(
        @{ name = 'claimassist-api-gateway'; out = 'live-deployment-claimassist-api-gateway.yaml' },
        @{ name = 'claimassist-customer-service'; out = 'live-deployment-claimassist-customer-service.yaml' },
        @{ name = 'claimassist-claims-service'; out = 'live-deployment-claimassist-claims-service.yaml' },
        @{ name = 'claimassist-agent-service'; out = 'live-deployment-claimassist-agent-service.yaml' }
    )
    foreach ($d in $deployments) {
        Write-Host "Getting deployment $($d.name)"
        & kubectl --kubeconfig $kubeconfig -n $namespace get deployment $($d.name) -o yaml 2>&1 | Tee-Object -FilePath (Join-Path $workspace $($d.out))
    }

    Write-Host "STEP 3b: (Optional) Extract environment keys from live deployments for quick comparison (simple grep-like extraction)"
    foreach ($d in $deployments) {
        $yamlFile = Join-Path $workspace $($d.out)
        if (Test-Path $yamlFile) {
            # accept both singular and plural forms of the additional locations env var
            Select-String -Path $yamlFile -Pattern "SPRING_PROFILES_ACTIVE|SPRING_CONFIG_IMPORT|SPRING_CLOUD_CONFIG_ENABLED|SPRING_CONFIG_ADDITIONAL_LOCATION|SPRING_CONFIG_ADDITIONAL_LOCATIONS|SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWKSETURI|imagePullSecrets|serviceAccountName|volumeMounts|volumes|securityContext|readinessProbe|livenessProbe" -SimpleMatch | Tee-Object -FilePath (Join-Path $workspace ("extract-$(($d.name))-env-and-keys.txt"))
        }
    }

    Write-Host "STEP 4: Verify registry secret existence and type"
    & kubectl --kubeconfig $kubeconfig -n $namespace get secret claimassist-registry-credentials 2>&1 | Tee-Object -FilePath "$workspace\kubectl-secret-claimassist-registry-credentials.txt"
    & kubectl --kubeconfig $kubeconfig -n $namespace get secret claimassist-registry-credentials -o jsonpath="{.type}" 2>&1 | Tee-Object -FilePath "$workspace\kubectl-secret-claimassist-registry-credentials-type.txt"

    Write-Host "STEP 5: Current Pod status and events"
    & kubectl --kubeconfig $kubeconfig -n $namespace get pods -o wide 2>&1 | Tee-Object -FilePath "$workspace\kubectl-pods.txt"
    & kubectl --kubeconfig $kubeconfig -n $namespace get events --sort-by=.lastTimestamp 2>&1 | Tee-Object -FilePath "$workspace\kubectl-events.txt"

    Write-Host "STEP 6: Helm status and history for release 'claimassist-dev' in namespace $namespace"
    & helm --kubeconfig $kubeconfig status claimassist-dev -n $namespace 2>&1 | Tee-Object -FilePath "$workspace\helm-status-claimassist-dev.txt"
    & helm --kubeconfig $kubeconfig history claimassist-dev -n $namespace 2>&1 | Tee-Object -FilePath "$workspace\helm-history-claimassist-dev.txt"

    Write-Host "STEP 7: Save rendered file excerpt and a copy for side-by-side comparison"
    Copy-Item -Path $rendered -Destination (Join-Path $workspace 'rendered-deployment-validation-copy.yaml') -Force

    Write-Host "All commands completed. Files written to workspace root. Review the files and attach them when requesting the final report."

    Write-Host "Generated files list:"
    Get-ChildItem -Path $workspace -Filter "kubectl-*" -File | Select-Object Name
    Get-ChildItem -Path $workspace -Filter "helm-*" -File | Select-Object Name
    Get-ChildItem -Path $workspace -Filter "live-deployment-*" -File | Select-Object Name
    Get-ChildItem -Path $workspace -Filter "extract-*" -File | Select-Object Name
    Get-ChildItem -Path $workspace -Filter "oci-tunnel-netstat.txt" -File | Select-Object Name
    Get-ChildItem -Path $workspace -Filter "rendered-deployment-validation-copy.yaml" -File | Select-Object Name
}
finally {
    Pop-Location
}

Write-Host "IMPORTANT: This script does NOT change cluster resources. It only collects outputs into files in the workspace."
Write-Host "Next steps: run this script on your Windows machine (where the SSH tunnel lives). Then upload or paste the resulting files so I can produce the full Final Report exactly as requested."
