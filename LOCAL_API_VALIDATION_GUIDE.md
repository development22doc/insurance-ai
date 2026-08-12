# Local API Validation Guide

## Overview

This guide documents the key API flows for local development and validation. Use these examples to verify the system is working correctly.

## Authentication Flow

### 1. Customer Signup

**Endpoint:** `POST /customer/auth/signup`

**Gateway URL:** `http://localhost:8080/customer/auth/signup`

**Direct Service URL:** `http://localhost:8081/auth/signup`

**Request:**
```json
{
  "username": "test@example.com",
  "fullName": "Test User",
  "password": "TestPassword123"
}
```

**Success Response (201):**
```json
{
  "customerId": 1,
  "username": "test@example.com",
  "fullName": "Test User",
  "createdAt": "2024-01-01T00:00:00Z"
}
```

**Validation Points:**
- Database record created in `customers` table
- Keycloak user provisioned in `claimassist` realm
- Response includes customer ID
- Duplicate username returns 400 Bad Request

**Curl Example:**
```bash
curl -X POST http://localhost:8080/customer/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"username":"test@example.com","fullName":"Test User","password":"TestPassword123"}'
```

### 2. Authorization (Keycloak Entry Point)

**Endpoint:** `GET /customer/auth/authorize`

**Gateway URL:** `http://localhost:8080/customer/auth/authorize`

**Direct Service URL:** `http://localhost:8081/auth/authorize`

**Request:**
```bash
curl http://localhost:8080/customer/auth/authorize
```

**Expected Behavior:**
- Redirects to Keycloak login page (HTTP 302)
- Keycloak login page displayed (HTTP 200)

**Validation Points:**
- Redirect to Keycloak occurs
- Keycloak realm is `claimassist`
- Keycloak client is configured correctly

### 3. Token Refresh

**Endpoint:** `POST /customer/auth/refresh`

**Gateway URL:** `http://localhost:8080/customer/auth/refresh`

**Direct Service URL:** `http://localhost:8081/auth/refresh`

**Request:**
```json
{
  "refreshToken": "<valid-refresh-token>"
}
```

**Success Response (200):**
```json
{
  "accessToken": "<new-access-token>",
  "refreshToken": "<new-refresh-token>",
  "expiresIn": 3600
}
```

**Validation Points:**
- New access token generated
- New refresh token generated
- Old tokens invalidated

### 4. Logout

**Endpoint:** `POST /customer/auth/logout`

**Gateway URL:** `http://localhost:8080/customer/auth/logout`

**Direct Service URL:** `http://localhost:8081/auth/logout`

**Request:**
```json
{
  "refreshToken": "<valid-refresh-token>"
}
```

**Success Response (200):**
```json
{
  "message": "Logged out successfully"
}
```

**Validation Points:**
- Refresh token invalidated
- Access token cannot be used after logout

## Customer API Flows

### 1. Get Customer Profile

**Endpoint:** `GET /customer/customers/me`

**Gateway URL:** `http://localhost:8080/customer/customers/me`

**Direct Service URL:** `http://localhost:8081/customers/me`

**Headers:**
```
Authorization: Bearer <valid-jwt-token>
```

**Success Response (200):**
```json
{
  "customerId": 1,
  "username": "test@example.com",
  "fullName": "Test User",
  "createdAt": "2024-01-01T00:00:00Z"
}
```

**Validation Points:**
- Requires valid JWT token
- Returns customer profile for authenticated user
- Unauthorized request returns 401

**Curl Example:**
```bash
curl http://localhost:8080/customer/customers/me \
  -H "Authorization: Bearer <valid-jwt-token>"
```

### 2. Get Customer Policies

**Endpoint:** `GET /customer/policies`

**Gateway URL:** `http://localhost:8080/customer/policies`

**Direct Service URL:** `http://localhost:8081/policies`

**Headers:**
```
Authorization: Bearer <valid-jwt-token>
```

**Success Response (200):**
```json
[
  {
    "policyId": 1,
    "policyNumber": "POL-001",
    "coverageType": "COMPREHENSIVE",
    "status": "ACTIVE",
    "startDate": "2024-01-01",
    "endDate": "2024-12-31"
  }
]
```

**Validation Points:**
- Requires valid JWT token
- Returns policies for authenticated customer
- Caching working (policyCoverage cache)
- Unauthorized request returns 401

### 3. Get Policy Coverage

**Endpoint:** `GET /customer/policies/{policyId}/coverage`

**Gateway URL:** `http://localhost:8080/customer/policies/{policyId}/coverage`

**Direct Service URL:** `http://localhost:8081/policies/{policyId}/coverage`

**Headers:**
```
Authorization: Bearer <valid-jwt-token>
```

**Success Response (200):**
```json
{
  "policyId": 1,
  "coverageType": "COMPREHENSIVE",
  "coverageDetails": "Full coverage for collision, comprehensive, and liability",
  "deductible": 500,
  "limits": {
    "bodilyInjury": 100000,
    "propertyDamage": 50000
  }
}
```

**Validation Points:**
- Requires valid JWT token
- Customer can only access their own policies
- Cache hit/miss behavior working
- Unauthorized request returns 401

## Claims API Flows

### 1. Get My Claims

**Endpoint:** `GET /claims/my-claims`

**Gateway URL:** `http://localhost:8080/claims/my-claims`

**Direct Service URL:** `http://localhost:8082/my-claims`

**Headers:**
```
Authorization: Bearer <valid-jwt-token>
```

**Success Response (200):**
```json
[
  {
    "claimId": 1,
    "policyId": 1,
    "status": "SUBMITTED",
    "incidentType": "COLLISION",
    "incidentDate": "2024-01-15",
    "estimatedAmount": 5000,
    "createdAt": "2024-01-15T10:00:00Z"
  }
]
```

**Validation Points:**
- Requires valid JWT token
- Returns claims for authenticated customer
- Query service working (CQRS read model)
- Unauthorized request returns 401

**Curl Example:**
```bash
curl http://localhost:8080/claims/my-claims \
  -H "Authorization: Bearer <valid-jwt-token>"
```

### 2. Get Claim Details

**Endpoint:** `GET /claims/{claimId}`

**Gateway URL:** `http://localhost:8080/claims/{claimId}`

**Direct Service URL:** `http://localhost:8082/claims/{claimId}`

**Headers:**
```
Authorization: Bearer <valid-jwt-token>
```

**Success Response (200):**
```json
{
  "claimId": 1,
  "policyId": 1,
  "customerId": 1,
  "status": "SUBMITTED",
  "incidentType": "COLLISION",
  "incidentDate": "2024-01-15",
  "description": "Rear-end collision at intersection",
  "estimatedAmount": 5000,
  "createdAt": "2024-01-15T10:00:00Z",
  "updatedAt": "2024-01-15T10:00:00Z"
}
```

**Validation Points:**
- Requires valid JWT token
- Customer can only access their own claims
- Permission checks working
- Unauthorized request returns 401
- Forbidden request returns 403

### 3. Get Claim Status with History

**Endpoint:** `GET /claims/{claimId}/status`

**Gateway URL:** `http://localhost:8080/claims/{claimId}/status`

**Direct Service URL:** `http://localhost:8082/claims/{claimId}/status`

**Headers:**
```
Authorization: Bearer <valid-jwt-token>
```

**Success Response (200):**
```json
{
  "claimId": 1,
  "currentStatus": "IN_REVIEW",
  "statusHistory": [
    {
      "status": "SUBMITTED",
      "changedAt": "2024-01-15T10:00:00Z",
      "changedBy": "customer"
    },
    {
      "status": "IN_REVIEW",
      "changedAt": "2024-01-15T14:00:00Z",
      "changedBy": "adjuster"
    }
  ]
}
```

**Validation Points:**
- Requires valid JWT token
- Status history maintained
- State transitions working
- Saga orchestration affecting status

## Agent API Flows

### 1. Agent Chat

**Endpoint:** `POST /agent/chat`

**Gateway URL:** `http://localhost:8080/agent/chat`

**Direct Service URL:** `http://localhost:8083/chat`

**Headers:**
```
Authorization: Bearer <valid-jwt-token>
Content-Type: application/json
```

**Request:**
```json
{
  "message": "What is the status of my claim?",
  "claimId": 1
}
```

**Success Response (200):**
```json
{
  "response": "Your claim #1 is currently in review. An adjuster is evaluating the damages.",
  "agentEventId": 123,
  "timestamp": "2024-01-15T15:00:00Z"
}
```

**Validation Points:**
- Requires valid JWT token
- AI agent processes message
- Agent event created in database
- Caching working for agent responses
- Redis cache integration working

**Curl Example:**
```bash
curl -X POST http://localhost:8080/agent/chat \
  -H "Authorization: Bearer <valid-jwt-token>" \
  -H "Content-Type: application/json" \
  -d '{"message":"What is the status of my claim?","claimId":1}'
```

### 2. Get Agent Event History

**Endpoint:** `GET /agent/events`

**Gateway URL:** `http://localhost:8080/agent/events`

**Direct Service URL:** `http://localhost:8083/events`

**Headers:**
```
Authorization: Bearer <valid-jwt-token>
```

**Success Response (200):**
```json
[
  {
    "agentEventId": 123,
    "customerId": 1,
    "claimId": 1,
    "status": "CONFIRMED",
    "content": "Agent response about claim status",
    "createdAt": "2024-01-15T15:00:00Z"
  }
]
```

**Validation Points:**
- Requires valid JWT token
- Returns agent events for authenticated customer
- Saga responses handled correctly
- Event status tracking working

## Authentication & Authorization Validation

### 1. Unauthorized Request

**Test:** Request protected endpoint without token

**Example:**
```bash
curl http://localhost:8080/customer/customers/me
```

**Expected Response (401):**
```json
{
  "error": "Unauthorized",
  "message": "Authentication required"
}
```

**Validation Points:**
- Gateway rejects unauthorized requests
- Proper error message returned
- No sensitive data leaked

### 2. Invalid Token

**Test:** Request with invalid or expired JWT token

**Example:**
```bash
curl http://localhost:8080/customer/customers/me \
  -H "Authorization: Bearer invalid-token"
```

**Expected Response (401):**
```json
{
  "error": "Unauthorized",
  "message": "Invalid or expired token"
}
```

**Validation Points:**
- Gateway validates JWT signature
- Expired tokens rejected
- Invalid tokens rejected
- Keycloak integration working

### 3. Cross-Customer Access

**Test:** Customer A tries to access Customer B's data

**Example:**
```bash
curl http://localhost:8080/claims/999 \
  -H "Authorization: Bearer <customer-a-token>"
```

**Expected Response (403):**
```json
{
  "error": "Forbidden",
  "message": "Access denied"
}
```

**Validation Points:**
- Permission checks working
- Customer isolation enforced
- Data protection working

## Error Response Validation

### Standard Error Format

All errors follow this structure:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Detailed error message",
  "path": "/api/endpoint",
  "timestamp": "2024-01-15T10:00:00Z",
  "correlationId": "uuid-here"
}
```

### Common Error Scenarios

**400 Bad Request:**
- Invalid request body
- Missing required fields
- Validation failures

**401 Unauthorized:**
- Missing authentication
- Invalid token
- Expired token

**403 Forbidden:**
- Insufficient permissions
- Cross-customer access attempt

**404 Not Found:**
- Resource not found
- Invalid ID

**500 Internal Server Error:**
- Unexpected server error
- Database connection failure

**503 Service Unavailable:**
- Dependent service down
- Circuit breaker open

## Integration Flow Validation

### Complete Signup to Claims Flow

**1. Signup Customer:**
```bash
curl -X POST http://localhost:8080/customer/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"username":"flowtest@example.com","fullName":"Flow Test","password":"TestPassword123"}'
```

**2. Get Token (via Keycloak):**
Use Keycloak token endpoint to obtain JWT

**3. Get Policies:**
```bash
curl http://localhost:8080/customer/policies \
  -H "Authorization: Bearer <jwt-token>"
```

**4. Submit Claim (via Claims Service):**
```bash
curl -X POST http://localhost:8080/claims \
  -H "Authorization: Bearer <jwt-token>" \
  -H "Content-Type: application/json" \
  -d '{"policyId":1,"incidentType":"COLLISION","incidentDate":"2024-01-15","description":"Test claim"}'
```

**5. Check Claim Status:**
```bash
curl http://localhost:8080/claims/my-claims \
  -H "Authorization: Bearer <jwt-token>"
```

**6. Ask Agent About Claim:**
```bash
curl -X POST http://localhost:8080/agent/chat \
  -H "Authorization: Bearer <jwt-token>" \
  -H "Content-Type: application/json" \
  -d '{"message":"What is my claim status?","claimId":1}'
```

**Validation Points:**
- Entire flow works end-to-end
- Correlation IDs propagated through all services
- Services communicate via Gateway/Eureka
- Database operations successful
- Cache working appropriately
- Kafka events published/consumed
- Saga orchestration working

## Gateway Routing Validation

### Route Testing

**Customer Routes:**
- `/customer/**` → CUSTOMER-SERVICE
- `/policies/**` → CUSTOMER-SERVICE

**Claims Routes:**
- `/claims/**` → CLAIMS-SERVICE

**Agent Routes:**
- `/agent/**` → AGENT-SERVICE

**Test Unknown Route:**
```bash
curl http://localhost:8080/unknown/endpoint
```

**Expected Response (404):**
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "No handler found"
}
```

## Performance Validation

### Latency Measurement

**Measure Signup Latency:**
```bash
time curl -X POST http://localhost:8080/customer/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"username":"perftest@example.com","fullName":"Perf Test","password":"TestPassword123"}'
```

**Expected:** ~3.5 seconds (local baseline)

**Measure Health Check Latency:**
```bash
time curl http://localhost:8081/actuator/health
```

**Expected:** < 3 seconds

**Cache Hit Latency:**
```bash
time curl http://localhost:8081/policies/1/coverage \
  -H "Authorization: Bearer <jwt-token>"
```

**Expected:** < 100ms (after cache warmup)

## Observability Validation

### Trace Validation

1. Make a request with valid correlation ID:
```bash
curl http://localhost:8080/customer/customers/me \
  -H "Authorization: Bearer <jwt-token>" \
  -H "X-Correlation-Id: test-correlation-123"
```

2. Check response headers contain:
- `X-Correlation-Id`
- `X-Trace-Id`
- `X-Span-Id`
- `X-Request-Id`

3. Verify trace in Zipkin: http://localhost:9411

### Metrics Validation

1. Check Prometheus targets: http://localhost:9090/targets
2. Verify all services show "UP" status
3. Query metrics in Prometheus:
   - `http_server_requests_seconds_count`
   - `jvm_memory_used_bytes`
   - `cache_requests_total`

### Log Validation

1. Check service logs for structured JSON format
2. Verify correlation IDs present in logs
3. Check for performance logging events
4. Verify sensitive data is masked

## Validation Checklist

### Authentication
- [ ] Signup creates database record
- [ ] Signup provisions Keycloak user
- [ ] Duplicate signup rejected (400)
- [ ] Authorization redirects to Keycloak
- [ ] Token refresh works
- [ ] Logout invalidates tokens

### Authorization
- [ ] Unauthorized requests return 401
- [ ] Invalid tokens return 401
- [ ] Expired tokens return 401
- [ ] Cross-customer access returns 403
- [ ] Permission checks enforced

### Customer API
- [ ] Get profile works
- [ ] Get policies works
- [ ] Get coverage works
- [ ] Cache hit/miss working
- [ ] Rate limiting active

### Claims API
- [ ] Get my claims works
- [ ] Get claim details works
- [ ] Get status history works
- [ ] Query service working
- [ ] CQRS read model working

### Agent API
- [ ] Chat endpoint works
- [ ] Agent events created
- [ ] Saga responses handled
- [ ] Cache integration working

### Gateway
- [ ] Routes configured correctly
- [ ] Unknown routes return 404
- [ ] Public routes accessible
- [ ] Protected routes require auth
- [ ] Circuit breaker fallbacks work

### Integration
- [ ] End-to-end flow works
- [ ] Correlation IDs propagated
- [ ] Services discoverable via Eureka
- [ ] Database operations working
- [ ] Redis caching working
- [ ] Kafka events working
- [ ] Saga orchestration working

### Observability
- [ ] Metrics exposed
- [ ] Prometheus scraping
- [ ] Grafana dashboards working
- [ ] Zipkin traces working
- [ ] Structured logs working
- [ ] Performance logging working

## Next Steps

After validating all API flows:
1. Review performance metrics
2. Check for any error patterns
3. Verify resilience patterns (circuit breaker, retry)
4. Test failure scenarios (see LOCAL_FAILURE_SIMULATION_GUIDE.md)
5. Establish performance baselines
