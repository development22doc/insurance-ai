package com.claimassist.platform.common_lib.error;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Thrown by a Resilience4j circuit-breaker fallback (or a retry-exhausted call)
 * when a downstream dependency (customer-service, claims-service, the LLM
 * provider, Stripe) is unreachable. Maps to HTTP 503 - "this is transient,
 * retrying shortly will likely work" - distinct from a 500 (our bug) or a 400
 * (caller's bad input).
 */
@Getter
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class ServiceUnavailableException extends RuntimeException {
    String message;
}
