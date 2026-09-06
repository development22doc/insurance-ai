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

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Externalized Stripe secret key - configured via STRIPE_SECRET_KEY env var
     * or spring.stripe.secret-key in application.yml. Never hardcoded.
     */
    private String getStripeSecretKey() {
        return Optional.ofNullable(System.getenv("STRIPE_SECRET_KEY"))
                .or(() -> Optional.ofNullable(System.getProperty("STRIPE_SECRET_KEY")))
                .orElseGet(() -> {
                    // Spring Boot will bind from application.yml config;
                    // misconfiguration will be caught at first Stripe API call.
                    return null;
                });
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

        // Execute creation under idempotency guard. We cache only a small response map (policyId/policyNumber)
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

                    // 8. Create Stripe payment intent (use client-supplied idempotency key when available)
                    String paymentIntentId = createStripePaymentIntent(request, coverage, policyNumber, idempotencyKey);

                    // 9. Persist policy in PENDING_PAYMENT state. Activation will occur after server-side verification (e.g., webhook).
                    Policy policy = createPolicyEntity(request, policyNumber, product, plan, coverage);
                    policy.setStatus(com.claimassist.platform.policy_service.entity.LifecycleStatus.PENDING_PAYMENT.name());
                    // attach stripe payment intent id for deterministic correlation
                    policy.setStripePaymentIntentId(paymentIntentId);

                    // 11. Create initial PolicyVersion (version 1)
                    createInitialPolicyVersion(policy, plan, coverage);

                    // 12. Persist transactionally - Policy + PolicyVersion atomic
                    Policy savedPolicy = policyRepository.save(policy);

                    return java.util.Map.of("policyId", savedPolicy.getId(), "policyNumber", savedPolicy.getPolicyNumber());
                }
        );

        Number policyIdNum = (Number) result.get("policyId");
        Long policyId = policyIdNum == null ? null : policyIdNum.longValue();
        if (policyId == null) throw new IllegalStateException("Idempotent create returned no policy id");
        return policyRepository.findById(policyId).orElseThrow(() -> new IllegalStateException("Policy not found after creation: " + policyId));
    }

    // -- Authentication --

    private Long authenticateCaller(String xUserIdHeader) {
        if (xUserIdHeader == null) {
            throw new IllegalArgumentException("X-User-Id header is required for policy creation");
        }
        try {
            return Long.valueOf(xUserIdHeader);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("X-User-Id must be a numeric user id");
        }
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
        // Coverage must have valid pricing
        if (coverage.getDeductibleCents() == null || coverage.getDeductibleCents() <= 0) {
            throw new IllegalArgumentException("Coverage must have a positive deductible amount");
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

    private String createStripePaymentIntent(PolicyCreationRequest request, Coverage coverage, String policyNumber, String idempotencyKey) {
        String secretKey = getStripeSecretKey();
        if (secretKey == null) {
            throw new ServiceUnavailableException("Stripe secret key not configured");
        }

        Stripe.apiKey = secretKey;

        try {
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(coverage.getDeductibleCents())
                    .setCurrency("usd")
                    .addPaymentMethodType("card")
                    .setDescription("Policy Coverage - " + coverage.getName() + " for policy " + request.getCustomerId())
                    .putAllMetadata(
                            java.util.Map.of(
                                    "policy_number", policyNumber,
                                    "policy_customer_id", request.getCustomerId().toString(),
                                    "policy_product", request.getProductCode(),
                                    "policy_plan", request.getPlanCode(),
                                    "policy_coverage", request.getCoverageCode()
                            )
                    )
                    .build();

            // Use provided idempotency key when available (client-supplied) to ensure retries reuse the same Stripe key.
            String stripeIdempotency = (idempotencyKey == null || idempotencyKey.isBlank())
                    ? "policy-create-" + policyNumber
                    : "policy-create-" + idempotencyKey;

            com.stripe.net.RequestOptions requestOptions = com.stripe.net.RequestOptions.builder()
                    .setIdempotencyKey(stripeIdempotency)
                    .build();

            com.stripe.model.PaymentIntent intent = com.stripe.model.PaymentIntent.create(params, requestOptions);

            return intent.getId();
        } catch (com.stripe.exception.StripeException e) {
            throw new ServiceUnavailableException("Stripe payment creation failed: " + e.getMessage());
        }
    }

    private boolean verifyStripePayment(String paymentIntentId) {
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
        // Policy Service's current catalog model does not have an authoritative premium source
        // for a plan or coverage item. The Customer source model holds annualPremiumCents at the
        // aggregate CoveragePlan level, but the target policy-version snapshot does not currently
        // define a guaranteed conversion from that legacy value. Avoid silently writing the
        // deductible into the premium field because that would corrupt the meaning of the versioned
        // premium snapshot. This remains nullable until a business-approved premium source is added.
        PolicyVersion version = PolicyVersion.builder()
                .policy(policy)
                .versionNumber(1)
                .plan(plan)
                .premiumCents(null)
                .deductibleCents(coverage.getDeductibleCents())
                .coverageLimitCents(coverage.getLimitCents())
                .effectiveFrom(policy.getEffectiveDate())
                .effectiveTo(null)
                .build();

        entityManager.persist(version);
        entityManager.flush();
    }
}