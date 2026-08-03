package com.claimassist.platform.customer_service.service;

import com.claimassist.platform.customer_service.config.RedisCacheConfig;
import com.claimassist.platform.customer_service.entity.Customer;
import com.claimassist.platform.customer_service.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerLookupService {

    private final CustomerRepository customerRepository;

    @Cacheable(cacheNames = RedisCacheConfig.CUSTOMER_LOOKUP_CACHE, key = "#username")
    public Optional<Customer> findByUsername(String username) {
        return customerRepository.findByUsername(username);
    }

    @CacheEvict(cacheNames = RedisCacheConfig.CUSTOMER_LOOKUP_CACHE, key = "#username")
    public void evictByUsername(String username) {
        // annotation-driven eviction
    }
}

