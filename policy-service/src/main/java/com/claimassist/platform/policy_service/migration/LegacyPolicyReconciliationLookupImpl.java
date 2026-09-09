package com.claimassist.platform.policy_service.migration;

import com.claimassist.platform.policy_service.entity.LegacyCustomerPolicyIdMap;
import com.claimassist.platform.policy_service.repository.LegacyCustomerPolicyIdMapRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class LegacyPolicyReconciliationLookupImpl implements LegacyPolicyReconciliationLookup {

    private final LegacyCustomerPolicyIdMapRepository repository;

    @Override
    public Optional<LegacyPolicyReconciliation> findByLegacyPolicyId(Long legacyPolicyId) {
        return repository.findByLegacyCustomerPolicyId(legacyPolicyId)
                .map(this::mapToReconciliation);
    }

    @Override
    public Optional<LegacyPolicyReconciliation> findByTargetPolicyId(Long targetPolicyId) {
        return repository.findByPolicyServicePolicyId(targetPolicyId)
                .map(this::mapToReconciliation);
    }

    @Override
    public Optional<LegacyPolicyReconciliation> findByLegacyPolicyNumber(String legacyPolicyNumber) {
        return repository.findByLegacyPolicyNumber(legacyPolicyNumber)
                .map(this::mapToReconciliation);
    }

    private LegacyPolicyReconciliation mapToReconciliation(LegacyCustomerPolicyIdMap map) {
        return new LegacyPolicyReconciliation(
                map.getLegacyCustomerPolicyId(),
                map.getPolicyServicePolicyId(),
                map.getLegacyCustomerId(),
                map.getLegacyPolicyNumber(),
                map.getSourceSystem()
        );
    }
}
