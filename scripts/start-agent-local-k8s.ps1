#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Starts agent-service locally with the local-k8s profile.
    Uses Tailscale NodePorts to reach OCI K3s infrastructure.
    Reads configuration from application-local-k8s.properties file.
#>

$ErrorActionPreference = "Stop"

# Check if the properties file exists
$propertiesPath = Join-Path $PSScriptRoot "..\application-local-k8s.properties"
if (-not (Test-Path $propertiesPath)) {
    Write-Host "ERROR: application-local-k8s.properties not found at $propertiesPath" -ForegroundColor Red
    Write-Host "Please copy application-local-k8s.properties.example to application-local-k8s.properties and fill in your credentials." -ForegroundColor Yellow
    exit 1
}

$env:SPRING_PROFILES_ACTIVE = "local-k8s"
$env:SPRING_CLOUD_CONFIG_ENABLED = "false"
$env:SERVER_PORT = "8083"
$env:CLAIMASSIST_DEV_ID = "local"

Write-Host "Starting agent-service with profile local-k8s on port 8083..." -ForegroundColor Cyan
Write-Host "Configuration loaded from application-local-k8s.properties" -ForegroundColor Green

$jarPath = Join-Path $PSScriptRoot "..\agent-service\target\agent-service-1.0.0.jar"

java "-Dspring.profiles.active=local-k8s" -jar $jarPath
