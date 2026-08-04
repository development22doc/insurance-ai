# Multi-stage build for discovery-service
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /workspace

COPY pom.xml .
COPY discovery-service .

RUN mvn clean package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine

# Install curl for healthcheck and create a non-root user
RUN apk add --no-cache curl \
 && addgroup -S app && adduser -S -G app app

WORKDIR /app

# copy the fat jar and set ownership to non-root user
COPY --from=builder --chown=app:app /workspace/target/discovery-service-1.0.0.jar app.jar

ENV JAVA_OPTS=""
ENV SERVICE_PORT=8761

EXPOSE 8761

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD curl -fsS http://localhost:${SERVICE_PORT}/actuator/health || curl -fsS http://localhost:${SERVICE_PORT}/ || exit 1

USER app

ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]
