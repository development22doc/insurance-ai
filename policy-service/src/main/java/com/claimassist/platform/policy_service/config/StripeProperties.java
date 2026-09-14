package com.claimassist.platform.policy_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "claimassist.stripe")
public record StripeProperties(
        boolean enabled,
        String apiKey,
        String webhookSecret,
        boolean testMode,
        String successUrl,
        String cancelUrl
) {
}
