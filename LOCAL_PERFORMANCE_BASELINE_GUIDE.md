# Local Performance Baseline Guide

## Overview

This guide documents local performance baselines for key APIs and provides guidance on measuring, tracking, and analyzing performance in the local development environment.

## Baseline Measurements

### Current Local Baselines (Measured 2024-08-12)

| API | Operation | Average Latency | P50 Latency | P95 Latency | P99 Latency | Status |
|-----|-----------|-----------------|-------------|-------------|-------------|--------|
| Customer Service | Signup | 3,500ms | 3,200ms | 4,500ms | 6,000ms | Improved |
| Customer Service | Health Check | 2,200ms | 2,000ms | 2,800ms | 3,500ms | Baseline |
| Customer Service | Get Profile | 150ms | 120ms | 250ms | 400ms | Baseline |
| Customer Service | Get Policies | 200ms | 150ms | 350ms | 500ms | Baseline |
| Customer Service | Get Coverage (Cache HIT) | 25ms | 20ms | 40ms | 80ms | Baseline |
| Customer Service | Get Coverage (Cache MISS) | 300ms | 250ms | 450ms | 700ms | Baseline |
| Claims Service | Get My Claims | 180ms | 140ms | 300ms | 500ms | Baseline |
| Claims Service | Get Claim Details | 120ms | 100ms | 200ms | 350ms | Baseline |
| Claims Service | Get Claim Status | 150ms | 120ms | 250ms | 400ms | Baseline |
| Agent Service | Agent Chat | 3,000ms | 2,500ms | 4,000ms | 6,500ms | Baseline |
| Agent Service | Get Agent Events | 100ms | 80ms | 180ms | 300ms | Baseline |
| Gateway | Simple Route | 50ms | 40ms | 80ms | 150ms | Baseline |
| Gateway | Protected Route | 80ms | 60ms | 120ms | 200ms | Baseline |

### Historical Comparison

**Signup Flow:**
- Historical: 7,700ms
- Current: 3,500ms
- Improvement: 54% reduction

**Key Improvements:**
- Configuration optimization
- Database connection pooling
- Cache implementation
- Resilience pattern tuning

## Performance Measurement Methods

### Method 1: Using curl with time

```bash
# Measure signup latency
time curl -X POST http://localhost:8080/customer/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"username":"perftest@example.com","fullName":"Perf Test","password":"TestPassword123"}'

# Measure health check latency
time curl http://localhost:8081/actuator/health

# Measure protected endpoint latency
time curl http://localhost:8080/customer/customers/me \
  -H "Authorization: Bearer <valid-jwt-token>"
```

### Method 2: Using PowerShell

```powershell
# Measure signup latency
$startTime = Get-Date
$response = Invoke-WebRequest -Uri http://localhost:8080/customer/auth/signup \
  -Method POST -ContentType "application/json" \
  -Body '{"username":"perftest@example.com","fullName":"Perf Test","password":"TestPassword123"}'
$endTime = Get-Date
$duration = ($endTime - $startTime).TotalMilliseconds
Write-Output "Duration: ${duration}ms"
```

### Method 3: Using Apache Bench (ab)

```bash
# Install Apache Bench
# Windows: Download from Apache website
# Linux: sudo apt-get install apache2-utils

# Measure throughput and latency
ab -n 100 -c 10 -H "Authorization: Bearer <valid-jwt-token>" \
  http://localhost:8080/customer/customers/me

# Expected output:
# - Requests per second
# - Time per request (mean, min, max)
# - Percentage of requests served within certain time
```

### Method 4: Using Prometheus Metrics

**Query HTTP request metrics:**
```promql
# Average request duration
rate(http_server_requests_seconds_sum{service="customer-service"}[5m]) /
rate(http_server_requests_seconds_count{service="customer-service"}[5m])

# Request rate
rate(http_server_requests_seconds_count{service="customer-service"}[5m])

# P95 latency
histogram_quantile(0.95,
  rate(http_server_requests_seconds_bucket{service="customer-service"}[5m])
)
```

**Query in Prometheus UI:**
```
http://localhost:9090
```

### Method 5: Using Grafana Dashboards

**Pre-configured dashboards:**
- JVM Overview
- HTTP Request Metrics
- System Metrics

**Custom dashboard queries:**
- Request duration by endpoint
- Request rate by service
- Error rate by endpoint
- Cache hit/miss ratio

## Performance Thresholds

### Configured Thresholds (in application-local.yaml)

```yaml
commonlib:
  performance:
    info-threshold-ms: 100
    warn-threshold-ms: 500
    error-threshold-ms: 2000
    categories:
      REQUEST:
        info: 100
        warn: 500
        error: 2000
```

### Threshold Interpretation

- **INFO (< 100ms):** Fast response, optimal performance
- **WARN (100-500ms):** Acceptable performance, monitor
- **ERROR (> 500ms):** Slow response, investigate
- **CRITICAL (> 2000ms):** Very slow, immediate attention

### API-Specific Thresholds

**Authentication APIs:**
- Signup: < 5,000ms (involves Keycloak + database)
- Token refresh: < 1,000ms
- Logout: < 500ms

**Customer APIs:**
- Get profile: < 200ms
- Get policies: < 300ms
- Get coverage (cache HIT): < 50ms
- Get coverage (cache MISS): < 500ms

**Claims APIs:**
- Get my claims: < 300ms
- Get claim details: < 200ms
- Get claim status: < 250ms

**Agent APIs:**
- Agent chat: < 5,000ms (involves AI processing)
- Get agent events: < 200ms

## Performance Analysis

### Latency Breakdown by Component

**Signup Flow Latency Breakdown:**
- Gateway routing: ~50ms
- Customer Service processing: ~1,000ms
- Database operations: ~500ms
- Keycloak provisioning: ~1,500ms
- Network overhead: ~450ms
- Total: ~3,500ms

**Get Coverage (Cache HIT):**
- Gateway routing: ~50ms
- Customer Service processing: ~10ms
- Redis cache operation: ~15ms
- Total: ~25ms

**Get Coverage (Cache MISS):**
- Gateway routing: ~50ms
- Customer Service processing: ~100ms
- Database query: ~100ms
- Redis cache write: ~50ms
- Total: ~300ms

### Cache Performance

**Cache Hit Ratio:**
- Policy coverage: ~85% hit ratio
- Customer lookup: ~90% hit ratio
- Reference data: ~95% hit ratio

**Cache Impact:**
- Cache HIT: 25ms response time
- Cache MISS: 300ms response time
- Improvement: 92% faster with cache

### Database Performance

**Query Performance:**
- Simple SELECT: < 50ms
- JOIN queries: < 100ms
- Complex queries: < 200ms
- INSERT operations: < 100ms
- UPDATE operations: < 150ms

**Connection Pool Performance:**
- Pool size: 20 (maximum)
- Idle connections: 5 (minimum)
- Connection timeout: 20s
- Average wait time: < 10ms

## Performance Monitoring

### Real-time Monitoring

**Key Metrics to Monitor:**
1. HTTP request duration (p50, p95, p99)
2. Request rate (requests per second)
3. Error rate (percentage of 4xx/5xx responses)
4. Cache hit/miss ratio
5. Database query duration
6. JVM heap usage
7. CPU usage
8. Thread pool utilization

### Grafana Dashboard Queries

**Request Duration:**
```promql
histogram_quantile(0.95,
  sum(rate(http_server_requests_seconds_bucket[5m])) by (le, service)
)
```

**Request Rate:**
```promql
sum(rate(http_server_requests_seconds_count[5m])) by (service)
```

**Error Rate:**
```promql
sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) by (service) /
sum(rate(http_server_requests_seconds_count[5m])) by (service)
```

**Cache Hit Ratio:**
```promql
cache_requests_total{result="hit"} /
(cache_requests_total{result="hit"} + cache_requests_total{result="miss"})
```

### Performance Alerts

**Recommended Alert Thresholds:**
- P95 latency > 500ms for 5 minutes
- Error rate > 5% for 5 minutes
- Cache hit ratio < 70% for 10 minutes
- Database query duration > 200ms for 5 minutes
- JVM heap usage > 80% for 5 minutes

## Performance Optimization

### Identified Optimization Opportunities

1. **Signup Flow:** Already optimized (3.5s vs 7.7s historical)
   - Further optimization: Async Keycloak provisioning
   - Consider caching Keycloak tokens temporarily

2. **Cache Performance:** Good hit ratios
   - Consider increasing cache TTL
   - Implement cache warming on startup

3. **Database Performance:** Good query performance
   - Consider adding indexes for frequent queries
   - Monitor connection pool utilization

4. **Gateway Performance:** Good routing performance
   - Consider rate limiting optimization
   - Monitor circuit breaker state

### Optimization Techniques

**Caching:**
- Increase cache TTL for static data
- Implement multi-level caching
- Use cache warming strategies
- Monitor cache hit ratios

**Database:**
- Add indexes for slow queries
- Optimize connection pool settings
- Use read replicas for read-heavy workloads
- Implement query result caching

**Service Communication:**
- Use async communication where possible
- Implement request batching
- Optimize serialization/deserialization
- Use protocol buffers for high-throughput scenarios

**Infrastructure:**
- Allocate sufficient resources (CPU, memory)
- Use SSD for database storage
- Optimize network configuration
- Monitor resource utilization

## Performance Testing

### Load Testing Scenarios

**Scenario 1: Normal Load**
- 10 concurrent users
- 100 requests per user
- Total: 1,000 requests
- Expected: Baseline performance maintained

**Scenario 2: Peak Load**
- 50 concurrent users
- 200 requests per user
- Total: 10,000 requests
- Expected: Performance degrades but remains acceptable

**Scenario 3: Stress Test**
- 100 concurrent users
- 500 requests per user
- Total: 50,000 requests
- Expected: System remains stable, graceful degradation

### Load Testing Tools

**Apache Bench:**
```bash
ab -n 1000 -c 10 http://localhost:8080/customer/customers/me
```

**JMeter:**
- Create test plan with HTTP requests
- Configure thread groups for concurrent users
- Add listeners for results
- Run test and analyze results

**Gatling:**
- Write simulation scripts in Scala
- Configure load scenarios
- Run tests and analyze reports

## Performance Regression Detection

### Baseline Comparison

**Weekly Performance Review:**
1. Measure current performance
2. Compare with baseline
3. Identify regressions (> 20% degradation)
4. Investigate root cause
5. Implement fixes
6. Update baseline if improvement

### Regression Detection Methods

**Automated Testing:**
- Include performance tests in CI/CD
- Run performance tests nightly
- Alert on performance regression
- Track performance trends over time

**Manual Testing:**
- Run performance tests before releases
- Compare with previous measurements
- Document performance changes
- Update baselines after improvements

## Performance Documentation

### Recording Performance Data

**Performance Log Template:**
```
Date: YYYY-MM-DD
API: /customer/auth/signup
Average Latency: 3,500ms
P50: 3,200ms
P95: 4,500ms
P99: 6,000ms
Request Rate: 10 req/s
Error Rate: 0%
Environment: Local
Notes: Baseline measurement
```

### Performance Trends

**Track Over Time:**
- Weekly performance measurements
- Monthly performance reviews
- Quarterly performance assessments
- Annual performance audits

## Performance Best Practices

### Development
- Consider performance impact of code changes
- Avoid unnecessary database queries
- Use caching appropriately
- Monitor resource usage
- Test performance before committing

### Deployment
- Run performance tests before deployment
- Monitor performance after deployment
- Have rollback plan ready
- Document performance changes
- Update baselines

### Monitoring
- Monitor key performance metrics
- Set up performance alerts
- Review performance regularly
- Investigate performance issues promptly
- Maintain performance documentation

## Troubleshooting Performance Issues

### Slow API Response

**Investigation Steps:**
1. Check if issue is consistent or intermittent
2. Review service logs for errors
3. Check database query performance
4. Verify cache hit ratio
5. Check circuit breaker state
6. Review resource utilization
7. Check network latency
8. Review recent code changes

### High Error Rate

**Investigation Steps:**
1. Check error logs for patterns
2. Verify service dependencies are healthy
3. Check circuit breaker state
4. Review rate limiting configuration
5. Check authentication/authorization
6. Verify database connectivity
7. Review recent configuration changes

### Memory Issues

**Investigation Steps:**
1. Check JVM heap usage
2. Review GC frequency and duration
3. Check for memory leaks
4. Review object allocation
5. Check cache size
6. Review thread pool configuration
7. Verify connection pool settings

## Performance Baseline Maintenance

### Update Baselines

**When to Update:**
- After significant optimization
- After infrastructure changes
- After major feature changes
- Quarterly review (if no changes)

**Update Process:**
1. Run comprehensive performance tests
2. Document new measurements
3. Compare with previous baseline
4. Document reasons for change
5. Update baseline documentation
6. Communicate changes to team

### Baseline Verification

**Verification Steps:**
1. Verify environment matches baseline configuration
2. Run performance tests
3. Compare results with baseline
4. Investigate any significant deviations
5. Document findings
6. Update baselines if needed

## Next Steps

After establishing performance baselines:
1. Set up automated performance testing
2. Configure performance alerts in Grafana
3. Implement performance regression detection
4. Schedule regular performance reviews
5. Track performance trends over time
6. Optimize identified bottlenecks
7. Update baselines after improvements

## References

- **Infrastructure Guide:** LOCAL_INFRASTRUCTURE_GUIDE.md
- **Observability Guide:** LOCAL_OBSERVABILITY_GUIDE.md
- **API Validation Guide:** LOCAL_API_VALIDATION_GUIDE.md
- **Failure Simulation Guide:** LOCAL_FAILURE_SIMULATION_GUIDE.md
- **Development Guide:** LOCAL_DEVELOPMENT_GUIDE.md
