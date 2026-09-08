package com.claimassist.platform.api_gateway.filter;

import com.claimassist.platform.common_lib.observability.LoggingConstants;
import com.claimassist.platform.common_lib.observability.DeveloperIdentity;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Reactive correlation / request-id handling for the gateway.
 * <p>
 * The shared {@code common-lib} correlation support ({@code CorrelationIdFilter}) is a
 * <b>servlet</b> {@code OncePerRequestFilter} and therefore does NOT run on the reactive
 * WebFlux stack that Spring Cloud Gateway uses. As a result the gateway previously did not
 * establish, validate or echo a business correlation id on its own.
 * <p>
 * This filter closes that gap at the edge:
 * <ul>
 *   <li>accepts an inbound {@code X-Correlation-Id} / {@code X-Request-Id} only if it is
 *       <b>sanitized</b> (printable ASCII, safe characters, bounded length) - otherwise a
 *       fresh {@link UUID} is generated, so a caller cannot spoof arbitrary/oversized values
 *       into logs or downstream tracing;</li>
 *   <li>always generates one when absent (traceability is never lost);</li>
 *   <li>sets the value into SLF4J MDC so gateway logging carries it;</li>
 *   <li>echoes it on the response and propagates it on the forwarded request so the
 *       downstream service (and its Kafka consumers) continue the same correlation.</li>
 * </ul>
 * No tokens, secrets or PII are logged.
 */
@Configuration
public class CorrelationIdWebFilter {

    private final DeveloperIdentity developerIdentity;

    public CorrelationIdWebFilter() {
        this(new DeveloperIdentity("local", "unknown"));
    }

    public CorrelationIdWebFilter(DeveloperIdentity developerIdentity) {
        this.developerIdentity = developerIdentity;
    }

    static final int MAX_LENGTH = 64;

    @Bean(name = "gatewayDeveloperIdentity")
    public DeveloperIdentity gatewayDeveloperIdentity() {
        return new DeveloperIdentity("local", "unknown");
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 10)
    public WebFilter correlationWebFilter(@org.springframework.beans.factory.annotation.Qualifier("gatewayDeveloperIdentity") DeveloperIdentity developerIdentity) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            String correlationId = sanitize(request.getHeaders().getFirst(LoggingConstants.CORRELATION_ID_HEADER));
            if (correlationId == null) {
                correlationId = UUID.randomUUID().toString();
            }
            String requestId = sanitize(request.getHeaders().getFirst(LoggingConstants.REQUEST_ID_HEADER));
            if (requestId == null) {
                requestId = UUID.randomUUID().toString();
            }
            final String effectiveCorrelationId = correlationId;
            final String effectiveRequestId = requestId;

            ServerHttpResponse response = exchange.getResponse();
            response.getHeaders().set(LoggingConstants.CORRELATION_ID_HEADER, effectiveCorrelationId);
            response.getHeaders().set(LoggingConstants.REQUEST_ID_HEADER, effectiveRequestId);

            ServerHttpRequest mutated = request.mutate()
                    .header(LoggingConstants.CORRELATION_ID_HEADER, effectiveCorrelationId)
                    .header(LoggingConstants.REQUEST_ID_HEADER, effectiveRequestId)
                    .build();

            return Mono.defer(() -> {
                MDC.put(LoggingConstants.MDC_CORRELATION_ID, effectiveCorrelationId);
                MDC.put(LoggingConstants.MDC_REQUEST_ID, effectiveRequestId);
                developerIdentity.populateMdc();
                return chain.filter(exchange.mutate().request(mutated).build())
                    .doFinally(signal -> {
                        MDC.remove(LoggingConstants.MDC_CORRELATION_ID);
                        MDC.remove(LoggingConstants.MDC_REQUEST_ID);
                        MDC.remove(LoggingConstants.MDC_DEVELOPER_ID);
                        MDC.remove(LoggingConstants.MDC_DEVELOPER_NAME);
                    });
            });
        };
    }

    /**
     * Returns a safe correlation/request id or {@code null} when the inbound value must be
     * replaced by a freshly generated one. Rejects: blank values, values longer than
     * {@link #MAX_LENGTH}, control / non-printable / non-ASCII characters, and anything
     * outside the printable {@code [A-Za-z0-9._-]} set - all of which could be used to
     * inject into logs or abuse downstream tracing.
     *
     * @param value the raw inbound header value (may be {@code null})
     * @return a sanitized value, or {@code null} to force regeneration
     */
    static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_LENGTH) {
            return null;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c < 0x21 || c > 0x7E) {
                return null;
            }
        }
        return trimmed.matches("[A-Za-z0-9._-]+") ? trimmed : null;
    }
}
