# Observability - Metrics, Tracing, and Logging

**Version:** 1.0.0  
**Last Updated:** August 4, 2026

---

## Three Pillars of Observability

### 1. Metrics (Prometheus)
Quantitative measurements of system behavior

### 2. Tracing (Zipkin)
Request flow across distributed system

### 3. Logging (Structured JSON)
Event records with context

---

## Metrics Collection

### Prometheus Endpoints

Each service exposes metrics at:
```
http://{service}:{port}/actuator/prometheus
```

**Services:**
- API Gateway: `http://localhost:8080/actuator/prometheus`
- Customer Service: `http://localhost:8081/actuator/prometheus`
- Claims Service: `http://localhost:8082/actuator/prometheus`
- Agent Service: `http://localhost:8083/actuator/prometheus`

### Scrape Configuration

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,info,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: ${spring.application.name}
      environment: ${APP_ENV:local}
```

### Key Metrics

#### HTTP Request Metrics

```
http.server.requests
├── Dimensions:
│   ├── method: GET, POST, PUT, DELETE
│   ├── uri: /api/claims, /api/customers, etc.
│   ├── status: 200, 201, 400, 401, 500, etc.
│   └── outcome: SUCCESS, CLIENT_ERROR, SERVER_ERROR
├── Measurements:
│   ├── Count (total requests)
│   ├── Total time
│   ├── Max time
│   └── Percentiles (P50, P95, P99)

Example Query (Grafana):
rate(http.server.requests_seconds_sum{service="claims-service"}[5m]) / 
rate(http.server.requests_seconds_count{service="claims-service"}[5m])
→ Average latency over 5 minutes
```

#### JVM Metrics

```
jvm.memory.used
├── Dimensions: area=heap|nonheap, id=memory_pool_name
├── Unit: bytes
└── Threshold: Alert if > 80% of max

jvm.threads.live
├── Current thread count
└── Threshold: Alert if unusual spike

jvm.gc.pause
├── Garbage collection pause duration
└── Threshold: Alert if > 100ms
```

#### Database Metrics

```
jdbc.connections.usage
├── Dimensions: name=connection_pool_name
├── Measurement: Active, idle, pending
└── Threshold: Alert if > 80% of max

jdbc.connections.creation
├── Time to create new connection
└── Threshold: Alert if > 1s
```

#### Kafka Metrics

```
kafka.producer
├── kafka_produce_record_total: Count of messages sent
├── kafka_produce_record_latency: Send latency
└── kafka_produce_record_error_total: Send failures

kafka.consumer
├── kafka_consumer_record_lag_max: Consumer lag
├── kafka_consumer_record_poll_total: Polls received
└── kafka_consumer_records_lag_avg: Average lag
```

#### Redis Metrics

```
redis.command.duration
├── Dimensions: command=GET|SET|DEL, etc.
├── Unit: milliseconds
└── Threshold: Alert if > 100ms

redis.commands.latency.total
├── Total commands executed
└── Threshold: Monitor for anomalies
```

#### Custom Business Metrics

```
saga.started
├── Counter: Number of sagas initiated
├── Tags: action=CREATE_CLAIM|APPROVE_CLAIM

saga.completed
├── Counter: Number of sagas completed successfully
└── Tags: action, duration_ms

saga.failed
├── Counter: Number of sagas that failed
└── Tags: action, failure_reason

claim.created
├── Counter: Number of claims created
├── Tags: claim_type=MEDICAL|AUTO|PROPERTY
└── Histogram: Amount distribution

claim.processing.latency
├── Timer: Time from creation to completion
└── Tags: status=APPROVED|REJECTED
```

### PromQL Queries

```promql
# Request rate (per second)
rate(http.server.requests_seconds_count[5m])

# Error rate
rate(http.server.requests_seconds_count{status=~"5.."}[5m])

# Latency (P99)
histogram_quantile(0.99, http.server.requests_seconds_bucket)

# Cache hit rate
rate(redis.command.duration_seconds_count{command="GET"}[5m]) /
rate(redis.command.duration_seconds_count[5m])

# Saga success rate
rate(saga.completed_total[5m]) /
(rate(saga.completed_total[5m]) + rate(saga.failed_total[5m]))

# Database connection pool utilization
jdbc.connections.active / jdbc.connections.max
```

---

## Distributed Tracing

### Zipkin Integration

**Endpoint:** `http://localhost:9411`

### Trace Collection

Each service sends traces to Zipkin via Brave:

```yaml
spring.cloud.sleuth:
  tracing:
    enabled: true
    # Sampling rate (0.0 to 1.0)
    sampler:
      probability: 0.1  # Sample 10% of requests (for performance)
  
  # Span details
  span:
    log: true  # Log span creation/closing
    
  # Propagation context headers
  propagation-keys:
  - X-Trace-ID
  - X-Span-ID
  - X-Parent-Span-ID
  
  # Zipkin exporter
  zipkin:
    endpoint: http://localhost:9411/api/v2/spans
    enabled: true
```

### Trace Context Propagation

**HTTP Headers:**
```
X-Trace-ID: 4e17d3a9c6b7f2d1e8f9a0b1c2d3e4f5
X-Span-ID: a1b2c3d4e5f6g7h8
X-Parent-Span-ID: 1a2b3c4d5e6f7g8
```

### Tracing Flow

```
1. User Request (API Gateway)
   → Trace ID: generated or extracted from header
   → Span ID: generated
   
2. API Gateway → Claims Service
   → Parent Span ID: copied from gateway span
   → New Span ID: generated
   → Trace ID: propagated
   
3. Claims Service → PostgreSQL
   → Span created for query
   → Query time tracked
   
4. Claims Service → Kafka Publish
   → Span created for publish
   → Topic and partition tracked
   
5. All spans sent to Zipkin
   → Zipkin reconstructs trace tree
   → Visual timeline displayed in UI
```

### Zipkin UI

**Trace Search:**
- By service name
- By trace ID
- By duration
- By error status

**Trace Details:**
- Span timeline
- Service dependencies
- Latency breakdown
- Error details

---

## Structured Logging

### Log Format

```json
{
  "timestamp": "2026-08-04T10:30:45.123Z",
  "level": "INFO",
  "logger_name": "com.claimassist.platform.claims_service.controller.ClaimController",
  "message": "Claim created successfully",
  "trace_id": "4e17d3a9c6b7f2d1",
  "span_id": "a1b2c3d4e5f6g7h8",
  "service_name": "claims-service",
  "environment": "local",
  "user_id": "user@example.com",
  "claim_id": "550e8400-e29b-41d4-a716-446655440000",
  "policy_id": "660e8400-e29b-41d4-a716-446655440001",
  "http_method": "POST",
  "http_path": "/api/claims",
  "http_status": 201,
  "http_duration_ms": 125,
  "exception": null,
  "mdc": {
    "userId": "user@example.com",
    "requestId": "550e8400-e29b-41d4-a716-446655440000"
  }
}
```

### Configuration

```yaml
logging:
  level:
    root: INFO
    com.claimassist.platform: DEBUG
    
  pattern:
    console: "%d{ISO8601} %5p %c{1} - %msg%n"
    file: "%d{ISO8601} %5p [%thread] %c{1} - %msg%n"
    
  logback:
    rollingpolicy:
      max-file-size: 10MB
      max-history: 30
```

### Logback Configuration

```xml
<configuration>
  <conversionRule conversionWord="clr" 
    converterClass="org.springframework.boot.logging.logback.ColorConverter" />
  
  <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>
        %d{ISO8601} %5p [%thread] %c{1} - %msg%n
      </pattern>
    </encoder>
  </appender>
  
  <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>logs/application.log</file>
    <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
      <fileNamePattern>logs/application-%d{yyyy-MM-dd}-%i.log.gz</fileNamePattern>
      <maxFileSize>10MB</maxFileSize>
      <maxHistory>30</maxHistory>
    </rollingPolicy>
    <encoder>
      <pattern>
        %d{ISO8601} %5p [%thread] %c{1} - %msg%n
      </pattern>
    </encoder>
  </appender>
  
  <root level="INFO">
    <appender-ref ref="CONSOLE" />
    <appender-ref ref="FILE" />
  </root>
</configuration>
```

### Log Aggregation

**Centralized Logging Setup:**

```bash
# Forward logs to Elasticsearch
# Or Datadog, Splunk, CloudWatch, etc.

# Example: ELK Stack
filebeat → Elasticsearch
       ↓
Kibana (visualization)

# Docker container logs accessible via:
docker logs claimassist-claims-service
kubectl logs -f pod/claims-service-xyz -n claimassist-core
```

---

## Health Checks

### Health Endpoints

```
/actuator/health              # Overall application health
/actuator/health/live         # Liveness probe (kubernetes)
/actuator/health/ready        # Readiness probe (kubernetes)
/actuator/health/outboxHealth # Outbox event status
/actuator/health/kafkaHealth  # Kafka broker connectivity
```

### Health Response Example

```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    },
    "redis": {
      "status": "UP",
      "details": {
        "host": "localhost",
        "port": 6379
      }
    },
    "kafka": {
      "status": "UP",
      "details": {
        "bootstrapServers": "localhost:9092"
      }
    },
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 536870912,
        "free": 268435456,
        "threshold": 10485760
      }
    }
  }
}
```

### Custom Health Indicator

```java
@Component
public class OutboxHealthIndicator extends AbstractHealthIndicator {
    
    @Override
    protected void doHealthCheck(Health.Builder builder) {
        Long staleCount = outboxEventRepository.countStalePendingEvents();
        
        if (staleCount < 1000) {
            builder.up()
                .withDetail("outboxEvents", "Healthy")
                .withDetail("stalePendingCount", staleCount);
        } else if (staleCount < 5000) {
            builder.degraded()
                .withDetail("outboxEvents", "Degraded")
                .withDetail("stalePendingCount", staleCount)
                .withDetail("recommendation", "Monitor outbox publisher");
        } else {
            builder.down()
                .withDetail("outboxEvents", "Critical")
                .withDetail("stalePendingCount", staleCount)
                .withDetail("action", "Check OutboxPublisher logs");
        }
    }
}
```

---

## Alerting Rules

### Prometheus Alerts

```yaml
groups:
- name: claims-service
  rules:
  - alert: HighErrorRate
    expr: rate(http.server.requests_seconds_count{status=~"5.."}[5m]) > 0.05
    for: 5m
    labels:
      severity: critical
    annotations:
      summary: "High error rate on {{ $labels.service }}"
      
  - alert: HighLatency
    expr: histogram_quantile(0.99, http.server.requests_seconds_bucket) > 5
    for: 10m
    labels:
      severity: warning
    annotations:
      summary: "High P99 latency on {{ $labels.service }}"
      
  - alert: OutboxBacklog
    expr: outbox_events_pending_total > 1000
    for: 5m
    labels:
      severity: warning
    annotations:
      summary: "Outbox event backlog growing"
```

---


