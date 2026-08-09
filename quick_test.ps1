#!/usr/bin/env pwsh
# Quick test of authorize endpoint
$uri = "http://localhost:8081/auth/authorize"
$result = $null

try {
    $response = Invoke-WebRequest -Uri $uri -TimeoutSec 10 -UseBasicParsing -MaximumRedirection 0 -ErrorAction Stop
    $result = @{
        StatusCode = $response.StatusCode
        Location = $response.Headers['Location']
    }
} catch {
    if ($_.Exception.Response) {
        $statusCode = $_.Exception.Response.StatusCode.value__
        $location = $_.Exception.Response.Headers['Location']
        $result = @{
            StatusCode = $statusCode
            Location = $location
        }

        if ($statusCode -ge 500) {
            try {
                $stream = $_.Exception.Response.GetResponseStream()
                $reader = New-Object System.IO.StreamReader($stream)
                $body = $reader.ReadToEnd()
                $result.Body = $body
                $reader.Close()
            } catch {}
        }
    } else {
        $result = @{
            Error = $_.Exception.Message
        }
    }
}

$result | ConvertTo-Json

