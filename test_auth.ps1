Write-Host "Testing /auth/authorize endpoint..."
$result = & curl -i http://localhost:8081/auth/authorize 2>&1
Write-Host $result

