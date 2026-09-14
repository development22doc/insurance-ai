package com.claimassist.platform.policy_service.service;

import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.error.ServiceUnavailableException;
import com.claimassist.platform.policy_service.config.StripeProperties;
import com.claimassist.platform.policy_service.entity.Purchase;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StripePaymentGatewayImpl implements StripePaymentGateway {

    private final StripeProperties stripeProperties;

    @Override
    public boolean isEnabled() {
        return stripeProperties.enabled() && stripeProperties.apiKey() != null && !stripeProperties.apiKey().isBlank();
    }

    @Override
    public boolean isTestMode() {
        return stripeProperties.testMode();
    }

    @Override
    public Optional<StripeCheckoutSession> prepareCheckoutSession(Purchase purchase) {
        if (!isEnabled()) {
            return Optional.empty();
        }

        if (purchase.getAmountCents() == null || purchase.getAmountCents() <= 0) {
            throw new BadRequestException("Plan pricing information is unavailable for Stripe checkout");
        }

        try {
            Stripe.apiKey = stripeProperties.apiKey();

            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(stripeProperties.successUrl())
                    .setCancelUrl(stripeProperties.cancelUrl())
                    .setClientReferenceId(String.valueOf(purchase.getId()))
                    .putMetadata("purchaseId", purchase.getId().toString())
                    .putMetadata("customerId", purchase.getCustomerId().toString())
                    .putMetadata("planId", purchase.getPlan().getId().toString())
                    .putMetadata("productId", purchase.getPlan().getProduct().getId().toString())
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setQuantity(1L)
                            .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                    .setCurrency(normalizeCurrency(purchase.getCurrency()))
                                    .setUnitAmount(purchase.getAmountCents())
                                    .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                            .setName(purchase.getPlan().getName())
                                            .build())
                                    .build())
                            .build())
                    .build();

            Session created = Session.create(params);
            return Optional.of(new StripeCheckoutSession(created.getId(), created.getUrl(), stripeProperties.testMode()));
        } catch (Exception ex) {
            throw new ServiceUnavailableException("Stripe checkout preparation is temporarily unavailable");
        }
    }

    @Override
    public Event verifyAndParseWebhook(String payload, String stripeSignatureHeader) {
        if (stripeProperties.webhookSecret() == null || stripeProperties.webhookSecret().isBlank()) {
            throw new BadRequestException("Stripe webhook secret is not configured");
        }

        if (stripeSignatureHeader == null || stripeSignatureHeader.isBlank()) {
            throw new BadRequestException("Missing Stripe-Signature header");
        }

        try {
            return Webhook.constructEvent(payload, stripeSignatureHeader, stripeProperties.webhookSecret());
        } catch (SignatureVerificationException ex) {
            throw new BadRequestException("Invalid Stripe webhook signature");
        } catch (Exception ex) {
            throw new BadRequestException("Malformed Stripe webhook payload");
        }
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new BadRequestException("Plan currency is unavailable for Stripe checkout");
        }
        return currency.toLowerCase(Locale.ROOT);
    }
}
