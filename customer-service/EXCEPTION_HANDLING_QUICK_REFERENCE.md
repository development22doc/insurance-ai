# Exception Handling Quick Reference Guide

## For Customer-Service Developers

### Exception Event Example

When an exception occurs, here's what gets logged:

```java
// In GlobalExceptionHandler.handleBadRequest():
try {
    // ... business logic ...
    throw new BadRequestException("Invalid input");
} catch (BadRequestException ex) {
    // Automatically:
    // 1. Creates EnhancedApiError response
    // 2. Emits exception event via EventLogger
    // 3. Includes correlation ID, trace ID, execution time
    // 4. Sanitizes error message
    // 5. Returns HTTP 400 with structured error
}

// Event emitted to event.logger:
// {"correlationId": "xyz-123", "traceId": "trace-456", ...}
```

### How to Handle New Exception Types

#### 1. If exception is already a common-lib type:
```java
// BadRequestException, ResourceNotFoundException, etc.
// are already handled by GlobalExceptionHandler
// No additional code needed!
throw new BadRequestException("Customer already exists");
```

#### 2. If exception needs service-specific handling:
```java
// Option A: Use common-lib exception
throw new ResourceNotFoundException("Policy", policyId.toString());

// Option B: Let exception bubble up to catch-all handler
throw new RuntimeException("Unexpected error during processing");

// Option C: Extend common-lib exception (if needed)
// Create custom exception extending common-lib base
// Then add handler in GlobalExceptionHandler
```

### Adding a New Exception Handler

If you need to handle a new exception type specifically:

```java
// 1. Add method to GlobalExceptionHandler:
@ExceptionHandler(YourCustomException.class)
public ResponseEntity<EnhancedApiError> handleYourException(YourCustomException ex) {
    long startTime = System.currentTimeMillis();
    EnhancedApiError error = buildEnhancedError(
            HttpStatus.BAD_REQUEST,
            "YOUR_ERROR_CODE",
            ex.getMessage() != null ? ex.getMessage() : "Default message"
    );
    
    emitExceptionEvent(ex, HttpStatus.BAD_REQUEST, "YOUR_ERROR_CODE", startTime);
    log.warn("Your custom error: {}", error.message());
    
    return ResponseEntity.status(error.status()).body(error);
}

// 2. Exception event is automatically emitted with all required fields
// 3. Error response includes correlation ID, trace ID, timestamp, etc.
```

### Adding Service-Level Event Logging

If you need to emit events from a service (not exception handler):

```java
// In your service class:
@Service
@RequiredArgsConstructor
public class YourService {
    
    private final EventLogger eventLogger;
    private final ExceptionEventBuilder exceptionEventBuilder;
    
    public void riskyOperation() {
        long startTime = System.currentTimeMillis();
        try {
            // ... do something risky ...
        } catch (Exception ex) {
            long executionTime = System.currentTimeMillis() - startTime;
            
            // Build event details
            Map<String, Object> details = exceptionEventBuilder.buildBaseEvent(
                    ex.getClass().getSimpleName(),
                    "YOUR_ERROR_CODE",
                    "Operation failed",
                    ExceptionEventBuilder.extractRootCause(ex)
            );
            
            // Add context
            exceptionEventBuilder.addPerformanceMetrics(details, executionTime);
            
            // Emit event
            eventLogger.logExceptionEvent("customer-service", "customer-service", details);
            
            // Then throw or handle as needed
            throw new ServiceUnavailableException("Operation failed");
        }
    }
}
```

### Sensitive Data - What NOT to Log

❌ **Never include in exception messages or events:**
- JWT tokens: `Bearer eyJhbGc...`
- Authorization headers: `Authorization: Bearer xyz`
- Passwords: `password=secret`
- API keys: `api_key=123`
- Refresh tokens: `refresh_token=abc`
- Personal info beyond username: emails, phone numbers, addresses

✅ **Safe to include:**
- Exception class name
- Error code
- Request URI
- HTTP method
- User username (for debugging)
- User ID
- Correlation/Trace IDs
- Execution time
- Root cause message (generic)

### How Sensitive Data is Handled

The system automatically sanitizes:

```java
// Input message:
String message = "Failed to authenticate user john@example.com with token Bearer eyJhbGc...";

// After sanitization:
String sanitized = "Failed to authenticate user john@example.com with token ***JWT_REDACTED***";

// Patterns sanitized:
// - JWT tokens → ***JWT_REDACTED***
// - Authorization → authorization=***
// - password → password=***
// - secret/token/refresh → $1=***
// - api_key → api_key=***
```

### Accessing Exception Events in Logs

Exception events are logged via SLF4J with logger name `event.logger`:

```xml
<!-- In logback configuration: -->
<appender name="EVENT_FILE" class="ch.qos.logback.core.FileAppender">
    <file>logs/events.log</file>
    <encoder>
        <pattern>%d{ISO8601} [%correlationId] %msg%n</pattern>
    </encoder>
</appender>

<logger name="event.logger" level="INFO" additivity="false">
    <appender-ref ref="EVENT_FILE"/>
</logger>
```

### Understanding EnhancedApiError Response

When an error occurs, client receives:

```json
{
  "timestamp": "2026-08-07T10:30:45.123Z",
  "status": 400,
  "errorCode": "BAD_REQUEST",
  "message": "A customer already exists with username: john@example.com",
  "correlationId": "abc-123-def-456",
  "traceId": "trace-xyz-789",
  "spanId": "span-001",
  "path": "/auth/signup",
  "method": "POST"
}
```

**Fields:**
- `timestamp`: When error occurred (ISO 8601)
- `status`: HTTP status code
- `errorCode`: Machine-readable error code
- `message`: Human-readable message (sanitized)
- `correlationId`: For tracing across services
- `traceId`: For distributed tracing
- `spanId`: For span correlation
- `path`: Request endpoint
- `method`: HTTP method

### Debugging Exception Events

To find related logs when user reports error:

```bash
# Search logs by correlation ID (from error response)
grep "abc-123-def-456" logs/application.log

# Search by error code
grep "BAD_REQUEST" logs/events.log

# Search by user
grep "username=john@example.com" logs/events.log

# Search by error type
grep "exceptionType.*Exception" logs/events.log
```

### Performance Considerations

Exception events include execution time:

```json
{
  "executionTimeMs": 45,
  "context": "keycloak_integration"
}
```

Use this to:
- Identify slow operations
- Monitor Keycloak integration latency
- Detect performance regressions
- Analyze error path performance

### Common Exception Codes

| Code | HTTP Status | Meaning | Example |
|------|---|---|---|
| BAD_REQUEST | 400 | Invalid input | Duplicate customer |
| AUTHENTICATION_FAILED | 401 | Auth error | Invalid credentials |
| MISSING_CREDENTIALS | 401 | No auth provided | Missing JWT |
| ACCESS_DENIED | 403 | Insufficient permissions | User not authorized |
| RESOURCE_NOT_FOUND | 404 | Resource missing | Policy not found |
| STATE_CONFLICT | 409 | Invalid state transition | Can't cancel active policy |
| KEYCLOAK_INTEGRATION_ERROR | 503 | Identity provider failure | Can't reach Keycloak |
| SERVICE_UNAVAILABLE | 503 | Service down | Database unavailable |
| INTERNAL_ERROR | 500 | Unexpected error | Null pointer exception |

### Testing Exception Handling

```java
@Test
void testExceptionEventEmitted() {
    // When: BadRequestException is thrown
    assertThrows(BadRequestException.class, () -> {
        throw new BadRequestException("Test error");
    });
    
    // Then: Verify EventLogger.logExceptionEvent() was called
    verify(eventLogger).logExceptionEvent(
            eq("customer-service"),
            eq("customer-service"),
            argThat(details -> 
                details.containsKey("correlationId") &&
                details.containsKey("exceptionType") &&
                details.containsKey("errorCode")
            )
    );
}

@Test
void testSensitiveDataRedaction() {
    // When: Exception message contains JWT
    String message = "Failed with token Bearer eyJhbGc...";
    
    // Then: Token should be redacted
    String sanitized = exceptionEventBuilder.sanitizeMessage(message);
    assertFalse(sanitized.contains("eyJhbGc"));
    assertTrue(sanitized.contains("***JWT_REDACTED***"));
}
```

### Troubleshooting

**Q: My exception events aren't appearing in logs**
- Check if event.logger appender is configured
- Verify SLF4J is properly set up
- Check EventLogger bean is being injected
- Look for errors in application startup logs

**Q: Sensitive data is appearing in logs**
- Add the pattern to ExceptionEventBuilder.sanitizeMessage()
- Verify GlobalExceptionHandler is calling buildEnhancedError()
- Check service methods are using ExceptionEventBuilder
- Review all catch blocks for message logging

**Q: EventLogger bean not found error**
- Ensure common-lib is in classpath
- Verify Spring configuration includes component scanning
- Check if ObservabilityAutoConfiguration is being loaded
- Rebuild project (IDE caching issue)

**Q: Correlation ID is null in events**
- Verify CorrelationIdFilter is registered (should be auto-registered)
- Check if request goes through servlet filter chain
- Look for MDC setup issues in logs
- Ensure X-Correlation-Id header is present in requests

---

**Last Updated**: August 7, 2026
**Version**: 1.0

