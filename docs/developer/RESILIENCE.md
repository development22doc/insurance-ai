# Resilience Patterns

**Version:** 1.0.0  
**Last Updated:** August 4, 2026

---

## Overview

Resilience patterns protect the system from cascading failures through circuit breakers, retries, timeouts, bulkheads, and rate limiting (using Resilience4j).

---

## Circuit Breaker Pattern

### Purpose
Prevents cascading failures by stopping requests to failing services.

### States

```
CLOSED (Normal)
├─ Requests pass through
├─ Failures counted
└─ If failure rate > threshold → OPEN

OPEN (Failing)
├─ Requests immediately rejected
├─ Wait for timeout (60s)
└─ If timeout elapsed → HALF_OPEN

HALF_OPEN (Testing)
├─ Limited requests allowed
├─ If successful → CLOSED
└─ If failure → OPEN (reset wait)
```

### Configuration

```yaml
resilience4j:
  circuitbreaker:
    instances:
      customer-service-cb:
        register-health-indicator: true
        sliding-window-size: 100          # Track last 100 calls
        failure-rate-threshold: 50        # > 50% failures → OPEN
        wait-duration-in-open-state: 60000 # Wait 60s in OPEN state
        permitted-calls-in-half-open-state: 10
        automatic-transition-from-open-to-half-open-enabled: true
        slow-call-rate-threshold: 100
        slow-call-duration-threshold: 2s
        
        # Exceptions to ignore
        ignored-exceptions:
          - java.io.IOException
          
        # Exceptions that trigger failure
        recorded-exceptions:
          - java.util.concurrent.TimeoutException
          - java.net.ConnectException
```

### Implementation

```java
@Service
@Slf4j
public class CustomerServiceClient {
    
    private final RestTemplate restTemplate;
    private final CircuitBreakerFactory circuitBreakerFactory;
    
    public PolicyDto getPolicy(String policyId) {
        // Wrap call in circuit breaker
        return circuitBreakerFactory
            .create("customer-service-cb")
            .run(() -> {
                try {
                    ResponseEntity<PolicyDto> response = restTemplate
                        .getForEntity(
                            "http://customer-service/api/policies/" + policyId,
                            PolicyDto.class);
                    return response.getBody();
                } catch (RestClientException e) {
                    log.error("Error calling customer service", e);
                    throw e;
                }
            }, throwable -> {
                // Fallback: Return null or cached value
                log.warn("Circuit breaker fallback for policyId: {}", policyId);
                return null; // Or fetch from cache
            });
    }
}

@Service
public class PolicyService {
    
    // Or use @CircuitBreaker annotation
    @CircuitBreaker(name = "customer-service-cb")
    public PolicyDto getPolicyWithAnnotation(String policyId) {
        // Call customer service
        return customerServiceClient.getPolicy(policyId);
    }
}
```

### Metrics

```
resilience4j_circuitbreaker_calls_total
├── Dimensions: state=CLOSED|OPEN|HALF_OPEN, outcome=SUCCESS|FAILURE
├── Example: Total calls to customer service

resilience4j_circuitbreaker_state
├── Current state (0=CLOSED, 1=OPEN, 2=HALF_OPEN, 3=DISABLED)
└── Used for alerting on state changes
```

---

## Retry Pattern

### Purpose
Automatically retry failed requests with backoff strategy.

### Configuration

```yaml
resilience4j:
  retry:
    instances:
      customer-service-retry:
        max-attempts: 3              # Max 3 attempts
        wait-duration: 1000          # Wait 1 second
        interval-function: exponential # Exponential backoff
        exponential-backoff-multiplier: 2.0
        retry-exceptions:
          - java.util.concurrent.TimeoutException
          - java.net.ConnectException
        ignore-exceptions:
          - com.claimassist.NotFoundException
```

### Implementation

```java
@Retry(name = "customer-service-retry")
public PolicyDto getPolicy(String policyId) {
    return customerServiceClient.getPolicy(policyId);
}

// Retry sequence:
// Attempt 1: Immediate failure
// Attempt 2: Wait 1s, retry
// Attempt 3: Wait 2s, retry
// If still failing: Throw exception
```

### Metrics

```
resilience4j_retry_calls_total
├── outcome=SUCCESS: Succeeded on first try
├── outcome=SUCCESS_WITH_RETRY: Succeeded after retries
└── outcome=FAILURE: Failed after all retries
```

---

## Timeout Pattern

### Purpose
Prevent requests from hanging indefinitely.

### Configuration

```yaml
resilience4j:
  timelimiter:
    instances:
      customer-service-timeout:
        timeout-duration: 5s         # 5 second timeout
        cancel-running-future: true  # Cancel if still running
```

### Implementation

```java
@TimeLimiter(name = "customer-service-timeout")
@CircuitBreaker(name = "customer-service-cb")
@Retry(name = "customer-service-retry")
public CompletableFuture<PolicyDto> getPolicyAsync(String policyId) {
    return CompletableFuture.supplyAsync(() -> 
        customerServiceClient.getPolicy(policyId)
    );
}
```

### Timeout Chain

```
Request starts
    ↓ (set timeout: 5s)
Waiting for response
    ├─ Response arrives (< 5s) → Success
    └─ Timeout reached (≥ 5s) → TimeoutException
        ↓
        If retryable → Retry with new timeout
        └─ If retries exhausted → Fail
```

---

## Bulkhead Pattern

### Purpose
Isolate resources to prevent one service from exhausting all resources.

### Configuration

```yaml
resilience4j:
  bulkhead:
    instances:
      customer-service-bulkhead:
        max-concurrent-calls: 10     # Max 10 concurrent calls
        max-wait-duration: 2s        # Max wait for thread
        core-thread-pool-size: 5
        max-thread-pool-size: 10
        queue-capacity: 20
```

### Implementation

```java
@Bulkhead(name = "customer-service-bulkhead")
public PolicyDto getPolicy(String policyId) {
    return customerServiceClient.getPolicy(policyId);
}

// If 10 calls already in progress:
// - New call waits up to 2 seconds for a thread
// - If queue full (20 waiting), request rejected
```

### Thread Pool Behavior

```
Thread Pool: [  Thread-1  |  Thread-2  | ... | Thread-10  ]
                    ↓ Active call
                    ↓ Waiting in queue
                    ↓ New request arrives
                    
If queue size = 20:
    New request: BulkheadFullException
```

---

## Rate Limiter Pattern

### Purpose
Control request rate to prevent overload.

### Configuration

```yaml
resilience4j:
  ratelimiter:
    instances:
      claim-creation-limiter:
        register-health-indicator: false
        limit-refresh-period: 1m     # Refresh every minute
        limit-for-period: 100        # Allow 100 requests per minute
        timeout-duration: 5s         # Wait up to 5s for available permit
        event-consumer-buffer-size: 100
```

### Implementation

```java
@RateLimiter(name = "claim-creation-limiter")
@PostMapping("/claims")
public ResponseEntity<ClaimResponse> createClaim(
    @RequestBody CreateClaimRequest request) {
    
    // Max 100 claims can be created per minute
    return claimService.createClaim(request);
}

// Request 101: RequestNotPermitted (rate limit exceeded)
```

### Token Bucket Algorithm

```
Bucket capacity: 100 tokens
Token generation: 1 token per 600ms (100/min)

Request arrives:
├─ If tokens available → Take 1 token → Process
└─ If tokens exhausted → Wait or reject
```

---

## Combining Patterns

### Optimal Sequence

```
Request
    ↓
Rate Limiter (protect from overload)
    ↓
Timeout (prevent hanging)
    ↓
Bulkhead (thread pool isolation)
    ↓
Circuit Breaker
    ├─ CLOSED → Call service
    │    ├─ Success → Return
    │    └─ Failure → Check if retryable
    │
    ├─ OPEN → Fallback immediately
    │
    └─ HALF_OPEN → Limited calls
       ├─ Success → CLOSED
       └─ Failure → OPEN

Retry (if retryable and attempts remain)
    ├─ Backoff wait
    └─ Retry request (up to max-attempts)

Fallback (if all attempts failed)
    ├─ Return cached value
    ├─ Return default value
    └─ Throw exception
```

### Configuration Stack

```yaml
@Service
public class ResilientCustomerServiceClient {
    
    @RateLimiter(name = "customer-service-limiter")
    @TimeLimiter(name = "customer-service-timeout")
    @CircuitBreaker(name = "customer-service-cb")
    @Retry(name = "customer-service-retry")
    @Bulkhead(name = "customer-service-bulkhead")
    public PolicyDto getPolicy(String policyId) {
        return customerServiceClient.getPolicy(policyId);
    }
    
    // Default fallback
    private PolicyDto getDefaultPolicy(String policyId) {
        return new PolicyDto(policyId, "DEFAULT", 0);
    }
}
```

---

## Fallback Strategies

### Cached Fallback

```java
@Retry(name = "retry")
@CircuitBreaker(name = "circuitbreaker", fallbackMethod = "cachedFallback")
public PolicyDto getPolicy(String policyId) {
    return customerServiceClient.getPolicy(policyId);
}

// Fallback uses cached data
private PolicyDto cachedFallback(String policyId, Exception ex) {
    log.warn("Circuit breaker fallback for policyId: {}", policyId);
    return redisTemplate.opsForValue().get("policy:" + policyId);
}
```

### Default Fallback

```java
private PolicyDto defaultFallback(String policyId, Exception ex) {
    log.error("Returning default policy for {}", policyId, ex);
    return new PolicyDto(
        policyId,
        "UNKNOWN",
        BigDecimal.ZERO,
        "INACTIVE"
    );
}
```

### Null Fallback

```java
private PolicyDto nullFallback(String policyId, Exception ex) {
    log.warn("Could not retrieve policy for {}", policyId);
    return null; // Client must handle null
}
```

---

## Monitoring Resilience

### Health Indicators

```java
// Automatically exposed at /actuator/health
resilience4j_circuitbreaker_health
resilience4j_retry_health
resilience4j_bulkhead_health
resilience4j_ratelimiter_health
resilience4j_timelimiter_health
```

### Actuator Endpoint

```
GET /actuator/resilience4j/circuitbreakers
GET /actuator/resilience4j/retries
GET /actuator/resilience4j/bulkheads
GET /actuator/resilience4j/ratelimiters
GET /actuator/resilience4j/timelimiters
```

### Custom Metrics

```java
@Component
public class ResilienceMetricsPublisher {
    
    @Scheduled(fixedDelay = 30000)
    public void publishMetrics() {
        MeterRegistry registry = meterRegistry;
        
        // Circuit breaker state
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("customer-service-cb");
        registry.gauge("resilience.circuitbreaker.state", 
            () -> cb.getState().getOrder());
        
        // Failed attempts
        registry.gauge("resilience.circuitbreaker.failures", 
            () -> cb.getMetrics().getNumberOfFailedCalls());
        
        // Successful attempts
        registry.gauge("resilience.circuitbreaker.successes", 
            () -> cb.getMetrics().getNumberOfSuccessfulCalls());
    }
}
```

---

## Best Practices

1. **Circuit Breaker First:** Detect and stop calling failing services
2. **Retry with Backoff:** Don't retry immediately, use exponential backoff
3. **Timeout Always:** Prevent indefinite waits
4. **Bulkhead Isolation:** Protect thread pools by service
5. **Rate Limit Critical:** Prevent overload on critical paths
6. **Fallback Strategy:** Plan graceful degradation
7. **Monitor State:** Track circuit breaker state changes
8. **Test Failure:** Test all failure scenarios

---


