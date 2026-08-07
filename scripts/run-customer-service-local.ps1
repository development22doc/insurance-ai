#!/usr/bin/env pwsh
# Local helper to start Customer Service in development with a fixed JVM timezone
# This script runs the maven spring-boot:run goal for the customer-service module
# and passes the JVM argument to enforce the timezone so the PostgreSQL driver
# will not send legacy IDs like "Asia/Calcutta" to the server.

Push-Location "$PSScriptRoot\.."
try {
    Write-Host "Starting customer-service with JVM arg -Duser.timezone=Asia/Kolkata"
    # Use the spring-boot.run.jvmArguments property so the forked JVM for the app
    # receives the timezone JVM arg. This does not modify Dockerfiles or CI.
    # Use double quotes for the JVM argument property so PowerShell passes it correctly to mvnw
    & .\mvnw.cmd -pl customer-service -DskipTests "-Dspring-boot.run.jvmArguments=-Duser.timezone=Asia/Kolkata" spring-boot:run
}
finally {
    Pop-Location
}

