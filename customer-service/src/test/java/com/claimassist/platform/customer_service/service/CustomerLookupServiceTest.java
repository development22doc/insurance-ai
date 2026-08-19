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
import org.springframework.cache.support.SimpleValueWrapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerLookupServiceTest {

    private CustomerRepository customerRepository;
    private CacheManager cacheManager;
    private Cache cache;
    private EventLogger eventLogger;
    private PerformanceLogger performanceLogger;
    private CustomerLookupService service;
    private Customer customer;

    @BeforeEach
    void setUp() {
        customerRepository = mock(CustomerRepository.class);
        cacheManager = mock(CacheManager.class);
        cache = mock(Cache.class);
        eventLogger = mock(EventLogger.class);
        performanceLogger = mock(PerformanceLogger.class);
        service = new CustomerLookupService(customerRepository, cacheManager, eventLogger, performanceLogger);
        customer = Customer.builder().id(1L).username("alice@example.com").fullName("Alice").build();
        when(cacheManager.getCache(RedisCacheConfig.CUSTOMER_LOOKUP_CACHE)).thenReturn(cache);
    }

    @Test
    void findByUsername_cacheHit_returnsCachedCustomerWithoutDbCall() {
        when(cache.get("alice@example.com")).thenReturn(new SimpleValueWrapper(customer));

        Optional<Customer> result = service.findByUsername("alice@example.com");

        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("alice@example.com");
        verify(customerRepository, never()).findByUsername(anyString());
        verify(eventLogger).logBusinessEvent(anyString(), anyString(), any());
    }

    @Test
    void findByUsername_cacheMiss_loadsDbAndPopulatesCache() {
        when(cache.get("bob@example.com")).thenReturn(null);
        when(customerRepository.findByUsername("bob@example.com")).thenReturn(Optional.of(customer));

        Optional<Customer> result = service.findByUsername("bob@example.com");

        assertThat(result).isPresent();
        verify(cache).put("bob@example.com", customer);
        verify(eventLogger).logDatabaseEvent(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void findByUsername_cacheMissNoCustomer_returnsEmptyAndDoesNotCache() {
        when(cache.get("ghost@example.com")).thenReturn(null);
        when(customerRepository.findByUsername("ghost@example.com")).thenReturn(Optional.empty());

        Optional<Customer> result = service.findByUsername("ghost@example.com");

        assertThat(result).isEmpty();
        verify(cache, never()).put(anyString(), any());
    }

    @Test
    void findByUsername_cacheGetThrows_fallsBackToDb() {
        when(cache.get("a@b.com")).thenThrow(new IllegalStateException("redis down"));
        when(customerRepository.findByUsername("a@b.com")).thenReturn(Optional.of(customer));

        Optional<Customer> result = service.findByUsername("a@b.com");

        assertThat(result).isPresent();
        verify(customerRepository).findByUsername("a@b.com");
    }

    @Test
    void findByUsername_cachePutThrows_returnsDbResultWithoutCaching() {
        when(cache.get("c@d.com")).thenReturn(null);
        when(customerRepository.findByUsername("c@d.com")).thenReturn(Optional.of(customer));
        org.mockito.Mockito.doThrow(new IllegalStateException("redis down")).when(cache).put("c@d.com", customer);

        Optional<Customer> result = service.findByUsername("c@d.com");

        assertThat(result).isPresent();
    }

    @Test
    void evictByUsername_evictsFromCacheAndLogs() {
        service.evictByUsername("alice@example.com");

        verify(cache).evict("alice@example.com");
        verify(eventLogger).logBusinessEvent(anyString(), anyString(), any());
    }

    @Test
    void evictByUsername_cacheEvictThrows_stillLogs() {
        org.mockito.Mockito.doThrow(new IllegalStateException("redis down")).when(cache).evict("alice@example.com");

        service.evictByUsername("alice@example.com");

        verify(eventLogger).logBusinessEvent(anyString(), anyString(), any());
    }

    @Test
    void findByUsername_cacheAbsent_skipsCacheReadsDb() {
        when(cacheManager.getCache(RedisCacheConfig.CUSTOMER_LOOKUP_CACHE)).thenReturn(null);
        when(customerRepository.findByUsername("z@z.com")).thenReturn(Optional.of(customer));

        Optional<Customer> result = service.findByUsername("z@z.com");

        assertThat(result).isPresent();
    }
}