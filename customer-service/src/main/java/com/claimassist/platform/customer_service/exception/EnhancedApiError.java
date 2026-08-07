package com.claimassist.platform.customer_service.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.Instant;

/**
 * Enhanced error response that extends the basic ApiError from common-lib.
 *
 * Includes all required fields for enterprise-grade exception handling:
 * - timestamp: When the error occurred
 * - status: HTTP status code
 * - errorCode: Application-specific error code for clients to handle programmatically
 * - message: Human-readable error message
 * - correlationId: For tracing requests across services
 * - traceId: For distributed tracing
 * - spanId: For span correlation in distributed tracing
 * - path: The endpoint that was called
 * - method: The HTTP method used
 *
 * Never includes sensitive information:
 * - JWT tokens
 * - Authorization headers
 * - Passwords
 * - Refresh tokens
 * - PII
 */
@Builder
public record EnhancedApiError(
        Instant timestamp,
        int status,
        String errorCode,
        String message,
        @JsonInclude(JsonInclude.Include.NON_NULL) String correlationId,
        @JsonInclude(JsonInclude.Include.NON_NULL) String traceId,
        @JsonInclude(JsonInclude.Include.NON_NULL) String spanId,
        @JsonInclude(JsonInclude.Include.NON_NULL) String path,
        @JsonInclude(JsonInclude.Include.NON_NULL) String method
) {
}

