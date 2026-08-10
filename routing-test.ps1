#!/usr/bin/env powershell

# Routing Test Script
# Tests all Gateway routes and verifies routing behavior

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "ROUTING AUDIT - TEST EXECUTION" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Configuration
$GATEWAY_URL = "http://localhost:8080"
$CUSTOMER_SERVICE_URL = "http://localhost:8081"
$CLAIMS_SERVICE_URL = "http://localhost:8082"
$AGENT_SERVICE_URL = "http://localhost:8083"
$EUREKA_URL = "http://localhost:8761"

# Helper function to test a route
function Test-Route {
    param(
        [string]$Description,
        [string]$Method,
        [string]$Url,
        [string]$ExpectedContains,
        [bool]$RequireToken = $false
    )

    Write-Host "TEST: $Description" -ForegroundColor Yellow
    Write-Host "  URL: $Url"
    Write-Host "  Method: $Method"

    try {
        $params = @{
            Uri = $Url
            Method = $Method
            UseBasicParsing = $true
            TimeoutSec = 5
            SkipCertificateCheck = $true
        }

        $response = Invoke-WebRequest @params

        Write-Host "  Status: $($response.StatusCode)" -ForegroundColor Green
        Write-Host "  Result: ✓ PASS" -ForegroundColor Green
        return @{
            Success = $true
            Status = $response.StatusCode
            Description = $Description
        }
    }
    catch {
        $statusCode = $_.Exception.Response.StatusCode.Value__
        Write-Host "  Status: $statusCode" -ForegroundColor Red
        Write-Host "  Error: $($_.Exception.Message)" -ForegroundColor Red
        Write-Host "  Result: ✗ FAIL" -ForegroundColor Red
        return @{
            Success = $false
            Status = $statusCode
            Description = $Description
        }
    }
    Write-Host ""
}

# Test results tracking
$results = @()

Write-Host "SECTION 1: EUREKA VERIFICATION" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
Write-Host ""

# Check Eureka
Write-Host "Checking Eureka Server at $EUREKA_URL/eureka/apps" -ForegroundColor Yellow
try {
    $eureka = Invoke-WebRequest -Uri "$EUREKA_URL/eureka/apps" -UseBasicParsing -TimeoutSec 5 -Headers @{"Accept"="application/json"}
    Write-Host "Eureka Status: $($eureka.StatusCode)" -ForegroundColor Green

    # Parse and display registered services
    $content = $eureka.Content | ConvertFrom-Json
    if ($content.applications.application) {
        Write-Host "Registered Services:" -ForegroundColor Green
        foreach ($app in $content.applications.application) {
            $count = @($app.instance).Count
            Write-Host "  - $($app.name) ($count instances)"
        }
    }
}
catch {
    Write-Host "Failed to reach Eureka: $_" -ForegroundColor Red
}
Write-Host ""

Write-Host "SECTION 2: CUSTOMER SERVICE ROUTING" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
Write-Host ""

# Test public customer endpoints (should work without token)
$results += Test-Route -Description "Customer Auth Signup (Public)" -Method "POST" -Url "$GATEWAY_URL/customer/auth/signup"
$results += Test-Route -Description "Customer Policies via Gateway" -Method "GET" -Url "$GATEWAY_URL/customer/policies" -RequireToken $true
$results += Test-Route -Description "Customer Policies Direct" -Method "GET" -Url "$CUSTOMER_SERVICE_URL/policies"

Write-Host "SECTION 3: CLAIMS SERVICE ROUTING" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
Write-Host ""

$results += Test-Route -Description "Claims List via Gateway" -Method "GET" -Url "$GATEWAY_URL/claims" -RequireToken $true
$results += Test-Route -Description "Claims List Direct" -Method "GET" -Url "$CLAIMS_SERVICE_URL/claims"

Write-Host "SECTION 4: AGENT SERVICE ROUTING (CRITICAL FIX)" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
Write-Host ""

$results += Test-Route -Description "Agent Claims via Gateway (Fixed)" -Method "GET" -Url "$GATEWAY_URL/agent/claims/1" -RequireToken $true
$results += Test-Route -Description "Agent Claims Direct" -Method "GET" -Url "$AGENT_SERVICE_URL/agent/claims/1"
$results += Test-Route -Description "Agent Stream via Gateway (Fixed)" -Method "POST" -Url "$GATEWAY_URL/agent/stream" -RequireToken $true
$results += Test-Route -Description "Agent Stream Direct" -Method "POST" -Url "$AGENT_SERVICE_URL/agent/stream"

Write-Host "SECTION 5: POLICIES ROUTING (FIXED - Points to CUSTOMER-SERVICE)" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
Write-Host ""

$results += Test-Route -Description "Policies via /policies/** (Fixed)" -Method "GET" -Url "$GATEWAY_URL/policies" -RequireToken $true
$results += Test-Route -Description "Policies via /customer/policies" -Method "GET" -Url "$GATEWAY_URL/customer/policies" -RequireToken $true

Write-Host "SECTION 6: NEGATIVE TESTS" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
Write-Host ""

$results += Test-Route -Description "Nonexistent Route (should 404)" -Method "GET" -Url "$GATEWAY_URL/nonexistent/path"

Write-Host "SECTION 7: ACTUATOR ENDPOINTS" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
Write-Host ""

$results += Test-Route -Description "Gateway Actuator Health" -Method "GET" -Url "$GATEWAY_URL/actuator/health"
$results += Test-Route -Description "Gateway Actuator Metrics" -Method "GET" -Url "$GATEWAY_URL/actuator/metrics"

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "TEST SUMMARY" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

$passCount = ($results | Where-Object { $_.Success -eq $true }).Count
$failCount = ($results | Where-Object { $_.Success -eq $false }).Count
$totalCount = $results.Count

Write-Host "Total Tests: $totalCount"
Write-Host "Passed: $passCount" -ForegroundColor Green
Write-Host "Failed: $failCount" -ForegroundColor Red

if ($failCount -gt 0) {
    Write-Host ""
    Write-Host "FAILED TESTS:" -ForegroundColor Red
    foreach ($result in ($results | Where-Object { $_.Success -eq $false })) {
        Write-Host "  - $($result.Description) (Status: $($result.Status))"
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "ROUTING AUDIT: COMPLETE" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

