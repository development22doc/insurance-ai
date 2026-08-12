# Local Infrastructure Guide

## Overview

This guide documents how to start and manage the local infrastructure for ClaimAssist development.

## Prerequisites

- Docker and Docker Compose installed
- Java 21+ installed
- Maven 3.8+ installed
- PostgreSQL client (optional, for direct database access)
- Redis client (optional, for direct cache access)

## Infrastructure Components

The following infrastructure components run via Docker Compose:

| Component | Purpose | Local Port | Description |
|-----------|---------|------------|-------------|
| PostgreSQL | Database | 5432 | Main application database |
| Redis | Cache | 6379 | Caching layer |
| Kafka | Message Broker | 9092, 29092 | Event streaming |
| Keycloak | Identity Provider | 8180 | Authentication and authorization |
| Prometheus | Metrics | 9090 | Metrics collection |
| Grafana | Visualization | 3000 | Metrics dashboards |
| Zipkin | Tracing | 9411 | Distributed tracing |
| Loki | Log Aggregation | 3100 | Log storage |
| Promtail | Log Collection | 9080 | Log shipping |
| Kafka UI | Kafka Management | 8085 | Kafka topic/browser interface |

## Starting Infrastructure

### Using Docker Compose

```bash
cd infrastructure/docker
docker-compose -f docker-compose.local.yml up -d
```

### Verification

Check that all containers are running:

```bash
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

Expected output should show all containers as "Up" and "healthy".

### Health Checks

Each infrastructure component has health checks configured:

- **PostgreSQL**: `pg_isready -U claimassist -d postgres`
- **Redis**: `redis-cli ping`
- **Kafka**: Internal health check
- **Keycloak**: HTTP endpoint health check
- **Prometheus**: HTTP endpoint health check
- **Grafana**: HTTP endpoint health check
- **Zipkin**: HTTP endpoint health check
- **Loki**: HTTP endpoint health check

## Component Details

### PostgreSQL

- **Database**: `claimassist`
- **User**: `claimassist`
- **Password**: `claimassist`
- **Port**: 5432
- **Data Volume**: `pgdata`
- **Init Script**: `postgres/init-db.sql`

Connection string:
```
jdbc:postgresql://localhost:5432/claimassist
```

### Redis

- **Port**: 6379
- **Password**: None (no password configured for local development)
- **Data Volume**: No persistence (stateless for local development)

Connection details:
```
host: localhost
port: 6379
password: (none)
```

### Kafka

- **Broker Ports**: 9092 (internal), 29092 (external)
- **UI Port**: 8085
- **Configuration**: KRaft mode (single node)
- **Topics**: Auto-created on demand

Bootstrap servers:
```
localhost:29092 (for services running on host)
kafka:9092 (for services running in Docker)
```

### Keycloak

- **Port**: 8180
- **Admin Console**: http://localhost:8180/admin
- **Realm**: `claimassist`
- **Admin User**: `admin`
- **Admin Password**: `admin`
- **Database**: PostgreSQL (keycloak-postgres container)

Keycloak realm import:
- File: `infrastructure/docker/keycloak/realm-export.json`
- Import happens automatically on container startup

### Prometheus

- **Port**: 9090
- **Web UI**: http://localhost:9090
- **Config**: `infrastructure/monitoring/prometheus/prometheus.yml`
- **Data Retention**: 30 days
- **Scrape Interval**: 15s (default)

Prometheus targets all services via `/actuator/prometheus` endpoints.

### Grafana

- **Port**: 3000
- **Web UI**: http://localhost:3000
- **Admin User**: `admin`
- **Admin Password**: `admin`
- **Provisioning**: Dashboards and datasources auto-configured
- **Data Sources**: Prometheus and Loki

### Zipkin

- **Port**: 9411
- **Web UI**: http://localhost:9411
- **Endpoint**: `/api/v2/spans`
- **Sample Rate**: 1.0 (100% sampling for local development)

### Loki

- **Port**: 3100
- **Config**: `infrastructure/monitoring/loki/loki-config.yml`
- **Data Volume**: `loki_data`

### Promtail

- **Port**: 9080
- **Config**: `infrastructure/monitoring/promtail/promtail.yml`
- **Log Path**: `./logs` (relative to project root)

## Stopping Infrastructure

```bash
cd infrastructure/docker
docker-compose -f docker-compose.local.yml down
```

To remove volumes (clear all data):
```bash
docker-compose -f docker-compose.local.yml down -v
```

## Troubleshooting

### Port Conflicts

If you encounter port conflicts, modify the port mappings in `docker-compose.local.yml`:

```yaml
ports:
  - "5432:5432"  # Change first port if needed
```

### Container Startup Issues

Check container logs:
```bash
docker logs claimassist-postgres
docker logs claimassist-keycloak
docker logs claimassist-kafka
```

### Health Check Failures

If containers fail health checks:
1. Check container logs for errors
2. Verify network connectivity
3. Check for resource constraints (memory/CPU)
4. Ensure proper order of startup (dependencies)

### Data Persistence

PostgreSQL data persists in Docker volume `pgdata`. To reset:
```bash
docker-compose -f docker-compose.local.yml down -v
docker-compose -f docker-compose.local.yml up -d
```

## Environment Variables

Key environment variables for infrastructure:

```bash
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_USER=claimassist
POSTGRES_PASSWORD=claimassist

REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

KAFKA_BOOTSTRAP_SERVERS=localhost:29092

KEYCLOAK_SERVER_URL=http://localhost:8180
KEYCLOAK_REALM=claimassist
KEYCLOAK_ISSUER_URI=http://localhost:8180/realms/claimassist
KEYCLOAK_JWKS_URI=http://localhost:8180/realms/claimassist/protocol/openid-connect/certs

ZIPKIN_ENDPOINT=http://localhost:9411/api/v2/spans
```

## Network Architecture

All infrastructure components communicate via the `backend` Docker network. Services running on the host communicate via localhost, while services in Docker use service names.

## Monitoring Access

- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3000 (admin/admin)
- **Zipkin**: http://localhost:9411
- **Kafka UI**: http://localhost:8085
- **Keycloak Admin**: http://localhost:8180/admin (admin/admin)

## Next Steps

After infrastructure is running, start the application services in order:

1. Discovery Service (port 8761)
2. Config Server (port 8888)
3. Customer Service (port 8081)
4. Claims Service (port 8082)
5. Agent Service (port 8083)
6. API Gateway (port 8080)

See `LOCAL_DEVELOPMENT_GUIDE.md` for service startup instructions.
