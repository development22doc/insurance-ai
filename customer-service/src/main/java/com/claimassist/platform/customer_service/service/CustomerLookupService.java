package com.claimassist.platform.customer_service.service;

import com.claimassist.platform.customer_service.config.RedisCacheConfig;
import com.claimassist.platform.customer_service.entity.Customer;
import com.claimassist.platform.customer_service.repository.CustomerRepository;
import com.claimassist.platform.common_lib.observability.LoggingConstants;
import com.claimassist.platform.common_lib.observability.event.EventLogger;
import com.claimassist.platform.common_lib.observability.PerformanceLogger;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class CustomerLookupService {

    private final CustomerRepository customerRepository;
    private final CacheManager cacheManager;
    private final EventLogger eventLogger;
    private final PerformanceLogger performanceLogger;

    public Optional<Customer> findByUsername(String username) {
        long start = System.currentTimeMillis();
        Cache cache = cacheManager.getCache(RedisCacheConfig.CUSTOMER_LOOKUP_CACHE);
        if (cache != null) {
            Cache.ValueWrapper wrapper;
            try {
                wrapper = cache.get(username);
            } catch (RuntimeException e) {
                log.warn("customerLookup cache GET failed for username={} - falling back to DB", username, e);
                wrapper = null;
            }
            if (wrapper != null) {
                long duration = System.currentTimeMillis() - start;
                Map<String,Object> hit = new java.util.HashMap<>();
                hit.put("cacheKey", username);
                hit.put("event", "CACHE_HIT");
                hit.put("executionTimeMs", duration);
                eventLogger.logBusinessEvent("customer-service", "customer-service", hit);
                performanceLogger.log("CACHE", "cache.lookup", duration,
                        Map.of("operation", "cache_hit", "cacheName", RedisCacheConfig.CUSTOMER_LOOKUP_CACHE));
                return Optional.ofNullable((Customer) wrapper.get());
            } else {
                long duration = System.currentTimeMillis() - start;
                Map<String,Object> miss = new java.util.HashMap<>();
                miss.put("cacheKey", username);
                miss.put("event", "CACHE_MISS");
                miss.put("executionTimeMs", duration);
                eventLogger.logBusinessEvent("customer-service", "customer-service", miss);
                performanceLogger.log("CACHE", "cache.lookup", duration,
                        Map.of("operation", "cache_miss", "cacheName", RedisCacheConfig.CUSTOMER_LOOKUP_CACHE));
            }
        }

        long dbStart = System.currentTimeMillis();
        Optional<Customer> found = customerRepository.findByUsername(username);
        long dbDuration = System.currentTimeMillis() - dbStart;
        Map<String,Object> dbDetails = new java.util.HashMap<>();
        dbDetails.put("username", username);
        dbDetails.put("event", found.isPresent() ? "CUSTOMER_FOUND" : "CUSTOMER_NOT_FOUND");
        dbDetails.put("executionTimeMs", dbDuration);
        eventLogger.logDatabaseEvent("customer-service", "customer-service", dbDuration, dbDetails);
        performanceLogger.log("REPOSITORY", "repository.customer.find", dbDuration,
                Map.of("username", username, "found", found.isPresent()));

        // populate cache when present
        if (cache != null && found.isPresent()) {
            try {
                cache.put(username, found.get());
            } catch (RuntimeException e) {
                log.warn("customerLookup cache PUT failed for username={} - DB result returned without caching", username, e);
            }
            Map<String,Object> evict = new java.util.HashMap<>();
            evict.put("cacheKey", username);
            evict.put("event", "CACHE_MISS_POPULATED");
            eventLogger.logBusinessEvent("customer-service", "customer-service", evict);
        }

        return found;
    }

    public void evictByUsername(String username) {
        Cache cache = cacheManager.getCache(RedisCacheConfig.CUSTOMER_LOOKUP_CACHE);
        if (cache != null) {
            long start = System.currentTimeMillis();
            try {
                cache.evict(username);
            } catch (RuntimeException e) {
                log.warn("customerLookup cache EVICT failed for username={} - stale entry bounded by TTL", username, e);
            }
            long duration = System.currentTimeMillis() - start;
            Map<String,Object> details = new java.util.HashMap<>();
            details.put("cacheKey", "customer:" + username);
            details.put("event", "CACHE_EVICTED");
            details.put("executionTimeMs", duration);
            details.put("correlationId", MDC.get(LoggingConstants.MDC_CORRELATION_ID));
            eventLogger.logBusinessEvent("customer-service", "customer-service", details);
            Map<String,Object> perfDetails = new java.util.HashMap<>();
            perfDetails.put("operation", "cache_evict");
            perfDetails.put("cacheName", RedisCacheConfig.CUSTOMER_LOOKUP_CACHE);
            performanceLogger.log("CACHE", "cache.evict", duration, perfDetails);
        }
    }
}

