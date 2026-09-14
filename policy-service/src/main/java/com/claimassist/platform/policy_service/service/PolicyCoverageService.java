package com.claimassist.platform.policy_service.service;

import com.claimassist.platform.common_lib.dto.PolicyCoverageDto;
import com.claimassist.platform.common_lib.error.BadRequestException;
import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.policy_service.entity.Plan;
import com.claimassist.platform.policy_service.entity.PolicyContract;
import com.claimassist.platform.policy_service.entity.PolicyPeriod;
import com.claimassist.platform.policy_service.repository.PlanRepository;
import com.claimassist.platform.policy_service.repository.PolicyContractRepository;
import com.claimassist.platform.policy_service.repository.PolicyPeriodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyCoverageService {

    private final PolicyContractRepository policyContractRepository;
    private final PolicyPeriodRepository policyPeriodRepository;
    private final PlanRepository planRepository;

    public PolicyCoverageDto getCoverageForCustomer(Long policyId, Long customerId, Instant incidentTime) {
        if (policyId == null) {
            throw new BadRequestException("Policy id is required");
        }
        if (customerId == null) {
            throw new BadRequestException("Customer id is required");
        }
        Instant asOf = incidentTime == null ? Instant.now() : incidentTime;

        PolicyContract contract = policyContractRepository.findByIdAndCustomerId(policyId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Policy", String.valueOf(policyId)));

        PolicyPeriod coveringPeriod = resolveCoveringPeriod(contract.getId(), asOf);
        Plan plan = planRepository.findById(coveringPeriod.getPlanId())
                .orElseThrow(() -> new ResourceNotFoundException("Plan", String.valueOf(coveringPeriod.getPlanId())));

        return toCoverageDto(contract, coveringPeriod, plan);
    }

    public PolicyPeriod resolveCoveringPeriod(Long policyContractId, Instant incidentTime) {
        if (policyContractId == null) {
            throw new BadRequestException("Policy contract id is required");
        }
        if (incidentTime == null) {
            throw new BadRequestException("Incident time is required");
        }

        List<PolicyPeriod> candidatePeriods = policyPeriodRepository.findCoveringPeriodsForContractAt(policyContractId, incidentTime);
        if (candidatePeriods.isEmpty()) {
            throw new ResourceNotFoundException("Policy period coverage", String.valueOf(policyContractId));
        }

        return candidatePeriods.get(0);
    }

    private PolicyCoverageDto toCoverageDto(PolicyContract contract, PolicyPeriod period, Plan plan) {
        return new PolicyCoverageDto(
                contract.getId(),
                contract.getPolicyNumber(),
                contract.getStatus(),
                plan.getProduct() != null ? plan.getProduct().getType().name() : null,
                plan.getName(),
                plan.getDeductibleCents(),
                plan.getCoverageLimitCents(),
                period.getRenewalDate() != null ? period.getRenewalDate().toString() : null
        );
    }
}
