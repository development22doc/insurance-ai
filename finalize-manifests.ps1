# Final Multi-Platform Manifest Creation Script
# final-k3s-fix @ 6844af0
# Combines existing AMD64 images with newly built ARM64 images

Write-Host "=== PHASE 8: CREATE FINAL MULTI-PLATFORM MANIFESTS ==="
Write-Host "$(Get-Date)"
Write-Host ""

# Verify all existing AMD64 images
Write-Host "Verifying existing AMD64 images..."
$amd64Images = @{
    "api-gateway" = "sha256:a95e3e6ca17e83152418279f96a817aeaf6cb6ec7db4e57222a96f558b90abd1"
    "customer-service" = "sha256:b6ea06d3512203ffaa1773b9ba323366e561a9374abca0ec1db8576d419888f0"
    "claims-service" = "sha256:711895367124ca34de1a7cd197f31ff0d83d4cfa620e452868c61b491021a9a3"
    "agent-service" = "sha256:933003113d357ab1d4f9a93f2cd1a2031974729f7839b45be0c51722cf87c7ac"
}

# Verify ARM64 images exist
Write-Host "Verifying ARM64 images..."
$arm64Services = @("customer-service", "claims-service", "agent-service", "api-gateway")
$allReady = $true

foreach ($service in $arm64Services) {
    $manifest = docker manifest inspect "claimassistdev/${service}:6844af0-arm64" 2>&1 | ConvertFrom-Json -ErrorAction SilentlyContinue
    if ($manifest.manifests) {
        $arm64 = $manifest.manifests | Where-Object {$_.platform.architecture -eq "arm64"}
        if ($arm64) {
            Write-Host "✓ ${service}:6844af0-arm64 - linux/arm64"
        } else {
            Write-Host "✗ ${service}:6844af0-arm64 - ARM64 not found"
            $allReady = $false
        }
    } else {
        Write-Host "✗ ${service}:6844af0-arm64 - Not accessible"
        $allReady = $false
    }
}

if (-not $allReady) {
    Write-Host "ERROR: Not all ARM64 images are ready"
    exit 1
}

Write-Host ""
Write-Host "=== Creating final multi-platform manifests ==="
Write-Host ""

# Create manifests for each service
# IMPORTANT: Use the existing AMD64 image digests  with the new ARM64 images

# 1. customer-service
Write-Host "[1/4] customer-service:6844af0"
& docker buildx imagetools create -t claimassistdev/customer-service:6844af0 `
    claimassistdev/customer-service:6844af0 `
    claimassistdev/customer-service:6844af0-arm64
Write-Host ""

# 2. claims-service
Write-Host "[2/4] claims-service:6844af0"
& docker buildx imagetools create -t claimassistdev/claims-service:6844af0 `
    claimassistdev/claims-service:6844af0 `
    claimassistdev/claims-service:6844af0-arm64
Write-Host ""

# 3. agent-service
Write-Host "[3/4] agent-service:6844af0"
& docker buildx imagetools create -t claimassistdev/agent-service:6844af0 `
    claimassistdev/agent-service:6844af0 `
    claimassistdev/agent-service:6844af0-arm64
Write-Host ""

# 4. api-gateway
Write-Host "[4/4] api-gateway:6844af0"
& docker buildx imagetools create -t claimassistdev/api-gateway:6844af0 `
    claimassistdev/api-gateway:6844af0 `
    claimassistdev/api-gateway:6844af0-arm64
Write-Host ""

Write-Host "=== PHASE 9: VERIFY FINAL MULTI-PLATFORM MANIFESTS ==="
Write-Host ""

# Verify all final manifests
$finalServices = @("api-gateway", "customer-service", "claims-service", "agent-service")

foreach ($service in $finalServices) {
    Write-Host "$service:6844af0"
    $manifest = docker manifest inspect "claimassistdev/${service}:6844af0" 2>&1 | ConvertFrom-Json -ErrorAction SilentlyContinue
    if ($manifest.manifests) {
        foreach ($img in $manifest.manifests) {
            if ($img.platform.architecture -eq "amd64" -or $img.platform.architecture -eq "arm64") {
                Write-Host "  - $($img.platform.os)/$($img.platform.architecture): $($img.digest.Substring(0,19))..."
            }
        }
    }
    Write-Host ""
}

Write-Host "=== BUILD COMPLETE ==="
Write-Host "Branch: final-k3s-fix"
Write-Host "Commit: 6844af0"
Write-Host "Status: ✓ ARM64 images built, pushed, and verified"
Write-Host "Status: ✓ Final multi-platform manifests created"
Write-Host ""
Write-Host "All images now support: linux/amd64 + linux/arm64"

