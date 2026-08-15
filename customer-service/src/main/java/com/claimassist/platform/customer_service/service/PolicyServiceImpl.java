package com.claimassist.platform.customer_service.service;

import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.common_lib.observability.LoggingConstants;
import com.claimassist.platform.common_lib.observability.PerformanceLogger;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import com.claimassist.platform.customer_service.dto.policy.PolicyCreateRequest;
import com.claimassist.platform.customer_service.dto.policy.PolicyResponse;
import com.claimassist.platform.customer_service.dto.policy.PolicyUpdateRequest;
import com.claimassist.platform.customer_service.entity.CoveragePlan;
import com.claimassist.platform.customer_service.entity.Customer;
import com.claimassist.platform.customer_service.entity.Policy;
import com.claimassist.platform.customer_service.mapper.PolicyMapper;
import com.claimassist.platform.customer_service.repository.CoveragePlanRepository;
import com.claimassist.platform.customer_service.repository.CustomerRepository;
import com.claimassist.platform.customer_service.repository.PolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PolicyServiceImpl implements PolicyService {

    private final PolicyRepository policyRepository;
    private final CustomerRepository customerRepository;
    private final CoveragePlanRepository coveragePlanRepository;
    private final PolicyMapper policyMapper;
    private final PolicyQueryService policyQueryService;
    private final EventLogger eventLogger;
    private final PerformanceLogger performanceLogger;

    @Override
    @Transactional
    public PolicyResponse createPolicy(PolicyCreateRequest request, Long customerId) {
        long startTime = System.currentTimeMillis();

        // Verify customer exists
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", String.valueOf(customerId)));

        // Verify coverage plan exists
        CoveragePlan coveragePlan = coveragePlanRepository.findById(request.coveragePlanId())
                .orElseThrow(() -> new ResourceNotFoundException("CoveragePlan", String.valueOf(request.coveragePlanId())));

        // Generate unique policy number
        String policyNumber = generatePolicyNumber();

        // Validate dates
        if (request.renewalDate() != null && request.renewalDate().isBefore(request.effectiveDate())) {
            throw new BadRequestException("Renewal date cannot be before effective date");
        }

        Map<String, Object> createStartDetails = new HashMap<>();
        createStartDetails.put("customerId", customerId);
        createStartDetails.put("coveragePlanId", request.coveragePlanId());
        createStartDetails.put("event", "POLICY_CREATE_STARTED");
        eventLogger.logBusinessEvent("customer-service", "customer-service", createStartDetails);

        try {
            // Create policy entity
            long dbStartTime = System.currentTimeMillis();
            Policy policy = Policy.builder()
                    .customer(customer)
                    .coveragePlan(coveragePlan)
                    .policyNumber(policyNumber)
                    .status("PENDING")
                    .effectiveDate(request.effectiveDate())
                    .renewalDate(request.renewalDate())
                    .build();

            policy = policyRepository.save(policy);
            long dbDuration = System.currentTimeMillis() - dbStartTime;

            Map<String, Object> dbCreateDetails = new HashMap<>();
            dbCreateDetails.put("policyId", policy.getId());
            dbCreateDetails.put("customerId", customerId);
            dbCreateDetails.put("event", "POLICY_DB_CREATED");
            dbCreateDetails.put("executionTimeMs", dbDuration);
            eventLogger.logDatabaseEvent("customer-service", "customer-service", dbDuration, dbCreateDetails);
            performanceLogger.log("REPOSITORY", "repository.policy.create", dbDuration,
                    Map.of("policyId", policy.getId(), "customerId", customerId));

            // Evict cache
            policyQueryService.evictMyPolicies(customerId);

            // Emit completion event
            long totalDuration = System.currentTimeMillis() - startTime;
            Map<String, Object> completedDetails = new HashMap<>();
            completedDetails.put("policyId", policy.getId());
            completedDetails.put("customerId", customerId);
            completedDetails.put("policyNumber", policyNumber);
            completedDetails.put("event", "POLICY_CREATE_COMPLETED");
            completedDetails.put("executionTimeMs", totalDuration);
            completedDetails.put("correlationId", MDC.get(LoggingConstants.MDC_CORRELATION_ID));
            completedDetails.put("traceId", MDC.get(LoggingConstants.MDC_TRACE_ID));
            eventLogger.logBusinessEvent("customer-service", "customer-service", completedDetails);
            performanceLogger.log("BUSINESS", "policy.create.total", totalDuration,
                    Map.of("policyId", policy.getId(), "customerId", customerId));

            return policyMapper.toPolicyResponse(policy);

        } catch (RuntimeException e) {
            long totalDuration = System.currentTimeMillis() - startTime;
            Map<String, Object> failedDetails = new HashMap<>();
            failedDetails.put("customerId", customerId);
            failedDetails.put("event", "POLICY_CREATE_FAILED");
            failedDetails.put("executionTimeMs", totalDuration);
            failedDetails.put("correlationId", MDC.get(LoggingConstants.MDC_CORRELATION_ID));
            failedDetails.put("traceId", MDC.get(LoggingConstants.MDC_TRACE_ID));
            eventLogger.logBusinessEvent("customer-service", "customer-service", failedDetails);

            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PolicyResponse getPolicyById(Long policyId, Long customerId) {
        long startTime = System.currentTimeMillis();

        Policy policy = policyRepository.findByIdAndCustomerId(policyId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Policy", String.valueOf(policyId)));

        long duration = System.currentTimeMillis() - startTime;
        performanceLogger.log("BUSINESS", "policy.get_by_id", duration,
                Map.of("policyId", policyId, "customerId", customerId));

        return policyMapper.toPolicyResponse(policy);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PolicyResponse> getPolicies(Long customerId, Pageable pageable) {
        long startTime = System.currentTimeMillis();

        // For paginated requests, query directly by customer ID
        // Note: This doesn't use the cached method since pagination varies per request
        Page<Policy> policies = policyRepository.findByCustomerId(customerId, pageable);

        long duration = System.currentTimeMillis() - startTime;
        performanceLogger.log("BUSINESS", "policy.get_all", duration,
                Map.of("customerId", customerId));

        return policies.map(policyMapper::toPolicyResponse);
    }

    @Override
    @Transactional
    public PolicyResponse updatePolicy(Long policyId, PolicyUpdateRequest request, Long customerId) {
        long startTime = System.currentTimeMillis();

        // Find policy and verify ownership
        Policy policy = policyRepository.findByIdAndCustomerId(policyId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Policy", String.valueOf(policyId)));

        Map<String, Object> updateStartDetails = new HashMap<>();
        updateStartDetails.put("policyId", policyId);
        updateStartDetails.put("customerId", customerId);
        updateStartDetails.put("event", "POLICY_UPDATE_STARTED");
        eventLogger.logBusinessEvent("customer-service", "customer-service", updateStartDetails);

        try {
            // Update allowed fields
            if (request.status() != null) {
                policy.setStatus(request.status());
            }
            if (request.renewalDate() != null) {
                if (request.renewalDate().isBefore(policy.getEffectiveDate())) {
                    throw new BadRequestException("Renewal date cannot be before effective date");
                }
                policy.setRenewalDate(request.renewalDate());
            }

            // Save changes
            long dbStartTime = System.currentTimeMillis();
            policy = policyRepository.save(policy);
            long dbDuration = System.currentTimeMillis() - dbStartTime;

            Map<String, Object> dbUpdateDetails = new HashMap<>();
            dbUpdateDetails.put("policyId", policyId);
            dbUpdateDetails.put("customerId", customerId);
            dbUpdateDetails.put("event", "POLICY_DB_UPDATED");
            dbUpdateDetails.put("executionTimeMs", dbDuration);
            eventLogger.logDatabaseEvent("customer-service", "customer-service", dbDuration, dbUpdateDetails);
            performanceLogger.log("REPOSITORY", "repository.policy.update", dbDuration,
                    Map.of("policyId", policyId, "customerId", customerId));

            // Evict cache
            policyQueryService.evictMyPolicies(customerId);
            policyQueryService.evictPolicyCoverage(policyId, customerId);

            // Emit completion event
            long totalDuration = System.currentTimeMillis() - startTime;
            Map<String, Object> completedDetails = new HashMap<>();
            completedDetails.put("policyId", policyId);
            completedDetails.put("customerId", customerId);
            completedDetails.put("event", "POLICY_UPDATE_COMPLETED");
            completedDetails.put("executionTimeMs", totalDuration);
            completedDetails.put("correlationId", MDC.get(LoggingConstants.MDC_CORRELATION_ID));
            completedDetails.put("traceId", MDC.get(LoggingConstants.MDC_TRACE_ID));
            eventLogger.logBusinessEvent("customer-service", "customer-service", completedDetails);
            performanceLogger.log("BUSINESS", "policy.update.total", totalDuration,
                    Map.of("policyId", policyId, "customerId", customerId));

            return policyMapper.toPolicyResponse(policy);

        } catch (RuntimeException e) {
            long totalDuration = System.currentTimeMillis() - startTime;
            Map<String, Object> failedDetails = new HashMap<>();
            failedDetails.put("policyId", policyId);
            failedDetails.put("customerId", customerId);
            failedDetails.put("event", "POLICY_UPDATE_FAILED");
            failedDetails.put("executionTimeMs", totalDuration);
            failedDetails.put("correlationId", MDC.get(LoggingConstants.MDC_CORRELATION_ID));
            failedDetails.put("traceId", MDC.get(LoggingConstants.MDC_TRACE_ID));
            eventLogger.logBusinessEvent("customer-service", "customer-service", failedDetails);

            throw e;
        }
    }

    @Override
    @Transactional
    public void deletePolicy(Long policyId, Long customerId) {
        long startTime = System.currentTimeMillis();

        // Find policy and verify ownership
        Policy policy = policyRepository.findByIdAndCustomerId(policyId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Policy", String.valueOf(policyId)));

        Map<String, Object> deleteStartDetails = new HashMap<>();
        deleteStartDetails.put("policyId", policyId);
        deleteStartDetails.put("customerId", customerId);
        deleteStartDetails.put("event", "POLICY_DELETE_STARTED");
        eventLogger.logBusinessEvent("customer-service", "customer-service", deleteStartDetails);

        try {
            // Delete policy (hard delete as per existing convention)
            long dbStartTime = System.currentTimeMillis();
            policyRepository.deleteById(policyId);
            long dbDuration = System.currentTimeMillis() - dbStartTime;

            Map<String, Object> dbDeleteDetails = new HashMap<>();
            dbDeleteDetails.put("policyId", policyId);
            dbDeleteDetails.put("customerId", customerId);
            dbDeleteDetails.put("event", "POLICY_DB_DELETED");
            dbDeleteDetails.put("executionTimeMs", dbDuration);
            eventLogger.logDatabaseEvent("customer-service", "customer-service", dbDuration, dbDeleteDetails);
            performanceLogger.log("REPOSITORY", "repository.policy.delete", dbDuration,
                    Map.of("policyId", policyId, "customerId", customerId));

            // Evict cache
            policyQueryService.evictMyPolicies(customerId);
            policyQueryService.evictPolicyCoverage(policyId, customerId);

            // Emit completion event
            long totalDuration = System.currentTimeMillis() - startTime;
            Map<String, Object> completedDetails = new HashMap<>();
            completedDetails.put("policyId", policyId);
            completedDetails.put("customerId", customerId);
            completedDetails.put("event", "POLICY_DELETE_COMPLETED");
            completedDetails.put("executionTimeMs", totalDuration);
            completedDetails.put("correlationId", MDC.get(LoggingConstants.MDC_CORRELATION_ID));
            completedDetails.put("traceId", MDC.get(LoggingConstants.MDC_TRACE_ID));
            eventLogger.logBusinessEvent("customer-service", "customer-service", completedDetails);
            performanceLogger.log("BUSINESS", "policy.delete.total", totalDuration,
                    Map.of("policyId", policyId, "customerId", customerId));

        } catch (RuntimeException e) {
            long totalDuration = System.currentTimeMillis() - startTime;
            Map<String, Object> failedDetails = new HashMap<>();
            failedDetails.put("policyId", policyId);
            failedDetails.put("customerId", customerId);
            failedDetails.put("event", "POLICY_DELETE_FAILED");
            failedDetails.put("executionTimeMs", totalDuration);
            failedDetails.put("correlationId", MDC.get(LoggingConstants.MDC_CORRELATION_ID));
            failedDetails.put("traceId", MDC.get(LoggingConstants.MDC_TRACE_ID));
            eventLogger.logBusinessEvent("customer-service", "customer-service", failedDetails);

            throw e;
        }
    }

    /**
     * Generates a unique policy number.
     * In production, this would follow a specific business format.
     * For this implementation, we use a UUID-based approach.
     */
    private String generatePolicyNumber() {
        return "POL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
