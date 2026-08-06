# Multi-stage build for agent-service
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /workspace

COPY pom.xml .
COPY common-lib ../common-lib
COPY agent-service .

RUN mvn clean package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine

# Install curl for healthcheck and create a non-root user
RUN apk add --no-cache curl \
 && addgroup -S app && adduser -S -G app app

WORKDIR /app

# copy the fat jar and set ownership to non-root user
COPY --from=builder --chown=app:app /workspace/target/agent-service-1.0.0.jar app.jar

# Allow runtime JVM options and service port override
ENV JAVA_OPTS="-Dapp.name=agent-service"
ENV SERVICE_PORT=8083

EXPOSE 8083

# Healthcheck: try actuator health first, fall back to root path
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD curl -fsS http://localhost:${SERVICE_PORT}/actuator/health || curl -fsS http://localhost:${SERVICE_PORT}/ || exit 1

# Run as non-root user
USER app

ENTRYPOINT ["sh","-c","exec java -Dapp.name=agent-service $JAVA_OPTS -jar /app/app.jar"]
