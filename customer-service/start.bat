@echo off
REM Change to the directory where this script is located (works regardless of original drive)
cd /d "%~dp0"
echo Starting customer-service with local profile at %time% > output.log 2>&1
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local" >> output.log 2>&1
echo Process completed at %time% >> output.log 2>&1

