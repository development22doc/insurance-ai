package com.claimassist.platform.policy_service.service;

import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.policy_service.config.StripeProperties;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;

class StripePaymentGatewayTest {

    private final StripeProperties stripeProperties = new StripeProperties(
            true,
            "sk_test_123",
            "whsec_123",
            true,
            "http://localhost:5173/#/checkout/success?session_id={CHECKOUT_SESSION_ID}",
            "http://localhost:5173/#/checkout/cancel?session_id={CHECKOUT_SESSION_ID}"
    );

    private final StripePaymentGatewayImpl gateway = new StripePaymentGatewayImpl(stripeProperties);

    @Test
    void verifyAndParseWebhook_acceptsValidSignature() throws Exception {
        Event event = Event.GSON.fromJson("{\"id\":\"evt_123\",\"object\":\"event\",\"type\":\"checkout.session.completed\"}", Event.class);

        try (MockedStatic<Webhook> mocked = mockStatic(Webhook.class)) {
            mocked.when(() -> Webhook.constructEvent("payload", "sig", "whsec_123")).thenReturn(event);

            Event parsed = gateway.verifyAndParseWebhook("payload", "sig");
            assertThat(parsed.getId()).isEqualTo("evt_123");
        }
    }

    @Test
    void verifyAndParseWebhook_rejectsInvalidSignature() {
        try (MockedStatic<Webhook> mocked = mockStatic(Webhook.class)) {
            mocked.when(() -> Webhook.constructEvent("payload", "sig", "whsec_123"))
                    .thenThrow(new SignatureVerificationException("bad signature", "bad signature"));

            assertThrows(BadRequestException.class, () -> gateway.verifyAndParseWebhook("payload", "sig"));
        }
    }
}
