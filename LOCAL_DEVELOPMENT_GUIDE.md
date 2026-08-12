# Local Development Guide

## Overview

This guide provides comprehensive instructions for setting up and running the ClaimAssist local development environment.

## Prerequisites

### Required Software
- **Java**: JDK 21+ 
- **Maven**: 3.8+ (Maven wrapper included in project)
- **Docker**: Latest version with Docker Compose
- **Git**: For version control

### System Requirements
- **RAM**: Minimum 8GB (16GB recommended)
- **Disk Space**: 10GB free space
- **Network**: Internet access for dependency downloads

## Quick Start

### 1. Clone Repository

```bash
git clone <repository-url>
cd insurance-ai
```

### 2. Start Infrastructure

```bash
cd infrastructure/docker
docker-compose -f docker-compose.local.yml up -d
```

Wait for all containers to show as "healthy" (approximately 1-2 minutes).

### 3. Start Services

Start services in the following order:

```bash
# Terminal 1: Discovery Service
cd discovery-service
mvn spring-boot:run

# Terminal 2: Config Service
cd config-service
mvn spring-boot:run

# Terminal 3: Customer Service
cd customer-service
mvn spring-boot:run

# Terminal 4: Claims Service
cd claims-service
mvn spring-boot:run

# Terminal 5: Agent Service
cd agent-service
mvn spring-boot:run

# Terminal 6: API Gateway
cd api-gateway
mvn spring-boot:run
```

### 4. Verify Startup

Check each service is accessible:
- **Eureka**: http://localhost:8761
- **Config Server**: http://localhost:8888
- **Gateway**: http://localhost:8080
- **Customer**: http://localhost:8081/actuator/health
- **Claims**: http://localhost:8082/actuator/health
- **Agent**: http://localhost:8083/actuator/health

## Detailed Startup Process

### Phase 1: Infrastructure Startup

#### Docker Compose

```bash
cd infrastructure/docker
docker-compose -f docker-compose.local.yml up -d
```

**Expected Components:**
- PostgreSQL (port 5432)
- Redis (port 6379)
- Kafka (ports 9092, 29092)
- Keycloak (port 8180)
- Prometheus (port 9090)
- Grafana (port 3000)
- Zipkin (port 9411)
- Loki (port 3100)
- Promtail (port 9080)
- Kafka UI (port 8085)

**Verification:**
```bash
docker ps --format "table {{.Names}}\t{{.Status}}"
```

All containers should show "Up" and "healthy".

### Phase 2: Service Startup Order

#### 1. Discovery Service (Eureka)

**Port:** 8761

**Startup Command:**
```bash
cd discovery-service
mvn spring-boot:run
```

**Verification:**
```bash
curl http://localhost:8761
```

Should return Eureka dashboard.

#### 2. Config Server

**Port:** 8888

**Startup Command:**
```bash
cd config-service
mvn spring-boot:run
```

**Profile:** native

**Verification:**
```bash
curl http://localhost:8888/customer-service/default
```

Should return customer-service configuration JSON.

#### 3. Customer Service

**Port:** 8081

**Startup Command:**
```bash
cd customer-service
mvn spring-boot:run
```

**Profile:** local

**Verification:**
```bash
curl http://localhost:8081/actuator/health
```

Should return `{"status":"UP"}`.

#### 4. Claims Service

**Port:** 8082

**Startup Command:**
```bash
cd claims-service
mvn spring-boot:run
```

**Profile:** local

**Verification:**
```bash
curl http://localhost:8082/actuator/health
```

Should return `{"status":"UP"}`.

#### 5. Agent Service

**Port:** 8083

**Startup Command:**
```bash
cd agent-service
mvn spring-boot:run
```

**Profile:** local

**Verification:**
```bash
curl http://localhost:8083/actuator/health
```

Should return `{"status":"UP"}`.

#### 6. API Gateway

**Port:** 8080

**Startup Command:**
```bash
cd api-gateway
mvn spring-boot:run
```

**Profile:** local

**Verification:**
```bash
curl http://localhost:8080/actuator/health
```

Should return `{"status":"UP"}`.

## Service Ports Summary

| Service | Port | Profile | Purpose |
|---------|------|---------|---------|
| Discovery Service | 8761 | local | Service discovery (Eureka) |
| Config Server | 8888 | native | Configuration management |
| API Gateway | 8080 | local | API gateway & routing |
| Customer Service | 8081 | local | Customer management |
| Claims Service | 8082 | local | Claims processing |
| Agent Service | 8083 | local | AI agent service |

## Infrastructure Ports Summary

| Component | Port | Access URL |
|-----------|------|------------|
| PostgreSQL | 5432 | jdbc:postgresql://localhost:5432/claimassist |
| Redis | 6379 | localhost:6379 |
| Kafka | 9092, 29092 | localhost:29092 (external) |
| Keycloak | 8180 | http://localhost:8180 |
| Prometheus | 9090 | http://localhost:9090 |
| Grafana | 3000 | http://localhost:3000 |
| Zipkin | 9411 | http://localhost:9411 |
| Kafka UI | 8085 | http://localhost:8085 |

## Environment Variables

### Required for Services

Most services use sensible defaults for local development. Key environment variables:

```bash
# PostgreSQL
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_USER=claimassist
POSTGRES_PASSWORD=claimassist

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# Kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:29092

# Eureka
EUREKA_SERVER_URL=http://localhost:8761/eureka

# Config Server
CONFIG_SERVER_URL=http://localhost:8888

# Keycloak
KEYCLOAK_SERVER_URL=http://localhost:8180
KEYCLOAK_REALM=claimassist
KEYCLOAK_ISSUER_URI=http://localhost:8180/realms/claimassist
KEYCLOAK_JWKS_URI=http://localhost:8180/realms/claimassist/protocol/openid-connect/certs

# Zipkin
ZIPKIN_ENDPOINT=http://localhost:9411/api/v2/spans
```

### Keycloak Setup

**Admin Access:**
- URL: http://localhost:8180/admin
- Username: `admin`
- Password: `admin`

**Realm:** `claimassist`

**Keycloak Realm Import:**
The realm is automatically imported from `infrastructure/docker/keycloak/realm-export.json` on container startup.

**Keycloak Clients:**
- `claimassist-customer-app` - Public client for customer-facing authentication
- `claimassist-admin-service` - Confidential client for service-to-service authentication

## Health Checks

### Service Health Endpoints

All services expose:
- `/actuator/health` - Overall health
- `/actuator/health/liveness` - Liveness probe
- `/actuator/health/readiness` - Readiness probe
- `/actuator/prometheus` - Prometheus metrics

### Quick Health Check Script

```bash
#!/bin/bash
echo "Checking service health..."
services=("8761" "8888" "8080" "8081" "8082" "8083")
for port in "${services[@]}"; do
    response=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:$port/actuator/health)
    if [ "$response" = "200" ]; then
        echo "✓ Port $port: UP"
    else
        echo "✗ Port $port: DOWN (HTTP $response)"
    fi
done
```

## Database Setup

### PostgreSQL Initialization

PostgreSQL is initialized via `infrastructure/docker/postgres/init-db.sql` on container startup.

### Database Schema

Each service has its own database:
- `claimassist_customer_local` - Customer Service
- `claimassist_claims_local` - Claims Service  
- `claimassist_agent_local` - Agent Service

Schema migrations are managed via Flyway on service startup.

### Direct Database Access

```bash
docker exec -it claimassist-postgres psql -U claimassist -d postgres
```

## Kafka Setup

### Topic Management

Kafka topics are auto-created by the applications when needed.

### Kafka UI Access

Kafka UI provides a web interface for managing topics and consumers:
- URL: http://localhost:8085
- Bootstrap servers: `kafka:9092`

### Consumer Groups

Key consumer groups:
- `agent-group` - Agent Service saga response handling
- `cqrs-read-model-sync-group` - CQRS read model synchronization

## Troubleshooting

### Port Conflicts

If you encounter port conflicts:
1. Identify the process using the port: `netstat -ano | findstr <port>`
2. Stop the conflicting process or change the port in service configuration

### Service Won't Start

1. Check infrastructure is running: `docker ps`
2. Check service logs for errors
3. Verify environment variables are set correctly
4. Check service is not already running on the port

### Eureka Registration Issues

1. Verify Discovery Service is running on port 8761
2. Check service Eureka configuration
3. Verify network connectivity between services
4. Check service logs for registration errors

### Configuration Issues

1. Verify Config Server is running on port 8888
2. Check Config Server can access config-repo
3. Verify service profile is set correctly
4. Check configuration files in config-repo

### Database Connection Issues

1. Verify PostgreSQL container is running: `docker ps`
2. Check database is accessible: `docker exec claimassist-postgres pg_isready`
3. Verify connection string in service configuration
4. Check database credentials match environment variables

## Development Workflow

### Making Configuration Changes

1. **Local changes**: Modify `application-local.yaml` in service resources
2. **Shared changes**: Modify files in `config-repo/`
3. **Infrastructure changes**: Modify `docker-compose.local.yml`

### Rebuilding Services

After making code changes:
```bash
cd <service-directory>
mvn clean install
mvn spring-boot:run
```

### Viewing Logs

Service logs appear in the terminal where Maven Spring Boot is running. For aggregated logs:
- Check Grafana at http://localhost:3000 (admin/admin)
- Loki stores logs in Docker volume

### Debugging

Use IDE debug mode:
1. Set breakpoints in your IDE
2. Run service in debug mode instead of `mvn spring-boot:run`
3. Connect IDE debugger to the service

## IDE Configuration

### IntelliJ IDEA

The project includes Maven wrapper, so you can:
1. Import as Maven project
2. Use IntelliJ's built-in Maven runner
3. Create run configurations for each service with profile:
   - Discovery Service: local
   - Config Service: native
   - Other services: local

### VS Code

1. Install Maven extension
2. Open project folder
3. Use integrated terminal for Maven commands
4. Use Java extension for debugging

## Clean Shutdown

### Stop Services

Press `Ctrl+C` in each service terminal or close the terminal window.

### Stop Infrastructure

```bash
cd infrastructure/docker
docker-compose -f docker-compose.local.yml down
```

### Clean All Data

```bash
cd infrastructure/docker
docker-compose -f docker-compose.local.yml down -v
```

## Next Steps

After successful startup:

1. Verify Eureka dashboard shows all services
2. Check Grafana dashboards for metrics
3. Use Zipkin to trace requests
4. Test authentication flow
5. Test API endpoints
6. Review logs for any warnings or errors

## Common Issues and Solutions

### "Address already in use" Error

Stop the process using the port or change the port in `application-local.yaml`.

### "Connection refused" Errors

Verify the target service is running and accessible on the expected port.

### Configuration Not Loading

Check Config Server is running and the service can reach it at the configured URL.

### Kafka Connection Issues

Verify Kafka container is running and accessible at `localhost:29092`.

### Keycloak Login Issues

- Clear browser cookies
- Verify Keycloak container is running
- Check realm configuration
- Verify client credentials in application configuration

## Additional Resources

- **Infrastructure Guide**: `LOCAL_INFRASTRUCTURE_GUIDE.md`
- **Observability Guide**: `LOCAL_OBSERVABILITY_GUIDE.md`
- **Architecture Documentation**: See project README
- **API Documentation**: See individual service controllers

## Support

For issues not covered in this guide:
1. Check service logs for error messages
2. Review infrastructure logs
3. Check application configuration
4. Consult project documentation
