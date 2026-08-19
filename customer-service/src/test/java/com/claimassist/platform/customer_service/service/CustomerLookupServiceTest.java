package com.claimassist.platform.customer_service.service;

import com.claimassist.platform.common_lib.observability.PerformanceLogger;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import com.claimassist.platform.customer_service.config.RedisCacheConfig;
import com.claimassist.platform.customer_service.entity.Customer;
import com.claimassist.platform.customer_service.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerLookupServiceTest {

    private CustomerRepository customerRepository;
    private CacheManager cacheManager;
    private Cache cache;
    private CustomerLookupService service;

    private Customer customer() {
        return Customer.builder().id(7L).username("alice@example.com").fullName("Alice A").build();
    }

    @BeforeEach
    void setUp() {
        customerRepository = mock(CustomerRepository.class);
        cacheManager = mock(CacheManager.class);
        cache = mock(Cache.class);
        when(cacheManager.getCache(RedisCacheConfig.CUSTOMER_LOOKUP_CACHE)).thenReturn(cache);
        service = new CustomerLookupService(customerRepository, cacheManager,
                mock(EventLogger.class), mock(PerformanceLogger.class));
    }

    @Test
    void returnsCachedCustomerOnCacheHit() {
        Cache.ValueWrapper wrapper = mock(Cache.ValueWrapper.class);
        Customer cached = customer();
        when(wrapper.get()).thenReturn(cached);
        when(cache.get("alice@example.com")).thenReturn(wrapper);

        Optional<Customer> result = service.findByUsername("alice@example.com");

        assertThat(result).isPresent().get().isEqualTo(cached);
        verify(customerRepository, never()).findByUsername("alice@example.com");
    }

    @Test
    void fallsBackToDbAndPopulatesCacheOnMiss() {
        Customer dbCustomer = customer();
        when(cache.get("alice@example.com")).thenReturn(null);
        when(customerRepository.findByUsername("alice@example.com")).thenReturn(Optional.of(dbCustomer));

        Optional<Customer> result = service.findByUsername("alice@example.com");

        assertThat(result).isPresent().get().isEqualTo(dbCustomer);
        verify(cache).put("alice@example.com", dbCustomer);
    }

    @Test
    void returnsEmptyWhenNotFoundInCacheOrDb() {
        when(cache.get("nobody@example.com")).thenReturn(null);
        when(customerRepository.findByUsername("nobody@example.com")).thenReturn(Optional.empty());

        assertThat(service.findByUsername("nobody@example.com")).isEmpty();
    }

    @Test
    void fallsBackToDbWhenCacheGetThrows() {
        doThrow(new RuntimeException("redis down"))
                .when(cache).get("alice@example.com");
        Customer dbCustomer = customer();
        when(customerRepository.findByUsername("alice@example.com")).thenReturn(Optional.of(dbCustomer));

        assertThat(service.findByUsername("alice@example.com")).isPresent();
    }

    @Test
    void skipsCacheWhenNoCacheConfigured() {
        when(cacheManager.getCache(RedisCacheConfig.CUSTOMER_LOOKUP_CACHE)).thenReturn(null);
        when(customerRepository.findByUsername("alice@example.com")).thenReturn(Optional.of(customer()));

        assertThat(service.findByUsername("alice@example.com")).isPresent();
    }

    @Test
    void evictByUsernameEvictsFromCache() {
        service.evictByUsername("alice@example.com");
        verify(cache).evict("alice@example.com");
    }

    @Test
    void evictByUsernameToleratesCacheFailure() {
        doThrow(new RuntimeException("redis down")).when(cache).evict("alice@example.com");
        service.evictByUsername("alice@example.com");
    }

    @Test
    void evictByUsernameDoesNothingWhenNoCacheConfigured() {
        when(cacheManager.getCache(RedisCacheConfig.CUSTOMER_LOOKUP_CACHE)).thenReturn(null);
        service.evictByUsername("alice@example.com");
    }
}
