package com.claimassist.platform.agent_service.config;

import com.claimassist.platform.agent_service.security.SecurityExpressions;
import com.claimassist.platform.agent_service.service.gateway.ClaimsServiceGatewayApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Explicit configuration to create the SecurityExpressions bean with the
 * reactive WebClient so it can be constructed deterministically (avoids
 * constructor selection issues during component scanning).
 */
@Configuration
public class SecurityExpressionsConfig {

    @Bean(name = "security")
    public SecurityExpressions securityExpressions(ClaimsServiceGatewayApi claimsServiceGateway,
                                                   WebClient claimsServiceWebClient) {
        return new SecurityExpressions(claimsServiceGateway, claimsServiceWebClient);
    }
}
