package com.claimassist.platform.policy_service.service;

import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import com.claimassist.platform.policy_service.config.RedisCacheConfig;
import com.claimassist.platform.policy_service.dto.PolicyContractDetailDto;
import com.claimassist.platform.policy_service.dto.PolicyContractSummaryDto;
import com.claimassist.platform.policy_service.dto.PolicyPeriodDto;
import com.claimassist.platform.policy_service.entity.Plan;
import com.claimassist.platform.policy_service.entity.PolicyContract;
import com.claimassist.platform.policy_service.entity.PolicyPeriod;
import com.claimassist.platform.policy_service.entity.Product;
import com.claimassist.platform.policy_service.repository.PlanRepository;
import com.claimassist.platform.policy_service.repository.PolicyContractRepository;
import com.claimassist.platform.policy_service.repository.PolicyPeriodRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PolicyContractReadService {

    private final PolicyContractRepository policyContractRepository;
    private final PolicyPeriodRepository policyPeriodRepository;
    private final PlanRepository planRepository;
    private final CurrentUserProvider currentUserProvider;

    public List<PolicyContractSummaryDto> getMyPolicies() {
        Long customerId = currentUserProvider.getCurrentUserId();
        return getPoliciesForCustomer(customerId);
    }

    @Cacheable(value = RedisCacheConfig.POLICY_CONTRACTS_CACHE, key = "T(com.claimassist.platform.policy_service.service.PolicyContractReadService).policyContractListKey(#customerId)", sync = true)
    public List<PolicyContractSummaryDto> getPoliciesForCustomer(Long customerId) {
        log.debug("Loading contract policy list for customerId={}", customerId);
        List<PolicyContract> contracts = policyContractRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
        Map<Long, Plan> plansById = loadPlansById(contracts.stream()
                .map(PolicyContract::getCurrentPolicyPeriod)
                .filter(Objects::nonNull)
                .map(PolicyPeriod::getPlanId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        return contracts.stream()
                .map(contract -> toSummaryDto(contract, plansById))
                .toList();
    }

    public PolicyContractDetailDto getMyPolicy(Long policyId) {
        Long customerId = currentUserProvider.getCurrentUserId();
        return getPolicyForCustomer(customerId, policyId);
    }

    @Cacheable(value = RedisCacheConfig.POLICY_CONTRACT_DETAILS_CACHE, key = "T(com.claimassist.platform.policy_service.service.PolicyContractReadService).policyContractDetailKey(#customerId, #policyId)", sync = true)
    public PolicyContractDetailDto getPolicyForCustomer(Long customerId, Long policyId) {
        log.debug("Loading contract policy detail for customerId={} policyId={}", customerId, policyId);
        PolicyContract contract = policyContractRepository.findByIdAndCustomerId(policyId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Policy contract", String.valueOf(policyId)));
        Map<Long, Plan> plansById = loadPlansById(contract.getCurrentPolicyPeriod() == null ? List.of() : List.of(contract.getCurrentPolicyPeriod().getPlanId()));
        return toDetailDto(contract, plansById);
    }

    public List<PolicyPeriodDto> getPeriodsForPolicy(Long policyId) {
        Long customerId = currentUserProvider.getCurrentUserId();
        PolicyContract contract = policyContractRepository.findByIdAndCustomerId(policyId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Policy contract", String.valueOf(policyId)));
        List<PolicyPeriod> periods = policyPeriodRepository.findByPolicyContractIdOrderByRenewalSequenceAsc(contract.getId());
        Map<Long, Plan> plansById = loadPlansById(periods.stream()
                .map(PolicyPeriod::getPlanId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        return periods.stream()
                .map(period -> toPeriodDto(period, plansById))
                .toList();
    }

    public PolicyPeriodDto getCurrentPeriod(Long policyId) {
        Long customerId = currentUserProvider.getCurrentUserId();
        PolicyContract contract = policyContractRepository.findByIdAndCustomerId(policyId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Policy contract", String.valueOf(policyId)));
        if (contract.getCurrentPolicyPeriod() == null) {
            throw new ResourceNotFoundException("Policy current period", String.valueOf(policyId));
        }
        Map<Long, Plan> plansById = loadPlansById(List.of(contract.getCurrentPolicyPeriod().getPlanId()));
        return toPeriodDto(contract.getCurrentPolicyPeriod(), plansById);
    }

    public static String policyContractListKey(Long customerId) {
        return "contract:customer:" + customerId + ":policies";
    }

    public static String policyContractDetailKey(Long customerId, Long policyId) {
        return "contract:customer:" + customerId + ":policy:" + policyId;
    }

    private PolicyContractSummaryDto toSummaryDto(PolicyContract contract, Map<Long, Plan> plansById) {
        PolicyPeriod currentPeriod = contract.getCurrentPolicyPeriod();
        Plan plan = currentPeriod != null && currentPeriod.getPlanId() != null ? plansById.get(currentPeriod.getPlanId()) : null;
        Product product = plan != null ? plan.getProduct() : null;

        return new PolicyContractSummaryDto(
                contract.getId(),
                contract.getPolicyNumber(),
                contract.getStatus(),
                currentPeriod != null ? currentPeriod.getId() : null,
                currentPeriod != null ? currentPeriod.getEffectiveDate() : null,
                currentPeriod != null ? currentPeriod.getExpirationDate() : null,
                currentPeriod != null ? currentPeriod.getRenewalDate() : null,
                product != null ? product.getId() : contract.getProductId(),
                product != null ? product.getName() : null,
                product != null ? product.getType().name() : null,
                plan != null ? plan.getId() : null,
                plan != null ? plan.getName() : null,
                plan != null ? plan.getAnnualPremiumCents() : null,
                plan != null ? plan.getDeductibleCents() : null,
                plan != null ? plan.getCoverageLimitCents() : null,
                plan != null ? plan.getCurrency() : null
        );
    }

    private PolicyContractDetailDto toDetailDto(PolicyContract contract, Map<Long, Plan> plansById) {
        PolicyPeriod currentPeriod = contract.getCurrentPolicyPeriod();
        Plan plan = currentPeriod != null && currentPeriod.getPlanId() != null ? plansById.get(currentPeriod.getPlanId()) : null;
        Product product = plan != null ? plan.getProduct() : null;

        return new PolicyContractDetailDto(
                contract.getId(),
                contract.getPolicyNumber(),
                contract.getStatus(),
                currentPeriod != null ? currentPeriod.getId() : null,
                currentPeriod != null ? currentPeriod.getEffectiveDate() : null,
                currentPeriod != null ? currentPeriod.getExpirationDate() : null,
                currentPeriod != null ? currentPeriod.getRenewalDate() : null,
                currentPeriod != null ? currentPeriod.getActivatedAt() : null,
                currentPeriod != null ? currentPeriod.getCancelledAt() : null,
                product != null ? product.getId() : contract.getProductId(),
                product != null ? product.getName() : null,
                product != null ? product.getType().name() : null,
                plan != null ? plan.getId() : null,
                plan != null ? plan.getName() : null,
                plan != null ? plan.getAnnualPremiumCents() : null,
                plan != null ? plan.getDeductibleCents() : null,
                plan != null ? plan.getCoverageLimitCents() : null,
                plan != null ? plan.getCurrency() : null
        );
    }

    private PolicyPeriodDto toPeriodDto(PolicyPeriod period, Map<Long, Plan> plansById) {
        Plan plan = period != null && period.getPlanId() != null ? plansById.get(period.getPlanId()) : null;
        return new PolicyPeriodDto(
                period.getId(),
                period.getPolicyContract() != null ? period.getPolicyContract().getId() : null,
                period.getPreviousPolicyPeriod() != null ? period.getPreviousPolicyPeriod().getId() : null,
                period.getRenewalSequence(),
                period.getStatus(),
                period.getPlanId(),
                plan != null ? plan.getName() : null,
                period.getEffectiveDate(),
                period.getExpirationDate(),
                period.getRenewalDate(),
                period.getActivatedAt(),
                period.getCancelledAt(),
                period.getCreatedAt(),
                period.getUpdatedAt()
        );
    }

    private Map<Long, Plan> loadPlansById(List<Long> planIds) {
        if (planIds == null || planIds.isEmpty()) {
            return Map.of();
        }
        return planRepository.findAllById(planIds).stream()
                .collect(Collectors.toMap(Plan::getId, Function.identity()));
    }
}
