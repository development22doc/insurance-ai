package com.claimassist.platform.policy_service.migration;

import com.claimassist.platform.policy_service.entity.Plan;
import com.claimassist.platform.policy_service.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TargetPlanLookupImpl implements TargetPlanLookup {

    private final PlanRepository planRepository;

    @Override
    public Optional<TargetPlanState> findById(Long planId) {
        if (planId == null) {
            return Optional.empty();
        }
        return planRepository.findById(planId)
                .map(plan -> new TargetPlanState(
                        plan.getId(),
                        true,
                        plan.getActive() != null && plan.getActive()
                ))
                .or(() -> Optional.of(TargetPlanState.missing(planId)));
    }
}
