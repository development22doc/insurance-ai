$url = "http://localhost:8081/auth/authorize"

Write-Host "Testing OAuth2 /auth/authorize endpoint..."
Write-Host "URL: $url"

try {
    # Make the request with -MaximumRedirection 0 to prevent automatic following of redirects
    $response = Invoke-WebRequest -Uri $url -MaximumRedirection 0 -ErrorAction Stop

    Write-Host ""
    Write-Host "HTTP Status Code: $($response.StatusCode)"
    Write-Host ""

    # Get the Location header
    if ($response.Headers['Location']) {
        Write-Host "Location Header:"
        Write-Host "$($response.Headers['Location'])"
    }

    if ($response.StatusCode -eq 302) {
        Write-Host ""
        Write-Host "SUCCESS: Endpoint returned HTTP 302 Redirect"
    } else {
        Write-Host ""
        Write-Host "WARNING: Endpoint returned HTTP $($response.StatusCode), expected 302"
    }
} catch [System.Net.WebException] {
    $response = $_.Exception.Response
    if ($response -and [int]$response.StatusCode -eq 302) {
        Write-Host ""
        Write-Host "HTTP Status Code: 302"
        Write-Host ""

        if ($response.Headers['Location']) {
            Write-Host "Location Header:"
            Write-Host "$($response.Headers['Location'])"
        }

        Write-Host ""
        Write-Host "SUCCESS: Endpoint returned HTTP 302 Redirect"
    } else {
        Write-Host ""
        Write-Host "Error occurred: $($_.Exception.Message)"
        if ($response) {
            Write-Host "Response Status Code: $($response.StatusCode)"
        }
    }
} catch {
    Write-Host ""
    Write-Host "Error occurred: $($_.Exception.Message)"
}

