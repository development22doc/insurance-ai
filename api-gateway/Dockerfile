# Multi-stage build for api-gateway
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /workspace

COPY pom.xml .
COPY api-gateway .

RUN mvn clean package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine

# Install curl for healthcheck and create a non-root user
RUN apk add --no-cache curl \
 && addgroup -S app && adduser -S -G app app

WORKDIR /app

# copy the fat jar and set ownership to non-root user
COPY --from=builder --chown=app:app /workspace/target/api-gateway-1.0.0.jar app.jar

ENV JAVA_OPTS="-Dapp.name=api-gateway -Duser.timezone=Asia/Kolkata"
ENV SERVICE_PORT=8080

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD curl -fsS http://localhost:${SERVICE_PORT}/actuator/health || curl -fsS http://localhost:${SERVICE_PORT}/ || exit 1

USER app

ENTRYPOINT ["sh","-c","exec java -Dapp.name=api-gateway $JAVA_OPTS -jar /app/app.jar"]
