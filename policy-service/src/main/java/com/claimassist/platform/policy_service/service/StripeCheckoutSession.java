package com.claimassist.platform.policy_service.service;

public record StripeCheckoutSession(String sessionId, String checkoutUrl, Boolean testMode) {
}
