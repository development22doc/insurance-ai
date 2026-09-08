package com.claimassist.platform.policy_service.controller;

import com.claimassist.platform.common_lib.dto.PolicyCoverageDto;
import com.claimassist.platform.policy_service.dto.PolicySummaryDto;
import com.claimassist.platform.policy_service.service.PolicyCoverageQueryService;
import com.claimassist.platform.policy_service.service.PolicyLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/v1/policies")
@RequiredArgsConstructor
public class InternalPolicyController {

    private final PolicyCoverageQueryService policyCoverageQueryService;
    private final PolicyLookupService policyLookupService;
    private final com.claimassist.platform.policy_service.security.InternalRequestIdentity internalRequestIdentity;
    private final com.claimassist.platform.policy_service.service.PolicyCreationService policyCreationService;
    private final com.claimassist.platform.policy_service.service.IdempotencyService idempotencyService;


    /**
     * Internal coverage lookup used by Claims and Agent services.
     * Phase 1: scaffold only. Implementation returns ServiceUnavailable by default.
     */
    @GetMapping("/{policyId}")
    public ResponseEntity<PolicySummaryDto> getPolicy(
            @PathVariable Long policyId,
            @RequestHeader(value = "X-User-Id", required = false) String xUserId) {
        Long actingUserId = internalRequestIdentity.resolveCallingUserId(xUserId);
        return ResponseEntity.ok(policyLookupService.getPolicy(policyId, actingUserId));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<PolicySummaryDto>> getPoliciesForCustomer(
            @PathVariable Long customerId,
            @RequestHeader(value = "X-User-Id", required = false) String xUserId) {
        Long actingUserId = internalRequestIdentity.resolveCallingUserId(xUserId);
        return ResponseEntity.ok(policyLookupService.getPoliciesForCustomer(customerId, actingUserId));
    }

    @GetMapping("/{policyId}/coverage")
    public ResponseEntity<PolicyCoverageDto> getPolicyCoverage(
            @PathVariable Long policyId,
            @RequestHeader(value = "X-User-Id", required = false) String xUserId) {

        // Resolve the calling user id according to the platform's internal endpoint contract
        Long callingUserId = internalRequestIdentity.resolveCallingUserId(xUserId);

        String callingUserIdStr = callingUserId == null ? null : callingUserId.toString();
        PolicyCoverageDto dto = policyCoverageQueryService.getPolicyCoverage(policyId, callingUserIdStr);
        if (dto == null) {
            return ResponseEntity.status(503).build();
        }
        return ResponseEntity.ok(dto);
    }

    @PostMapping
    public ResponseEntity<com.claimassist.platform.policy_service.dto.PolicyCreateResponseDto> createPolicy(
            @RequestBody com.claimassist.platform.policy_service.dto.PolicyCreateRequestDto body,
            @RequestHeader(value = "X-User-Id", required = false) String xUserId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        // Require idempotency header at controller boundary for internal API
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new com.claimassist.platform.common_lib.error.BadRequestException("Missing Idempotency-Key header");
        }

        // Resolve the calling user id according to the platform's internal endpoint contract
        Long callingUserId = internalRequestIdentity.resolveCallingUserId(xUserId);
        String callingUserIdStr = callingUserId == null ? null : callingUserId.toString();

        // Map request DTO to service request
        com.claimassist.platform.policy_service.service.PolicyCreationRequest svcReq = new com.claimassist.platform.policy_service.service.PolicyCreationRequest();
        svcReq.setCustomerId(body.customerId);
        svcReq.setProductCode(body.productCode);
        svcReq.setPlanCode(body.planCode);
        svcReq.setCoverageCode(body.coverageCode);
        svcReq.setEffectiveDate(body.effectiveDate);
        svcReq.setRenewalDate(body.renewalDate);
        svcReq.setSuccessUrl(body.successUrl);
        svcReq.setCancelUrl(body.cancelUrl);

        com.claimassist.platform.policy_service.entity.Policy created = policyCreationService.createPolicy(svcReq, callingUserIdStr, idempotencyKey);

        // Retrieve the cached Stripe payment details from the idempotency service
        java.util.Map<String, Object> cachedPaymentDetails = policyCreationService.getCachedPaymentDetails(idempotencyKey, callingUserId);

        String clientSecret = cachedPaymentDetails != null ? (String) cachedPaymentDetails.get("clientSecret") : null;
        Long amount = null;
        if (cachedPaymentDetails != null) {
            Object amountObj = cachedPaymentDetails.get("amount");
            if (amountObj instanceof Number) {
                amount = ((Number) amountObj).longValue();
            }
        }
        String currency = cachedPaymentDetails != null ? (String) cachedPaymentDetails.get("currency") : "usd";

        com.claimassist.platform.policy_service.dto.PolicyCreateResponseDto resp = new com.claimassist.platform.policy_service.dto.PolicyCreateResponseDto(
                created.getId(), created.getPolicyNumber(), created.getStatus(), created.getStripePaymentIntentId(), clientSecret, amount, currency);

        return ResponseEntity.ok(resp);
    }
}
