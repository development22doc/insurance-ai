@echo off
cd /d D:\Mayur\claimsassist\insurance-ai-platform\customer-service
echo Starting customer-service with local profile at %time% > output.log 2>&1
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local" >> output.log 2>&1
echo Process completed at %time% >> output.log 2>&1

