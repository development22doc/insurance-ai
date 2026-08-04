# Architecture Documentation

**Version:** 1.0.0  
**Last Updated:** August 4, 2026

---

## Table of Contents
1. [High-Level Architecture](#high-level-architecture)
2. [Service Architecture](#service-architecture)
3. [Data Architecture](#data-architecture)
4. [Communication Patterns](#communication-patterns)
5. [Deployment Architecture](#deployment-architecture)
6. [Security Architecture](#security-architecture)

---

## High-Level Architecture

### System Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                         Client Layer                             │
│                    (Web, Mobile, Desktop)                        │
└────────────────────────┬────────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────────┐
│                    API Gateway (Port 8080)                       │
│         (OAuth2 Validation, Rate Limiting, Routing)             │
└────┬─────────┬──────────┬──────────┬──────────────────────────┬─┘
     │         │          │          │                          │
┌────▼──┐  ┌────▼──┐  ┌───▼───┐  ┌──▼────┐              ┌──────▼┐
│Config │  │Discovery│ │Customer  │Claims  │              │Agent  │
│Service│  │Service  │ │Service   │Service │              │Service│
│(8888) │  │(8761)   │ │(8081)    │(8082)  │              │(8083) │
└────┬──┘  └────┬──┘  └───┬───┘  └──┬────┘              └──┬────┘
     │          │         │         │                      │
     └──────────┴─────────┼─────────┴──────────────────────┘
                          │
        ┌─────────────────┼─────────────────┐
        │                 │                 │
     ┌──▼──┐        ┌─────▼────┐       ┌───▼────┐
     │Redis│        │PostgreSQL │       │MongoDB │
     │Cache│        │(Port 5432)│       │(27017) │
     └─────┘        └───────────┘       └────────┘
        │                 │                 │
        └─────────────────┼─────────────────┘
                          │
        ┌─────────────────┼─────────────────┐
        │                 │                 │
     ┌──▼────┐       ┌───▼───┐        ┌────▼────┐
     │Keycloak│      │Kafka  │        │Zipkin   │
     │(8180)  │      │(9092) │        │(9411)   │
     └────────┘      └───────┘        └─────────┘
```

### Component Interactions

1. **Client Request Flow:**
   - Client sends request to API Gateway
   - Gateway validates JWT token with Keycloak
   - Gateway routes to appropriate microservice

2. **Event-Driven Flow:**
   - Service creates domain event (persisted to OutboxEvent table)
   - Scheduled task publishes OutboxEvent to Kafka topic
   - Consumer services listen to topics and process events
   - Processing may trigger saga steps or CQRS updates

3. **Observability Flow:**
   - Services emit metrics to Prometheus endpoints
   - Distributed traces sent to Zipkin via Brave
   - Logs written to stdout in JSON format

---

## Service Architecture

### API Gateway Architecture

**Purpose:** Single entry point for all client requests

**Responsibilities:**
- HTTP request routing
- OAuth2/JWT validation
- Rate limiting and throttling
- CORS and security headers
- Request/response logging

**Key Components:**
- `GatewaySecurityConfig` - Spring Security configuration
- Spring Cloud Gateway routes
- OAuth2 Resource Server configuration

**External Dependencies:**
- Keycloak (token validation)
- Discovery Service (service lookup)
- Configuration Service (dynamic config)

**Failure Scenarios:**
- Keycloak unavailable → Return 503 (token validation fails)
- Backend service unavailable → Return 503 (circuit breaker OPEN)
- Rate limit exceeded → Return 429 (Too Many Requests)

---

### Claims Service Architecture

**Purpose:** Core claims processing engine and saga orchestrator

**Responsibilities:**
- Claim aggregate management
- Saga orchestration (state machine)
- Event publishing via transactional outbox
- Kafka consumer for claim update requests
- Redis-backed claim caching

**Domain Model:**
```
Claim (Aggregate Root)
├── claim_id (PK)
├── policy_id (FK to Customer Service)
├── claim_type (MEDICAL, AUTO, PROPERTY)
├── amount
├── status (INITIATED, UNDER_REVIEW, APPROVED, REJECTED, PAID)
├── created_at
└── updated_at

ClaimSagaOrchestration (Process Manager)
├── saga_id
├── claim_id
├── action (CREATE_CLAIM, APPROVE_CLAIM, etc.)
├── state (INITIATED, IN_PROGRESS, COMPLETED, etc.)
├── timeout_at
└── recovery_attempts

OutboxEvent (Transactional Outbox)
├── event_id
├── aggregate_id
├── event_type
├── payload (JSON)
├── status (PENDING, PUBLISHED, FAILED)
└── retry_count
```

**Event Flow:**
```
1. REST API receives claim creation request
2. Claim aggregate created in PostgreSQL
3. OutboxEvent created in same transaction
4. Transaction commits (atomic)
5. OutboxPublisher scheduled task polls every 2 seconds
6. OutboxEvent published to Kafka claim-saga-orchestration-request-event
7. Listeners process events and update saga state
8. Compensation events published on failure
```

**Saga Flow:**
```
CREATE_CLAIM (claims-service)
    ↓
APPROVE_CLAIM (claims-service)
    ├─→ Success → PAYMENT (agent-service)
    └─→ Failure → COMPENSATION
         ↓
    NOTIFICATION (external service)
         ↓
    ROLLBACK (claims-service)
```

**Key Classes:**
- `Claim.java` - Claim aggregate
- `ClaimSagaOrchestration.java` - Saga state machine
- `ClaimSagaOrchestratorService.java` - Orchestration logic
- `OutboxEvent.java` - Event storage entity
- `OutboxEventPublisher.java` - Scheduled event publisher
- `ClaimUpdateConsumer.java` - Kafka listener

---

### Agent Service Architecture

**Purpose:** Intelligent claim evaluation and fraud detection

**Responsibilities:**
- Claim evaluation and scoring
- Fraud detection and risk assessment
- Saga step processing
- Event consumption and response handling

**Domain Model:**
```
AgentEvent (Event tracking)
├── event_id
├── claim_id
├── evaluation_score
├── fraud_risk_level (LOW, MEDIUM, HIGH)
├── status (PENDING, CONFIRMED, FAILED)
└── error_message
```

**Event Processing:**
```
Consumes: claim-saga-orchestration-request-event
    ↓
Evaluates claim (business logic)
    ↓
Publishes: claim-saga-step-result-event
    ↓
Claims Service consumes and updates saga state
```

**Failure Handling:**
- Failed evaluation → Publishes FAILED status
- Timeout handling → Claims service compensation logic
- Circuit breaker for external ML model calls

---

### Customer Service Architecture

**Purpose:** Customer profile and policy management

**Responsibilities:**
- Customer identity management
- Policy information retrieval
- Customer validation for claims
- Service-to-service authentication

**Domain Model:**
```
Customer
├── customer_id (PK)
├── first_name
├── last_name
├── email
├── phone
└── kyc_status (VERIFIED, PENDING)

Policy
├── policy_id (PK)
├── customer_id (FK)
├── policy_type (HEALTH, AUTO, PROPERTY)
├── coverage_amount
├── premium
└── status (ACTIVE, LAPSED, TERMINATED)
```

**Integration Points:**
- Called by Claims Service (via OAuth2 client credentials)
- Provides policy lookup for claim creation
- Redis caching for frequently accessed policies

---

### Config Service Architecture

**Purpose:** Centralized configuration management

**Responsibilities:**
- Spring Cloud Config Server
- Environment-specific configuration
- Dynamic property refresh support
- Configuration versioning

**Configuration Sources:**
```
local-config-repo/
├── api-gateway.yml
├── customer-service.yml
├── claims-service.yml
├── agent-service.yml
├── discovery-service.yml
└── config-service.yml
```

**Client Configuration:**
```
spring:
  cloud:
    config:
      uri: http://config-service:8888
      fail-fast: true
      retry:
        initial-interval: 1000
        max-interval: 10000
        max-attempts: 6
```

---

### Discovery Service Architecture

**Purpose:** Service registry and discovery

**Responsibilities:**
- Eureka server for service registration
- Service health monitoring
- Client-side load balancing

**Service Registration:**
```
Each service registers with Eureka on startup:
  GET  /eureka/v2/apps/{SERVICE_NAME}
  POST /eureka/v2/apps/{SERVICE_NAME}
  
Heartbeat interval: 30 seconds
Eviction timeout: 90 seconds (after 3 missed heartbeats)
```

**Service Lookup:**
```
Spring Cloud LoadBalancer uses Eureka registry
to discover available instances of target service
```

---

## Data Architecture

### PostgreSQL Schema

**Database: claims_db**
```sql
-- Core claim management
CREATE TABLE claims (
    claim_id UUID PRIMARY KEY,
    policy_id UUID NOT NULL,
    claim_type VARCHAR(50) NOT NULL,
    amount DECIMAL(12, 2),
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- Claim parties (claimant, beneficiary, etc.)
CREATE TABLE claim_parties (
    party_id UUID PRIMARY KEY,
    claim_id UUID NOT NULL REFERENCES claims,
    party_type VARCHAR(50),
    name VARCHAR(255),
    relationship VARCHAR(50)
);

-- Attached documents
CREATE TABLE claim_documents (
    document_id UUID PRIMARY KEY,
    claim_id UUID NOT NULL REFERENCES claims,
    document_type VARCHAR(50),
    file_path VARCHAR(1024),
    uploaded_at TIMESTAMP
);

-- Status change audit trail
CREATE TABLE claim_status_history (
    history_id UUID PRIMARY KEY,
    claim_id UUID NOT NULL REFERENCES claims,
    old_status VARCHAR(50),
    new_status VARCHAR(50),
    changed_at TIMESTAMP,
    changed_by VARCHAR(255)
);

-- Transactional Outbox for event publishing
CREATE TABLE outbox_events (
    event_id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
);

-- Saga orchestration state
CREATE TABLE claim_saga_orchestrations (
    saga_id UUID PRIMARY KEY,
    claim_id UUID NOT NULL REFERENCES claims,
    action VARCHAR(50) NOT NULL,
    state VARCHAR(50) NOT NULL,
    steps_completed INTEGER,
    timeout_at TIMESTAMP,
    recovery_attempts INTEGER DEFAULT 0,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- Idempotency tracking
CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    claim_id UUID NOT NULL,
    event_type VARCHAR(255),
    processed_at TIMESTAMP NOT NULL
);
```

**Database: customer_db**
```sql
CREATE TABLE customers (
    customer_id UUID PRIMARY KEY,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE,
    phone VARCHAR(20),
    kyc_status VARCHAR(50)
);

CREATE TABLE policies (
    policy_id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customers,
    policy_type VARCHAR(50),
    coverage_amount DECIMAL(12, 2),
    premium DECIMAL(10, 2),
    status VARCHAR(50)
);
```

---

### MongoDB Schema

**Database: claims_read_model**

```json
{
  "claims": [
    {
      "_id": ObjectId(),
      "claim_id": "uuid",
      "policy_id": "uuid",
      "customer_name": "John Doe",
      "claim_type": "MEDICAL",
      "amount": 5000.00,
      "status": "UNDER_REVIEW",
      "agent_evaluation": {
        "score": 0.85,
        "fraud_risk": "LOW"
      },
      "created_at": ISODate(),
      "updated_at": ISODate()
    }
  ],
  "claim_events": [
    {
      "_id": ObjectId(),
      "claim_id": "uuid",
      "event_type": "CLAIM_CREATED",
      "event_data": { /* full event payload */ },
      "timestamp": ISODate()
    }
  ]
}
```

**Synchronization:** MongoDB collections updated via Kafka event listeners (async from transaction)

---

### Redis Caching Strategy

**Key Patterns:**

```
customer:{customer_id}             → Customer profile (TTL: 300s)
policy:{policy_id}                 → Policy details (TTL: 600s)
claim:{claim_id}                   → Claim summary (TTL: 60s)
claim:status:{claim_id}            → Current status (TTL: 30s)
lookup:claim-types                 → Lookup table (TTL: 3600s)
```

**Cache Invalidation:**
```
On claim update:
  1. Update database
  2. Delete Redis key: claim:{claim_id}
  3. Delete Redis key: claim:status:{claim_id}
  4. Update customer policy cache if policy changed
```

**Connection Pool Configuration:**
```yaml
spring.redis:
  max-active: 20      # Max active connections
  max-idle: 10        # Max idle connections
  min-idle: 5         # Min idle connections
  max-wait: -1ms      # Wait forever for connection
  timeout: 2000ms     # Connection timeout
```

---

## Communication Patterns

### Synchronous (HTTP/REST)

Used for:
- API Gateway → Microservices
- Customer Service lookups (claims-service → customer-service)
- Configuration retrieval (all services → config-service)

**Resilience:**
```
→ Circuit Breaker (Resilience4j)
  - Threshold: 50% failure rate
  - State: CLOSED → OPEN → HALF_OPEN
  
→ Retry Strategy
  - Max attempts: 3
  - Wait: 1 second base
  - Backoff: Exponential
  
→ Timeout
  - Default: 5 seconds
  - Per-service configurable
  
→ Bulkhead (Thread Pool Isolation)
  - Pool size: 10 threads
  - Queue: LinkedBlockingQueue
  - Rejection: CallerRunsPolicy
```

---

### Asynchronous (Kafka/Events)

Used for:
- Saga orchestration (claim processing workflow)
- CQRS updates (PostgreSQL → MongoDB)
- Event audit trail
- Inter-service decoupling

**Kafka Topics:**

```
claim-update-request-event
├── Partitions: 3
├── Replication: 1
├── DLT: claim-update-request-event.DLT
├── Producer: Agent Service
└── Consumer: Claims Service

claim-update-response-event
├── Partitions: 3
├── DLT: claim-update-response-event.DLT
├── Producer: Claims Service
└── Consumer: Agent Service

claim-saga-orchestration-request-event
├── Partitions: 3
├── DLT: .DLT variant
├── Producer: Claims Service
└── Consumer: Saga Orchestrator

claim-saga-step-command-event
├── Partitions: 3
├── DLT: .DLT variant
├── Producer: Saga Orchestrator
└── Consumer: Saga Step Processors

claim-saga-step-result-event
├── Partitions: 3
├── DLT: .DLT variant
├── Producer: Step Processor
└── Consumer: Saga Orchestrator

claim-saga-orchestration-result-event
├── Partitions: 3
├── DLT: .DLT variant
├── Producer: Saga Orchestrator
└── Consumer: External Services
```

**Event Publishing (Transactional Outbox):**
```
1. Business operation writes to outbox table
   (same transaction as domain change)
2. Transaction commits
3. OutboxPublisher polls every 2 seconds
4. Publishes batch of 100 events to Kafka
5. Updates status to PUBLISHED in database
6. On failure → retry with exponential backoff
7. Max retries: 5 (1s, 2s, 4s, 8s, 16s, then 60s)
8. Unreplayable messages → Dead Letter Topic
```

**Event Consumption (Idempotent):**
```
1. Listener receives message
2. Check ProcessedEventRepository for event_id
3. If found → Skip (already processed)
4. If not found → Process event
5. Insert into ProcessedEventRepository (atomic)
6. Update domain model
7. Manually commit offset
```

---

### Service-to-Service Authentication

**OAuth2 Client Credentials Flow:**

```
Service A needs to call Service B:

1. Service A → Keycloak: POST /protocol/openid-connect/token
   {
     grant_type: "client_credentials",
     client_id: "claimassist-admin-service",
     client_secret: "secret"
   }

2. Keycloak → Service A: JWT token
   {
     access_token: "eyJhbGc...",
     expires_in: 3600,
     token_type: "Bearer"
   }

3. Service A → Service B: GET /api/endpoint
   Header: Authorization: Bearer eyJhbGc...

4. Service B validates token:
   a. Fetch JWKS from Keycloak
   b. Verify signature
   c. Check expiration
   d. Extract claims
   e. Verify scopes
   f. Process request
```

**JWT Token Structure:**
```json
{
  "iss": "http://keycloak:8180/realms/claimassist",
  "sub": "claimassist-admin-service",
  "aud": "account",
  "exp": 1600000000,
  "iat": 1599996400,
  "jti": "unique-id",
  "scope": "read write",
  "client_id": "claimassist-admin-service",
  "realm_access": {
    "roles": ["ADMIN", "SERVICE"]
  }
}
```

---

## Deployment Architecture

### Kubernetes Resources

**Namespaces:**
```
claimassist-core          # Application services
claimassist-infra         # Database, cache, messaging
claimassist-observability # Prometheus, Zipkin, Grafana
```

**Resources per Service:**
```
Deployment
├── Replicas: 1 (scalable via HPA)
├── Container: Docker image
├── Resources:
│   ├── Request: 200m CPU, 512Mi memory
│   └── Limit: 500m CPU, 1Gi memory
├── Probes:
│   ├── Liveness: /actuator/health/live (10s)
│   ├── Readiness: /actuator/health/ready (5s)
│   └── Startup: /actuator/health (1m timeout)
└── Environment: ConfigMap + Secrets

Service
├── Type: ClusterIP (internal) or LoadBalancer (API Gateway)
├── Port: Service port
├── Target Port: Container port
└── Session Affinity: None (stateless)

ConfigMap
├── configuration files (yaml)
└── Non-sensitive properties

Secrets
├── Database credentials
├── API keys
├── TLS certificates
└── OAuth2 client secrets

PersistentVolumeClaim
├── PostgreSQL: 20Gi storage
├── MongoDB: 20Gi storage
├── Redis: 5Gi storage
└── Logs: 10Gi storage
```

---

### Horizontal Pod Autoscaler

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: claims-service-hpa
spec:
  scaleTargetRef:
    kind: Deployment
    name: claims-service
  minReplicas: 1
  maxReplicas: 5
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

---

### Network Policies

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-claims-to-postgres
  namespace: claimassist-core
spec:
  podSelector:
    matchLabels:
      app: claims-service
  policyTypes:
  - Egress
  egress:
  - to:
    - namespaceSelector:
        matchLabels:
          name: claimassist-infra
    ports:
    - protocol: TCP
      port: 5432
```

---

## Observability Architecture

### Metrics Collection

**Prometheus Endpoints:**
- API Gateway: `http://api-gateway:8080/actuator/prometheus`
- Claims Service: `http://claims-service:8082/actuator/prometheus`
- Agent Service: `http://agent-service:8083/actuator/prometheus`
- Customer Service: `http://customer-service:8081/actuator/prometheus`

**Scraped Metrics:**
- HTTP request latencies (percentiles)
- Error rates
- Database connection pool status
- Redis command latencies
- Kafka consumer lag
- JVM memory and GC
- Saga state transitions (custom)

---

### Distributed Tracing

**Zipkin Integration:**
```
Service → Brave (Spring Cloud Sleuth)
  → Instrumented HTTP client
  → Sends spans to Zipkin (http://zipkin:9411)
  
Trace Context:
  X-Trace-ID: Unique trace identifier
  X-Span-ID: Span within trace
  X-Parent-Span-ID: Parent span reference
```

**Spans Created For:**
- HTTP requests (incoming)
- HTTP client calls (outgoing)
- Database queries
- Kafka producer sends
- Kafka consumer receives
- Cache operations

---

### Logging Strategy

**Format:** JSON structured logging

```json
{
  "timestamp": "2026-08-04T10:30:45.123Z",
  "level": "INFO",
  "logger_name": "com.claimassist.platform.claims_service.controller",
  "message": "Claim created successfully",
  "trace_id": "4e17d3a9c6b7f2d1",
  "span_id": "6b7f2d1a",
  "claim_id": "550e8400-e29b-41d4-a716-446655440000",
  "user_id": "user@example.com",
  "http_method": "POST",
  "http_status": 201
}
```

**Log Destinations:**
- Container stdout (Kubernetes logs)
- Log aggregation (ELK, Splunk, Datadog) via agents

---

## Summary

The ClaimAssist AI Platform architecture implements:

1. **Microservices:** Independently deployable services with clear responsibilities
2. **Event-Driven:** Kafka-based async communication with transactional outbox
3. **Resilient:** Circuit breakers, retries, timeouts, rate limiting
4. **Observable:** Prometheus metrics, Zipkin traces, structured logging
5. **Secure:** OAuth2/JWT authentication, zero-trust networking
6. **Scalable:** Horizontal pod autoscaling, load balancing
7. **Maintainable:** Clear separation of concerns, consistent patterns


