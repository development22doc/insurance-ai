package com.claimassist.platform.policy_service.service;

import com.claimassist.platform.policy_service.entity.Purchase;
import com.stripe.model.Event;

import java.util.Optional;

public interface StripePaymentGateway {

    boolean isEnabled();

    boolean isTestMode();

    Optional<StripeCheckoutSession> prepareCheckoutSession(Purchase purchase);

    Event verifyAndParseWebhook(String payload, String stripeSignatureHeader);
}
