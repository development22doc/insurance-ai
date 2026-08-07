package com.claimassist.platform.api_gateway.config;

import com.claimassist.platform.common_lib.observability.ObservabilityAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Ensures observability auto-configuration beans are registered in api-gateway.
 */
@Configuration
@Import(ObservabilityAutoConfiguration.class)
public class GatewayAutoConfiguration {
}

