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
 * services (claims-service, customer-service) perform authorization checks
 * against the same principal.
 */
@Configuration
@Slf4j
public class FeignAuthConfig {

    @Bean
    public RequestInterceptor feignAuthRequestInterceptor(CurrentUserProvider currentUserProvider) {
        return template -> {
            try {
                var jwt = currentUserProvider.getCurrentJwt();
                if (jwt != null) {
                    String token = jwt.getTokenValue();
                    if (StringUtils.hasText(token)) {
                        template.header("Authorization", "Bearer " + token);
                    }
                }
            } catch (Exception e) {
                // If the JWT cannot be read (no reactive context), do nothing.
                // The ClaimsServiceGateway fails closed and logs the condition.
                log.debug("FeignAuthConfig: no JWT available to propagate: {}", e.toString());
            }
        };
    }
}
