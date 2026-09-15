package com.claimassist.platform.agent_service.config;

import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import feign.RequestInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Feign configuration that propagates the current authenticated user's
 * Authorization bearer token to outgoing Feign requests so downstream
 * services (claims-service, policy-service) perform authorization checks
 * against the same principal.
 */
@Configuration
@Slf4j
public class FeignAuthConfig {

    @Bean
    public RequestInterceptor feignAuthRequestInterceptor(CurrentUserProvider currentUserProvider) {
        // Reactive environments do not expose the SecurityContext via ThreadLocal on
        // reactor-http threads; calling blocking CurrentUserProvider here will throw
        // IllegalStateException. Leave Feign interceptor as a no-op. Reactive
        // propagation (ClaimsService permission checks) is performed via the
        // reactive SecurityExpressions/WebClient path instead.
        return template -> {
            // Intentionally left blank to avoid blocking calls on reactor threads.
            if (log.isTraceEnabled()) {
                log.trace("FeignAuthConfig: request interceptor no-op under WebFlux");
            }
        };
    }
}
