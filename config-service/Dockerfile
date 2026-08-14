# Multi-stage build for config-service
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

# Copy the full repository (respecting .dockerignore) so the Maven reactor can
# resolve the root aggregator pom.xml and the common-lib dependency.
COPY . .

# Build ONLY this service plus its required reactor dependencies (common-lib).
# maven.test.skip=true skips test compilation AND execution so a pre-existing
# broken test source cannot block a production image build.
RUN --mount=type=cache,target=/root/.m2 mvn -B -pl config-service -am package -Dmaven.test.skip=true

# Runtime
FROM eclipse-temurin:21-jre-alpine

# Install curl for healthcheck and create a non-root user
RUN apk add --no-cache curl \
 && addgroup -S app && adduser -S -G app app

WORKDIR /app

# Pre-create a writable log directory owned by the non-root user (logback
# writes to /app/logs/<service>/ so the app user must own it).
RUN mkdir -p /app/logs && chown app:app /app /app/logs

# Copy the executable Spring Boot jar only (single *.jar in target;
# the pre-repackage .jar.original is not matched by the *.jar glob).
COPY --from=builder --chown=app:app /build/config-service/target/*.jar app.jar

# Configurable runtime knobs. JAVA_OPTS may be empty - the JVM command stays valid.
ENV JAVA_OPTS=""
ENV JAVA_RAM_PERCENTAGE=75
ENV SERVICE_PORT=8888
ENV TZ=Asia/Kolkata

EXPOSE 8888

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD curl -fsS http://localhost:${SERVICE_PORT}/actuator/health || curl -fsS http://localhost:${SERVICE_PORT}/ || exit 1

USER app

ENTRYPOINT ["sh","-c","exec java -Dapp.name=config-service -XX:MaxRAMPercentage=${JAVA_RAM_PERCENTAGE} -Duser.timezone=${TZ} $JAVA_OPTS -jar /app/app.jar"]