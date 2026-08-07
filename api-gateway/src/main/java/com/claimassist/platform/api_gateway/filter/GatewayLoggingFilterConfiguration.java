package com.claimassist.platform.api_gateway.filter;

import com.claimassist.platform.common_lib.observability.PerformanceLogger;
import com.claimassist.platform.common_lib.observability.LoggingConstants;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;
import java.util.HashMap;

/**
 * Reactive WebFilter for logging API gateway requests and responses with latency.
 * Reuses EventLogger from common-lib to emit structured REQUEST events.
 */
@Configuration
public class GatewayLoggingFilterConfiguration {
    private static final Logger log = LoggerFactory.getLogger(GatewayLoggingFilterConfiguration.class);

    @Bean
    public WebFilter gatewayRequestResponseLoggingFilter(Optional<EventLogger> eventLogger, Optional<PerformanceLogger> perfLogger) {
        return (exchange, chain) -> {
            long startNanos = System.nanoTime();
            String method = exchange.getRequest().getMethod().toString();
            String path = exchange.getRequest().getURI().getPath();
            String remoteIp = Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                    .map(addr -> addr.getAddress().getHostAddress())
                    .orElse("unknown");

            // Attempt to read matched Route if available
            Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            String routeId = route != null ? route.getId() : null;

            // Emit REQUEST_RECEIVED event immediately (duration 0)
            Map<String, Object> recvDetails = new HashMap<>();
            recvDetails.put("method", method);
            recvDetails.put("path", path);
            recvDetails.put("remoteIp", remoteIp);
            if (routeId != null) recvDetails.put("routeId", routeId);
            String recvCorrelation = MDC.get(LoggingConstants.MDC_CORRELATION_ID);
            if (recvCorrelation != null) recvDetails.put("correlationId", recvCorrelation);
            recvDetails.put("phase", "RECEIVED");
            eventLogger.ifPresent(el -> el.logRequestEvent("api-gateway", "api-gateway", 0L, recvDetails));

            return ReactiveSecurityContextHolder.getContext()
                    .map(SecurityContext::getAuthentication)
                    .map(Authentication::getPrincipal)
                    .flatMap(principal -> {
                        // Null-safe extraction of "sub" claim
                        if (principal instanceof Jwt jwt) {
                            Object sub = jwt.getClaim("sub");
                            if (sub != null) return Mono.just(String.valueOf(sub));
                        }
                        return Mono.empty();
                    })
                    .defaultIfEmpty("anonymous")
                    .flatMap(username -> chain.filter(exchange)
                            .doFinally(signal -> {
                                long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
                                int status = exchange.getResponse().getStatusCode() != null
                                        ? exchange.getResponse().getStatusCode().value()
                                        : 0;

                                String correlationId = MDC.get(LoggingConstants.MDC_CORRELATION_ID);

                                Map<String, Object> details = new HashMap<>();
                                details.put("method", method);
                                details.put("path", path);
                                details.put("status", status);
                                details.put("remoteIp", remoteIp);
                                details.put("username", username);
                                details.put("latencyMs", elapsedMs);
                                if (routeId != null) details.put("routeId", routeId);
                                if (correlationId != null) details.put("correlationId", correlationId);

                                // Apply performance thresholds if available
                                perfLogger.ifPresent(pl -> pl.log("REQUEST", method + " " + path + (routeId != null ? " route:" + routeId : ""), elapsedMs, details));

                                // Emit structured request completed event
                                details.put("phase", "COMPLETED");
                                eventLogger.ifPresent(el -> el.logRequestEvent("api-gateway", "api-gateway", elapsedMs, details));
                            }));
        };
    }
}

