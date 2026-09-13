#!/usr/bin/env pwsh
# Local helper to start Customer Service in development with a fixed JVM timezone
# This script runs the maven spring-boot:run goal for the customer-service module
# and passes the JVM argument to enforce the timezone so the PostgreSQL driver
# will not send legacy IDs like "Asia/Calcutta" to the server.

Push-Location "$PSScriptRoot\.."
try {
    Write-Host "Starting customer-service with JVM arg -Duser.timezone=Asia/Kolkata"
    # Set environment variables for local-k8s profile
    $env:POSTGRES_USER = "claimassist"
    $env:POSTGRES_PASSWORD = "901137dc1a8f4cdcbcf2744aA1!"
    $env:CLAIMASSIST_DEV_ID = "mayur"
    $env:CLAIMASSIST_DEVELOPER_NAME = "Mayur"
    $env:KEYCLOAK_REDIRECT_URI = "http://localhost:5173/callback"

    # Use the spring-boot.run.jvmArguments property so the forked JVM for the app
    # receives the timezone JVM arg. This does not modify Dockerfiles or CI.
    # Use double quotes for the JVM argument property so PowerShell passes it correctly to mvnw
    & .\mvnw.cmd -pl customer-service -DskipTests "-Dspring-boot.run.jvmArguments=-Duser.timezone=Asia/Kolkata" "-Dspring-boot.run.profiles=local-k8s" "-Dspring-boot.run.arguments=--server.port=8081" spring-boot:run
}
finally {
    Pop-Location
}

