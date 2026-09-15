package com.claimassist.platform.agent_service.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * WebClient configuration for reactive service-to-service calls.
 * Provides WebClients for calling ClaimsService and PolicyService endpoints reactively.
 */
@Configuration
public class ServiceWebClientConfig {

    private static final Logger log = LoggerFactory.getLogger(ServiceWebClientConfig.class);

    @Value("${CLAIMS_SERVICE_URI:http://localhost:8082}")
    private String claimsServiceUri;

    @Value("${CUSTOMER_SERVICE_URI:http://localhost:8081}")
    private String customerServiceUri;

    @Value("${POLICY_SERVICE_URI:http://localhost:8084}")
    private String policyServiceUri;

    @Bean
    public WebClient claimsServiceWebClient(WebClient.Builder builder) {
        // Normalize property: ensure a scheme is present and fallback to default if empty.
        String normalized = claimsServiceUri == null || claimsServiceUri.isBlank()
                ? "http://localhost:8082"
                : claimsServiceUri.trim();
        if (!normalized.matches("^https?://.*")) {
            // If someone set CLAIMS_SERVICE_URI=localhost:8082 or similar, make it valid.
            normalized = "http://" + normalized;
        }
        log.info("ClaimsService WebClient baseUrl={}", normalized);
        return builder
                .baseUrl(normalized)
                .filter(logRequest())
                .build();
    }

    @Bean
    public WebClient customerServiceWebClient(WebClient.Builder builder) {
        // Normalize property: ensure a scheme is present and fallback to default if empty.
        String normalized = customerServiceUri == null || customerServiceUri.isBlank()
                ? "http://localhost:8081"
                : customerServiceUri.trim();
        if (!normalized.matches("^https?://.*")) {
            // If someone set CUSTOMER_SERVICE_URI=localhost:8081 or similar, make it valid.
            normalized = "http://" + normalized;
        }
        log.info("CustomerService WebClient baseUrl={}", normalized);
        return builder
                .baseUrl(normalized)
                .filter(logRequest())
                .build();
    }

    @Bean
    public WebClient policyServiceWebClient(WebClient.Builder builder) {
        String normalized = policyServiceUri == null || policyServiceUri.isBlank()
                ? "http://localhost:8084"
                : policyServiceUri.trim();
        if (!normalized.matches("^https?://.*")) {
            normalized = "http://" + normalized;
        }
        log.info("PolicyService WebClient baseUrl={}", normalized);
        return builder
                .baseUrl(normalized)
                .filter(logRequest())
                .build();
    }

    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            log.debug("WebClient request: {} {}", clientRequest.method(), clientRequest.url());
            return Mono.just(clientRequest);
        });
    }
}
