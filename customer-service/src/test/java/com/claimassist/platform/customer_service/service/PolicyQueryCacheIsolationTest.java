package com.claimassist.platform.customer_service.service;

import com.claimassist.platform.common_lib.dto.PolicyCoverageDto;
import com.claimassist.platform.common_lib.error.ResourceNotFoundException;
import com.claimassist.platform.customer_service.config.RedisCacheConfig;
import com.claimassist.platform.customer_service.entity.CoveragePlan;
import com.claimassist.platform.customer_service.entity.Policy;
import com.claimassist.platform.customer_service.mapper.PolicyMapper;
import com.claimassist.platform.customer_service.repository.PolicyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 3, Section 14 (MANDATORY): the cache must NEVER become an authorization
 * bypass.
 *
 * <p>This drives the REAL {@link PolicyQueryService} through a REAL Spring
 * {@code @Cacheable} proxy (ConcurrentMapCacheManager, no Redis required) and
 * verifies that:
 * <ol>
 *   <li>a second read for the SAME caller is served from cache (backend not hit);</li>
 *   <li>a DIFFERENT caller hitting the same policyId uses a DIFFERENT cache key,
 *       so User B can never receive User A's cached coverage;</li>
 *   <li>ownership is still enforced on the caching (miss) path - an unowned
 *       policy throws ResourceNotFoundException rather than being served from
 *       another user's cache entry.</li>
 * </ol>
 * The cache key {@code #policyId + '-' + #callingUserId} is what guarantees
 * per-user isolation, so authorization is not weakened by a cache hit.
 */
class PolicyQueryCacheIsolationTest {

    @Configuration
    @EnableCaching
    static class CacheConfig {

        @Bean
        CacheManager cacheManager() {
            ConcurrentMapCacheManager manager = new ConcurrentMapCacheManager();
            manager.setCacheNames(Set.of(
                    RedisCacheConfig.POLICY_COVERAGE_CACHE,
                    RedisCacheConfig.MY_POLICIES_CACHE));
            return manager;
        }

        @Bean
        PolicyRepository policyRepository() {
            return mock(PolicyRepository.class);
        }

        @Bean
        PolicyMapper policyMapper() {
            return mock(PolicyMapper.class);
        }

        @Bean
        com.claimassist.platform.customer_service.client.PolicyServiceAdapter policyServiceAdapter() {
            return mock(com.claimassist.platform.customer_service.client.PolicyServiceAdapter.class);
        }

        @Bean
        com.claimassist.platform.customer_service.config.PolicyServiceProperties policyServiceProperties() {
            com.claimassist.platform.customer_service.config.PolicyServiceProperties props = mock(com.claimassist.platform.customer_service.config.PolicyServiceProperties.class);
            when(props.isReadDelegationEnabled()).thenReturn(false);
            return props;
        }

        @Bean
        PolicyQueryService policyQueryService(PolicyRepository repo, PolicyMapper mapper,
                                            com.claimassist.platform.customer_service.client.PolicyServiceAdapter adapter,
                                            com.claimassist.platform.customer_service.config.PolicyServiceProperties props) {
            return new PolicyQueryService(repo, mapper, adapter, props);
        }
    }

    private Policy ownedPolicy(long policyId, String status) {
        CoveragePlan plan = CoveragePlan.builder()
                .id(5L).name("Comprehensive").productType("HOME")
                .deductibleCents(500_00L).coverageLimitCents(1_000_000_00L).build();
        Policy policy = Policy.builder()
                .id(policyId).policyNumber("POL-" + policyId).status(status).build();
        policy.setCoveragePlan(plan);
        return policy;
    }

    @Test
    void cacheHitServesSameCallerAndAnotherCallerIsIsolated() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(CacheConfig.class)) {
            PolicyRepository repo = ctx.getBean(PolicyRepository.class);
            PolicyQueryService proxy = ctx.getBean(PolicyQueryService.class);

            // User 1 owns policy 42.
            when(repo.findByIdAndCustomerId(42L, 1L)).thenReturn(Optional.of(ownedPolicy(42L, "ACTIVE")));
            // User 2 does NOT own policy 42.
            when(repo.findByIdAndCustomerId(42L, 2L)).thenReturn(Optional.empty());

            PolicyCoverageDto first = proxy.getPolicyCoverage(42L, 1L);
            assertThat(first.status()).isEqualTo("ACTIVE");

            // Same caller -> cache hit, backend NOT hit again.
            PolicyCoverageDto second = proxy.getPolicyCoverage(42L, 1L);
            assertThat(second).isEqualTo(first);
            verify(repo, times(1)).findByIdAndCustomerId(42L, 1L);

            // Different caller, same policyId -> different cache key -> backend hit,
            // ownership enforced: user 2 is denied, never served user 1's cached data.
            assertThatThrownBy(() -> proxy.getPolicyCoverage(42L, 2L))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(repo).findByIdAndCustomerId(42L, 2L);

            // User 1's entry is untouched and still served from cache.
            assertThat(proxy.getPolicyCoverage(42L, 1L)).isEqualTo(first);
            verify(repo, times(1)).findByIdAndCustomerId(42L, 1L);
        }
    }

    @Test
    void distinctUsersWithOwnPoliciesNeverShareCacheEntries() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(CacheConfig.class)) {
            PolicyRepository repo = ctx.getBean(PolicyRepository.class);
            PolicyQueryService proxy = ctx.getBean(PolicyQueryService.class);

            // Each user owns its own policy with distinct status.
            when(repo.findByIdAndCustomerId(42L, 1L)).thenReturn(Optional.of(ownedPolicy(42L, "ACTIVE")));
            when(repo.findByIdAndCustomerId(43L, 2L)).thenReturn(Optional.of(ownedPolicy(43L, "PENDING")));

            PolicyCoverageDto a = proxy.getPolicyCoverage(42L, 1L);
            PolicyCoverageDto b = proxy.getPolicyCoverage(43L, 2L);

            assertThat(a.status()).isEqualTo("ACTIVE");
            assertThat(b.status()).isEqualTo("PENDING");
            // Keys are "42-1" and "43-2" - fully disjoint.
            verify(repo).findByIdAndCustomerId(42L, 1L);
            verify(repo).findByIdAndCustomerId(43L, 2L);
        }
    }
}