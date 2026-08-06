# ClaimAssist Insurance AI Platform - Observability Stack

Complete production-grade observability implementation for the ClaimAssist microservices platform.

## Components

### 1. **Prometheus** (Metrics Collection)
- **Port**: 9090
- **Configuration**: `monitoring/prometheus/prometheus.yml`
- **Storage**: 30-day retention
- **Features**:
  - Auto-scrapes all microservices on port `/actuator/prometheus`
  - Collects JVM metrics, HTTP requests, database connections, Redis operations, Kafka metrics
  - Health checks and availability monitoring
  - Time series database for historical analysis

### 2. **Grafana** (Visualization & Dashboards)
- **Port**: 3000
- **Credentials**: admin/admin
- **Configuration**: 
  - Datasources: `monitoring/grafana/provisioning/datasources/datasources.yml`
  - Dashboards: `monitoring/grafana/provisioning/dashboards/`
- **Features**:
  - Pre-configured Prometheus and Loki data sources
  - ClaimAssist platform overview dashboard
  - Real-time metrics visualization
  - Alert management capability

### 3. **Loki** (Log Aggregation)
- **Port**: 3100
- **Configuration**: `monitoring/loki/loki-config.yml`
- **Storage**: Filesystem-based (BoltDB + filesystem)
- **Features**:
  - Centralized log collection from all services
  - Label-based log querying
  - JSON log parsing
  - 30-day log retention

### 4. **Promtail** (Log Collection Agent)
- **Port**: 9080
- **Configuration**: `monitoring/promtail/promtail.yml`
- **Features**:
  - Collects logs from all microservices
  - Extracts structured logging fields (traceId, spanId, correlationId, level)
  - Applies labels for easy filtering and correlation
  - Forwards logs to Loki

### 5. **Zipkin** (Distributed Tracing)
- **Port**: 9411
- **Status**: Already configured
- **Features**:
  - Distributed trace visualization
  - Service dependency mapping
  - Performance bottleneck identification

## Microservices Monitored

1. **discovery-service** (Eureka) - Port 8761
   - Service registry metrics
   - Instance registration/deregistration tracking
   
2. **config-service** - Port 8888
   - Configuration server health
   - Configuration refresh metrics
   
3. **api-gateway** - Port 8080
   - Request routing metrics
   - Rate limiting statistics
   - JWT validation metrics
   
4. **customer-service** - Port 8081
   - Customer data operations
   - Cache hit/miss ratios
   - Premium billing metrics
   
5. **claims-service** - Port 8082
   - Claim processing metrics
   - Document storage operations
   - Saga orchestration metrics
   
6. **agent-service** - Port 8083
   - AI model invocation metrics
   - Tool-calling success rates
   - Streaming response metrics

## Logging

### Logback Configuration
- **Location**: `common-lib/src/main/resources/logback-spring.xml`
- **Features**:
  - JSON structured logging (logstash-logback-encoder)
  - Console output (real-time debugging)
  - File output with rolling policy
    - Location: `logs/<service-name>.log`
    - Rotation: Daily or 100MB
    - Retention: 30 days max / 5GB total
  
### Log Format
Structured JSON with fields:
```json
{
  "timestamp": "2024-01-15T10:30:45.123Z",
  "level": "INFO",
  "logger": "com.claimassist.service.ClassName",
  "message": "Operation completed",
  "thread": "tomcat-1",
  "traceId": "a1b2c3d4e5f6g7h8",
  "spanId": "x1y2z3a4",
  "correlationId": "req-12345",
  "requestId": "req-12345"
}
```

## Metrics Available

### JVM Metrics
- Memory usage (heap, non-heap, buffer pools)
- Garbage collection rates and pauses
- Thread count and states
- Class loading

### HTTP Metrics
- Request rate and latency percentiles (p50, p95, p99)
- Response status code distribution
- Exception rates

### Database Metrics
- Connection pool utilization
- Query execution times
- Transaction rates

### Cache Metrics (Redis)
- Cache hit/miss ratios
- Command execution times
- Key eviction rates

### Message Queue Metrics (Kafka)
- Producer/consumer rates
- Message lag
- Error rates

### Custom Business Metrics
- Claim processing duration
- AI model invocation metrics
- Service integration health

## Quick Start

### 1. Start all infrastructure
```bash
cd C:\claimassist\insurance-ai
docker-compose up -d postgres redis kafka zipkin prometheus loki promtail grafana
```

### 2. Access Dashboards
- **Grafana**: http://localhost:3000 (admin/admin)
- **Prometheus**: http://localhost:9090
- **Zipkin**: http://localhost:9411
- **Loki**: http://localhost:3100

### 3. Start Microservices
From IntelliJ or terminal (they will auto-register with Prometheus):
```bash
# Services will automatically expose metrics at:
# http://localhost:<port>/actuator/prometheus
```

## Configuration Profiles

### Local Development (default)
- Sampling rate: 100% (all traces collected)
- Log level: DEBUG
- Metrics export: Enabled
- Full MDC context in logs

### Production (application-prod.yaml)
- Sampling rate: 10% (sampling for cost optimization)
- Log level: WARN
- Metrics aggregation: 15-second intervals
- Compression enabled

## Queries & Alerts

### Useful Prometheus Queries

```promql
# Service availability
up{job=~".*-service"}

# HTTP request latency (95th percentile)
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))

# Memory usage per service
jvm_memory_used_bytes{job=~".*-service"} / jvm_memory_max_bytes

# Error rates
rate(http_server_requests_seconds_count{status=~"5.."}[5m])

# Kafka lag
kafka_consumer_lag

# Claims processing duration
claims_processing_duration_seconds
```

### Useful Loki Queries

```logql
# All errors across platform
{level="ERROR"}

# Trace-based filtering
{traceId="a1b2c3d4e5f6g7h8"}

# Service-specific logs with errors
{service="claims-service", level="ERROR"}

# All logs from API gateway
{service="api-gateway"}
```

## Maintenance

### Disk Space Management
- Prometheus: Retains 30 days of metrics (~50GB typical)
- Loki: Retains 30 days of logs (~20GB typical)
- Grafana: Database size ~500MB

### Cleanup
```bash
# Remove old Prometheus data (manual only if needed)
docker exec claimassist-prometheus rm -rf /prometheus/wal/*

# View Loki status
docker exec claimassist-loki loki -config.file=/etc/loki/loki-config.yml --print-config

# View Promtail positions
docker exec claimassist-promtail cat /tmp/positions.yaml
```

### Backup
```bash
# Backup Grafana dashboards
docker exec claimassist-grafana grafana-cli admin export-dashboard

# Backup Prometheus configuration
docker cp claimassist-prometheus:/etc/prometheus/prometheus.yml ./backup/
```

## Troubleshooting

### Prometheus not scraping services
- Ensure services are running on expected ports
- Check `prometheus.yml` has correct service targets
- Verify services expose `/actuator/prometheus` endpoint
- Review Prometheus UI -> Status -> Targets

### Loki not receiving logs
- Verify Promtail is running: `docker ps | grep promtail`
- Check Promtail configuration paths are correct
- Ensure log files exist in configured directories
- Review Promtail logs: `docker logs claimassist-promtail`

### Grafana not showing data
- Verify Prometheus datasource is working (Settings -> Data Sources -> Prometheus)
- Ensure queries are syntactically correct
- Check time range matches available data
- Verify services have been running for sufficient time

## Performance Tuning

### For high-volume environments
```yaml
# prometheus.yml
global:
  scrape_interval: 30s  # Increase from 15s
  evaluation_interval: 30s

# loki-config.yml
ingestion_rate_mb: 20  # Increase from 10
ingestion_burst_size_mb: 40  # Increase from 20
```

### Memory optimization
```bash
# Reduce Prometheus retention
--storage.tsdb.retention.time=7d

# Increase Grafana cache
GF_SERVER_HTTP_MAX_OPEN_CONNECTIONS: 512
```

## Integration with External Systems

### Send alerts to Slack/PagerDuty
- Configure Alertmanager in `prometheus.yml`
- Define alert rules in `monitoring/prometheus/rules/`

### Custom dashboard sharing
- Export dashboards as JSON from Grafana UI
- Version control in `monitoring/grafana/provisioning/dashboards/`

### Log analysis pipelines
- Configure additional log processors in Promtail
- Pipeline stages support regex, JSON, time parsing

## Security

### Current Configuration
- Grafana: Basic auth enabled (admin/admin)
- Prometheus: No auth (behind docker-compose network)
- Loki: No auth (behind docker-compose network)
- Promtail: Metrics-only, no sensitive data

### Recommendations for Production
1. Change Grafana admin password
2. Use Keycloak/OAuth2 for Grafana authentication
3. Place Prometheus/Loki behind reverse proxy with auth
4. Use TLS for all communication
5. Implement RBAC for dashboards
6. Enable audit logging in Grafana

## References

- [Prometheus Documentation](https://prometheus.io/docs/)
- [Grafana Documentation](https://grafana.com/docs/)
- [Loki Documentation](https://grafana.com/docs/loki/)
- [Promtail Documentation](https://grafana.com/docs/loki/latest/clients/promtail/)
- [Micrometer Documentation](https://micrometer.io/)
- [Spring Boot Actuator](https://spring.io/guides/gs/actuator-service/)

