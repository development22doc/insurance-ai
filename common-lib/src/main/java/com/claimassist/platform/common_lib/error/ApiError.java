package com.claimassist.platform.common_lib.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

/**
 * correlationId is pulled from MDC (set by CorrelationIdFilter) so every
 * error body a client sees can be handed back to support/ops and grepped
 * straight to the matching log lines - no separate "what was your
 * X-Correlation-Id" round trip.
 */
public record ApiError(
        HttpStatus status,
        String message,
        Instant timestamp,
        @JsonInclude(JsonInclude.Include.NON_NULL) String correlationId,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<ApiFieldError> errors
) {
    public ApiError(HttpStatus status, String message) {
        this(status, message, Instant.now(), MDC.get("correlationId"), null);
    }

    public ApiError(HttpStatus status, String message, List<ApiFieldError> errors) {
        this(status, message, Instant.now(), MDC.get("correlationId"), errors);
    }
}

record ApiFieldError(String field, String message) {}
