# Local Observability Guide

## Overview

This guide documents the observability stack for local development, including metrics, tracing, and logging infrastructure.

## Observability Stack

| Component | Purpose | Local Port | Access |
|-----------|---------|------------|--------|
| Prometheus | Metrics collection | 9090 | http://localhost:9090 |
| Grafana | Metrics visualization | 3000 | http://localhost:3000 (admin/admin) |
| Zipkin | Distributed tracing | 9411 | http://localhost:9411 |
| Loki | Log aggregation | 3100 | Internal use |
| Promtail | Log collection | 9080 | Internal use |

## Metrics (Micrometer + Prometheus)

### Metrics Configuration

All services are configured with Micrometer metrics via `config-repo/application.yml`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,info,prometheus
  metrics:
    enable:
      jvm: true
      process: true
      logback: true
    distribution:
      percentiles-histogram:
        http.server.requests: true
    tags:
      application: ${spring.application.name}
  prometheus:
    metrics:
      export:
        enabled: true
```

### Metrics Collection

Prometheus scrapes metrics from all services every 10 seconds:

- **Discovery Service**: http://host.docker.internal:8761/actuator/prometheus
- **Config Service**: http://host.docker.internal:8888/actuator/prometheus
- **API Gateway**: http://host.docker.internal:8080/actuator/prometheus
- **Customer Service**: http://host.docker.internal:8081/actuator/prometheus
- **Claims Service**: http://host.docker.internal:8082/actuator/prometheus
- **Agent Service**: http://host.docker.internal:8083/actuator/prometheus

### Metric Categories

#### JVM Metrics
- Memory usage (heap, non-heap)
- Garbage collection
- Thread count
- Class loading

#### Process Metrics
- CPU usage
- File descriptors
- Uptime

#### HTTP Metrics
- Request count
- Request duration (with percentiles)
- Response status codes
- Active requests

#### Application Metrics
- Custom business metrics
- Cache hit/miss rates
- Database operation timings
- Kafka producer/consumer metrics

### Accessing Metrics

#### Prometheus UI
```
http://localhost:9090
```

Use PromQL queries to explore metrics:
- `jvm_memory_used_bytes{service="customer-service"}`
- `http_server_requests_seconds_count{service="api-gateway"}`
- `cache_requests_total{service="agent-service",result="hit"}`

#### Grafana Dashboards
```
http://localhost:3000
```

Pre-configured dashboards include:
- JVM Overview
- HTTP Request Metrics
- System Metrics
- Service Health

Login: `admin` / `admin`

## Distributed Tracing (Zipkin)

### Tracing Configuration

Tracing is enabled with 100% sampling for local development:

```yaml
tracing:
  sampling:
    probability: 1.0

zipkin:
  tracing:
    endpoint: ${ZIPKIN_ENDPOINT:http://localhost:9411/api/v2/spans}
```

### Trace Propagation

The system maintains trace context across service boundaries:

**Request Flow Example:**
```
Client Request
    ↓ (traceId: abc-123, spanId: def-456)
API Gateway
    ↓ (same traceId, new spanId: ghi-789)
Customer Service
    ↓ (same traceId, new spanId: jkl-012)
Database/Keycloak
```

### Trace IDs in Logs

All services include trace context in structured logs:
- `traceId`: Overall request identifier
- `spanId`: Specific operation identifier
- `correlationId`: Business correlation identifier
- `requestId`: HTTP request identifier

### Accessing Traces

#### Zipkin UI
```
http://localhost:9411
```

**Features:**
- Search traces by service name
- Filter by time range
- View trace timeline
- Analyze span duration
- Identify latency bottlenecks

**Common Queries:**
- Find slow traces: Sort by duration
- Service-specific: Filter by service name
- Error traces: Filter by error status

### Correlation Headers

The following headers are propagated through the request chain:
- `X-Trace-Id`: Distributed trace identifier
- `X-Span-Id`: Current span identifier
- `X-Correlation-Id`: Business correlation identifier
- `X-Request-Id`: HTTP request identifier

## Logging (Loki + Promtail)

### Log Configuration

Logs are structured JSON format with standard fields:
- `timestamp`: ISO 8601 timestamp
- `level`: Log level (INFO, WARN, ERROR, etc.)
- `service`: Service name
- `traceId`: Distributed trace ID
- `spanId`: Span ID
- `correlationId`: Correlation ID
- `requestId`: Request ID
- `event`: Event type
- `message`: Log message

### Log Levels

Service log levels are configured in `application-local.yaml`:
- `com.claimassist`: DEBUG (for local development)
- `org.springframework.security`: WARN
- `org.hibernate.SQL`: WARN
- `org.springframework.kafka`: WARN
- `org.springframework.ai`: WARN

### Log Storage

Logs are aggregated by Loki and stored in Docker volume `loki_data`.

### Log Viewing

#### Application Logs
Check service console output or IDE logs for real-time log viewing.

#### Grafana Logs
Grafana is configured with Loki as a datasource for log queries:
```
http://localhost:3000
```

Use Explore → Loki to query logs with:
- Service filters
- Log level filters
- Time range queries
- Full-text search

## Health Checks

All services expose health endpoints:

### Standard Health Endpoints
- `/actuator/health` - Overall health status
- `/actuator/health/liveness` - Liveness probe
- `/actuator/health/readiness` - Readiness probe
- `/actuator/health/redis` - Redis connectivity (if configured)
- `/actuator/health/kafka` - Kafka connectivity (if configured)

### Health Check Response Format

```json
{
  "status": "UP",
  "groups": [
    "liveness",
    "readiness"
  ]
}
```

## Custom Metrics

### Performance Logging

Services use `PerformanceLogger` for business operation timing:

```java
performanceLogger.log("BUSINESS", "signup.total", durationMs, 
    Map.of("username", username, "customerId", customerId));
```

### Event Logging

Services use `EventLogger` for business event tracking:

```java
eventLogger.logBusinessEvent("customer-service", "customer-service", 
    Map.of("event", "SIGNUP_COMPLETED", "customerId", customerId));
```

## Monitoring Workflows

### Performance Analysis

1. **Identify Slow API**: Check Prometheus HTTP metrics
2. **Trace Request**: Use Zipkin to find trace by time
3. **Analyze Spans**: Identify which service/span caused latency
4. **Check Logs**: Review service logs for errors or warnings

### Error Investigation

1. **Check Health Endpoints**: Verify service health status
2. **Review Error Logs**: Search logs for ERROR level messages
3. **Check Error Metrics**: Look at error rate in Prometheus
4. **Trace Errors**: Find traces with error status in Zipkin

### Resource Monitoring

Monitor JVM metrics for:
- Memory pressure
- GC frequency
- Thread pool utilization
- CPU usage

## Grafana Dashboards

### Pre-configured Dashboards

1. **JVM Overview**
   - Memory usage
   - GC activity
   - Thread states
   - Class loading

2. **HTTP Request Metrics**
   - Request rate
   - Response time percentiles
   - Error rate
   - Active requests

3. **Service Health**
   - Service uptime
   - Health check status
   - Dependency health

### Creating Custom Dashboards

1. Access Grafana at http://localhost:3000
2. Go to Dashboards → Create
3. Add Prometheus panels
4. Use PromQL queries to visualize metrics

## Troubleshooting

### Metrics Not Appearing

1. Check Prometheus targets: http://localhost:9090/targets
2. Verify service is exposing `/actuator/prometheus`
3. Check service is running on expected port
4. Review Prometheus logs for scrape errors

### Traces Not Appearing

1. Verify Zipkin is accessible: http://localhost:9411
2. Check tracing configuration in service
3. Verify sampling probability (should be 1.0 for local)
4. Check service logs for tracing errors

### High Memory Usage

1. Check JVM heap metrics in Prometheus
2. Review GC frequency and duration
3. Check for memory leaks in application logs
4. Adjust JVM heap size if needed

## Performance Baselines

Local performance baselines (from Phase 2 measurements):

- **Signup API**: ~3.5 seconds (improved from historical 7.7s)
- **Health Check**: ~2.2 seconds
- **Cache Operations**: < 50ms (typical)
- **Database Operations**: < 100ms (typical simple queries)

## Next Steps

After setting up observability:

1. Monitor metrics during development
2. Use traces to debug distributed issues
3. Review logs for application health
4. Set up alerts in Grafana for critical metrics
5. Use performance data to identify optimization opportunities
