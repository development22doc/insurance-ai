package com.claimassist.platform.api_gateway.filter;

import com.claimassist.platform.common_lib.observability.event.EventLogger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Detect rate limit responses (HTTP 429) and emit a structured SECURITY event.
 * This observes responses after routing and does not change rate-limiting behaviour.
 */
@Configuration
@ConditionalOnBean(EventLogger.class)
public class RateLimitResponseFilterConfiguration {

    @Bean
    public WebFilter rateLimitResponseFilter(java.util.Optional<EventLogger> eventLogger) {
        return (exchange, chain) -> chain.filter(exchange)
                .then(Mono.fromRunnable(() -> {
                    HttpStatusCode status = exchange.getResponse().getStatusCode();
                    if (status != null && status.value() == 429) {
                        String path = exchange.getRequest().getURI().getPath();
                        String remoteIp = exchange.getRequest().getRemoteAddress() != null
                                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                                : "unknown";

                        Map<String, Object> details = Map.of(
                                "event", "rateLimited",
                                "path", path,
                                "remoteIp", remoteIp
                        );
                        eventLogger.ifPresent(el -> el.logSecurityEvent("api-gateway", "api-gateway", details));
                    }
                }));
    }
}

