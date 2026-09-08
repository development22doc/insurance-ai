package com.claimassist.platform.customer_service.client;

import com.claimassist.platform.customer_service.entity.CoveragePlan;
import lombok.extern.slf4j.Slf4j;

/**
 * Mapper for converting Customer Service policy requests to Policy Service requests.
 * This handles the data model transformation between Customer's CoveragePlan
 * and Policy Service's hierarchical Product/Plan/Coverage structure.
 *
 * Phase 6: Integration preparation only. Not activated in existing flow.
 */
@Slf4j
public class PolicyServiceMapper {

    /**
     * Maps Customer's CoveragePlan-based request to Policy Service's hierarchical request.
     *
     * CRITICAL MAPPING LIMITATION:
     * Customer's CoveragePlan entity has:
     * - productType (AUTO/HOME/HEALTH)
     * - name (Basic/Standard/Comprehensive)
     *
     * Policy Service requires:
     * - productCode
     * - planCode
     * - coverageCode
     *
     * CURRENT GAPS:
     * 1. Customer does NOT have coverage-level granularity (no coverageCode equivalent)
     * 2. CoveragePlan.name is a display name, not necessarily a standardized plan code
     * 3. No deterministic mapping exists from Customer's flat model to Policy Service's hierarchy
     *
     * This method throws an exception because the mapping cannot be safely established.
     * The adapter is explicitly unavailable for real activation until:
     * 1. Customer's data model is enhanced with proper plan/coverage codes, OR
     * 2. A configuration table mapping CoveragePlan.id to (productCode, planCode, coverageCode) is created, OR
     * 3. Policy Service's API is enhanced to accept Customer's current data model
     *
     * DO NOT use this method for real policy creation until the mapping is resolved.
     *
     * @param coveragePlan Customer's CoveragePlan entity
     * @param customerId Customer ID
     * @param effectiveDate Policy effective date
     * @param renewalDate Policy renewal date
     * @param successUrl Stripe success URL
     * @param cancelUrl Stripe cancel URL
     * @return Policy Service request DTO
     * @throws UnsupportedOperationException mapping cannot be safely established
     */
    public static PolicyCreateRequestDto toPolicyServiceRequest(
            CoveragePlan coveragePlan,
            Long customerId,
            java.time.Instant effectiveDate,
            java.time.Instant renewalDate,
            String successUrl,
            String cancelUrl) {

        throw new UnsupportedOperationException(
                "CoveragePlan → Product/Plan/Coverage mapping cannot be safely established. " +
                "Customer's CoveragePlan model lacks coverage-level granularity and standardized codes. " +
                "This adapter is explicitly unavailable for real policy creation until the mapping is resolved. " +
                "Required: either data model enhancement, configuration table, or Policy Service API enhancement. " +
                "CoveragePlan ID: " + coveragePlan.getId() + ", productType: " + coveragePlan.getProductType() + ", name: " + coveragePlan.getName());
    }

    /**
     * Maps Policy Service response to Customer's PolicyResponse format.
     *
     * NOTE: Policy Service returns (policyId, policyNumber, status, stripePaymentIntentId, clientSecret, amount, currency).
     * The effectiveDate and renewalDate are not returned in the current contract.
     * This method uses the original request dates as a workaround.
     *
     * @param policyServiceResponse Policy Service response
     * @param coveragePlanName Coverage plan name from Customer's database
     * @param productType Product type from Customer's database
     * @param effectiveDate Original effective date from request
     * @param renewalDate Original renewal date from request
     * @return Customer's PolicyResponse
     */
    public static com.claimassist.platform.customer_service.dto.policy.PolicyResponse toCustomerResponse(
            PolicyCreateResponseDto policyServiceResponse,
            String coveragePlanName,
            String productType,
            java.time.Instant effectiveDate,
            java.time.Instant renewalDate) {

        return new com.claimassist.platform.customer_service.dto.policy.PolicyResponse(
                policyServiceResponse.policyId,
                policyServiceResponse.policyNumber,
                policyServiceResponse.status,
                coveragePlanName,
                productType,
                effectiveDate,
                renewalDate
        );
    }
}
