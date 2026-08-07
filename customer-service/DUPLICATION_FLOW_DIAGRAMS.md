# Duplication Flow Diagrams

## Current Implementation (WITH DUPLICATES)

### Flow 1: Keycloak User Creation Failure

```
┌─────────────────────────────────────────────────────────────────┐
│ User Registration Request: POST /auth/signup                    │
└────────────────────────┬────────────────────────────────────────┘
                         ↓
         ┌───────────────────────────────┐
         │ AuthController.signup()       │
         └────────────┬──────────────────┘
                      ↓
         ┌──────────────────────────────────────────────────────┐
         │ KeycloakUserProvisioningService.createUser()        │
         │  ├─ 1. Fetch admin token (fetchAdminToken)          │
         │  ├─ 2. Create user payload                          │
         │  ├─ 3. POST to Keycloak API                         │
         │  ├─ 4. Extract location header ← FAILS HERE         │
         │  ├─ 5. On failure: Build ServiceUnavailableEx       │
         └──────────────┬───────────────────────────────────────┘
                        ↓
         ┌────────────────────────────────────────────────┐
         │ emitKeycloakErrorEvent() [SERVICE LAYER]       │
         │ ┌──────────────────────────────────────────┐   │
         │ │ EVENT #1 LOGGED                          │   │
         │ ├──────────────────────────────────────────┤   │
         │ │ eventLogger.logExceptionEvent(            │   │
         │ │   "customer-service",                    │   │
         │ │   "customer-service",                    │   │
         │ │   {                                      │   │
         │ │     errorCode: "KEYCLOAK_INTEGRATION_ERROR" │
         │ │     exceptionType: "ServiceUnavailable"  │   │
         │ │     message: "Keycloak user creation..." │   │
         │ │     executionTimeMs: 45                  │   │
         │ │     username: "john@example.com"         │   │
         │ │     userId: 123                          │   │
         │ │   }                                      │   │
         │ └──────────────────────────────────────────┘   │
         └────────────────┬───────────────────────────────┘
                          ↓
         ┌──────────────────────────────────────────────────┐
         │ throw new ServiceUnavailableException(...)       │
         └──────────────────┬───────────────────────────────┘
                            ↓
                   ┌─────────────────────┐
                   │ Exception propagates │
                   │ up to HTTP layer    │
                   └──────────┬──────────┘
                              ↓
         ┌────────────────────────────────────────────────┐
         │ GlobalExceptionHandler.handleServiceUnavailable()│ [HANDLER LAYER]
         │ ┌──────────────────────────────────────────┐   │
         │ │ long startTime = currentTime             │   │
         │ │ ...                                      │   │
         └──────────────┬───────────────────────────────┘
                        ↓
         ┌────────────────────────────────────────────────┐
         │ emitExceptionEvent() [HANDLER LAYER]          │
         │ ┌──────────────────────────────────────────┐   │
         │ │ EVENT #2 LOGGED (DUPLICATE!)             │   │
         │ ├──────────────────────────────────────────┤   │
         │ │ eventLogger.logExceptionEvent(            │   │
         │ │   "customer-service",                    │   │
         │ │   "customer-service",                    │   │
         │ │   {                                      │   │
         │ │     errorCode: "SERVICE_UNAVAILABLE"  ←  │   │  ⚠️ DIFFERENT!
         │ │     exceptionType: "ServiceUnavailable"  │   │
         │ │     message: "Service temporarily..."    │   │  ⚠️ DIFFERENT!
         │ │     httpMethod: "POST"                   │   │  ← New in handler
         │ │     requestUri: "/auth/signup"           │   │  ← New in handler
         │ │     executionTimeMs: 2                   │   │  ⚠️ DIFFERENT!
         │ │     username: "john@example.com"         │   │
         │ │   }                                      │   │
         │ └──────────────────────────────────────────┘   │
         └────────────────┬───────────────────────────────┘
                          ↓
         ┌──────────────────────────────────────────────────┐
         │ buildEnhancedError()                             │
         └──────────────────┬───────────────────────────────┘
                            ↓
         ┌───────────────────────────────────────────────────┐
         │ ResponseEntity<EnhancedApiError>                 │
         │ HTTP 503                                          │
         │ {                                                 │
         │   timestamp: "2026-08-07T10:30:45.123Z",         │
         │   status: 503,                                    │
         │   errorCode: "SERVICE_UNAVAILABLE",              │
         │   message: "Service temporarily unavailable",    │
         │   correlationId: "abc-123-xyz",                  │
         │   path: "/auth/signup",                          │
         │   method: "POST"                                 │
         │ }                                                 │
         └───────────────────────────────────────────────────┘
                            ↓
         ┌──────────────────────────────────────────────────┐
         │ CLIENT RECEIVES RESPONSE                         │
         └──────────────────────────────────────────────────┘

RESULT:
✅ Service logs EVENT #1: "KEYCLOAK_INTEGRATION_ERROR"
✅ Handler logs EVENT #2: "SERVICE_UNAVAILABLE" ← DUPLICATE!
❌ Two events for one error
❌ Different error codes
❌ Conflicting information
```

---

### Flow 2: Refresh Token Validation Failure (REVOKED)

```
┌─────────────────────────────────────────────────────────────────┐
│ Token Refresh Request: POST /auth/refresh?refreshToken=abc123   │
└────────────────────────┬────────────────────────────────────────┘
                         ↓
         ┌───────────────────────────────┐
         │ AuthController.refresh()      │
         └────────────┬──────────────────┘
                      ↓
         ┌──────────────────────────────────────────────────────┐
         │ OAuth2TokenService.refreshToken()                    │
         │  ├─ 1. Validate refresh token locally               │
         │  ├─ 2. Call refreshTokenService.validateAndRotate() │
         │  ↓                                                    │
         └──────────────┬───────────────────────────────────────┘
                        ↓
         ┌──────────────────────────────────────────────────────┐
         │ RefreshTokenService.validateAndRotate()             │
         │  ├─ 1. Find token in DB                            │
         │  ├─ 2. Check if revoked ← FAILS HERE               │
         │  ├─ 3. On revoked: Build IllegalArgumentException  │
         └──────────────┬───────────────────────────────────────┘
                        ↓
         ┌────────────────────────────────────────────────┐
         │ emitRefreshTokenEvent() [SERVICE LAYER]        │
         │ ┌──────────────────────────────────────────┐   │
         │ │ EVENT #1 LOGGED                          │   │
         │ ├──────────────────────────────────────────┤   │
         │ │ eventLogger.logExceptionEvent(            │   │
         │ │   {                                      │   │
         │ │     errorCode: "REVOKED_TOKEN"        ✅ │   │
         │ │     message: "Refresh token revoked"  ✅ │   │
         │ │     context: "refresh_token_validation" │   │
         │ │     executionTimeMs: 12                 │   │
         │ │   }                                      │   │
         │ └──────────────────────────────────────────┘   │
         └────────────────┬───────────────────────────────┘
                          ↓
         ┌──────────────────────────────────────────────────┐
         │ throw new IllegalArgumentException(...)         │
         └──────────────────┬───────────────────────────────┘
                            ↓
                   ┌─────────────────────┐
                   │ Exception propagates │
                   │ up to HTTP layer    │
                   └──────────┬──────────┘
                              ↓
         ┌────────────────────────────────────────────────────┐
         │ GlobalExceptionHandler.handleUnexpectedException() │  [HANDLER LAYER]
         │ (Note: No specific handler for IllegalArgument)    │
         │ ┌──────────────────────────────────────────────┐   │
         │ │ Fallback to catch-all: Exception.class       │   │
         └──────────────┬───────────────────────────────────┘
                        ↓
         ┌────────────────────────────────────────────────────┐
         │ emitExceptionEvent() [HANDLER LAYER]               │
         │ ┌──────────────────────────────────────────────┐   │
         │ │ EVENT #2 LOGGED (DUPLICATE!)                │   │
         │ ├──────────────────────────────────────────────┤   │
         │ │ eventLogger.logExceptionEvent(               │   │
         │ │   {                                         │   │
         │ │     errorCode: "INTERNAL_ERROR"  ❌ WRONG!  │   │
         │ │     httpStatus: 500              ❌ WRONG!  │   │
         │ │     message: "An unexpected..."  ❌ WRONG!  │   │
         │ │     executionTimeMs: 3          (different) │   │
         │ │   }                                         │   │
         │ └──────────────────────────────────────────────┘   │
         └────────────────┬───────────────────────────────────┘
                          ↓
         ┌────────────────────────────────────────────────────┐
         │ buildEnhancedError()                               │
         │ HTTP 500 ❌ (Should be 401)                        │
         └────────────────┬───────────────────────────────────┘
                          ↓
         ┌───────────────────────────────────────────────────┐
         │ ResponseEntity<EnhancedApiError>                 │
         │ HTTP 500 ← WRONG STATUS!                         │
         │ {                                                 │
         │   errorCode: "INTERNAL_ERROR",  ← WRONG!         │
         │   message: "An unexpected error occurred"  ← WRONG!
         │ }                                                 │
         └───────────────────────────────────────────────────┘
                            ↓
         ┌──────────────────────────────────────────────────┐
         │ CLIENT RECEIVES RESPONSE                         │
         └──────────────────────────────────────────────────┘

RESULT:
✅ Service logs EVENT #1: "REVOKED_TOKEN" (Correct)
✅ Handler logs EVENT #2: "INTERNAL_ERROR" (Wrong!)
❌ Two events for one error
❌ CONFLICTING error codes
❌ CONFLICTING HTTP status
❌ Client gets HTTP 500 for revoked token!
```

---

## Recommended Implementation (SIMPLIFIED)

### Flow: Exception Handling (Without Duplicates)

```
┌─────────────────────────────────────────────────────────────────┐
│ Request (e.g., POST /auth/signup or POST /auth/refresh)        │
└────────────────────────┬────────────────────────────────────────┘
                         ↓
         ┌───────────────────────────────┐
         │ AuthController.signup()       │
         │ or                            │
         │ AuthController.refresh()      │
         └────────────┬──────────────────┘
                      ↓
         ┌──────────────────────────────────────────────────────┐
         │ Service Layer (e.g., KeycloakUserProvisioningService) │
         │  ├─ Perform business logic                           │
         │  ├─ On error: throw appropriate exception            │
         │  ├─ DO NOT emit events ❌ (was here, now removed)    │
         │  └─ DO NOT use EventLogger ❌ (was here, now removed)│
         └──────────────┬───────────────────────────────────────┘
                        ↓
        ┌─────────────────────────────────┐
        │ Exception propagates            │
        │ up to HTTP layer                │
        └────────────┬──────────────────────┘
                     ↓
         ┌────────────────────────────────────────────────────┐
         │ GlobalExceptionHandler (Single Point of Truth)     │
         │                                                     │
         │ SPECIFIC HANDLERS:                                  │
         │  @ExceptionHandler(ServiceUnavailableException)    │
         │  @ExceptionHandler(IllegalArgumentException)       │
         │  @ExceptionHandler(BadRequestException)            │
         │  ...                                                │
         │  @ExceptionHandler(Exception.class) // catch-all    │
         └──────────────┬───────────────────────────────────────┘
                        ↓
         ┌────────────────────────────────────────────────┐
         │ Exception Handler Logic:                       │
         │  1. Start timer                                │
         │  2. Determine HTTP status                      │
         │  3. Determine error code                       │
         │  4. Build EnhancedApiError                     │
         │  5. EMIT SINGLE EVENT ✅                       │
         │  6. Return response                            │
         └────────────────┬───────────────────────────────┘
                          ↓
         ┌────────────────────────────────────────────────┐
         │ emitExceptionEvent()                           │
         │ ┌──────────────────────────────────────────┐   │
         │ │ EVENT #1 LOGGED (ONLY ONE!) ✅           │   │
         │ ├──────────────────────────────────────────┤   │
         │ │ eventLogger.logExceptionEvent(            │   │
         │ │   {                                      │   │
         │ │     errorCode: (determined)              │   │
         │ │     exceptionType: (exception class)     │   │
         │ │     message: (sanitized)                 │   │
         │ │     httpMethod: (from request) ✅       │   │
         │ │     requestUri: (from request) ✅        │   │
         │ │     httpStatus: (determined)  ✅         │   │
         │ │     executionTimeMs: (measured) ✅       │   │
         │ │     username: (if authenticated) ✅      │   │
         │ │     correlationId: (from MDC) ✅         │   │
         │ │   }                                      │   │
         │ └──────────────────────────────────────────┘   │
         └────────────────┬───────────────────────────────┘
                          ↓
         ┌──────────────────────────────────────────────────┐
         │ ResponseEntity<EnhancedApiError>                │
         │ {                                                │
         │   status: (determined by handler),              │
         │   errorCode: (determined by handler),           │
         │   message: (sanitized),                         │
         │   timestamp, correlationId, path, method, ...   │
         │ }                                                │
         └──────────────────┬───────────────────────────────┘
                            ↓
         ┌──────────────────────────────────────────────────┐
         │ CLIENT RECEIVES RESPONSE                         │
         └──────────────────────────────────────────────────┘

RESULT:
✅ Single event logged per exception
✅ Correct error code determined in handler
✅ Correct HTTP status determined in handler
✅ Full context included (HTTP method, URI, timing, user, IDs)
✅ Clean observability
✅ Easier to debug
✅ No duplicate logs
```

---

## Event Emission Comparison

### CURRENT (With Duplicates)

| Scenario | Service Event | Handler Event | Total |
|----------|---|---|---|
| Keycloak user creation fails | ✅ KEYCLOAK_INTEGRATION_ERROR | ✅ SERVICE_UNAVAILABLE | **2** ❌ |
| Keycloak token fetch fails | ✅ KEYCLOAK_INTEGRATION_ERROR | ✅ SERVICE_UNAVAILABLE | **2** ❌ |
| Refresh token invalid | ✅ INVALID_TOKEN | ✅ INTERNAL_ERROR | **2** ❌ |
| Refresh token revoked | ✅ REVOKED_TOKEN | ✅ INTERNAL_ERROR | **2** ❌ |
| Refresh token expired | ✅ EXPIRED_TOKEN | ✅ INTERNAL_ERROR | **2** ❌ |
| JWT validation fails | ❌ None (returns null) | ✅ INTERNAL_ERROR | **1** |

**Total for normal flow**: 10 events (5 duplicated + 1 unique)

### RECOMMENDED (Simplified)

| Scenario | Service Event | Handler Event | Total |
|----------|---|---|---|
| Keycloak user creation fails | ❌ | ✅ SERVICE_UNAVAILABLE | **1** ✅ |
| Keycloak token fetch fails | ❌ | ✅ SERVICE_UNAVAILABLE | **1** ✅ |
| Refresh token invalid | ❌ | ✅ INVALID_TOKEN | **1** ✅ |
| Refresh token revoked | ❌ | ✅ REVOKED_TOKEN | **1** ✅ |
| Refresh token expired | ❌ | ✅ EXPIRED_TOKEN | **1** ✅ |
| JWT validation fails | ❌ | ✅ JWT_VALIDATION_FAILED | **1** ✅ |

**Total for normal flow**: 6 events (correct, no duplicates)

**Improvement**: 40% reduction in event volume, 100% accuracy

---

**End of Review**

