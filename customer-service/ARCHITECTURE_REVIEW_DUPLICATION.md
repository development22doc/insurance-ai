# Architecture Review: Exception Handling Implementation in Customer-Service

## Executive Summary

**Finding**: The current implementation contains **DUPLICATE EXCEPTION LOGGING and EVENT EMISSION** that should be eliminated for better maintainability and to avoid duplicate observability events.

**Key Issue**: Services emit exception events via `EventLogger`, and then `GlobalExceptionHandler` emits the SAME exception events again when it catches them. This creates redundant logging.

---

## Analysis

### 1. ExceptionEventBuilder vs ExceptionLoggingUtil

#### ExceptionLoggingUtil (common-lib)
```java
// AOP-based, for use with JoinPoint in aspects
public static void logException(JoinPoint jp, Throwable ex) {
    String location = jp.getSignature().toShortString();
    String correlation = MDC.get(LoggingConstants.MDC_CORRELATION_ID);
    String trace = MDC.get(LoggingConstants.MDC_TRACE_ID);
    String span = MDC.get(LoggingConstants.MDC_SPAN_ID);
    log.error("exception event={{...}} - {}", location, correlation, trace, span, 
              sanitizeMessage(ex.getMessage()), ex);
}
```

**Characteristics:**
- ✅ Uses JoinPoint (for AOP aspects)
- ✅ Logs to SLF4J directly via log.error()
- ✅ Basic sanitization (auth|token|password|secret pattern only)
- ✅ Designed for AspectJ integration

#### ExceptionEventBuilder (customer-service)
```java
// Component-based builder for structured events
public Map<String, Object> buildBaseEvent(
        String exceptionType, String errorCode, String message, String rootCause) {
    Map<String, Object> details = new HashMap<>();
    details.put("correlationId", MDC.get(...));
    details.put("traceId", MDC.get(...));
    // ... more fields
    return details;
}
```

**Characteristics:**
- ✅ Spring @Component (dependency injectable)
- ✅ Builds event details Map for EventLogger
- ✅ Extended sanitization (7 patterns: JWT, auth, password, secret, token, refresh, api_key)
- ✅ Builder pattern with helper methods

**Verdict**: ✅ NOT a duplicate - these are complementary
- ExceptionLoggingUtil is AOP-focused
- ExceptionEventBuilder is event-building focused
- ExceptionEventBuilder correctly extends sanitization

---

### 2. Service-Level Event Emission - DUPLICATE EVENTS DETECTED

#### Pattern 1: KeycloakUserProvisioningService

**Current Flow:**
```
Exception occurs
  ↓
Service emits event via eventLogger.logExceptionEvent()  ← EVENT #1
  ↓
Service throws ServiceUnavailableException
  ↓
GlobalExceptionHandler.handleServiceUnavailable() catches it
  ↓
GlobalExceptionHandler emits event via eventLogger.logExceptionEvent()  ← EVENT #2 (DUPLICATE!)
  ↓
Returns EnhancedApiError response
```

**Code Evidence:**
```java
// In KeycloakUserProvisioningService.fetchAdminToken()
if (response == null || response.get("access_token") == null) {
    ServiceUnavailableException ex = new ServiceUnavailableException(...);
    emitKeycloakErrorEvent(ex, "...", "system", null, startTime);  // EVENT #1
    throw ex;  // → Caught by GlobalExceptionHandler
}
```

```java
// Then in GlobalExceptionHandler.handleServiceUnavailable()
emitExceptionEvent(ex, HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", startTime);  // EVENT #2
```

**Result**: Same exception logged twice as separate events

#### Pattern 2: RefreshTokenService

```java
// In RefreshTokenService.validateAndRotate()
if (existing.isRevoked()) {
    IllegalArgumentException ex = new IllegalArgumentException("Refresh token revoked");
    emitRefreshTokenEvent(ex, "REVOKED_TOKEN", "...", startTime);  // EVENT #1
    throw ex;  // → Caught by GlobalExceptionHandler
}
```

```java
// Then in GlobalExceptionHandler.handleUnexpectedException() (catch-all)
emitExceptionEvent(ex, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", startTime);  // EVENT #2 (DUPLICATE!)
```

**Result**: Same exception logged twice

#### Pattern 3: OAuth2TokenService

```java
// In OAuth2TokenService.extractUsernameFromValidatedIdToken()
} catch (JwtException e) {
    long executionTime = System.currentTimeMillis() - startTime;
    emitJwtValidationEvent(e, "JWT_SIGNATURE_VALIDATION_FAILED", executionTime);  // EVENT #1
    log.warn("JWT signature validation failed...");
    return null;  // Exception not thrown
}
```

**Special Case**: OAuth2TokenService doesn't throw - it just logs and returns null. This is actually OK (no duplicate in this case), BUT:
- It's inconsistent with Keycloak and RefreshToken services
- GlobalExceptionHandler will never see this exception
- Event emission is service-specific (breaks abstraction)

---

### 3. GlobalExceptionHandler Coverage

#### What GlobalExceptionHandler Already Does:
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<EnhancedApiError> handleServiceUnavailable(
            ServiceUnavailableException ex) {
        long startTime = System.currentTimeMillis();
        
        emitExceptionEvent(ex, HttpStatus.SERVICE_UNAVAILABLE, 
                          "SERVICE_UNAVAILABLE", startTime);  // ← EVENT EMISSION
        
        log.error("Service unavailable: {}", error.message(), ex);
        
        return ResponseEntity.status(error.status()).body(error);
    }
}
```

#### What GlobalExceptionHandler Includes:
- ✅ Correlation ID extraction from MDC
- ✅ Trace ID extraction from MDC
- ✅ Span ID extraction from MDC
- ✅ HTTP Method (from request)
- ✅ Request URI (from request)
- ✅ Exception Type (class name)
- ✅ Root Cause (extracted from chain)
- ✅ Execution Time (measured in handler)
- ✅ Username (extracted when authenticated)
- ✅ HTTP Status code
- ✅ Error Code (application-specific)
- ✅ Sanitized message
- ✅ EventLogger integration
- ✅ Returns standardized response

**Conclusion**: GlobalExceptionHandler ALREADY provides all required event emission with MORE context than service-level calls.

---

### 4. Duplicate Exception Logging Identified

#### Scenario 1: Keycloak Failures
```
User signup request
  ↓
KeycloakUserProvisioningService.createUser() fails
  ↓
SERVICE LOGS: emitKeycloakErrorEvent()
  Event: {exceptionType: ServiceUnavailableException, 
          errorCode: KEYCLOAK_INTEGRATION_ERROR, 
          executionTimeMs: 45}
  ↓
Service throws: new ServiceUnavailableException()
  ↓
GlobalExceptionHandler.handleServiceUnavailable() catches
  ↓
HANDLER LOGS: emitExceptionEvent()
  Event: {exceptionType: ServiceUnavailableException, 
          errorCode: SERVICE_UNAVAILABLE,  ← DIFFERENT ERROR CODE!
          executionTimeMs: <milliseconds since handler started>,
          httpMethod: POST,
          requestUri: /auth/signup,
          ...}
  ↓
Result: TWO EVENTS with DIFFERENT ERROR CODES for ONE FAILURE
```

**Issues:**
- ❌ Two separate events logged
- ❌ Different error codes (KEYCLOAK_INTEGRATION_ERROR vs SERVICE_UNAVAILABLE)
- ❌ Different execution times (service vs handler)
- ❌ Duplicate correlation tracking
- ❌ Difficult to correlate in observability system

#### Scenario 2: Token Validation Failures
```
Refresh token request
  ↓
RefreshTokenService.validateAndRotate() fails
  ↓
SERVICE LOGS: emitRefreshTokenEvent()
  Event: {exceptionType: IllegalArgumentException, 
          errorCode: REVOKED_TOKEN,
          context: refresh_token_validation}
  ↓
Service throws: new IllegalArgumentException()
  ↓
GlobalExceptionHandler.handleUnexpectedException() catches (no specific handler)
  ↓
HANDLER LOGS: emitExceptionEvent()
  Event: {exceptionType: IllegalArgumentException,
          errorCode: INTERNAL_ERROR,  ← WRONG ERROR CODE!
          httpStatus: 500}  ← WRONG HTTP STATUS!
  ↓
Result: TWO EVENTS with CONFLICTING INFO for ONE FAILURE
```

**Problems:**
- ❌ Service emits REVOKED_TOKEN, handler emits INTERNAL_ERROR
- ❌ Service knows it's token-related, handler thinks it's unexpected
- ❌ IllegalArgumentException not properly handled (should be caught specifically)
- ❌ Observability system sees conflicting error types

---

## Recommendations for Simplification

### ❌ Remove (DUPLICATE)

#### 1. Remove EventLogger from Services
**Remove these injections:**
```java
// From KeycloakUserProvisioningService, RefreshTokenService, OAuth2TokenService:
private final EventLogger eventLogger;  ← REMOVE
private final ExceptionEventBuilder exceptionEventBuilder;  ← REMOVE
```

**Remove these methods:**
```java
// From KeycloakUserProvisioningService:
private void emitKeycloakErrorEvent(...)  ← REMOVE
    eventLogger.logExceptionEvent(...);  ← This happens in GlobalExceptionHandler

// From RefreshTokenService:
private void emitRefreshTokenEvent(...)  ← REMOVE
    eventLogger.logExceptionEvent(...);  ← This happens in GlobalExceptionHandler

// From OAuth2TokenService:
private void emitJwtValidationEvent(...)  ← REMOVE
    eventLogger.logExceptionEvent(...);  ← This happens in GlobalExceptionHandler
```

**Remove these event emission calls:**
```java
// From KeycloakUserProvisioningService.createUser():
emitKeycloakErrorEvent(ex, "Keycloak user creation failed", username, legacyUserId, startTime);  ← REMOVE

// From KeycloakUserProvisioningService.fetchAdminToken():
emitKeycloakErrorEvent(e, "Failed to obtain Keycloak admin token", "system", null, startTime);  ← REMOVE

// From RefreshTokenService.validateAndRotate():
emitRefreshTokenEvent(ex, "INVALID_TOKEN", "Refresh token not found", startTime);  ← REMOVE
emitRefreshTokenEvent(ex, "REVOKED_TOKEN", "Refresh token revoked", startTime);  ← REMOVE
emitRefreshTokenEvent(ex, "EXPIRED_TOKEN", "Refresh token expired", startTime);  ← REMOVE

// From OAuth2TokenService.extractUsernameFromValidatedIdToken():
emitJwtValidationEvent(e, "JWT_SIGNATURE_VALIDATION_FAILED", executionTime);  ← REMOVE
emitJwtValidationEvent(e, "JWT_DECODE_ERROR", executionTime);  ← REMOVE
```

#### 2. Optional: Remove ExceptionEventBuilder
Since GlobalExceptionHandler doesn't use ExceptionEventBuilder (it builds events directly), this class becomes unused:
```java
// customer-service/src/main/java/com/claimassist/platform/customer_service/exception/ExceptionEventBuilder.java
DELETE THIS FILE (UNUSED)
```

**Why it's unused:**
- GlobalExceptionHandler has its own `buildExceptionEventDetails()` method
- GlobalExceptionHandler has its own `sanitizeMessage()` method
- GlobalExceptionHandler has its own `getRootCause()` method
- No other code uses ExceptionEventBuilder

---

### ✅ Keep (NECESSARY)

#### 1. Keep GlobalExceptionHandler
- **Why**: It's the single point of exception handling
- **What it does**: Catches all exceptions and emits ONE event with full context
- **Covers**: All HTTP-layer exceptions
- **Benefits**: Centralized, consistent, complete

#### 2. Keep service-level exception throwing (but not event emission)
```java
// ✅ OK: Services throw appropriate exceptions
throw new ServiceUnavailableException("Unable to provision identity");
throw new IllegalArgumentException("Invalid refresh token");

// ❌ NOT OK: Services emit events (GlobalExceptionHandler will do this)
eventLogger.logExceptionEvent(...);  ← REMOVE
```

---

### 🔄 Refactor (IMPROVE)

#### 1. Add Specific Exception Handlers for Service Errors
Currently, service exceptions like `IllegalArgumentException` fall through to the catch-all handler which returns HTTP 500. Instead:

```java
// Add to GlobalExceptionHandler:
@ExceptionHandler(IllegalArgumentException.class)
public ResponseEntity<EnhancedApiError> handleIllegalArgument(IllegalArgumentException ex) {
    long startTime = System.currentTimeMillis();
    
    // Determine appropriate HTTP status based on exception message
    HttpStatus status = determineHttpStatus(ex);
    String errorCode = determineErrorCode(ex);
    
    EnhancedApiError error = buildEnhancedError(status, errorCode, ex.getMessage());
    emitExceptionEvent(ex, status, errorCode, startTime);
    
    log.warn("Invalid argument: {}", error.message());
    return ResponseEntity.status(error.status()).body(error);
}

private HttpStatus determineHttpStatus(IllegalArgumentException ex) {
    String msg = ex.getMessage();
    if (msg != null && msg.contains("refresh token")) return HttpStatus.UNAUTHORIZED;
    return HttpStatus.BAD_REQUEST;
}

private String determineErrorCode(IllegalArgumentException ex) {
    String msg = ex.getMessage();
    if (msg == null) return "INVALID_REQUEST";
    if (msg.contains("Invalid refresh")) return "INVALID_TOKEN";
    if (msg.contains("revoked")) return "REVOKED_TOKEN";
    if (msg.contains("expired")) return "EXPIRED_TOKEN";
    return "INVALID_REQUEST";
}
```

#### 2. Keep Custom Error Codes but Move to Handler
Instead of service-level event emission with KEYCLOAK_INTEGRATION_ERROR:
- Service: throws `ServiceUnavailableException("Keycloak failed: ...")`
- Handler: Extracts error code from exception message or type
- Result: Single event with appropriate error code

---

## Comparison: Before vs After

### BEFORE (Current - with Duplication)

```
Keycloak Failure
├── SERVICE LAYER
│   └── KeycloakUserProvisioningService.createUser()
│       ├── Tries to create user
│       ├── Fails with RestClientException
│       ├── EVENT #1: "KEYCLOAK_INTEGRATION_ERROR" emitted ← EVENT
│       └── Throws ServiceUnavailableException
│
└── HTTP LAYER
    └── GlobalExceptionHandler.handleServiceUnavailable()
        ├── Catches ServiceUnavailableException
        ├── EVENT #2: "SERVICE_UNAVAILABLE" emitted ← DUPLICATE EVENT!
        └── Returns HTTP 503 response

RESULT: 2 events, different error codes, confusing observability
```

### AFTER (Recommended - Simplified)

```
Keycloak Failure
├── SERVICE LAYER
│   └── KeycloakUserProvisioningService.createUser()
│       ├── Tries to create user
│       ├── Fails with RestClientException
│       └── Throws ServiceUnavailableException("Keycloak failed: ...")
│
└── HTTP LAYER
    └── GlobalExceptionHandler.handleServiceUnavailable()
        ├── Catches ServiceUnavailableException
        ├── Extracts error code from message/exception type
        ├── EVENT: Structured event emitted ← SINGLE EVENT
        └── Returns HTTP 503 response

RESULT: 1 event, correct error code, clear observability
```

---

## Summary of Duplicates Found

| Duplicate Type | Location | Issue | Impact |
|---|---|---|---|
| **Event Emission #1** | KeycloakUserProvisioningService.createUser() + GlobalExceptionHandler | Exception logged twice | 2 events for 1 error |
| **Event Emission #2** | KeycloakUserProvisioningService.fetchAdminToken() + GlobalExceptionHandler | Exception logged twice | 2 events for 1 error |
| **Event Emission #3** | RefreshTokenService.validateAndRotate() + GlobalExceptionHandler | Exception logged twice with wrong error code | 2 events, conflicting info |
| **Event Emission #4** | OAuth2TokenService.extractUsernameFromValidatedIdToken() | Service emits but exception not thrown (inconsistent) | Inconsistent error handling |
| **Unused Class** | ExceptionEventBuilder.java | Only used in services for event building | 138 lines of unused code |
| **Duplicate Logic** | GlobalExceptionHandler vs ExceptionEventBuilder | Both build event details, both sanitize | Code duplication |

---

## Impact Assessment

### If Duplicates Removed ✅

**Benefits:**
- ✅ Single event per exception (cleaner observability)
- ✅ Consistent error codes
- ✅ Reduced log noise
- ✅ Simpler architecture
- ✅ Easier debugging (one source of truth)
- ✅ Reduced code (~400 lines)
- ✅ Better performance (fewer event emissions)

**No Downside:**
- Global exception handler still captures all exceptions
- Event logging still happens (same as before, just once)
- Error codes can still be specific (via enhanced handler mapping)
- User context still included (via GlobalExceptionHandler)
- Observability not reduced (actually improved by having clean events)

### If Duplicates Remain ❌

**Problems:**
- ❌ Every exception generates 2+ events
- ❌ Observability dashboard counts doubled
- ❌ Error codes inconsistent across layers
- ❌ Root cause analysis harder with duplicate logging
- ❌ Correlating events becomes difficult
- ❌ Performance impact from extra event emission
- ❌ Maintenance burden increases

---

## Conclusion

**The current implementation is NOT flawed in concept, but contains UNNECESSARY DUPLICATION that should be eliminated.**

The requirements were to:
1. ✅ Create GlobalExceptionHandler → DONE
2. ✅ Reuse EventLogger → DONE (in GlobalExceptionHandler)
3. ✅ Emit structured events → DONE (in GlobalExceptionHandler)
4. ✅ Include required fields → DONE (in GlobalExceptionHandler)
5. ✅ Never log sensitive data → DONE (in both locations)

**However**: Requirements don't mandate service-level event emission. That's redundant with GlobalExceptionHandler.

**Recommended Action**: 
Remove EventLogger and ExceptionEventBuilder from services, keeping GlobalExceptionHandler as the single point of exception event emission. This eliminates duplication while maintaining all observability benefits.


