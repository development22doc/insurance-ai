package com.claimassist.platform.customer_service.service.migration;

import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import com.claimassist.platform.customer_service.entity.Policy;
import com.claimassist.platform.customer_service.migration.LegacyPolicyDataset;
import com.claimassist.platform.customer_service.migration.LegacyPolicyRecord;
import com.claimassist.platform.customer_service.repository.PolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MigrationExtractService {

    private final PolicyRepository policyRepository;
    private final CurrentUserProvider currentUserProvider;

    @Value("${security.internal.trusted-service-client-ids:}")
    private String trustedServiceClientIds;

    private boolean isTrustedService(String clientId) {
        if (clientId == null || clientId.isBlank()) return false;
        String[] parts = trustedServiceClientIds == null ? new String[0] : trustedServiceClientIds.split(",");
        for (String p : parts) {
            if (p != null && p.trim().equals(clientId)) return true;
        }
        return false;
    }

    /**
     * Extract a page of legacy policies for migration. Requires a service token
     * from a trusted client id.
     */
    @Transactional(readOnly = true)
    public LegacyPolicyDataset extractPage(int page, int size, String batchId) {
        // authorize: only trusted service tokens
        if (!currentUserProvider.isServiceToken()) {
            throw new AccessDeniedException("Extraction endpoint requires a service token");
        }
        String clientId = currentUserProvider.getServiceClientId();
        if (!isTrustedService(clientId)) {
            throw new AccessDeniedException("Untrusted service caller for extraction: " + clientId);
        }

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
        Page<Policy> policies = policyRepository.findAll(pageable);

        List<LegacyPolicyRecord> records = policies.stream().map(p -> {
            var plan = p.getCoveragePlan();
            var customer = p.getCustomer();
            return new LegacyPolicyRecord(
                    p.getId(),
                    customer != null ? customer.getId() : null,
                    p.getPolicyNumber(),
                    plan != null ? plan.getId() : null,
                    plan != null ? plan.getName() : null,
                    plan != null ? plan.getProductType() : null,
                    plan != null ? plan.getAnnualPremiumCents() : null,
                    plan != null ? plan.getDeductibleCents() : null,
                    plan != null ? plan.getCoverageLimitCents() : null,
                    plan != null ? plan.getStripePriceId() : null,
                    p.getStripeSubscriptionId(),
                    p.getEffectiveDate(),
                    p.getRenewalDate(),
                    p.getStatus(),
                    "customer_service"
            );
        }).collect(Collectors.toList());

        LegacyPolicyDataset base = new LegacyPolicyDataset("customer_service", batchId, Instant.now(), "legacy-policy-v1", null, records);
        String checksum = base.computeChecksum();
        return new LegacyPolicyDataset(base.sourceSystem(), base.batchId(), base.extractedAt(), base.schemaVersion(), checksum, records);
    }
}
