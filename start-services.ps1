# Start all microservices with correct app.name property
$services = @(
    @{name="discovery-service"; port="8761"},
    @{name="config-service"; port="8888"},
    @{name="customer-service"; port="8081"},
    @{name="claims-service"; port="8082"},
    @{name="agent-service"; port="8083"},
    @{name="api-gateway"; port="8080"}
)

$baseDir = "C:\claimassist\insurance-ai"
$processes = @()

Write-Host "Starting all microservices..." -ForegroundColor Green

foreach ($service in $services) {
    $jarPath = Join-Path $baseDir "$($service.name)/target/$($service.name)-1.0.0.jar"
    if (Test-Path $jarPath) {
        Write-Host "Starting $($service.name) on port $($service.port)..." -ForegroundColor Yellow
        $proc = Start-Process java -ArgumentList "-Dapp.name=$($service.name)", "-jar", $jarPath -WindowStyle Hidden -PassThru
        $processes += $proc
        Start-Sleep -Seconds 3
    } else {
        Write-Host "JAR not found: $jarPath" -ForegroundColor Red
    }
}

Write-Host "`nAll services started. PIDs: $($processes.Id -join ', ')" -ForegroundColor Green
Write-Host "Waiting 20 seconds for services to initialize..." -ForegroundColor Cyan
Start-Sleep -Seconds 20

$logDir = Join-Path $baseDir "logs"
$logFiles = Get-ChildItem -Path $logDir -Recurse -File -ErrorAction SilentlyContinue |
    Select-Object FullName, @{N="Lines";E={(Get-Content $_.FullName | Measure-Object -Line).Lines}}

Write-Host "`nLog files created:" -ForegroundColor Cyan
$logFiles | Format-Table -AutoSize

if ($logFiles.Count -gt 0) {
    Write-Host "`n[OK] Logging pipeline is working!" -ForegroundColor Green
} else {
    Write-Host "`n[FAIL] No log files were created" -ForegroundColor Red
}

