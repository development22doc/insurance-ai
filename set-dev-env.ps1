# PowerShell script to set development environment variables for ClaimAssist
# Run this script before starting IntelliJ or set these in your IntelliJ run configurations

$env:CLAIMASSIST_DEV_ID="mayur"
$env:CLAIMASSIST_DEVELOPER_NAME="Mayur"
$env:POSTGRES_PASSWORD="claimassist"
$env:SERVICE_CLIENT_ID="claimassist-admin-service"
$env:SERVICE_CLIENT_SECRET="local-dev-admin-client-secret"
$env:SERVICE_TOKEN_URI="http://100.114.133.69:30080/realms/claimassist-dev/protocol/openid-connect/token"

Write-Host "Development environment variables set:"
Write-Host "CLAIMASSIST_DEV_ID: $env:CLAIMASSIST_DEV_ID"
Write-Host "CLAIMASSIST_DEVELOPER_NAME: $env:CLAIMASSIST_DEVELOPER_NAME"
Write-Host "POSTGRES_PASSWORD: $env:POSTGRES_PASSWORD"
Write-Host "SERVICE_CLIENT_ID: $env:SERVICE_CLIENT_ID"
Write-Host "SERVICE_CLIENT_SECRET: $env:SERVICE_CLIENT_SECRET"
Write-Host "SERVICE_TOKEN_URI: $env:SERVICE_TOKEN_URI"

Write-Host ""
Write-Host "Please restart IntelliJ services with the local-k8s profile"
