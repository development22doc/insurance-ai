# Common-lib Logging & Tracing Guide

This module provides reusable logging, correlation-id and tracing helpers for all microservices in the ClaimAssist platform.

Overview
- Correlation ID: `X-Correlation-Id` header is read from incoming requests or generated if absent. Stored in MDC key `correlationId` and returned on responses.
- Distributed Tracing: Micrometer Tracing integration. Trace and Span ids are placed into MDC keys `traceId` and `spanId` when a tracer implementation is available.
- Interceptors: RestTemplate, WebClient and Feign interceptors add the correlation id and trace id to outgoing requests.
- AOP: Execution time and exception logging aspects for controllers/services.
- Logback: `logback-spring.xml` provided in the module configures structured JSON logs and includes MDC fields.

Files and purpose
- `CorrelationIdFilter` - Servlet filter that reads/creates a correlation id and request id, places them in MDC, and ensures they are returned in response headers. Also sets trace/span into MDC when Micrometer Tracer is available.
- `LoggingConstants` - Central constants for header and MDC key names.
- `MDCUtility` - Small utility for putting/clearing MDC values safely.
- `LoggingHelper` - Helper functions for masking sensitive values before logging.
- `RestTemplateCorrelationInterceptor` - Propagates correlation and trace ids on RestTemplate calls.
- `WebClientCorrelationFilter` - ExchangeFilterFunction to propagate headers for WebClient.
- `FeignCorrelationRequestInterceptor` - Feign RequestInterceptor for propagation.
- `ExecutionTimeAspect` / `ExceptionLoggingAspect` - AOP aspects for execution time and exception logging.
- `ExceptionLoggingUtil`, `PerformanceLoggingUtil` - Helpers used by the aspects.
- `ObservabilityAutoConfiguration` - Spring configuration that registers the filter, interceptors and aspects automatically when this module is on the classpath.
 - `ObservabilityAutoConfiguration` - Spring configuration that registers the filter, interceptors and aspects automatically when this module is on the classpath.
 - `event` package - contains a generic `EventLogger` API and a `DefaultEventLogger` implementation for emitting structured events (Request, Business, Security, Database, Kafka, Performance, Exception).

How to integrate (downstream services)
1. Add `common-lib` as a dependency in your service's `pom.xml`.
2. Ensure Micrometer Tracing implementation is on the classpath (for example `micrometer-tracing-bridge-brave`). If present, traceId and spanId will automatically be set in MDC and propagated.
3. No code changes are required to get correlation id support. The `CorrelationIdFilter` is registered automatically by `ObservabilityAutoConfiguration`.
4. For `RestTemplate` and `WebClient` usage:
   - RestTemplate instances created by Spring will be auto-customized and include the correlation interceptor.
   - For WebClient, obtain the `ExchangeFilterFunction` bean named `webClientCorrelationFilter` and add it to your WebClient builder:

```java
@Bean
public WebClient webClient(WebClient.Builder builder, ExchangeFilterFunction webClientCorrelationFilter) {
    return builder.filter(webClientCorrelationFilter).build();
}
```

5. Feign clients will pick up the `RequestInterceptor` bean and include headers automatically.
6. Use MDC keys in your log pattern if you have a custom logback configuration; the module's `logback-spring.xml` already includes them.

EventLogger usage
-----------------
The module exposes a Spring bean implementing `com.claimassist.platform.common_lib.observability.event.EventLogger`.

Inject and use it like this:

```java
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import org.springframework.stereotype.Service;

@Service
public class MyService {
    private final EventLogger eventLogger;

    public MyService(EventLogger eventLogger) {
        this.eventLogger = eventLogger;
    }

    public void process() {
        eventLogger.logBusinessEvent("claims-service", "claims-app", Map.of("action", "claim.created", "claimId", 12345));
    }
}
```

All events will include canonical fields: eventType, service, application, correlationId, traceId, spanId, timestamp, duration (when provided) and logger. Details are available in the `details` object.

Log categories & constants
--------------------------
This module exposes a centralized set of log category constants in `com.claimassist.platform.common_lib.observability.LogCategories`.

Key constants:
- `LogCategories.LOGGER_REQUEST` => "event.request"
- `LogCategories.LOGGER_BUSINESS` => "event.business"
- `LogCategories.LOGGER_SECURITY` => "event.security"
- `LogCategories.LOGGER_DATABASE` => "event.database"
- `LogCategories.LOGGER_CACHE` => "event.cache"
- `LogCategories.LOGGER_KAFKA` => "event.kafka"
- `LogCategories.LOGGER_PERFORMANCE` => "event.performance"
- `LogCategories.LOGGER_EXCEPTION` => "event.exception"

These constants are intended for downstream services to use when emitting logs directly or when configuring logback to route event categories to different appenders/indexes. The `DefaultEventLogger` will automatically route events to the appropriate logger name by EventType, but services can also use the constants directly:

```java
import static com.claimassist.platform.common_lib.observability.LogCategories.LOGGER_BUSINESS;
private static final Logger business = LoggerFactory.getLogger(LOGGER_BUSINESS);
business.info("{\"eventType\":\"BUSINESS\",\"service\":\"claims-service\", ...}");
```

Using these constants ensures logs are consistent across services and simplifies centralized routing/indexing.

Security & privacy
- The library intentionally masks Authorization headers, tokens, passwords, secrets and other likely-sensitive values when used by the in-module helpers.
- Do not log raw request bodies that contain PII or secrets. The aspects and utilities avoid logging request payloads.

Extending
- If you need additional propagation headers, extend the interceptors or create custom filters that call `MDC.get("correlationId")` and `MDC.get("traceId")`.

Contact
- For changes to the logging contract, coordinate with the platform team so all services continue to emit compatible structured logs.

