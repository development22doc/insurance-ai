package com.claimassist.platform.customer_service.service;

import com.claimassist.platform.customer_service.entity.CoveragePlan;
import com.claimassist.platform.customer_service.entity.Policy;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PolicyReadDelegationCacheTest {

    @Configuration
    @EnableCaching
    static class CacheConfig {
        @Bean
        CacheManager cacheManager() {
            ConcurrentMapCacheManager manager = new ConcurrentMapCacheManager();
            manager.setCacheNames(Set.of(
                    com.claimassist.platform.customer_service.config.RedisCacheConfig.POLICY_COVERAGE_CACHE,
                    com.claimassist.platform.customer_service.config.RedisCacheConfig.MY_POLICIES_CACHE));
            return manager;
        }

        @Bean
        com.claimassist.platform.customer_service.repository.PolicyRepository policyRepository() {
            return mock(com.claimassist.platform.customer_service.repository.PolicyRepository.class);
        }

        @Bean
        com.claimassist.platform.customer_service.mapper.PolicyMapper policyMapper() {
            return mock(com.claimassist.platform.customer_service.mapper.PolicyMapper.class);
        }

        @Bean
        com.claimassist.platform.customer_service.client.PolicyServiceAdapter policyServiceAdapter() {
            return mock(com.claimassist.platform.customer_service.client.PolicyServiceAdapter.class);
        }

        @Bean
        com.claimassist.platform.customer_service.config.PolicyServiceProperties policyServiceProperties() {
            com.claimassist.platform.customer_service.config.PolicyServiceProperties props = mock(com.claimassist.platform.customer_service.config.PolicyServiceProperties.class);
            when(props.isReadDelegationEnabled()).thenReturn(true);
            return props;
        }

        @Bean
        PolicyQueryService policyQueryService(com.claimassist.platform.customer_service.repository.PolicyRepository repo, com.claimassist.platform.customer_service.mapper.PolicyMapper mapper, com.claimassist.platform.customer_service.client.PolicyServiceAdapter adapter, com.claimassist.platform.customer_service.config.PolicyServiceProperties props) {
            return new PolicyQueryService(repo, mapper, adapter, props);
        }
    }

    @Test
    void cacheWithDelegation_usesAdapterAndCachesResultPerCustomer() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(CacheConfig.class)) {
            com.claimassist.platform.customer_service.repository.PolicyRepository repo = ctx.getBean(com.claimassist.platform.customer_service.repository.PolicyRepository.class);
            com.claimassist.platform.customer_service.client.PolicyServiceAdapter adapter = ctx.getBean(com.claimassist.platform.customer_service.client.PolicyServiceAdapter.class);
            com.claimassist.platform.customer_service.config.PolicyServiceProperties props = ctx.getBean(com.claimassist.platform.customer_service.config.PolicyServiceProperties.class);
            PolicyQueryService proxy = ctx.getBean(PolicyQueryService.class);

            // Setup adapter to return list for customer 7
            com.claimassist.platform.customer_service.dto.policy.PolicyResponse p = new com.claimassist.platform.customer_service.dto.policy.PolicyResponse(401L, "POL-401", "ACTIVE", "Plan X", "AUTO", null, null);
            when(adapter.getPoliciesForCustomer(7L, 7L)).thenReturn(java.util.List.of(p));

            // First call -> adapter invoked
            java.util.List<com.claimassist.platform.customer_service.dto.policy.PolicyResponse> first = proxy.getMyPolicies(7L);
            assertThat(first).hasSize(1);
            verify(adapter, times(1)).getPoliciesForCustomer(7L, 7L);

            // Second call -> should be served from cache, adapter NOT called again
            java.util.List<com.claimassist.platform.customer_service.dto.policy.PolicyResponse> second = proxy.getMyPolicies(7L);
            assertThat(second).hasSize(1);
            verify(adapter, times(1)).getPoliciesForCustomer(7L, 7L);

            // Ensure repository was never used
            verify(repo, times(0)).findByCustomerId(7L);
        }
    }
}
