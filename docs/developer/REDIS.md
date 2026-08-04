# Redis - Caching & Session Management

**Version:** 1.0.0  
**Last Updated:** August 4, 2026

---

## Overview

Redis provides distributed caching and session management for high-performance reads and reduced database load.

**Version:** Redis 7  
**Port:** 6379  
**Protocol:** RESP3  
**Persistence:** RDB (production)

---

## Cache Architecture

### Cache Layers

```
Client Request
    ↓
Redis (L1 Cache) ← Fast, sub-millisecond
    ├─ Hit: Return
    └─ Miss:
        ↓
        PostgreSQL / MongoDB (L2 Source)
            ↓
            Load data
            ↓
            Update Redis cache
            ↓
            Return to client
```

### Cache Patterns

#### Cache-Aside Pattern (Lazy Loading)

```java
public ClaimResponse getClaim(String claimId) {
    // 1. Check cache
    ClaimResponse cached = redisTemplate
        .opsForValue()
        .get("claim:" + claimId);
    
    if (cached != null) {
        return cached; // Cache hit
    }
    
    // 2. Load from database
    Claim claim = claimRepository.findById(claimId)
        .orElseThrow(ClaimNotFoundException::new);
    
    ClaimResponse response = new ClaimResponse(claim);
    
    // 3. Update cache
    redisTemplate.opsForValue()
        .set("claim:" + claimId, response, Duration.ofSeconds(60));
    
    return response;
}
```

#### Write-Through Pattern (Update Cache on Write)

```java
@Transactional
public void updateClaimStatus(String claimId, ClaimStatus newStatus) {
    // 1. Update database
    Claim claim = claimRepository.findById(claimId)
        .orElseThrow();
    claim.setStatus(newStatus);
    claimRepository.save(claim);
    
    // 2. Update cache
    redisTemplate.delete("claim:" + claimId); // Invalidate
    
    // 3. Optional: Warm cache with new value
    redisTemplate.opsForValue()
        .set("claim:" + claimId, new ClaimResponse(claim), 
             Duration.ofSeconds(60));
}
```

---

## Cache Keys

### Key Naming Strategy

```
Pattern: {entity}:{id}[:{version}]

Examples:
customer:550e8400-e29b-41d4-a716-446655440000
policy:660e8400-e29b-41d4-a716-446655440001
claim:770e8400-e29b-41d4-a716-446655440002
claim:status:770e8400-e29b-41d4-a716-446655440002
lookup:claim-types
lookup:claim-statuses
session:{session_id}
```

### Cached Data

| Entity | TTL | Pattern | Purpose |
|--------|-----|---------|---------|
| Customer | 300s | `customer:{id}` | Profile data |
| Policy | 600s | `policy:{id}` | Policy details |
| Claim | 60s | `claim:{id}` | Full claim info |
| Claim Status | 30s | `claim:status:{id}` | Current status only |
| Lookup Tables | 3600s | `lookup:{table}` | Static reference data |
| Session | 3600s | `session:{id}` | User session data |

---

## Connection Pool

### Configuration

```yaml
spring.redis:
  host: localhost
  port: 6379
  password: null                    # No password in dev
  timeout: 2000ms                  # Connection timeout
  database: 0                       # Default database
  
  jedis:
    pool:
      max-active: 20               # Max connections
      max-idle: 10                 # Max idle connections
      min-idle: 5                  # Min idle connections
      max-wait: -1ms               # Wait indefinitely
      time-between-eviction-runs-millis: 30000
      min-evictable-idle-time-millis: 60000
```

### Pool Behavior

```
Request arrives:
    ↓
Get connection from pool:
  - If available: Return immediately
  - If not available:
    - Create new (if < max-active)
    - OR wait (if >= max-active)
    
After request:
  - Return connection to pool
  - Idle connection evicted if > max-idle
```

### Monitoring Pool

```bash
# Monitor connections
redis-cli INFO stats

# Output includes:
# connected_clients: 5
# blocked_clients: 0
# used_memory: 1.5MB
```

---

## Caching Strategies

### Temporal Cache Expiration

```java
// Short TTL for frequently changing data
redisTemplate.opsForValue()
    .set("claim:status:" + claimId, status, Duration.ofSeconds(30));

// Medium TTL for regular data
redisTemplate.opsForValue()
    .set("claim:" + claimId, claim, Duration.ofSeconds(60));

// Long TTL for static reference data
redisTemplate.opsForValue()
    .set("lookup:claim-types", types, Duration.ofHours(1));
```

### Invalidation Strategy

**On Update:**
```java
// 1. Delete exact key
redisTemplate.delete("claim:" + claimId);

// 2. Or pattern delete (slower)
Set<String> keys = redisTemplate.keys("claim:*");
if (!keys.isEmpty()) {
    redisTemplate.delete(keys);
}

// 3. Or delete by pattern (Lua script for atomic operation)
script.execute(
    "return redis.call('del', unpack(redis.call('keys', 'claim:*')))",
    Collections.emptyList()
);
```

**Cascade Invalidation:**
```java
// When policy updates, invalidate related caches
@Transactional
public void updatePolicy(Policy policy) {
    policyRepository.save(policy);
    
    // Invalidate all related claims
    Set<String> claimIds = claimRepository
        .findAllByPolicyId(policy.getPolicyId())
        .stream()
        .map(Claim::getClaimId)
        .collect(Collectors.toSet());
    
    claimIds.forEach(id -> 
        redisTemplate.delete("claim:" + id)
    );
    
    // Invalidate policy cache
    redisTemplate.delete("policy:" + policy.getPolicyId());
}
```

---

## Spring Cache Abstraction

### Cache Manager Configuration

```java
@Configuration
public class CacheConfig {
    
    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory();
    }
    
    @Bean
    public RedisCacheManager cacheManager(
            LettuceConnectionFactory connectionFactory) {
        
        RedisCacheConfiguration config = RedisCacheConfiguration
            .defaultCacheConfig()
            .entryTtl(Duration.ofSeconds(60))
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(new GenericJackson2JsonRedisSerializer()));
        
        return RedisCacheManager
            .create(connectionFactory)
            .cacheDefaults(config);
    }
}
```

### @Cacheable Annotation

```java
@Service
public class ClaimService {
    
    @Cacheable(
        value = "claims",
        key = "#claimId",
        cacheManager = "cacheManager"
    )
    public ClaimResponse getClaimCached(String claimId) {
        // This method only called on cache miss
        Claim claim = claimRepository.findById(claimId)
            .orElseThrow();
        return new ClaimResponse(claim);
    }
    
    @CacheEvict(
        value = "claims",
        key = "#claimId"
    )
    public void updateClaim(String claimId, UpdateClaimRequest req) {
        // Cache invalidated automatically
        Claim claim = claimRepository.findById(claimId)
            .orElseThrow();
        claim.update(req);
        claimRepository.save(claim);
    }
}
```

---

## Performance Optimization

### Pipeline (Batch Operations)

```java
// Without pipeline: N+1 Redis calls
List<Claim> claims = claimIds.stream()
    .map(id -> redisTemplate.opsForValue().get("claim:" + id))
    .collect(Collectors.toList());

// With pipeline: 1 Redis call
List<Object> claims = redisTemplate.executePipelined(
    (RedisCallback<Void>) connection -> {
        StringRedisConnection stringConnection = 
            (StringRedisConnection) connection;
        
        for (String claimId : claimIds) {
            stringConnection.get("claim:" + claimId);
        }
        return null;
    }
);
```

### Compression

```java
// Large values should be compressed
String compressedValue = compress(jsonString);
redisTemplate.opsForValue()
    .set("large_claim:" + claimId, compressedValue, 
         Duration.ofSeconds(60));

// On retrieval, decompress
String value = (String) redisTemplate.opsForValue()
    .get("large_claim:" + claimId);
String decompressed = decompress(value);
```

---

## Monitoring & Debugging

### Health Check

```java
@Component
public class RedisHealthIndicator extends AbstractHealthIndicator {
    
    @Override
    protected void doHealthCheck(Health.Builder builder) {
        try {
            redisTemplate.getConnectionFactory()
                .getConnection()
                .ping();
            
            builder.up()
                .withDetail("cache", "Redis")
                .withDetail("host", "localhost")
                .withDetail("port", 6379);
        } catch (Exception e) {
            builder.down()
                .withDetail("error", e.getMessage());
        }
    }
}
```

### Metrics Export

```yaml
management.metrics.export.prometheus.enabled: true

# Prometheus endpoint: /actuator/prometheus
# Search for: redis_* metrics
```

### Debugging Commands

```bash
# Connected clients
redis-cli CLIENT LIST

# Memory usage
redis-cli INFO memory

# All keys
redis-cli KEYS "*"

# Specific key details
redis-cli GET "claim:123"
redis-cli TTL "claim:123"

# Cache stats
redis-cli INFO stats

# Monitor in real-time
redis-cli MONITOR
```

---

## Production Considerations

### Persistence

**RDB (Snapshot):**
```
save 900 1        # Save after 900 sec if 1 key changed
save 300 10       # Save after 300 sec if 10 keys changed
save 60 10000     # Save after 60 sec if 10000 keys changed
```

**AOF (Append-Only File):**
```
appendonly yes
appendfsync everysec  # Fsync every second
```

### Eviction Policy

```
maxmemory-policy allkeys-lru  # Remove least recently used keys
# when max memory reached
```

### Replication

```
# Master-replica setup for HA
replicaof master-host master-port
```

### Clustering

```
# Redis Cluster for horizontal scaling
cluster-enabled yes
cluster-node-timeout 15000
```

---

## Troubleshooting

### High Memory Usage

```
Check:
1. redis-cli INFO memory
2. redis-cli KEYS count
3. TTLs not being respected

Solution:
1. Increase max-memory limit
2. Implement aggressive TTLs
3. Use eviction policy
4. Consider compression
```

### Slow Operations

```
Check:
1. redis-cli SLOWLOG GET 10
2. redis-cli CLIENT LIST

Solution:
1. Use pipelining for batch ops
2. Avoid expensive key patterns
3. Use local Redis (reduce network latency)
4. Consider Redis Cluster
```

### Cache Misses

```
Check:
1. TTL too short?
2. Cache invalidation too aggressive?
3. Pattern keys not matching?

Solution:
1. Increase TTL
2. Review invalidation logic
3. Use consistent key naming
```

---


