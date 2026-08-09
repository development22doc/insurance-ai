#!/usr/bin/env pwsh
# Test the /auth/authorize endpoint

$url = "http://localhost:8081/auth/authorize"
Write-Host "Testing endpoint: $url"

try {
    $response = Invoke-WebRequest -Uri $url -TimeoutSec 10 -UseBasicParsing -MaximumRedirection 0 -ErrorAction Stop
    Write-Host "HTTP Status Code: $($response.StatusCode)"
    Write-Host "Location Header: $($response.Headers['Location'])"
} catch {
    if ($_.Exception.Response) {
        $statusCode = $_.Exception.Response.StatusCode.value__
        $location = $_.Exception.Response.Headers['Location']
        Write-Host "HTTP Status Code: $statusCode"
        Write-Host "Location Header: $location"

        # Try to read response body
        try {
            $stream = $_.Exception.Response.GetResponseStream()
            $reader = New-Object System.IO.StreamReader($stream)
            $body = $reader.ReadToEnd()
            Write-Host "Response Body: $body"
            $reader.Close()
        } catch {
            Write-Host "Could not read response body"
        }
    } else {
        Write-Host "Error: $_"
    }
}

