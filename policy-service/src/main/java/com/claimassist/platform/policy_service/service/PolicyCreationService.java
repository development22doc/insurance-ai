package com.claimassist.platform.policy_service.service;

import com.claimassist.platform.common_lib.error.ServiceUnavailableException;
import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.policy_service.entity.*;
import com.claimassist.platform.policy_service.repository.CoverageRepository;
import com.claimassist.platform.policy_service.repository.PlanRepository;
import com.claimassist.platform.policy_service.repository.ProductRepository;
import com.claimassist.platform.policy_service.repository.PolicyRepository;
import com.stripe.Stripe;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.model.PaymentIntent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PolicyCreationService {

    private final PolicyRepository policyRepository;
    private final PlanRepository planRepository;
    private final ProductRepository productRepository;
    private final CoverageRepository coverageRepository;
    private final com.claimassist.platform.policy_service.service.IdempotencyService idempotencyService;
    private final com.claimassist.platform.policy_service.security.InternalRequestIdentity internalRequestIdentity;

    @PersistenceContext
    private EntityManager entityManager;

    @org.springframework.beans.factory.annotation.Value("${stripe.secret-key:}")
    private String stripeSecretKey;

    @org.springframework.beans.factory.annotation.Value("${stripe.webhook-signing-secret:}")
    private String stripeWebhookSigningSecret;

    /**
     * Externalized Stripe secret key - configured via STRIPE_SECRET_KEY env var
     * or stripe.secret-key in application.yml. Never hardcoded.
     */
    private String getStripeSecretKey() {
        return Optional.ofNullable(stripeSecretKey)
                .or(() -> Optional.ofNullable(System.getenv("STRIPE_SECRET_KEY")))
                .or(() -> Optional.ofNullable(System.getProperty("STRIPE_SECRET_KEY")))
                .orElse(null);
    }

    /**
     * Externalized Stripe webhook signing secret - configured via SPRING_STRIPE_WEBHOOK_SIGNING_SECRET env var
     * or stripe.webhook-signing-secret in application.yml. Never hardcoded.
     */
    private String getStripeWebhookSigningSecret() {
        return Optional.ofNullable(stripeWebhookSigningSecret)
                .or(() -> Optional.ofNullable(System.getenv("SPRING_STRIPE_WEBHOOK_SIGNING_SECRET")))
                .or(() -> Optional.ofNullable(System.getProperty("SPRING_STRIPE_WEBHOOK_SIGNING_SECRET")))
                .orElse(null);
    }

    /**
     * Full policy creation flow with Stripe payment.
     * Flow: authenticate → validate product/plan/coverage → validate business rules
     *       → create Stripe payment intent → server-side verify → create Policy → create PolicyVersion
     *
     * @param request the policy creation request
     * @param xUserIdHeader the X-User-Id header for caller identification
     * @return the created policy
     * @throws ResourceNotFoundException if product/plan/coverage not found
     * @throws ServiceUnavailableException if payment verification fails
     */
@Transactional
public Policy createPolicy(PolicyCreationRequest request, String xUserIdHeader, String idempotencyKey) {
        // Authenticate/authorize caller
        Long callingUserId = authenticateCaller(xUserIdHeader);

        // Require idempotency key for policy creation to eliminate orphan Stripe PaymentIntent risk.
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required for policy creation");
        }

        // Compute deterministic request fingerprint (exclude idempotency key and non-business fields)
        String requestFingerprint = computeRequestFingerprint(request);

        // Execute creation under idempotency guard. We cache the full response including Stripe details
        java.util.Map<String, Object> result = idempotencyService.execute(
                idempotencyKey,
                "create-policy",
                callingUserId,
                requestFingerprint,
                java.util.Map.class,
                () -> {
                    // 2. Validate Product
                    Product product = validateProduct(request.getProductCode());

                    // 3. Validate Plan belongs to Product
                    Plan plan = validatePlanBelongsToProduct(request.getPlanCode(), product.getId());

                    // 4. Validate Coverage belongs to Plan
                    Coverage coverage = validateCoverageBelongsToPlan(request.getCoverageCode(), plan.getId());

                    // 5. Validate required policy/customer information
                    validatePolicyInformation(request);

                    // 6. Validate policy/business invariants
                    validateBusinessInvariants(request, product, plan, coverage);

                    // 7. Generate unique policy number
                    String policyNumber = generatePolicyNumber(request.getCustomerId());

                    // 8. Create and persist policy in PENDING_PAYMENT state so we have a server-generated policyId
                    Policy policy = createPolicyEntity(request, policyNumber, product, plan, coverage);
                    policy.setStatus(com.claimassist.platform.policy_service.entity.LifecycleStatus.PENDING_PAYMENT.name());
                    // Persist now to obtain an id for inclusion in Stripe metadata
                    entityManager.flush();

                    // 9. Create Stripe payment intent (use client-supplied idempotency key when available)
                    java.util.Map<String, Object> paymentResult = createStripePaymentIntent(request, plan, policyNumber, idempotencyKey, policy.getId());
                    String paymentIntentId = (String) paymentResult.get("paymentIntentId");
                    String clientSecret = (String) paymentResult.get("clientSecret");
                    Long amount = (Long) paymentResult.get("amount");

                    // 10. Attach stripe payment intent id for deterministic correlation and create initial PolicyVersion
                    policy.setStripePaymentIntentId(paymentIntentId);

                    // 11. Create initial PolicyVersion (version 1)
                    createInitialPolicyVersion(policy, plan, coverage);

                    // 12. Persist transactionally - Policy + PolicyVersion atomic
                    Policy savedPolicy = policyRepository.save(policy);

                    return java.util.Map.of(
                            "policyId", savedPolicy.getId(),
                            "policyNumber", savedPolicy.getPolicyNumber(),
                            "stripePaymentIntentId", paymentIntentId,
                            "clientSecret", clientSecret,
                            "amount", amount,
                            "currency", "usd"
                    );
                }
        );

        Number policyIdNum = (Number) result.get("policyId");
        Long policyId = policyIdNum == null ? null : policyIdNum.longValue();
        if (policyId == null) throw new IllegalStateException("Idempotent create returned no policy id");
        return policyRepository.findById(policyId).orElseThrow(() -> new IllegalStateException("Policy not found after creation: " + policyId));
    }

    /**
     * Get the cached Stripe payment details for an idempotent create request.
     * This is used by the controller to return client_secret for the initial request.
     */
    public java.util.Map<String, Object> getCachedPaymentDetails(String idempotencyKey, Long callingUserId) {
        return idempotencyService.getCachedResponse(idempotencyKey, callingUserId, "create-policy");
    }

    // -- Authentication --

    private Long authenticateCaller(String xUserIdHeader) {
        // Use InternalRequestIdentity to properly resolve caller identity
        // This handles both USER JWT (ignores X-User-Id) and SERVICE JWT (requires X-User-Id)
        return internalRequestIdentity.resolveCallingUserId(xUserIdHeader);
    }

    // -- Validation helpers --

    // Compute a deterministic fingerprint for the business request. Excludes idempotency key
    // and non-business fields (successUrl/cancelUrl). Uses SHA-256 hex of a stable pipe-separated string.
    private String computeRequestFingerprint(PolicyCreationRequest request) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(request.getCustomerId() == null ? "" : request.getCustomerId().toString()).append('|');
            sb.append(request.getProductCode() == null ? "" : request.getProductCode()).append('|');
            sb.append(request.getPlanCode() == null ? "" : request.getPlanCode()).append('|');
            sb.append(request.getCoverageCode() == null ? "" : request.getCoverageCode()).append('|');
            sb.append(request.getEffectiveDate() == null ? "" : request.getEffectiveDate().toString()).append('|');
            sb.append(request.getRenewalDate() == null ? "" : request.getRenewalDate().toString());

            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute request fingerprint", e);
        }
    }

    private Product validateProduct(String productCode) {
        return productRepository.findByCode(productCode)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productCode));
    }

    private Plan validatePlanBelongsToProduct(String planCode, Long productId) {
        return planRepository.findByCodeAndProductId(planCode, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", planCode + " belonging to product " + productId));
    }

    private Coverage validateCoverageBelongsToPlan(String coverageCode, Long planId) {
        return coverageRepository.findByCodeAndPlanId(coverageCode, planId)
                .orElseThrow(() -> new ResourceNotFoundException("Coverage", coverageCode + " belonging to plan " + planId));
    }

    private void validatePolicyInformation(PolicyCreationRequest request) {
        if (request.getCustomerId() == null || request.getCustomerId() <= 0) {
            throw new IllegalArgumentException("Customer ID must be a positive value");
        }
        if (request.getEffectiveDate() == null) {
            throw new IllegalArgumentException("Effective date is required");
        }
    }

    private void validateBusinessInvariants(PolicyCreationRequest request, Product product, Plan plan, Coverage coverage) {
        // Effective date must not be before plan creation
        if (request.getEffectiveDate().isBefore(plan.getCreatedAt())) {
            throw new IllegalArgumentException("Effective date cannot be before plan creation date");
        }
        // Plan must be active
        if (!Boolean.TRUE.equals(plan.getActive())) {
            throw new IllegalArgumentException("Cannot create policy on inactive plan");
        }
        // Plan must have authoritative premium pricing
        if (plan.getPremiumCents() == null || plan.getPremiumCents() <= 0) {
            throw new IllegalArgumentException("Plan must have a positive premium amount");
        }
    }

    private String generatePolicyNumber(Long customerId) {
        // Use DB-backed sequence for concurrency-safe numeric part
        try {
            Object nextValObj = entityManager
                    .createNativeQuery("SELECT nextval('policy_number_seq')")
                    .getSingleResult();
            long seq = ((Number) nextValObj).longValue();
            return String.format("POL-%d-%06d", customerId, seq);
        } catch (Exception e) {
            // Fallback to time-based suffix if sequence unavailable (should not happen in deployed DB)
            long sequence = System.currentTimeMillis() % 100000L;
            return String.format("POL-%d-%05d", customerId, sequence);
        }
    }

    // -- Stripe payment operations --

    private java.util.Map<String, Object> createStripePaymentIntent(PolicyCreationRequest request, Plan plan, String policyNumber, String idempotencyKey, Long policyId) {
        String secretKey = getStripeSecretKey();
        if (secretKey == null) {
            throw new ServiceUnavailableException("Stripe secret key not configured");
        }

        Stripe.apiKey = secretKey;

        try {
            // Build metadata from server-side authoritative values only
            java.util.Map<String, String> metadata = java.util.Map.of(
                    "policy_id", policyId == null ? "" : String.valueOf(policyId),
                    "policy_number", policyNumber,
                    "policy_customer_id", request.getCustomerId() == null ? "" : request.getCustomerId().toString(),
                    "product_code", request.getProductCode() == null ? "" : request.getProductCode(),
                    "plan_code", request.getPlanCode() == null ? "" : request.getPlanCode(),
                    "coverage_code", request.getCoverageCode() == null ? "" : request.getCoverageCode()
            );

            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(plan.getPremiumCents())
                    .setCurrency("usd")
                    .addPaymentMethodType("card")
                    .setDescription("ClaimAssist Policy Purchase - " + policyNumber)
                    .putAllMetadata(metadata)
                    .build();

            // Use provided idempotency key when available (client-supplied) to ensure retries reuse the same Stripe key.
            String stripeIdempotency = (idempotencyKey == null || idempotencyKey.isBlank())
                    ? "policy-create-" + policyNumber
                    : "policy-create-" + idempotencyKey;

            com.stripe.net.RequestOptions requestOptions = com.stripe.net.RequestOptions.builder()
                    .setIdempotencyKey(stripeIdempotency)
                    .build();

            com.stripe.model.PaymentIntent intent = com.stripe.model.PaymentIntent.create(params, requestOptions);

            return java.util.Map.of(
                    "paymentIntentId", intent.getId(),
                    "clientSecret", intent.getClientSecret(),
                    "amount", intent.getAmount(),
                    "currency", intent.getCurrency()
            );
        } catch (com.stripe.exception.StripeException e) {
            throw new ServiceUnavailableException("Stripe payment creation failed: " + e.getMessage());
        }
    }

    public boolean verifyStripePayment(String paymentIntentId) {
        String secretKey = getStripeSecretKey();
        if (secretKey == null) {
            return false;
        }

        Stripe.apiKey = secretKey;

        try {
            com.stripe.model.PaymentIntent intent = com.stripe.model.PaymentIntent.retrieve(paymentIntentId);
            // Server-side: payment is verified when status is 'succeeded'
            return "succeeded".equals(intent.getStatus());
        } catch (com.stripe.exception.StripeException e) {
            // Log but do not expose sensitive payment information
            throw new ServiceUnavailableException("Stripe payment verification failed");
        }
    }

    /**
     * Verify PaymentIntent status AND that the PaymentIntent metadata references the given policy.
     * This defends against accepting a PaymentIntent that succeeded but was created for a different policy.
     */
    public boolean verifyStripePaymentForPolicy(String paymentIntentId, com.claimassist.platform.policy_service.entity.Policy policy) {
        String secretKey = getStripeSecretKey();
        if (secretKey == null) {
            return false;
        }

        Stripe.apiKey = secretKey;

        try {
            com.stripe.model.PaymentIntent intent = com.stripe.model.PaymentIntent.retrieve(paymentIntentId);
            boolean succeeded = "succeeded".equals(intent.getStatus());
            if (!succeeded) return false;

            // Additional metadata checks: policy_number and policy_customer_id if present
            java.util.Map<String, String> metadata = intent.getMetadata();
            if (metadata == null) return false;

            String metaPolicyNumber = metadata.get("policy_number");
            String metaCustomerId = metadata.get("policy_customer_id");

            if (metaPolicyNumber != null && !metaPolicyNumber.isBlank()) {
                if (!metaPolicyNumber.equals(policy.getPolicyNumber())) return false;
            }

            if (metaCustomerId != null && !metaCustomerId.isBlank()) {
                if (policy.getCustomerId() == null) return false;
                if (!metaCustomerId.equals(String.valueOf(policy.getCustomerId()))) return false;
            }

            return true;
        } catch (com.stripe.exception.StripeException e) {
            throw new ServiceUnavailableException("Stripe payment verification failed");
        }
    }

    // -- Policy entity creation --

    private Policy createPolicyEntity(PolicyCreationRequest request, String policyNumber,
                                      Product product, Plan plan, Coverage coverage) {
        Policy policy = Policy.builder()
                .policyNumber(policyNumber)
                .customerId(request.getCustomerId())
                .coveragePlan(plan)
                .status(LifecycleStatus.PENDING_PAYMENT.name())
                .effectiveDate(request.getEffectiveDate())
                .renewalDate(request.getRenewalDate())
                .build();

        entityManager.persist(policy);
        entityManager.flush();

        return policy;
    }

    private void createInitialPolicyVersion(Policy policy, Plan plan, Coverage coverage) {
        // Use authoritative Plan premium for initial policy version
        PolicyVersion version = PolicyVersion.builder()
                .policy(policy)
                .versionNumber(1)
                .plan(plan)
                .premiumCents(plan.getPremiumCents())
                .deductibleCents(plan.getDeductibleCents())
                .coverageLimitCents(plan.getCoverageLimitCents())
                .effectiveFrom(policy.getEffectiveDate())
                .effectiveTo(null)
                .build();

        entityManager.persist(version);
        entityManager.flush();
    }
}
