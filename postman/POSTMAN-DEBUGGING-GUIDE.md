# ClaimAssist Postman Debugging Guide

## Purpose

This guide maps Postman requests to the complete ClaimAssist system architecture, providing the kubectl commands, log locations, failure modes, and debugging strategies needed for the 7-day intensive learning sprint. Use this guide to trace requests from Postman through the entire stack: Gateway → Authentication → Microservices → Database/Redis/Kafka/AI → Kubernetes → Logs.

---

## Quick Reference: Architecture Overview

```
Postman Request
       ↓
API Gateway (8080)
       ↓
Keycloak Authentication (30080)
       ↓
Microservices:
  - Customer Service (8081) → PostgreSQL (30432) + Redis (30379)
  - Claims Service (8082) → PostgreSQL (30432) + Kafka (30092)
  - Agent Service (8083) → H2 + Redis (30379) + Ollama (11434) + Kafka (30092)
       ↓
Infrastructure:
  - PostgreSQL (100.114.133.69:30432)
  - Redis (100.114.133.69:30379)
  - Kafka (100.114.133.69:30092)
  - Keycloak (100.114.133.69:30080)
  - Ollama (localhost:11434)
  - Zipkin (100.114.133.69:30941)
       ↓
Observability:
  - Prometheus (100.114.133.69:30900)
  - Grafana (100.114.133.69:30300)
  - Loki (100.114.133.69:30310)
```

---

## Kubernetes Context

### Cluster Information
- **Platform:** OCI K3s via Tailscale
- **Access:** Tailscale NodePorts
- **Namespace:** claimassist-core (typically)

### Basic kubectl Commands

```bash
# Get all pods in the namespace
kubectl get pods -n claimassist-core

# Get services with NodePorts
kubectl get svc -n claimassist-core

# Get pod details
kubectl describe pod <pod-name> -n claimassist-core

# Get pod logs
kubectl logs <pod-name> -n claimassist-core -f

# Get logs for a specific container
kubectl logs <pod-name> -n claimassist-core -c <container-name> -f

# Get pod events
kubectl get events -n claimassist-core --sort-by='.lastTimestamp'

# Exec into a pod
kubectl exec -it <pod-name> -n claimassist-core -- /bin/bash

# Check pod resource usage
kubectl top pods -n claimassist-core
```

---

## 00 - Environment & Health

### Gateway Health Check
**Postman Request:** `GET {{baseUrl}}/actuator/health`

**Expected Service:** api-gateway  
**Expected Pod:** `api-gateway-*`  
**Expected Logs:** Gateway startup, health check responses  
**Kubernetes Commands:**
```bash
# Check gateway pod status
kubectl get pods -n claimassist-core -l app=api-gateway

# Check gateway logs
kubectl logs -n claimassist-core -l app=api-gateway -f

# Check gateway events
kubectl describe pod -n claimassist-core -l app=api-gateway
```

**Common Failure Modes:**
1. **Pod Not Running:** Check pod status, describe pod for events
2. **Health Check Timeout:** Check resource limits, network connectivity
3. **Circuit Breaker Open:** Check downstream service availability
4. **Redis Connection Failed:** Check Redis pod and connectivity

**Log Locations:**
- Application logs: `kubectl logs api-gateway-*`
- Gateway routing logs: Look for "Route applied" messages
- Circuit breaker logs: Look for "CircuitBreaker" messages

---

### Customer Service Health
**Postman Request:** `GET {{customerServiceUrl}}/actuator/health`

**Expected Service:** customer-service  
**Expected Pod:** `customer-service-*`  
**Expected Database:** PostgreSQL (claimassist_customer)  
**Expected Cache:** Redis  
**Kubernetes Commands:**
```bash
# Check customer service pod
kubectl get pods -n claimassist-core -l app=customer-service

# Check customer service logs
kubectl logs -n claimassist-core -l app=customer-service -f

# Check PostgreSQL connectivity
kubectl exec -it customer-service-* -n claimassist-core -- pg_isready -h <postgres-host> -p 5432
```

**Common Failure Modes:**
1. **Database Connection Failed:** Check PostgreSQL pod, credentials, network
2. **Redis Connection Failed:** Check Redis pod, connection settings
3. **Flyway Migration Failed:** Check database schema, migration scripts
4. **OutOfMemory:** Check pod memory limits, JVM heap settings

**Log Locations:**
- Application logs: `kubectl logs customer-service-*`
- Database logs: PostgreSQL pod logs
- Redis logs: Redis pod logs

---

### Claims Service Health
**Postman Request:** `GET {{claimsServiceUrl}}/actuator/health`

**Expected Service:** claims-service  
**Expected Pod:** `claims-service-*`  
**Expected Database:** PostgreSQL (claimassist_claims)  
**Expected Kafka:** Kafka broker connectivity  
**Kubernetes Commands:**
```bash
# Check claims service pod
kubectl get pods -n claimassist-core -l app=claims-service

# Check claims service logs
kubectl logs -n claimassist-core -l app=claims-service -f

# Check Kafka connectivity from claims service
kubectl exec -it claims-service-* -n claimassist-core -- nc -zv <kafka-host> 9092
```

**Common Failure Modes:**
1. **Kafka Connection Failed:** Check Kafka broker, topic creation
2. **Database Connection Failed:** Check PostgreSQL pod, credentials
3. **Outbox Publisher Stuck:** Check outbox table, Kafka consumer lag
4. **Saga Recovery Failed:** Check saga orchestration table, stuck sagas

**Log Locations:**
- Application logs: `kubectl logs claims-service-*`
- Kafka logs: Kafka broker pod logs
- Database logs: PostgreSQL pod logs

---

### Agent Service Health
**Postman Request:** `GET {{agentServiceUrl}}/actuator/health`

**Expected Service:** agent-service  
**Expected Pod:** `agent-service-*`  
**Expected LLM:** Ollama (localhost:11434)  
**Expected Cache:** Redis  
**Expected Kafka:** Kafka broker connectivity  
**Kubernetes Commands:**
```bash
# Check agent service pod
kubectl get pods -n claimassist-core -l app=agent-service

# Check agent service logs
kubectl logs -n claimassist-core -l app=agent-service -f

# Check Ollama connectivity
kubectl exec -it agent-service-* -n claimassist-core -- curl http://localhost:11434/api/tags
```

**Common Failure Modes:**
1. **Ollama Connection Failed:** Check Ollama service, model availability
2. **Redis Connection Failed:** Check Redis pod, cache configuration
3. **Kafka Connection Failed:** Check Kafka broker, topic creation
4. **Tool Execution Timeout:** Check tool execution guards, downstream service timeouts
5. **Prompt Injection Detected:** Check input guardrails logs

**Log Locations:**
- Application logs: `kubectl logs agent-service-*`
- Ollama logs: Ollama service logs
- Tool execution logs: Look for "Tool call:" messages
- AI response logs: Look for "LLM response:" messages

---

## 01 - Authentication

### Signup
**Postman Request:** `POST {{baseUrl}}/customer/auth/signup`

**Request Flow:**
```
Postman → Gateway → Customer Service → Keycloak Admin API → Customer DB → Response
```

**Expected Services:**
- API Gateway (routing)
- Customer Service (business logic)
- Keycloak (user creation)
- PostgreSQL (customer record)

**Kubernetes Commands:**
```bash
# Check customer service logs for signup
kubectl logs -n claimassist-core -l app=customer-service | grep -i signup

# Check Keycloak for user creation
kubectl exec -it keycloak-* -n claimassist-core -- /opt/keycloak/bin/kcadm.sh get users

# Check database for customer record
kubectl exec -it postgres-* -n claimassist-core -- psql -U claimassist -d claimassist_customer -c "SELECT * FROM customers WHERE username = '<email>';"
```

**Common Failure Modes:**
1. **Keycloak Connection Failed:** Check Keycloak pod, admin credentials
2. **Duplicate Email:** Check database for existing customer
3. **Validation Failed:** Check request payload against DTO validation rules
4. **Database Constraint Failed:** Check database constraints, unique indexes

**Debugging Steps:**
1. Check Gateway logs for routing success
2. Check Customer Service logs for signup processing
3. Check Keycloak logs for user creation
4. Check database for customer record
5. Check response for validation errors

---

### OAuth2 Authorization
**Postman Request:** `GET {{baseUrl}}/customer/auth/authorize`

**Request Flow:**
```
Postman → Gateway → Customer Service → PKCE Code Generation → Keycloak Redirect
```

**Expected Services:**
- API Gateway (routing)
- Customer Service (PKCE logic)
- Keycloak (authorization endpoint)

**Kubernetes Commands:**
```bash
# Check customer service logs for authorization
kubectl logs -n claimassist-core -l app=customer-service | grep -i authorize

# Check Keycloak logs for authorization requests
kubectl logs -n claimassist-core -l app=keycloak | grep -i authorize
```

**Common Failure Modes:**
1. **Keycloak Redirect Failed:** Check Keycloak URL configuration
2. **PKCE Code Generation Failed:** Check code verifier storage (Redis)
3. **State Parameter Missing:** Check state generation and validation
4. **CORS Error:** Check Gateway CORS configuration

**Debugging Steps:**
1. Check response for redirect location
2. Check Keycloak URL in redirect
3. Check state parameter in redirect
4. Check Customer Service logs for PKCE generation

---

### OAuth2 Callback
**Postman Request:** `GET {{baseUrl}}/customer/auth/callback?code=...&state=...`

**Request Flow:**
```
Postman → Gateway → Customer Service → Code Exchange → Keycloak Token Endpoint → JWT Validation → Response
```

**Expected Services:**
- API Gateway (routing)
- Customer Service (token exchange)
- Keycloak (token endpoint)

**Kubernetes Commands:**
```bash
# Check customer service logs for callback processing
kubectl logs -n claimassist-core -l app=customer-service | grep -i callback

# Check Keycloak logs for token exchange
kubectl logs -n claimassist-core -l app=keycloak | grep -i token
```

**Common Failure Modes:**
1. **Invalid Authorization Code:** Check code parameter, expiration
2. **State Parameter Mismatch:** Check state validation
3. **Keycloak Token Error:** Check Keycloak token endpoint, client credentials
4. **JWT Validation Failed:** Check JWT issuer, signature validation

**Debugging Steps:**
1. Check response for error messages
2. Check Customer Service logs for token exchange
3. Check Keycloak logs for token issuance
4. Verify JWT structure and claims
5. Check customerId in response

---

## 02 - Customer APIs

### Create Policy
**Postman Request:** `POST {{baseUrl}}/customer/policies`

**Request Flow:**
```
Postman → Gateway → Customer Service → Policy Validation → PostgreSQL → Redis Cache → Response
```

**Expected Services:**
- API Gateway (routing, JWT validation)
- Customer Service (business logic)
- PostgreSQL (policy persistence)
- Redis (caching)

**Kubernetes Commands:**
```bash
# Check customer service logs for policy creation
kubectl logs -n claimassist-core -l app=customer-service | grep -i policy

# Check database for policy record
kubectl exec -it postgres-* -n claimassist-core -- psql -U claimassist -d claimassist_customer -c "SELECT * FROM policies ORDER BY created_at DESC LIMIT 1;"

# Check Redis cache for policy
kubectl exec -it redis-* -n claimassist-core -- redis-cli KEYS "policy:*"
```

**Common Failure Modes:**
1. **Coverage Plan Not Found:** Check coverage_plan table, coveragePlanId
2. **Database Constraint Failed:** Check policy constraints, unique indexes
3. **Cache Write Failed:** Check Redis connectivity, cache configuration
4. **Authorization Failed:** Check JWT token, customerId claim

**Debugging Steps:**
1. Check response for validation errors
2. Check Customer Service logs for policy creation
3. Check database for policy record
4. Check Redis cache for policy data
5. Verify policyId and policyNumber in response

---

### Get All Policies (Cached)
**Postman Request:** `GET {{baseUrl}}/customer/policies/all`

**Request Flow:**
```
Postman → Gateway → Customer Service → Redis Cache Check → (Cache Miss: PostgreSQL) → Response
```

**Expected Services:**
- API Gateway (routing, JWT validation)
- Customer Service (cached query service)
- Redis (cache lookup)
- PostgreSQL (cache miss fallback)

**Kubernetes Commands:**
```bash
# Check customer service logs for cache hit/miss
kubectl logs -n claimassist-core -l app=customer-service | grep -i cache

# Check Redis cache for policies
kubectl exec -it redis-* -n claimassist-core -- redis-cli KEYS "policies:*"

# Monitor Redis commands
kubectl exec -it redis-* -n claimassist-core -- redis-cli MONITOR
```

**Common Failure Modes:**
1. **Cache Miss (First Request):** Expected behavior, should populate cache
2. **Cache Hit (Subsequent Request):** Expected behavior, faster response
3. **Redis Connection Failed:** Check Redis pod, connection settings
4. **Cache Deserialization Failed:** Check cache serialization format

**Debugging Steps:**
1. Compare response times between first and second requests
2. Check Customer Service logs for cache hit/miss
3. Check Redis cache for policy data
4. Monitor Redis commands in real-time
5. Verify cache TTL configuration

---

## 03 - Claims APIs

### Submit Claim
**Postman Request:** `POST {{baseUrl}}/claims`

**Request Flow:**
```
Postman → Gateway → Claims Service → Idempotency Check → Policy Validation → PostgreSQL → Response
```

**Expected Services:**
- API Gateway (routing, JWT validation)
- Claims Service (business logic)
- Customer Service (policy validation via Feign)
- PostgreSQL (claim persistence)

**Kubernetes Commands:**
```bash
# Check claims service logs for claim submission
kubectl logs -n claimassist-core -l app=claims-service | grep -i claim

# Check database for claim record
kubectl exec -it postgres-* -n claimassist-core -- psql -U claimassist -d claimassist_claims -c "SELECT * FROM claims ORDER BY created_at DESC LIMIT 1;"

# Check idempotency record
kubectl exec -it postgres-* -n claimassist-core -- psql -U claimassist -d claimassist_claims -c "SELECT * FROM idempotency_records;"
```

**Common Failure Modes:**
1. **Duplicate Idempotency Key:** Check idempotency_records table
2. **Policy Not Found:** Check Customer Service Feign call, policyId
3. **Policy Ownership Failed:** Check customerId vs policy ownership
4. **Database Constraint Failed:** Check claim constraints, unique indexes

**Debugging Steps:**
1. Check response for duplicate submission error
2. Check Claims Service logs for Feign calls to Customer Service
3. Check database for claim record
4. Check idempotency record for key
5. Verify claimId and claimNumber in response

---

### Update Claim Status
**Postman Request:** `PATCH {{baseUrl}}/claims/{{claimId}}/status`

**Request Flow:**
```
Postman → Gateway → Claims Service → Permission Check → State Machine Validation → PostgreSQL → Response
```

**Expected Services:**
- API Gateway (routing, JWT validation)
- Claims Service (business logic, state machine)
- PostgreSQL (claim update)

**Kubernetes Commands:**
```bash
# Check claims service logs for status update
kubectl logs -n claimassist-core -l app=claims-service | grep -i status

# Check database for claim status change
kubectl exec -it postgres-* -n claimassist-core -- psql -U claimassist -d claimassist_claims -c "SELECT * FROM claim_status_history WHERE claim_id = <claimId> ORDER BY changed_at DESC;"

# Check claim version (optimistic locking)
kubectl exec -it postgres-* -n claimassist-core -- psql -U claimassist -d claimassist_claims -c "SELECT id, version, status FROM claims WHERE id = <claimId>;"
```

**Common Failure Modes:**
1. **Permission Denied:** Check user's UPDATE_STATUS permission on claim
2. **Invalid State Transition:** Check state machine rules, current status
3. **Concurrent Update Conflict:** Check optimistic locking version
4. **State Machine Exception:** Check state transition validation

**Debugging Steps:**
1. Check response for permission error
2. Check Claims Service logs for permission check
3. Check claim_status_history for state changes
4. Check claim version for concurrent updates
5. Verify state machine rules in code

---

## 04 - AI / LLM / Agent

### Stream Agent Response
**Postman Request:** `POST {{baseUrl}}/agent/stream`

**Request Flow:**
```
Postman → Gateway → Agent Service → Input Guardrails → LLM (Ollama) → Tool Calling → Tool Execution → Response (SSE)
```

**Expected Services:**
- API Gateway (routing, JWT validation)
- Agent Service (AI orchestration, tool calling)
- Ollama (LLM inference)
- Claims/Customer Services (tool execution)
- Redis (caching, conversation memory)

**Kubernetes Commands:**
```bash
# Check agent service logs for AI processing
kubectl logs -n claimassist-core -l app=agent-service | grep -i "tool\|llm\|agent"

# Check Ollama logs for model inference
kubectl logs -n claimassist-core -l app=ollama | grep -i inference

# Check Redis for conversation cache
kubectl exec -it redis-* -n claimassist-core -- redis-cli KEYS "conversation:*"

# Monitor tool execution
kubectl logs -n claimassist-core -l app=agent-service | grep "Tool call:"
```

**Common Failure Modes:**
1. **Ollama Connection Failed:** Check Ollama service, model availability
2. **Tool Execution Timeout:** Check tool execution guards, downstream service timeouts
3. **Prompt Injection Detected:** Check input guardrails logs
4. **Tool Permission Denied:** Check user permissions on claim
5. **LLM Malformed Response:** Check LLM response parsing, tool call extraction

**Debugging Steps:**
1. Check Agent Service logs for tool selection
2. Check Ollama logs for model inference
3. Check Claims/Customer Service logs for tool execution
4. Check Redis for conversation memory
5. Verify SSE streaming response
6. Check tool execution results in logs

---

### Agent Tool Calling (get_claim_status)
**Postman Request:** `POST {{baseUrl}}/agent/stream` with "What's my claim status?"

**Tool Execution Flow:**
```
Agent Service → LLM → Tool Selection (get_claim_status) → Claims Service (Internal API) → PostgreSQL → Tool Result → LLM → Response
```

**Expected Services:**
- Agent Service (tool orchestration)
- Claims Service (internal API)
- PostgreSQL (claim data)
- Redis (cache invalidation)

**Kubernetes Commands:**
```bash
# Check agent service logs for tool execution
kubectl logs -n claimassist-core -l app=agent-service | grep "get_claim_status"

# Check claims service logs for internal API call
kubectl logs -n claimassist-core -l app=claims-service | grep "internal"

# Check Redis cache for claim status
kubectl exec -it redis-* -n claimassist-core -- redis-cli KEYS "claim_status:*"
```

**Common Failure Modes:**
1. **Tool Not Selected:** Check LLM tool description, prompt
2. **Tool Execution Failed:** Check Claims Service internal API, database
3. **Permission Denied:** Check user's VIEW permission on claim
4. **Cache Invalidation Failed:** Check Redis cache eviction

**Debugging Steps:**
1. Check Agent Service logs for tool selection
2. Check Claims Service logs for internal API call
3. Check database for claim status
4. Check Redis cache for claim status
5. Verify tool result in LLM response

---

### Agent Tool Calling (propose_claim_update)
**Postman Request:** `POST {{baseUrl}}/agent/stream` with "Move to UNDER_REVIEW"

**Tool Execution Flow:**
```
Agent Service → LLM → Tool Selection (propose_claim_update) → Outbox Table → Kafka (claim-update-request-event) → Claims Service → State Machine → Kafka (claim-update-response-event) → Agent Service → Response
```

**Expected Services:**
- Agent Service (tool orchestration, outbox publisher)
- Kafka (event streaming)
- Claims Service (saga participant, state machine)
- PostgreSQL (outbox, claim update)

**Kubernetes Commands:**
```bash
# Check agent service logs for tool execution
kubectl logs -n claimassist-core -l app=agent-service | grep "propose_claim_update"

# Check Kafka topic for request event
kubectl exec -it kafka-* -n claimassist-core -- kafka-console-consumer --bootstrap-server localhost:9092 --topic claim-update-request-event --from-beginning

# Check claims service logs for saga processing
kubectl logs -n claimassist-core -l app=claims-service | grep -i saga

# Check outbox table for pending events
kubectl exec -it postgres-* -n claimassist-core -- psql -U claimassist -d claimassist_agent -c "SELECT * FROM outbox_events WHERE status = 'PENDING';"
```

**Common Failure Modes:**
1. **Outbox Publisher Stuck:** Check outbox table, Kafka connectivity
2. **Kafka Topic Not Found:** Check Kafka topic creation
3. **Claims Service Not Consuming:** Check Kafka consumer lag, consumer group
4. **Saga Validation Failed:** Check state machine, permission check
5. **Saga Response Lost:** Check response topic, agent service consumer

**Debugging Steps:**
1. Check Agent Service logs for tool execution
2. Check outbox table for pending events
3. Check Kafka topic for request event
4. Check Claims Service logs for saga processing
5. Check Kafka topic for response event
6. Check Agent Service logs for response handling
7. Verify claim status update in database

---

## 05 - Internal APIs

### Internal Policy Coverage
**Postman Request:** `GET {{customerServiceUrl}}/internal/v1/policies/{{policyId}}/coverage`

**Request Flow:**
```
Postman → Customer Service → Permission Check → Policy Query → PostgreSQL → Response
```

**Expected Services:**
- Customer Service (internal API)
- PostgreSQL (policy data)

**Kubernetes Commands:**
```bash
# Check customer service logs for internal API call
kubectl logs -n claimassistant-core -l app=customer-service | grep "internal"

# Check database for policy coverage
kubectl exec -it postgres-* -n claimassist-core -- psql -U claimassist -d claimassist_customer -c "SELECT * FROM policies WHERE id = <policyId>;"
```

**Common Failure Modes:**
1. **Service Token Invalid:** Check service client credentials
2. **X-User-Id Missing:** Check request headers for service-to-service calls
3. **Policy Not Found:** Check policyId, database
4. **Permission Denied:** Check user ownership of policy

**Debugging Steps:**
1. Check Customer Service logs for internal API call
2. Check authorization headers (service token vs user token)
3. Check X-User-Id header for service-to-service calls
4. Check database for policy record
5. Verify policy coverage data in response

---

## 06 - Gateway Routing

### Route to Customer Service
**Postman Request:** `GET {{baseUrl}}/customer/policies/all`

**Routing Flow:**
```
Postman → Gateway → Path Matcher (/customer/**) → Route to customer-service → Customer Service → Response
```

**Expected Services:**
- API Gateway (routing, JWT validation)
- Customer Service (business logic)

**Kubernetes Commands:**
```bash
# Check gateway logs for routing
kubectl logs -n claimassist-core -l app=api-gateway | grep -i route

# Check gateway route configuration
kubectl exec -it api-gateway-* -n claimassist-core -- curl http://localhost:8080/actuator/gateway/routes
```

**Common Failure Modes:**
1. **Route Not Found:** Check gateway route configuration
2. **Service Unreachable:** Check customer service pod status
3. **Circuit Breaker Open:** Check downstream service health
4. **JWT Validation Failed:** Check JWT token, issuer URI

**Debugging Steps:**
1. Check Gateway logs for routing decisions
2. Check route configuration in Gateway
3. Check Customer Service pod status
4. Check JWT token validation in Gateway
5. Verify service discovery (if using Eureka)

---

## 07 - End-to-End Business Flows

### Complete Customer Journey
**Postman Flow:** Signup → Create Policy → Submit Claim → AI Inquiry → Verification

**End-to-End Flow:**
```
1. Signup: Gateway → Customer Service → Keycloak → PostgreSQL
2. Create Policy: Gateway → Customer Service → PostgreSQL → Redis
3. Submit Claim: Gateway → Claims Service → Customer Service (Feign) → PostgreSQL
4. AI Inquiry: Gateway → Agent Service → Ollama → Claims/Customer Services → Redis
5. Verification: Gateway → Claims Service → PostgreSQL
```

**Kubernetes Commands:**
```bash
# Monitor all services during flow
kubectl logs -n claimassist-core -l app=customer-service -f &
kubectl logs -n claimassist-core -l app=claims-service -f &
kubectl logs -n claimassist-core -l app=agent-service -f &

# Check database state after flow
kubectl exec -it postgres-* -n claimassist-core -- psql -U claimassist -d claimassist_customer -c "SELECT * FROM customers WHERE username = '<email>';"
kubectl exec -it postgres-* -n claimassist-core -- psql -U claimassist -d claimassist_customer -c "SELECT * FROM policies WHERE customer_id = <customerId>;"
kubectl exec -it postgres-* -n claimassist-core -- psql -U claimassist -d claimassist_claims -c "SELECT * FROM claims WHERE policy_id = <policyId>;"

# Check Redis cache state
kubectl exec -it redis-* -n claimassist-core -- redis-cli KEYS "*"
```

**Common Failure Modes:**
1. **Authentication Chain Break:** Check Keycloak, JWT tokens
2. **Service Communication Failure:** Check Feign calls, service discovery
3. **Database Transaction Failure:** Check database connectivity, constraints
4. **AI Tool Execution Failure:** Check Ollama, tool permissions
5. **Cache Inconsistency:** Check Redis cache, invalidation

**Debugging Steps:**
1. Execute flow step-by-step, verifying each step
2. Check logs for each service at each step
3. Verify database state after each step
4. Check cache state after each step
5. Verify Kafka events (if applicable)
6. Check final state matches expected outcome

---

### AI-Driven Claim Update Flow
**Postman Flow:** Agent Proposal → Kafka Saga → Claims Processing → Agent Resolution

**Saga Flow:**
```
1. Agent Proposal: Agent Service → LLM → propose_claim_update tool → Outbox
2. Kafka Publication: Outbox Publisher → Kafka (claim-update-request-event)
3. Claims Processing: Claims Service Consumer → Validation → State Machine → Outbox
4. Kafka Response: Outbox Publisher → Kafka (claim-update-response-event)
5. Agent Resolution: Agent Service Consumer → AgentEvent Update → Response
```

**Kubernetes Commands:**
```bash
# Monitor Kafka topics during saga
kubectl exec -it kafka-* -n claimassist-core -- kafka-console-consumer --bootstrap-server localhost:9092 --topic claim-update-request-event --from-beginning &
kubectl exec -it kafka-* -n claimassist-core -- kafka-console-consumer --bootstrap-server localhost:9092 --topic claim-update-response-event --from-beginning &

# Check outbox tables
kubectl exec -it postgres-* -n claimassist-core -- psql -U claimassist -d claimassist_agent -c "SELECT * FROM outbox_events WHERE aggregate_id LIKE '%saga%';"
kubectl exec -it postgres-* -n claimassist-core -- psql -U claimassist -d claimassist_claims -c "SELECT * FROM outbox_events WHERE aggregate_id LIKE '%saga%';"

# Check processed events (idempotency)
kubectl exec -it postgres-* -n claimassist-core -- psql -U claimassist -d claimassist_claims -c "SELECT * FROM processed_events;"

# Check claim status after saga
kubectl exec -it postgres-* -n claimassist-core -- psql -U claimassist -d claimassist_claims -c "SELECT * FROM claims WHERE id = <claimId>;"
```

**Common Failure Modes:**
1. **Outbox Publisher Stuck:** Check outbox table, Kafka connectivity
2. **Kafka Consumer Lag:** Check consumer group lag, consumer health
3. **Saga Validation Failed:** Check state machine, permission check
4. **Duplicate Saga Processing:** Check processed_events table
5. **Saga Timeout:** Check saga timeout configuration, stuck sagas

**Debugging Steps:**
1. Check Agent Service logs for tool execution
2. Check outbox table for pending events
3. Monitor Kafka topics for events
4. Check Claims Service logs for saga processing
5. Check processed_events for idempotency
6. Verify claim status update in database
7. Check Agent Service logs for response handling
8. Verify AgentEvent status update

---

## 08 - Caching & Performance

### Redis Cache Behavior
**Postman Flow:** First Request (Cache Miss) → Second Request (Cache Hit)

**Cache Flow:**
```
1. First Request: Service → Redis Check → Cache Miss → Database → Cache Write → Response
2. Second Request: Service → Redis Check → Cache Hit → Response
```

**Kubernetes Commands:**
```bash
# Monitor Redis commands in real-time
kubectl exec -it redis-* -n claimassist-core -- redis-cli MONITOR

# Check cache keys
kubectl exec -it redis-* -n claimassist-core -- redis-cli KEYS "*"

# Check cache TTL
kubectl exec -it redis-* -n claimassist-core -- redis-cli TTL <key>

# Check cache memory usage
kubectl exec -it redis-* -n claimassist-core -- redis-cli INFO memory
```

**Common Failure Modes:**
1. **Cache Miss Every Request:** Check cache write logic, Redis connectivity
2. **Cache Stale Data:** Check cache TTL, invalidation logic
3. **Cache Eviction Too Aggressive:** Check Redis memory policy, TTL
4. **Cache Deserialization Error:** Check cache serialization format

**Debugging Steps:**
1. Monitor Redis commands during requests
2. Compare response times between requests
3. Check cache keys after first request
4. Check cache TTL configuration
5. Verify cache invalidation on writes

---

## Common Debugging Strategies

### 1. Correlation ID Tracing

**Purpose:** Trace a request across all services

**Commands:**
```bash
# Extract correlation ID from response headers
# Look for "Correlation-ID" or "X-Correlation-ID" header

# Search logs for correlation ID
kubectl logs -n claimassist-core -l app=customer-service | grep "<correlation-id>"
kubectl logs -n claimassist-core -l app=claims-service | grep "<correlation-id>"
kubectl logs -n claimassist-core -l app=agent-service | grep "<correlation-id>"
```

**Debugging Steps:**
1. Extract correlation ID from Postman response headers
2. Search all service logs for correlation ID
3. Trace request flow across services
4. Identify where request failed or slowed down

---

### 2. Distributed Tracing with Zipkin

**Purpose:** Visualize request flow across services

**Commands:**
```bash
# Access Zipkin UI
# Open browser: http://100.114.133.69:30941

# Search traces by correlation ID or service name
# View span timeline and service dependencies
```

**Debugging Steps:**
1. Open Zipkin UI
2. Search for trace by correlation ID
3. View span timeline to identify bottlenecks
4. Check service dependencies and call graph

---

### 3. Metrics Analysis with Prometheus

**Purpose:** Monitor system performance and health

**Commands:**
```bash
# Access Prometheus UI
# Open browser: http://100.114.133.69:30900

# Query metrics
# - http_server_requests_seconds: HTTP request latency
# - jvm_memory_used_bytes: JVM memory usage
# - redis_command_duration_seconds: Redis operation latency
# - kafka_consumer_lag: Kafka consumer lag

# Example queries:
# rate(http_server_requests_seconds_sum[5m])
# jvm_memory_used_bytes{area="heap"}
# redis_command_duration_seconds{command="get"}
```

**Debugging Steps:**
1. Open Prometheus UI
2. Query relevant metrics for the service
3. Identify performance anomalies
4. Correlate with request failures

---

### 4. Log Aggregation with Loki

**Purpose:** Centralized log search and analysis

**Commands:**
```bash
# Access Grafana UI (includes Loki)
# Open browser: http://100.114.133.69:30300

# Query logs by service, correlation ID, or error
# Example Loki queries:
# {app="customer-service"}
# {correlation_id="<correlation-id>"}
# {level="error"}
```

**Debugging Steps:**
1. Open Grafana UI
2. Navigate to Explore → Loki
3. Query logs by service or correlation ID
4. Analyze log patterns and errors

---

### 5. Database Query Analysis

**Purpose:** Identify slow queries and database issues

**Commands:**
```bash
# Connect to PostgreSQL
kubectl exec -it postgres-* -n claimassist-core -- psql -U claimassist -d claimassist_customer

# Enable query logging (if not already enabled)
ALTER SYSTEM SET log_statement = 'all';
SELECT pg_reload_conf();

# Check slow queries
SELECT query, mean_exec_time, calls FROM pg_stat_statements ORDER BY mean_exec_time DESC LIMIT 10;

# Check table sizes
SELECT schemaname, tablename, pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size
FROM pg_tables WHERE schemaname = 'public' ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;
```

**Debugging Steps:**
1. Connect to PostgreSQL
2. Check query performance statistics
3. Identify slow queries
4. Analyze table sizes and indexes

---

### 6. Kafka Consumer Lag Monitoring

**Purpose:** Identify Kafka processing delays

**Commands:**
```bash
# Check consumer group lag
kubectl exec -it kafka-* -n claimassist-core -- kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group claims-group
kubectl exec -it kafka-* -n claimassist-core -- kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group agent-group

# Check topic offsets
kubectl exec -it kafka-* -n claimassist-core -- kafka-run-class kafka.tools.GetOffsetShell --broker-list localhost:9092 --topic claim-update-request-event
```

**Debugging Steps:**
1. Check consumer group lag
2. Identify lagging consumers
3. Check consumer health and throughput
4. Verify topic partition distribution

---

## Failure Mode Quick Reference

### Authentication Failures

| Symptom | Check | Commands |
|---------|-------|----------|
| 401 Unauthorized | JWT token validation | Check Gateway logs, JWT claims |
| 403 Forbidden | Permission check | Check service logs, user roles |
| Keycloak connection failed | Keycloak pod/network | `kubectl get pods -l app=keycloak` |
| Invalid redirect | OAuth2 configuration | Check callback URL, state parameter |

### Database Failures

| Symptom | Check | Commands |
|---------|-------|----------|
| Connection refused | PostgreSQL pod/network | `kubectl get pods -l app=postgres` |
| Constraint violation | Data validation | Check request payload, database constraints |
| Slow queries | Query performance | Check pg_stat_statements, indexes |
| Transaction deadlock | Concurrent updates | Check claim version, optimistic locking |

### Kafka Failures

| Symptom | Check | Commands |
|---------|-------|----------|
| Topic not found | Topic creation | Check Kafka topic configuration |
| Consumer lag | Consumer throughput | Check consumer group lag |
| Outbox stuck | Publisher health | Check outbox table, Kafka connectivity |
| Saga timeout | Saga processing | Check saga table, consumer health |

### AI/LLM Failures

| Symptom | Check | Commands |
|---------|-------|----------|
| Ollama connection failed | Ollama service | Check Ollama pod, model availability |
| Tool execution timeout | Downstream service | Check service health, timeout config |
| Prompt injection detected | Input guardrails | Check guardrails logs, input validation |
| Malformed LLM response | Response parsing | Check LLM logs, tool call extraction |

### Cache Failures

| Symptom | Check | Commands |
|---------|-------|----------|
| Cache miss every time | Cache write logic | Check service logs, Redis connectivity |
| Stale cache data | Cache invalidation | Check cache TTL, invalidation logic |
| Redis connection failed | Redis pod/network | Check Redis pod, connection settings |
| High memory usage | Cache eviction | Check Redis memory policy, key count |

---

## Recommended Execution Order for 7-Day Sprint

### Day 1: Environment & Health
1. Import Postman collection and environment
2. Configure DEV environment variables
3. Run all health check requests
4. Verify all services are healthy
5. Explore Gateway routing

### Day 2: Authentication & Authorization
1. Run complete authentication flow (signup → authorize → callback → refresh → logout)
2. Test public vs protected routes
3. Test authorization failures
4. Explore JWT structure and claims
5. Verify Keycloak integration

### Day 3: Customer & Claims APIs
1. Run customer journey flow (signup → create policy → submit claim)
2. Test all customer API endpoints
3. Test all claims API endpoints
4. Explore database state changes
5. Verify Redis caching behavior

### Day 4: AI/LLM Integration
1. Test basic agent chat
2. Test each tool individually
3. Test multi-step tool calling
4. Test AI error scenarios
5. Explore Ollama integration

### Day 5: Kafka Event-Driven Architecture
1. Test AI-driven claim update flow
2. Monitor Kafka topics during saga
3. Check outbox pattern implementation
4. Test saga error scenarios
5. Verify idempotency

### Day 6: Caching & Performance
1. Test Redis cache behavior
2. Monitor cache hit/miss ratios
3. Test cache invalidation
4. Analyze performance metrics
5. Explore observability stack

### Day 7: End-to-End & Debugging
1. Run complete end-to-end flows
2. Practice correlation ID tracing
3. Use Zipkin for distributed tracing
4. Analyze metrics in Prometheus
5. Aggregate logs in Loki
6. Debug complex failure scenarios

---

## Conclusion

This debugging guide provides a comprehensive mapping between Postman requests and the complete ClaimAssist system architecture. Use the kubectl commands, log locations, and debugging strategies to trace requests through the entire stack: Gateway → Authentication → Microservices → Database/Redis/Kafka/AI → Kubernetes → Logs.

Master these debugging techniques during the 7-day sprint to achieve interview-level capability in explaining and debugging the complete ClaimAssist system.
