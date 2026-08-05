@echo off
cd /d D:\Mayur\claimsassist\insurance-ai-platform\customer-service
echo Starting customer-service with local profile...
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"
pause

