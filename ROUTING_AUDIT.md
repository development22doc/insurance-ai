# Routing Audit & Fix - Complete Analysis

## STEP 1: DISCOVERY - Complete Service Mapping

### Services Discovered in Repository

| Service | Module | Eureka Name | Port | Status |
|---------|--------|-------------|------|--------|
| discovery-service | discovery-service | discovery-service | 8761 | Eureka Server |
| config-service | config-service | config-service | 8888 | Config Server |
| api-gateway | api-gateway | api-gateway | 8080 | Spring Cloud Gateway |
| customer-service | customer-service | CUSTOMER-SERVICE | 8081 | Active Service |
| claims-service | claims-service | CLAIMS-SERVICE | 8082 | Active Service |
| agent-service | agent-service | AGENT-SERVICE | 8083 | Active Service |

### Services Declared in Gateway (config-repo/api-gateway.yml) but NOT in Repository

| Service | Status | Impact |
|---------|--------|--------|
| POLICY-SERVICE | **MISSING** | Routes to /policies/** → lb://POLICY-SERVICE will FAIL |
| NOTIFICATION-SERVICE | **MISSING** | Routes to /notifications/** → lb://NOTIFICATION-SERVICE will FAIL |

---

## CONTROLLER ENDPOINTS BY SERVICE

### CUSTOMER-SERVICE (Port 8081)

**PolicyController** (@RequestMapping("/policies"))
- `GET /policies` - List customer policies (requires auth)

**AuthController** (@RequestMapping("/auth"))
- `POST /auth/signup` - Sign up (public)
- `GET /auth/authorize` - OAuth authorize (public)
- `GET /auth/callback` - OAuth callback (public)
- `POST /auth/refresh` - Refresh token (public)
- `POST /auth/logout` - Logout (public)

**InternalCustomerController** (@RequestMapping("/internal/v1/policies"))
- `GET /internal/v1/policies/{policyId}/coverage` - Internal API (requires auth)

---

### CLAIMS-SERVICE (Port 8082)

**ClaimController** (@RequestMapping("/claims"))
- `GET /claims` - List claims (requires auth)
- `GET /claims/{id}` - Get claim detail (requires auth)
- `POST /claims` - Create claim (requires auth)
- `PATCH /claims/{id}/status` - Update claim status (requires auth)

**InternalClaimsController** (@RequestMapping("/internal/v1/claims"))
- `GET /internal/v1/claims/{claimId}/status` - Internal API (requires auth)
- `GET /internal/v1/claims/{claimId}/documents` - Internal API (requires auth)
- `GET /internal/v1/claims/{claimId}/permissions/check` - Internal API (requires auth)

---

### AGENT-SERVICE (Port 8083)

**AgentController** (@RequestMapping("/agent"))
- `POST /agent/stream` - Stream chat response (produces: text/event-stream, requires auth)
- `GET /agent/claims/{claimId}` - Get conversation history (requires auth)

---

## GATEWAY ROUTES (from config-repo/api-gateway.yml)

| Route ID | Path Pattern | Target Service | StripPrefix | Issues |
|----------|--------------|-----------------|-------------|--------|
| customer-service | /customer/** | lb://CUSTOMER-SERVICE | 1 | ✓ Correct |
| claims-service | /claims/** | lb://CLAIMS-SERVICE | 1 | ✓ Correct |
| agent-service | /agent/** | lb://AGENT-SERVICE | 1 | ❌ WRONG StripPrefix |
| policy-service | /policies/** | lb://POLICY-SERVICE | (none) | ❌ Service doesn't exist |
| notification-service | /notifications/** | lb://NOTIFICATION-SERVICE | (none) | ❌ Service doesn't exist |

---

## STEP 2: ROUTING PROBLEMS IDENTIFIED

### PROBLEM #1: Agent-Service StripPrefix Mismatch

**Issue**: Request path transformation mismatch

- **Gateway Configuration**:
  - Route: `/agent/**` → `lb://AGENT-SERVICE` with `StripPrefix=1`
  
- **Controller Mapping**:
  - Controller: `@RequestMapping("/agent")`
  - Endpoint: `@PostMapping("/stream")` → Full path: `/agent/stream`

- **Flow**:
  ```
  Client Request: POST /agent/stream
                    ↓
  Gateway matches: /agent/** → StripPrefix=1
                    ↓
  Stripped path: /stream (removes /agent)
                    ↓
  Sent to AGENT-SERVICE: POST /stream
                    ↓
  Controller expects: /agent/stream
                    ↓
  Result: 404 NOT FOUND
  ```

- **Root Cause**: The controller includes the service prefix in its @RequestMapping, but the Gateway strips it. This is a path mismatch.

- **Both Endpoints Affected**:
  - `POST /agent/stream` → becomes `/stream` → Controller expects `/agent/stream` → 404
  - `GET /agent/claims/{claimId}` → becomes `/claims/{claimId}` → Controller expects `/agent/claims/{claimId}` → 404

- **Fix Options**:
  1. **Option A (Configuration-Only)**: Remove StripPrefix from agent-service route in api-gateway.yml
     - Pros: Minimal change, preserves controller contracts
     - Cons: Inconsistent with other routes
  2. **Option B (Code Change)**: Change controller @RequestMapping from "/agent" to "/" 
     - Pros: Consistent with other routes (customer, claims use root path)
     - Cons: Changes code, affects direct service calls

- **Recommended Fix**: **Option B** - Change controller mapping to "/" for consistency with other services

---

### PROBLEM #2: Policy-Service Routes Non-Existent Service

**Issue**: Routing to service that doesn't exist in repository

- **Gateway Configuration**:
  ```yaml
  - id: policy-service
    uri: lb://POLICY-SERVICE
    predicates:
      - Path=/policies/**
  ```

- **Reality**:
  - No POLICY-SERVICE deployed or in repository
  - PolicyController is in CUSTOMER-SERVICE (port 8081)
  - Controller path: `@RequestMapping("/policies")`

- **Flow**:
  ```
  Client Request: GET /policies
                    ↓
  Gateway matches: /policies/** → lb://POLICY-SERVICE
                    ↓
  Eureka lookup: POLICY-SERVICE
                    ↓
  Result: SERVICE NOT FOUND (503 Service Unavailable)
  ```

- **Root Cause**: Configuration references non-existent service. Policies are part of customer-service.

- **Fix**: Change route to point to CUSTOMER-SERVICE with StripPrefix=1
  ```yaml
  - id: policy-service
    uri: lb://CUSTOMER-SERVICE
    predicates:
      - Path=/policies/**
    filters:
      - StripPrefix=1
  ```

---

### PROBLEM #3: Notification-Service Routes Non-Existent Service

**Issue**: Routing to service that doesn't exist in repository

- **Gateway Configuration**:
  ```yaml
  - id: notification-service
    uri: lb://NOTIFICATION-SERVICE
    predicates:
      - Path=/notifications/**
  ```

- **Reality**:
  - No NOTIFICATION-SERVICE deployed or in repository
  - No notifications controller found

- **Flow**:
  ```
  Client Request: GET /notifications/**
                    ↓
  Gateway matches: /notifications/** → lb://NOTIFICATION-SERVICE
                    ↓
  Eureka lookup: NOTIFICATION-SERVICE
                    ↓
  Result: SERVICE NOT FOUND (503 Service Unavailable)
  ```

- **Root Cause**: Configuration references non-existent service not implemented in this phase.

- **Fix Options**:
  1. Remove the route entirely (recommended if not needed now)
  2. Implement notification-service (larger change)

- **Recommended Fix**: Remove route (service not yet implemented)

---

## STEP 3: SECURITY CHECK

### Public Routes (from api-gateway.yml)
```
/customer/auth/signup
/customer/auth/authorize
/customer/auth/callback
/customer/auth/refresh
/customer/auth/logout
/customer/webhooks/**
/actuator/**
/v3/api-docs/**
/swagger-ui/**
/swagger-ui.html
```

**Verification**:
- ✓ `/customer/auth/*` - Correctly public (OAuth endpoints)
- ✓ `/customer/webhooks/**` - Correctly public (webhook endpoints)
- ✓ `/actuator/**` - Correctly public (monitoring)
- ✓ Other endpoints protected - All other routes require JWT token

**Expected Behavior** (after fixes):
- Public request to `/customer/auth/signup` → 200/POST OK or 400 (validation error)
- Authenticated request to `/customer/policies` → 200 or 400
- Unauthenticated request to `/customer/policies` → 401 Unauthorized
- Invalid token to any protected route → 401 Unauthorized
- Valid token to `/claims` → 200 or 400
- Nonexistent route `/foo/bar` → 404 Not Found

---

## GATEWAY → SERVICE PATH MAPPING (AFTER FIXES)

| Client Request | Gateway Route | Match | StripPrefix | Service Path | Service | Controller |
|---|---|---|---|---|---|---|
| `/customer/policies` | /customer/** | ✓ | 1 | `/policies` | CUSTOMER-SERVICE | PolicyController |
| `/customer/auth/signup` | /customer/** | ✓ | 1 | `/auth/signup` | CUSTOMER-SERVICE | AuthController |
| `/claims/123` | /claims/** | ✓ | 1 | `/123` | CLAIMS-SERVICE | ClaimController |
| `/agent/stream` | /agent/** | ✓ | **0** (no strip) | `/agent/stream` | AGENT-SERVICE | AgentController |
| `/agent/claims/123` | /agent/** | ✓ | **0** (no strip) | `/agent/claims/123` | AGENT-SERVICE | AgentController |
| `/policies` | /policies/** | ✓ | 1 | `/` | CUSTOMER-SERVICE | PolicyController |
| `/notifications/**` | /notifications/** | ✓ | ? | ??? | **SERVICE MISSING** | N/A |

---

## STEP 4 & 5: PROPOSED FIXES

### Fix #1: Agent-Service Route - Change to Remove StripPrefix

**File**: `config-repo/api-gateway.yml`

**Change**:
```yaml
# BEFORE:
- id: agent-service
  uri: lb://AGENT-SERVICE
  predicates:
    - Path=/agent/**
  filters:
    - StripPrefix=1

# AFTER:
- id: agent-service
  uri: lb://AGENT-SERVICE
  predicates:
    - Path=/agent/**
  filters: []
  # OR add no-op filter - Spring Cloud Gateway doesn't strip by default if no filters specified
```

**Alternative Fix #1B**: Change Agent Controller Mapping

**File**: `agent-service/src/main/java/.../AgentController.java`

**Change**:
```java
// BEFORE:
@RequestMapping("/agent")

// AFTER:
@RequestMapping("")  // or "/"
```

**Recommendation**: **Fix #1** (configuration-only) - removes the StripPrefix.

---

### Fix #2: Policy-Service Route - Point to Correct Service

**File**: `config-repo/api-gateway.yml`

**Change**:
```yaml
# BEFORE:
- id: policy-service
  uri: lb://POLICY-SERVICE
  predicates:
    - Path=/policies/**

# AFTER:
- id: policy-service
  uri: lb://CUSTOMER-SERVICE
  predicates:
    - Path=/policies/**
  filters:
    - StripPrefix=1
```

---

### Fix #3: Notification-Service Route - Remove (Not Implemented)

**File**: `config-repo/api-gateway.yml`

**Change**:
```yaml
# REMOVE THIS ENTIRE SECTION (not in this phase):
- id: notification-service
  uri: lb://NOTIFICATION-SERVICE
  predicates:
    - Path=/notifications/**
```

---

## FEIGN CLIENTS (Service-to-Service Communication)

Searching for Feign clients to verify inter-service routing:

- **Claims-Service** → Calls CUSTOMER-SERVICE for policy/coverage info
- **Agent-Service** → Calls CLAIMS-SERVICE for claim details

These use service discovery (Eureka) directly, not through Gateway, so routing rules above don't apply.

---

## TESTING PLAN

### Prerequisite Services
```bash
# Terminal 1: Eureka Server (Discovery)
cd D:\Mayur\claimsassist\insurance-ai-platform
.\mvnw.cmd -pl discovery-service spring-boot:run

# Terminal 2: Config Server
.\mvnw.cmd -pl config-service spring-boot:run

# Terminal 3: Customer Service
.\mvnw.cmd -pl customer-service spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"

# Terminal 4: Claims Service
.\mvnw.cmd -pl claims-service spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"

# Terminal 5: Agent Service
.\mvnw.cmd -pl agent-service spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"

# Terminal 6: API Gateway
.\mvnw.cmd -pl api-gateway spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"
```

### Verify Eureka Dashboard
- http://localhost:8761 - Should show all 6 services registered

### Test Routes (After Fixes)

#### Positive Tests
```bash
# Customer Public Routes (should return 200/400, not 401)
curl -X POST http://localhost:8080/customer/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"pass123"}'

# Customer Protected Routes (should require token)
curl -X GET http://localhost:8080/customer/policies \
  -H "Authorization: Bearer <token>"

# Claims Routes (should require token)
curl -X GET http://localhost:8080/claims \
  -H "Authorization: Bearer <token>"

# Agent Routes (should require token)
curl -X POST http://localhost:8080/agent/stream \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"message":"test","claimId":1}'

# Direct service access (should work without Gateway)
curl -X GET http://localhost:8081/policies
curl -X GET http://localhost:8082/claims
curl -X GET http://localhost:8083/agent/claims/1
```

#### Negative Tests
```bash
# Nonexistent route should return 404
curl -X GET http://localhost:8080/foo/bar

# Protected route without token should return 401
curl -X GET http://localhost:8080/customer/policies

# Unavailable service should return 503
curl -X GET http://localhost:8080/policies  # (before fix - will return 503)
```

---

## SUMMARY OF ISSUES

| Problem | Service | Root Cause | Fix | Impact |
|---------|---------|-----------|-----|--------|
| #1: Agent StripPrefix Wrong | agent-service | Config mismatch with controller mapping | Remove StripPrefix=1 from route | `/agent/*` returns 404 |
| #2: Policy Service Missing | policy-service | Config references non-existent service | Route to CUSTOMER-SERVICE instead | `/policies` returns 503 |
| #3: Notification Service Missing | notification-service | Config references non-existent service | Remove route | `/notifications` returns 503 |

---

## NEXT STEPS

1. Apply all three fixes to `config-repo/api-gateway.yml`
2. Rebuild: `mvnw clean package -DskipTests`
3. Restart api-gateway service
4. Run all test cases
5. Verify Eureka registrations
6. Document all test results
7. Update COPILOT_HANDOFF.md with final audit results


