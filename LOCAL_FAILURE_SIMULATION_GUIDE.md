# Local Failure Simulation Guide

## Overview

This guide documents how to simulate failure scenarios in the local development environment to verify resilience patterns, fallback behavior, and system robustness.

## Purpose

Failure simulation helps verify:
- Circuit breaker behavior
- Retry logic
- Fallback mechanisms
- Graceful degradation
- Error handling
- Compensation logic

## General Approach

### Simulation Commands

Most failure simulations involve:
1. Stopping or pausing a component
2. Making requests to dependent services
3. Observing behavior
4. Restarting the component
5. Verifying recovery

### Monitoring During Simulation

While simulating failures, monitor:
- Service logs for error messages
- Circuit breaker state in Prometheus
- Fallback behavior in application logs
- Error rates in Grafana
- Trace completion in Zipkin

## PostgreSQL Failure Simulation

### Scenario 1: PostgreSQL Container Stop

**Purpose:** Verify database unavailability handling

**Steps:**
```bash
# Stop PostgreSQL
docker stop claimassist-postgres

# Attempt database operation
curl http://localhost:8081/customer/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"username":"dbtest@example.com","fullName":"DB Test","password":"TestPassword123"}'

# Expected: 503 Service Unavailable or timeout
```

**Expected Behavior:**
- Health check shows database component down
- Database operations fail gracefully
- Circuit breaker may open
- Fallback returns appropriate error
- No data corruption

**Recovery:**
```bash
# Restart PostgreSQL
docker start claimassist-postgres

# Wait for health check
docker exec claimassist-postgres pg_isready

# Retry operation
curl http://localhost:8081/customer/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"username":"dbtest@example.com","fullName":"DB Test","password":"TestPassword123"}'

# Expected: Success (201)
```

### Scenario 2: Database Connection Pool Exhaustion

**Purpose:** Verify connection pool handling

**Steps:**
- Monitor connection pool metrics in Prometheus
- Check for connection timeout errors
- Verify pool size configuration

**Expected Behavior:**
- Connection requests wait if pool exhausted
- Appropriate timeout after wait period
- Pool metrics reflect usage
- No connection leaks

## Redis Failure Simulation

### Scenario 1: Redis Container Stop

**Purpose:** Verify cache failure handling

**Steps:**
```bash
# Stop Redis
docker stop claimassist-redis

# Make request that uses cache
curl http://localhost:8081/policies/1/coverage \
  -H "Authorization: Bearer <valid-jwt-token>"

# Expected: Request succeeds (cache MISS, database fallback)
```

**Expected Behavior:**
- Cache operations fail gracefully
- Application falls back to database
- No data loss or corruption
- Performance degrades but functionality remains
- Health indicator shows Redis down

**Recovery:**
```bash
# Restart Redis
docker start claimassist-redis

# Verify connectivity
docker exec claimassist-redis redis-cli ping

# Expected: PONG

# Make same request - should hit cache
curl http://localhost:8081/policies/1/coverage \
  -H "Authorization: Bearer <valid-jwt-token>"

# Expected: Faster response (cache HIT)
```

### Scenario 2: Gateway Rate Limiting (Redis-Dependent)

**Purpose:** Verify rate limiting without Redis

**Steps:**
```bash
# Stop Redis
docker stop claimassist-redis

# Make rapid requests to gateway
for i in {1..20}; do
  curl http://localhost:8080/customer/auth/authorize
done

# Expected: Rate limiting may not work (depends on implementation)
```

**Expected Behavior:**
- Gateway may allow all requests if Redis unavailable
- Or may fallback to local rate limiting
- Logs show rate limiting errors
- No complete service failure

## Kafka Failure Simulation

### Scenario 1: Kafka Container Stop

**Purpose:** Verify Kafka unavailability handling

**Steps:**
```bash
# Stop Kafka
docker stop claimassist-kafka

# Make request that publishes to Kafka
curl -X POST http://localhost:8080/claims \
  -H "Authorization: Bearer <valid-jwt-token>" \
  -H "Content-Type: application/json" \
  -d '{"policyId":1,"incidentType":"COLLISION","incidentDate":"2024-01-15","description":"Kafka test"}'

# Expected: Request may succeed (outbox pattern stores event for later publication)
```

**Expected Behavior:**
- Outbox pattern stores events locally
- No immediate failure due to Kafka
- Events retained until Kafka recovers
- Outbox publisher retries when Kafka available
- No data loss

**Recovery:**
```bash
# Restart Kafka
docker start claimassist-kafka

# Wait for Kafka to be ready
docker exec claimassist-kafka kafka-broker-api-versions --bootstrap-server localhost:9092

# Outbox publisher should publish queued events
# Check logs for successful publication
```

### Scenario 2: Consumer Service Stop

**Purpose:** Verify consumer restart and offset handling

**Steps:**
```bash
# Stop a service that consumes Kafka (e.g., Agent Service)
# (Use Ctrl+C in the service terminal)

# Publish events to Kafka via another service
# (Events will accumulate in Kafka topics)

# Restart the consumer service
cd agent-service
mvn spring-boot:run

# Expected: Consumer processes missed messages from committed offset
```

**Expected Behavior:**
- Consumer re-subscribes to topics
- Resumes from last committed offset
- Processes accumulated messages
- No message loss
- Idempotency handles duplicates

## Keycloak Failure Simulation

### Scenario 1: Keycloak Container Stop

**Purpose:** Verify authentication fallback

**Steps:**
```bash
# Stop Keycloak
docker stop claimassist-keycloak

# Attempt signup
curl -X POST http://localhost:8080/customer/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"username":"keycloaktest@example.com","fullName":"Keycloak Test","password":"TestPassword123"}'

# Expected: 503 Service Unavailable or timeout
```

**Expected Behavior:**
- Signup fails (cannot provision Keycloak user)
- Database transaction should rollback
- No partial state
- Appropriate error message
- Circuit breaker may open

**Recovery:**
```bash
# Restart Keycloak
docker start claimassist-keycloak

# Wait for health check
curl http://localhost:8180/health

# Retry signup
curl -X POST http://localhost:8080/customer/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"username":"keycloaktest@example.com","fullName":"Keycloak Test","password":"TestPassword123"}'

# Expected: Success (201)
```

### Scenario 2: Keycloak Network Partition

**Purpose:** Verify timeout behavior

**Steps:**
- Simulate network partition using firewall rules
- Or change Keycloak endpoint to unreachable address
- Observe timeout behavior

**Expected Behavior:**
- Request times out after configured timeout
- No indefinite hanging
- Appropriate error message
- Circuit breaker may open after failures

## Service Failure Simulation

### Scenario 1: Customer Service Stop

**Purpose:** Verify gateway fallback and circuit breaker

**Steps:**
```bash
# Stop Customer Service
# (Use Ctrl+C in the Customer Service terminal)

# Make request through gateway
curl http://localhost:8080/customer/customers/me \
  -H "Authorization: Bearer <valid-jwt-token>"

# Expected: 503 Service Unavailable (gateway cannot reach customer service)
```

**Expected Behavior:**
- Gateway returns error (no fallback for direct service calls)
- Circuit breaker state changes
- Eureka shows service as DOWN
- Health check shows dependency down
- No cascading failures

**Recovery:**
```bash
# Restart Customer Service
cd customer-service
mvn spring-boot:run

# Wait for Eureka registration
curl http://localhost:8761

# Retry request
curl http://localhost:8080/customer/customers/me \
  -H "Authorization: Bearer <valid-jwt-token>"

# Expected: Success (200)
```

### Scenario 2: Claims Service Stop

**Purpose:** Verify Claims Service dependency handling

**Steps:**
```bash
# Stop Claims Service
# (Use Ctrl+C in the Claims Service terminal)

# Agent Service may try to call Claims Service
# Make agent request that depends on claims
curl -X POST http://localhost:8080/agent/chat \
  -H "Authorization: Bearer <valid-jwt-token>" \
  -H "Content-Type: application/json" \
  -d '{"message":"What is my claim status?","claimId":1}'

# Expected: Fallback response (graceful degradation)
```

**Expected Behavior:**
- Agent Service uses fallback for Claims Service
- Returns placeholder or cached data
- Does not fail completely
- Logs show fallback activation
- Circuit breaker opens after failures

**Recovery:**
```bash
# Restart Claims Service
cd claims-service
mvn spring-boot:run

# Retry agent request
curl -X POST http://localhost:8080/agent/chat \
  -H "Authorization: Bearer <valid-jwt-token>" \
  -H "Content-Type: application/json" \
  -d '{"message":"What is my claim status?","claimId":1}'

# Expected: Normal operation
```

### Scenario 3: Agent Service Stop

**Purpose:** Verify Agent Service unavailability

**Steps:**
```bash
# Stop Agent Service
# (Use Ctrl+C in the Agent Service terminal)

# Try to use agent endpoint
curl -X POST http://localhost:8080/agent/chat \
  -H "Authorization: Bearer <valid-jwt-token>" \
  -H "Content-Type: application/json" \
  -d '{"message":"Test message"}'

# Expected: 503 Service Unavailable
```

**Expected Behavior:**
- Gateway returns error
- No fallback for agent endpoint
- Circuit breaker opens
- Other services unaffected

**Recovery:**
```bash
# Restart Agent Service
cd agent-service
mvn spring-boot:run

# Retry request
curl -X POST http://localhost:8080/agent/chat \
  -H "Authorization: Bearer <valid-jwt-token>" \
  -H "Content-Type: application/json" \
  -d '{"message":"Test message"}'

# Expected: Success (200)
```

## Gateway Failure Simulation

### Scenario 1: Gateway Stop

**Purpose:** Verify direct service access

**Steps:**
```bash
# Stop Gateway
# (Use Ctrl+C in the Gateway terminal)

# Try to access services directly
curl http://localhost:8081/customer/customers/me \
  -H "Authorization: Bearer <valid-jwt-token>"

# Expected: Works (direct service access bypasses gateway)
```

**Expected Behavior:**
- Direct service access still works
- Gateway routing unavailable
- Rate limiting not applied
- No authentication layer (direct access)

**Recovery:**
```bash
# Restart Gateway
cd api-gateway
mvn spring-boot:run

# Retry through gateway
curl http://localhost:8080/customer/customers/me \
  -H "Authorization: Bearer <valid-jwt-token>"

# Expected: Success (200)
```

## Circuit Breaker Simulation

### Scenario 1: Force Circuit Breaker Open

**Purpose:** Verify circuit breaker state transitions

**Steps:**
```bash
# Stop dependent service (e.g., Customer Service)
# (Use Ctrl+C in the Customer Service terminal)

# Make multiple rapid requests through gateway
for i in {1..15}; do
  curl http://localhost:8080/customer/customers/me \
    -H "Authorization: Bearer <valid-jwt-token>"
done

# Expected: Circuit breaker opens after failure threshold
```

**Expected Behavior:**
- First requests timeout/fail
- Circuit breaker opens after threshold (default: 50% failure rate)
- Subsequent requests fail immediately (no timeout)
- Circuit breaker enters HALF_OPEN state after timeout
- Successful request closes circuit breaker

**Monitor Circuit Breaker:**
```bash
# Check circuit breaker metrics in Prometheus
curl http://localhost:9090/api/v1/query?query=resilience4j_circuitbreaker_state

# Expected state values: CLOSED, OPEN, HALF_OPEN
```

**Recovery:**
```bash
# Restart dependent service
cd customer-service
mvn spring-boot:run

# Wait for circuit breaker HALF_OPEN timeout
# Make request - should succeed and close circuit breaker
curl http://localhost:8080/customer/customers/me \
  -H "Authorization: Bearer <valid-jwt-token>"

# Expected: Circuit breaker closes
```

## Retry Simulation

### Scenario 1: Intermittent Service Failure

**Purpose:** Verify retry logic

**Steps:**
```bash
# Simulate intermittent failure by starting/stopping service rapidly
# Or use a test endpoint that fails intermittently

# Make request to service with retry configured
curl http://localhost:8080/customer/policies/1/coverage \
  -H "Authorization: Bearer <valid-jwt-token>"

# Expected: Request retries on failure (max 3 attempts by default)
```

**Expected Behavior:**
- First attempt fails
- Retry after configured delay (1000ms)
- Subsequent attempts until success or max attempts
- Successful attempt returns response
- Retry metrics updated in Prometheus

**Monitor Retry Metrics:**
```bash
# Check retry metrics in Prometheus
curl http://localhost:9090/api/v1/query?query=resilience4j_retry_calls_total
```

## Saga Failure Simulation

### Scenario 1: Saga Step Failure

**Purpose:** Verify compensation logic

**Steps:**
```bash
# Stop service needed for saga step (e.g., Kafka or specific service)
# Submit claim that triggers saga
curl -X POST http://localhost:8080/claims \
  -H "Authorization: Bearer <valid-jwt-token>" \
  -H "Content-Type: application/json" \
  -d '{"policyId":1,"incidentType":"COLLISION","incidentDate":"2024-01-15","description":"Saga test"}'

# Expected: Saga fails, compensation triggered
```

**Expected Behavior:**
- Saga orchestration records failure
- Compensation steps executed (reverse successful steps)
- Saga status changes to COMPENSATED or FAILED
- No inconsistent business state
- Metrics show saga failure

**Monitor Saga State:**
```bash
# Check saga orchestration status in database
# Or check saga metrics in Prometheus
curl http://localhost:9090/api/v1/query?query=saga_status
```

**Recovery:**
```bash
# Restart failed service
# Saga recovery mechanism may retry failed sagas
# Check logs for recovery attempts
```

### Scenario 2: Saga Timeout

**Purpose:** Verify timeout handling

**Steps:**
```bash
# Simulate slow saga step (or stop service temporarily)
# Submit claim
curl -X POST http://localhost:8080/claims \
  -H "Authorization: Bearer <valid-jwt-token>" \
  -H "Content-Type: application/json" \
  -d '{"policyId":1,"incidentType":"COLLISION","incidentDate":"2024-01-15","description":"Timeout test"}'

# Wait for saga timeout (default: 180 seconds)
```

**Expected Behavior:**
- Saga marked as TIMED_OUT
- Compensation triggered
- Final result event published
- No hanging transactions
- Timeout metrics updated

## Bulkhead Simulation

### Scenario 1: Concurrent Request Overload

**Purpose:** Verify bulkhead limiting

**Steps:**
```bash
# Make many concurrent requests to bulkhead-protected endpoint
for i in {1..20}; do
  curl http://localhost:8080/customer/policies/1/coverage \
    -H "Authorization: Bearer <valid-jwt-token>" &
done
wait

# Expected: Some requests succeed, others rejected due to bulkhead limit
```

**Expected Behavior:**
- Requests up to bulkhead limit (default: 10) execute
- Additional requests rejected or queued
- Bulkhead metrics show concurrent requests
- No resource exhaustion

**Monitor Bulkhead Metrics:**
```bash
# Check bulkhead metrics in Prometheus
curl http://localhost:9090/api/v1/query?query=resilience4j_bulkhead_available_concurrent_calls
```

## Rate Limiter Simulation

### Scenario 1: Exceed Rate Limit

**Purpose:** Verify rate limiting

**Steps:**
```bash
# Make rapid requests to rate-limited endpoint
for i in {1}{..150}; do
  curl http://localhost:8080/customer/policies
done

# Expected: Requests after rate limit rejected (429 Too Many Requests)
```

**Expected Behavior:**
- Requests within limit succeed
- Requests after limit return 429
- Rate limiter metrics updated
- Headers show rate limit info

**Monitor Rate Limiter Metrics:**
```bash
# Check rate limiter metrics in Prometheus
curl http://localhost:9090/api/v1/query?query=resilience4j_ratelimiter_available_permissions
```

## Monitoring During Failures

### Health Checks

Monitor health endpoints during failure simulation:
```bash
# Watch health status
watch -n 1 curl http://localhost:8081/actuator/health
```

### Circuit Breaker State

Monitor circuit breaker in Prometheus:
```
resilience4j_circuitbreaker_state{service="customer-service"}
```

### Error Rates

Monitor error rates in Grafana:
```
rate(http_server_requests_seconds_count{status=~"5.."}[5m])
```

### Trace Failures

Check Zipkin for failed traces:
```
http://localhost:9411
```
Filter by error status or high duration.

## Recovery Verification

After each failure simulation, verify:

1. **Service Recovery:** Service restarts successfully
2. **Health Check:** Health endpoint returns UP
3. **Eureka Registration:** Service appears in Eureka
4. **Circuit Breaker:** Circuit breaker closes
5. **Data Consistency:** No data corruption
6. **Event Processing:** Missed events processed
7. **Performance:** Performance returns to baseline

## Best Practices

### During Simulation
- Use test data, not production data
- Monitor logs continuously
- Document observed behavior
- Test recovery mechanisms
- Verify no data loss

### After Simulation
- Clean up test data
- Reset circuit breakers if needed
- Clear caches if appropriate
- Restart services to clean state
- Document findings

### Safety Precautions
- Never simulate failures in production
- Backup databases before destructive tests
- Use separate test environment
- Inform team before simulations
- Have rollback plan ready

## Common Issues

### Service Won't Restart
- Check port conflicts
- Clear application state
- Verify configuration
- Check resource availability

### Circuit Breaker Won't Close
- Verify underlying service is healthy
- Check timeout configuration
- Manually reset if needed
- Verify health checks passing

### Data Inconsistency After Failure
- Check compensation logic
- Verify transaction boundaries
- Review saga orchestration
- Check idempotency handling

### Events Not Processed After Recovery
- Verify consumer restart
- Check offset management
- Verify topic configuration
- Check consumer group settings

## Next Steps

After completing failure simulations:
1. Document observed behavior
2. Update resilience configurations if needed
3. Improve error messages
4. Enhance monitoring
5. Refine fallback mechanisms
6. Update operational procedures
