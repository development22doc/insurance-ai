package com.claimassist.platform.api_gateway.error;

import com.claimassist.platform.common_lib.observability.ExceptionLoggingUtil;
import com.claimassist.platform.common_lib.observability.LoggingConstants;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.autoconfigure.web.reactive.error.AbstractErrorWebExceptionHandler;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.ConnectException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.Optional;

@Component
@Order(-2)
@Slf4j
public class GatewayExceptionHandler extends AbstractErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;
    private final EventLogger eventLogger;

    public GatewayExceptionHandler(
            ErrorAttributes errorAttributes,
            WebProperties webProperties,
            ApplicationContext applicationContext,
            ServerCodecConfigurer codecConfigurer,
            ObjectMapper objectMapper,
            EventLogger eventLogger) {

        super(errorAttributes, webProperties.getResources(), applicationContext);

        this.objectMapper = objectMapper;
        this.eventLogger = eventLogger;

        setMessageReaders(codecConfigurer.getReaders());
        setMessageWriters(codecConfigurer.getWriters());
    }

    @Override
    protected RouterFunction<ServerResponse> getRoutingFunction(ErrorAttributes errorAttributes) {
        return RouterFunctions.route(RequestPredicates.all(), this::renderError);
    }

    private Mono<ServerResponse> renderError(ServerRequest request) {

        Throwable error = getError(request);
        HttpStatus status = resolveStatus(error);
        String message = resolveMessage(error, status);
        String correlationId = Optional.ofNullable(request.headers().firstHeader(LoggingConstants.CORRELATION_ID_HEADER))
                .orElseGet(() -> MDC.get(LoggingConstants.MDC_CORRELATION_ID));

        // Emit structured exception event via common-lib's EventLogger
        Map<String, Object> exceptionDetails = Map.of(
                "method", request.methodName(),
                "path", request.path(),
                "status", status.value(),
                "exceptionType", error.getClass().getSimpleName(),
                "message", message
        );
        eventLogger.logExceptionEvent("api-gateway", "api-gateway", exceptionDetails);

        // Build response body
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.name());
        body.put("message", message);
        body.put("timestamp", Instant.now().toString());

        if (correlationId != null && !correlationId.isBlank()) {
            body.put("correlationId", correlationId);
        }

        return ServerResponse.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(body));
    }

    private HttpStatus resolveStatus(Throwable error) {

        log.debug("Resolving HTTP status for exception type {}",
                error.getClass().getSimpleName());

        if (error instanceof ResponseStatusException rse) {
            HttpStatus resolved = HttpStatus.resolve(rse.getStatusCode().value());
            return resolved != null
                    ? resolved
                    : HttpStatus.INTERNAL_SERVER_ERROR;
        }

        if (error instanceof CallNotPermittedException) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }

        if (error instanceof TimeoutException
                || error instanceof java.util.concurrent.CancellationException) {
            return HttpStatus.GATEWAY_TIMEOUT;
        }

        if (error instanceof ConnectException
                || error instanceof IOException) {
            return HttpStatus.BAD_GATEWAY;
        }

        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String resolveMessage(Throwable error, HttpStatus status) {

        log.debug("Resolving client message for status {}", status);

        return switch (status) {

            case NOT_FOUND ->
                    "No route matches this request";

            case SERVICE_UNAVAILABLE ->
                    "Dependent service is temporarily unavailable - please retry shortly";

            case GATEWAY_TIMEOUT ->
                    "Dependent service call timed out";

            case BAD_GATEWAY ->
                    "Unable to reach the dependent service";

            case INTERNAL_SERVER_ERROR ->
                    "An unexpected error occurred. Please try again later.";

            default ->
                    error instanceof ResponseStatusException rse
                            && rse.getReason() != null
                            ? rse.getReason()
                            : status.getReasonPhrase();
        };
    }
}
