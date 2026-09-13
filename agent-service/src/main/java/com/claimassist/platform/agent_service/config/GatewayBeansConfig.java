package com.claimassist.platform.agent_service.config;

import com.claimassist.platform.agent_service.cache.CacheService;
import com.claimassist.platform.agent_service.client.ClaimsClient;
import com.claimassist.platform.agent_service.client.CustomerClient;
import com.claimassist.platform.agent_service.service.gateway.ClaimsServiceGateway;
import com.claimassist.platform.agent_service.service.gateway.CustomerServiceGateway;
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
    public CustomerServiceGateway customerServiceGateway(CustomerClient customerClient,
                                                        CacheService cacheService,
                                                        CacheProperties cacheProperties,
                                                        WebClient customerServiceWebClient) {
        return new CustomerServiceGateway(customerClient, cacheService, cacheProperties, customerServiceWebClient);
    }
}
