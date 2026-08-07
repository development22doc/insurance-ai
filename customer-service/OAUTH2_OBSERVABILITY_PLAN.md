# Authentication & OAuth2 Observability - Internal Migration Plan

**Status**: Planning Phase  
**Target**: Customer-Service  
**Scope**: Add EventLogger and PerformanceLogger to all authentication flows  

---

## Current State Analysis

### Existing Authentication Flows

1. **Signup Flow** (AuthController.signup)
   - Customer lookup (cache read)
   - Customer creation (DB write)
   - Keycloak user creation (external API)
   - Cache eviction
   - Current logging: Basic log.info() calls

2. **Authorization Flow** (AuthController.authorize)
   - PKCE generation (code verifier, code challenge)
   - State parameter generation
   - Authorization URL construction
   - Temporary in-memory PKCE store (state -> code_verifier)
   - Current logging: None

3. **OAuth Callback Flow** (AuthController.callback)
   - State/code verifier consumption
   - Authorization code exchange with Keycloak
   - JWT validation
   - Customer lookup
   - Refresh token creation
   - Current logging: None

4. **Refresh Token Flow** (AuthController.refresh)
   - Local refresh token validation
   - Token rotation
   - Keycloak token refresh
   - Customer lookup
   - Current logging: None

5. **Logout Flow** (AuthController.logout)
   - Keycloak logout (external API)
   - Local refresh token revocation
   - Current logging: None

### Supporting Components

- **KeycloakUserProvisioningService**: Creates Keycloak users (Keycloak Admin API)
- **RefreshTokenService**: Manages refresh tokens locally (DB)
- **CustomerLookupService**: Finds customers with Redis caching
- **PkceService**: PKCE and state generation
- **OAuth2AuthorizationService**: Authorization flow logic
- **OAuth2TokenService**: Token exchange and validation
- **OAuth2LogoutService**: Logout logic

### Current Observability Gaps

- ❌ No structured business event logging
- ❌ No performance measurements
- ❌ No cache hit/miss tracking
- ❌ No repository call timing
- ❌ No Keycloak API call tracking
- ❌ No correlation ID propagation documented
- ❌ Duplicate log.info() calls
- ❌ No security event logging for auth failures

---

## Implementation Plan

### Phase 1: AuthController Enhancements (Signup)

**Changes**:
1. Inject EventLogger
2. Replace/enhance log.info() with structured events
3. Add performance tracking
4. Add cache event tracking via CustomerLookupService

**Events to Emit**:
- SIGNUP_STARTED (businessEvent)
- CUSTOMER_LOOKUP_STARTED → CUSTOMER_FOUND/CUSTOMER_NOT_FOUND (businessEvent)
- CUSTOMER_ALREADY_EXISTS (securityEvent, failure)
- VALIDATION_FAILED (securityEvent, failure)
- CUSTOMER_CREATION_STARTED (businessEvent)
- CUSTOMER_SAVED (businessEvent)
- KEYCLOAK_USER_CREATION_STARTED (businessEvent)
- KEYCLOAK_USER_CREATED (businessEvent)
- KEYCLOAK_USER_CREATION_FAILED (securityEvent, failure)
- CACHE_EVICT (cacheEvent)
- SIGNUP_COMPLETED (businessEvent)
- SIGNUP_FAILED (securityEvent, failure)

**Performance Tracking**:
- Entire signup flow
- Customer lookup
- Customer creation
- Keycloak user creation

---

### Phase 2: AuthController Enhancements (Authorization)

**Changes**:
1. Inject EventLogger
2. Add structured business events
3. Add performance tracking

**Events to Emit**:
- AUTHORIZATION_STARTED (businessEvent)
- PKCE_VALIDATION_STARTED (businessEvent)
- PKCE_VALIDATION_COMPLETED (businessEvent)
- AUTHORIZATION_CODE_RECEIVED (businessEvent)
- AUTHORIZATION_COMPLETED (businessEvent)

**Performance Tracking**:
- PKCE generation
- Authorization URL construction
- Overall authorization flow

---

### Phase 3: AuthController Enhancements (Callback)

**Changes**:
1. Inject EventLogger
2. Add structured business events
3. Add performance tracking
4. Track cache operations

**Events to Emit**:
- CALLBACK_STARTED (businessEvent)
- TOKEN_EXCHANGE_STARTED (businessEvent)
- TOKEN_EXCHANGE_COMPLETED (businessEvent)
- USERINFO_FETCH_STARTED (businessEvent)
- USERINFO_FETCH_COMPLETED (businessEvent)
- CUSTOMER_LOOKUP_STARTED (businessEvent)
- CUSTOMER_FOUND (businessEvent)
- CUSTOMER_NOT_FOUND (securityEvent, failure)
- CACHE_EVICT (cacheEvent)
- CALLBACK_COMPLETED (businessEvent)

**Performance Tracking**:
- Token exchange
- JWT validation
- Customer lookup
- Refresh token creation
- Overall callback flow

---

### Phase 4: AuthController Enhancements (Refresh & Logout)

**Changes**:
1. Inject EventLogger
2. Add structured business events
3. Add performance tracking

**Events for Refresh**:
- TOKEN_REFRESH_STARTED (businessEvent)
- REFRESH_TOKEN_VALIDATED (businessEvent)
- TOKEN_ROTATION_STARTED (businessEvent)
- TOKEN_ROTATED (businessEvent)
- TOKEN_REFRESH_COMPLETED (businessEvent)
- Failures: INVALID_REFRESH_TOKEN, REVOKED_REFRESH_TOKEN, EXPIRED_REFRESH_TOKEN (securityEvent)

**Events for Logout**:
- LOGOUT_STARTED (businessEvent)
- REFRESH_TOKEN_REVOKED (businessEvent)
- CACHE_EVICT (cacheEvent)
- LOGOUT_COMPLETED (businessEvent)

**Performance Tracking**:
- Token validation
- Token exchange
- Overall refresh/logout flows

---

### Phase 5: KeycloakUserProvisioningService Enhancements

**Changes**:
1. Inject EventLogger and PerformanceLogger
2. Add structured business events for Keycloak operations
3. Wrap external REST calls with performance tracking
4. Track admin token fetch separately

**Events to Emit**:
- KEYCLOAK_REQUEST_STARTED (businessEvent)
- KEYCLOAK_REQUEST_COMPLETED (businessEvent)
- KEYCLOAK_REQUEST_FAILED (securityEvent, failure)

**Performance Tracking**:
- Admin token fetch
- User creation API call
- Overall createUser() method

---

### Phase 6: RefreshTokenService Enhancements

**Changes**:
1. Inject EventLogger and PerformanceLogger
2. Add structured business events
3. Wrap database operations with performance tracking

**Events to Emit**:
- REFRESH_TOKEN_CREATED (businessEvent)
- REFRESH_TOKEN_VALIDATED (businessEvent)
- TOKEN_ROTATION_STARTED (businessEvent)
- TOKEN_ROTATED (businessEvent)
- Failures: INVALID_REFRESH_TOKEN, REVOKED_REFRESH_TOKEN, EXPIRED_REFRESH_TOKEN (securityEvent)

**Performance Tracking**:
- validateAndRotate() method
- createRefreshToken() method
- Database operations

---

### Phase 7: CustomerLookupService Enhancements

**Changes**:
1. Inject EventLogger
2. Add cache hit/miss tracking
3. Add database lookup performance tracking

**Events to Emit**:
- CACHE_HIT (cacheEvent)
- CACHE_MISS (cacheEvent)
- CUSTOMER_LOOKUP_STARTED (businessEvent)
- CUSTOMER_FOUND (businessEvent)
- CUSTOMER_NOT_FOUND (businessEvent)
- DATABASE_QUERY (databaseEvent with timing)

**Performance Tracking**:
- findByUsername() method

---

### Phase 8: OAuth2TokenService Enhancements

**Changes**:
1. Inject EventLogger and PerformanceLogger
2. Add structured events for token operations
3. Track external API calls
4. Track JWT validation

**Events to Emit**:
- TOKEN_EXCHANGE_STARTED (businessEvent)
- TOKEN_EXCHANGE_COMPLETED (businessEvent)
- JWT_VALIDATION_STARTED (securityEvent)
- JWT_VALIDATION_COMPLETED (securityEvent)
- CUSTOMER_LOOKUP_STARTED (businessEvent)
- CUSTOMER_FOUND/NOT_FOUND (businessEvent)

**Performance Tracking**:
- exchangeAuthorizationCode() method
- refreshToken() method
- JWT validation
- Customer lookup

---

### Phase 9: OAuth2LogoutService Enhancements

**Changes**:
1. Inject EventLogger and PerformanceLogger
2. Add structured logout events
3. Track external API call

**Events to Emit**:
- LOGOUT_STARTED (businessEvent)
- KEYCLOAK_LOGOUT_STARTED (businessEvent)
- KEYCLOAK_LOGOUT_COMPLETED (businessEvent)
- REFRESH_TOKEN_REVOKED (businessEvent)

**Performance Tracking**:
- logout() method
- Keycloak logout call

---

### Phase 10: OAuth2AuthorizationService Enhancements

**Changes**:
1. Inject EventLogger
2. Add structured authorization events
3. Track PKCE generation

**Events to Emit**:
- AUTHORIZATION_REQUEST_CREATED (businessEvent)
- PKCE_GENERATED (businessEvent)
- CODE_VERIFIER_STORED (businessEvent)
- CODE_VERIFIER_CONSUMED (businessEvent)

---

## Sensitive Data Handling

### NEVER Log
- ❌ Authorization header values
- ❌ JWT tokens (access_token, id_token, refresh_token)
- ❌ Passwords
- ❌ Client secrets
- ❌ Code verifiers and code challenges
- ❌ PII (only correlationId for tracing)

### Safe to Log
- ✅ Username (for debugging)
- ✅ Customer ID (numeric)
- ✅ Keycloak user ID (UUID)
- ✅ State parameter (generic UUID)
- ✅ Performance metrics
- ✅ Cache hit/miss
- ✅ Event timestamps
- ✅ HTTP status codes
- ✅ Error codes (not error messages containing secrets)

---

## Correlation & Tracing

**Goal**: Every authentication request traceable by one Correlation ID

**Mechanism**:
- CorrelationIdFilter sets Correlation ID in MDC at request entry
- All EventLogger calls automatically include MDC fields:
  - correlationId
  - traceId
  - spanId
- These propagate through entire auth flow
- All events linked by correlationId

**Implementation**:
- No manual MDC handling needed
- EventLogger handles automatic inclusion
- Use LoggingConstants.MDC_* constants

---

## Removed Duplicates

### Duplicate log.info() calls to replace
- AuthController.signup: 2 log.info() calls
- KeycloakUserProvisioningService.createUser: 1 log.info() call
- Multiple generic log statements

### Duplicate EventLogger calls to remove
- None currently exist (cleaned in PR-9)

---

## Testing Strategy

1. **Manual Testing**
   - Execute each auth flow end-to-end
   - Verify events appear in logs
   - Verify one correlationId per request
   - Verify no duplicate events
   - Verify no sensitive data in logs

2. **Integration Testing**
   - Mock Keycloak API responses
   - Verify event sequence for each flow
   - Verify performance metrics are reasonable

3. **Log Analysis**
   - Parse structured event logs
   - Verify event types are correct
   - Verify all required fields present
   - Verify timestamps are ordered

---

## Timeline & Order

1. AuthController.signup ← Most impactful (initial user journey)
2. AuthController.authorize
3. AuthController.callback ← Complex flow, test extensively
4. KeycloakUserProvisioningService ← Support for signup
5. RefreshTokenService ← Support for callback
6. CustomerLookupService ← Foundation for all flows
7. OAuth2TokenService ← Deep token operations
8. AuthController.refresh/logout
9. OAuth2LogoutService
10. OAuth2AuthorizationService

---

## Success Criteria

✅ Every auth request has one correlationId  
✅ All 50+ business events emitted  
✅ No sensitive data in logs  
✅ No duplicate log statements  
✅ Performance metrics for all major operations  
✅ Cache hit/miss tracked  
✅ Zero business logic changes  
✅ Zero API contract changes  
✅ Backward compatible  
✅ All exceptions handled by GlobalExceptionHandler  

---

**Next**: Begin implementation with Phase 1 (AuthController.signup)


