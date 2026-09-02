package com.claimassist.platform.common_lib.observability;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Provides the configured developer identity to request filters. MDC is thread-local,
 * so it must be populated by the active request/reactive execution thread rather than
 * once on the ApplicationReadyEvent startup thread.
 */
@Configuration
public class DeveloperIdentityConfiguration {

    @Bean
    public DeveloperIdentity developerIdentity(Environment environment) {
        return DeveloperIdentity.from(environment);
    }
}
