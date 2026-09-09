package com.claimassist.platform.customer_service.service.migration;

import com.claimassist.platform.common_lib.security.CurrentUserProvider;
import com.claimassist.platform.customer_service.entity.CoveragePlan;
import com.claimassist.platform.customer_service.entity.Customer;
import com.claimassist.platform.customer_service.entity.Policy;
import com.claimassist.platform.customer_service.migration.LegacyPolicyDataset;
import com.claimassist.platform.customer_service.migration.LegacyPolicyRecord;
import com.claimassist.platform.customer_service.repository.PolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MigrationExtractServiceTest {

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private MigrationExtractService extractService;

    @BeforeEach
    void setUp() {
        extractService = new MigrationExtractService(policyRepository, currentUserProvider);
        // set trusted clients via reflection since @Value isn't wired in unit test
        java.lang.reflect.Field f;
        try {
            f = MigrationExtractService.class.getDeclaredField("trustedServiceClientIds");
            f.setAccessible(true);
            f.set(extractService, "trusted-service,other-service");
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private Policy makePolicy(long id, long customerId, long planId, String status, long premium, long deductible, long limit, String stripePriceId, String stripeSubscriptionId) {
        CoveragePlan plan = CoveragePlan.builder()
                .id(planId)
                .name("Plan-" + planId)
                .productType("AUTO")
                .annualPremiumCents(premium)
                .deductibleCents(deductible)
                .coverageLimitCents(limit)
                .stripePriceId(stripePriceId)
                .build();

        Customer customer = Customer.builder().id(customerId).build();

        Policy p = Policy.builder()
                .id(id)
                .customer(customer)
                .coveragePlan(plan)
                .policyNumber("PN-" + id)
                .status(status)
                .effectiveDate(Instant.now())
                .renewalDate(Instant.now().plusSeconds(86400))
                .stripeSubscriptionId(stripeSubscriptionId)
                .build();
        return p;
    }

    @Test
    void extracts_active_policy_and_preserves_fields() {
        Policy p = makePolicy(1L, 10L, 100L, "ACTIVE", 10000L, 500L, 200000L, "price_abc", "sub_123");
        when(currentUserProvider.isServiceToken()).thenReturn(true);
        when(currentUserProvider.getServiceClientId()).thenReturn("trusted-service");
        Pageable pg = PageRequest.of(0,10);
        when(policyRepository.findAll(pg)).thenReturn(new PageImpl<>(List.of(p)));

        LegacyPolicyDataset dataset = extractService.extractPage(0,10,null);

        assertThat(dataset.recordCount()).isEqualTo(1);
        LegacyPolicyRecord r = dataset.records().get(0);
        assertThat(r.legacyPolicyId()).isEqualTo(1L);
        assertThat(r.legacyCustomerId()).isEqualTo(10L);
        assertThat(r.legacyPolicyNumber()).isEqualTo("PN-1");
        assertThat(r.legacyCoveragePlanId()).isEqualTo(100L);
        assertThat(r.annualPremiumCents()).isEqualTo(10000L);
        assertThat(r.deductibleCents()).isEqualTo(500L);
        assertThat(r.coverageLimitCents()).isEqualTo(200000L);
        assertThat(r.stripePriceId()).isEqualTo("price_abc");
        assertThat(r.stripeSubscriptionId()).isEqualTo("sub_123");
        assertThat(r.legacyStatus()).isEqualTo("ACTIVE");
        assertThat(dataset.hasChecksum()).isTrue();
        assertThat(dataset.isChecksumValid()).isTrue();
    }

    @Test
    void preserves_various_statuses_and_coverage_plan_id() {
        Policy p1 = makePolicy(2L, 20L, 200L, "PENDING", 111L, 11L, 222L, null, null);
        Policy p2 = makePolicy(3L, 30L, 300L, "LAPSED", 222L, 22L, 333L, null, null);
        Policy p3 = makePolicy(4L, 40L, 400L, "PENDING_RENEWAL", 333L, 33L, 444L, null, null);

        when(currentUserProvider.isServiceToken()).thenReturn(true);
        when(currentUserProvider.getServiceClientId()).thenReturn("trusted-service");
        when(policyRepository.findAll(org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class))).thenReturn(new PageImpl<>(List.of(p1,p2,p3)));

        LegacyPolicyDataset dataset = extractService.extractPage(0,10,null);

        assertThat(dataset.recordCount()).isEqualTo(3);
        assertThat(dataset.records().stream().map(LegacyPolicyRecord::legacyStatus)).containsExactly("PENDING","LAPSED","PENDING_RENEWAL");
        assertThat(dataset.records().stream().map(LegacyPolicyRecord::legacyCoveragePlanId)).containsExactly(200L,300L,400L);
    }

    @Test
    void preserves_stripe_references_without_calling_stripe() {
        Policy p = makePolicy(5L, 50L, 500L, "CANCELLED", 777L, 77L, 999L, "price_xyz", "sub_xyz");
        when(currentUserProvider.isServiceToken()).thenReturn(true);
        when(currentUserProvider.getServiceClientId()).thenReturn("trusted-service");
        Pageable pg = PageRequest.of(0,10);
        when(policyRepository.findAll(pg)).thenReturn(new PageImpl<>(List.of(p)));

        LegacyPolicyDataset dataset = extractService.extractPage(0,10,null);
        LegacyPolicyRecord r = dataset.records().get(0);
        assertThat(r.stripePriceId()).isEqualTo("price_xyz");
        assertThat(r.stripeSubscriptionId()).isEqualTo("sub_xyz");
    }

    @Test
    void read_only_behavior_does_not_modify_entities() {
        Policy p = makePolicy(6L, 60L, 600L, "ACTIVE", 123L, 12L, 321L, null, null);
        when(currentUserProvider.isServiceToken()).thenReturn(true);
        when(currentUserProvider.getServiceClientId()).thenReturn("trusted-service");
        Pageable pg = PageRequest.of(0,10);
        when(policyRepository.findAll(pg)).thenReturn(new PageImpl<>(List.of(p)));

        LegacyPolicyDataset dataset = extractService.extractPage(0,10,null);
        // the original policy instance should remain unchanged
        assertThat(p.getStatus()).isEqualTo("ACTIVE");
        assertThat(p.getCoveragePlan().getAnnualPremiumCents()).isEqualTo(123L);
    }

    @Test
    void unauthorized_caller_is_rejected() {
        when(currentUserProvider.isServiceToken()).thenReturn(false);
        assertThatThrownBy(() -> extractService.extractPage(0,10,null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void untrusted_service_is_rejected() {
        when(currentUserProvider.isServiceToken()).thenReturn(true);
        when(currentUserProvider.getServiceClientId()).thenReturn("evil-service");
        assertThatThrownBy(() -> extractService.extractPage(0,10,null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void pagination_returns_requested_page() {
        // create 3 policies, request page size 2 and page 1 -> should return last 1 element
        Policy p1 = makePolicy(7L, 70L, 700L, "ACTIVE", 1L,1L,1L, null, null);
        Policy p2 = makePolicy(8L, 80L, 800L, "ACTIVE", 2L,2L,2L, null, null);
        Policy p3 = makePolicy(9L, 90L, 900L, "ACTIVE", 3L,3L,3L, null, null);
        when(currentUserProvider.isServiceToken()).thenReturn(true);
        when(currentUserProvider.getServiceClientId()).thenReturn("trusted-service");
        Pageable pg0 = PageRequest.of(0,2);
        Pageable pg1 = PageRequest.of(1,2);
        when(policyRepository.findAll(pg0)).thenReturn(new PageImpl<>(List.of(p1,p2)));
        when(policyRepository.findAll(pg1)).thenReturn(new PageImpl<>(List.of(p3)));

        LegacyPolicyDataset page0 = extractService.extractPage(0,2,null);
        LegacyPolicyDataset page1 = extractService.extractPage(1,2,null);

        assertThat(page0.recordCount()).isEqualTo(2);
        assertThat(page1.recordCount()).isEqualTo(1);
        assertThat(page1.records().get(0).legacyPolicyId()).isEqualTo(9L);
    }
}
