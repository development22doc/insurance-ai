package com.claimassist.platform.agent_service.config;

import com.claimassist.platform.agent_service.cache.CacheService;
import com.claimassist.platform.agent_service.client.ClaimsClient;
import com.claimassist.platform.agent_service.client.PolicyClient;
import com.claimassist.platform.agent_service.service.gateway.ClaimsServiceGateway;
import com.claimassist.platform.agent_service.service.gateway.PolicyServiceGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class GatewayBeansConfig {

    @Bean
    public ClaimsServiceGateway claimsServiceGateway(ClaimsClient claimsClient,
                                                    CacheService cacheService,
                                                    CacheProperties cacheProperties,
                                                    WebClient claimsServiceWebClient) {
        return new ClaimsServiceGateway(claimsClient, cacheService, cacheProperties, claimsServiceWebClient);
    }

    @Bean
    public PolicyServiceGateway policyServiceGateway(PolicyClient policyClient,
                                                        CacheService cacheService,
                                                        CacheProperties cacheProperties,
                                                        WebClient policyServiceWebClient) {
        return new PolicyServiceGateway(policyClient, cacheService, cacheProperties, policyServiceWebClient);
    }
}

