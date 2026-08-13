# Phase 1.6 — Duplicate Code Audit Progress

**Session Date**: 2026-08-12

**Objective**: Audit-only phase to identify duplicate implementations across the codebase. No code changes, deletions, or refactoring in this phase.

---

## Current Step
Audit complete. All eight requested areas have been investigated.

---

## Areas Checked

### 1. Redis Cache Implementations

**AREA**: Redis Cache Implementations

**IMPLEMENTATION A**: `claims-service/src/main/java/com/claimassist/platform/claims_service/cache/CacheService.java`
- Full-featured Cache-Aside pattern implementation
- 313 lines with TTL support, pattern-based eviction, typed caching
- Includes performance logging integration
- Cache keys: customer, policy, claim, lookup prefixes
- TTLs: 300s (customer), 600s (policy), 3600s (lookup), 60s (claim status)

**IMPLEMENTATION B**: `agent-service/src/main/java/com/claimassist/platform/agent_service/cache/CacheService.java`
- Similar Cache-Aside pattern implementation
- 198 lines with TTL support, pattern-based eviction, typed caching
- Cache keys: agent:session, agent:event, agent:llm prefixes
- TTLs: 1800s (session), 300s (events), 3600s (LLM responses)
- Uses `@Autowired(required=false)` for optional RedisTemplate (local dev)

**REFERENCES/USAGE**:
- Claims CacheService used by ClaimQueryServiceImpl, ClaimCommandServiceImpl, CqrsReadModelSynchronizer
- Agent CacheService used by AgentTurnPersistenceService, AgentGenerationServiceImpl

**WHICH ONE IS ACTIVE**: Both are active in their respective services

**FUNCTIONALLY DUPLICATE**: NO - Similar pattern but different data domains (claims vs agent sessions/events)

**CONFIDENCE**: HIGH

**RECOMMENDED ONE TO KEEP**: Keep both - they serve different business domains

**REASON**: While the pattern is similar (Cache-Aside with TTL), the cache keys, TTL values, and business domains are completely different. Claims service caches customer/policy/claim data, while agent service caches session state and LLM responses. The agent-service version also uses optional injection for local development convenience.

---

### 2. Kafka Configuration

**AREA**: Kafka Configuration

**IMPLEMENTATION A**: `claims-service/src/main/java/com/claimassist/platform/claims_service/messaging/OutboxKafkaConfig.java`
- 259 lines
- Configures String/String producer/consumer for outbox pattern
- 6 claim-specific topics + 6 DLT topics (claim-update, claim-saga orchestration, claim-saga step)
- Producer config: idempotence, retries, batch size, compression
- Consumer config: manual ack, earliest offset reset
- Dead Letter Publishing with exponential backoff (4 attempts)
- @Bean methods for ProducerFactory, KafkaTemplate, ConsumerFactory, CommonErrorHandler, ConcurrentKafkaListenerContainerFactory

**IMPLEMENTATION B**: `agent-service/src/main/java/com/claimassist/platform/agent_service/messaging/OutboxKafkaConfig.java`
- 165 lines
- Nearly identical configuration structure
- Comment explicitly references claims-service: "See claims-service's identically-named class for the full rationale"
- Only 2 topics + 2 DLT topics (claim-update-request, claim-update-response)
- Same producer/consumer configuration values
- Same error handling pattern with DeadLetterPublishingRecoverer and ExponentialBackOff

**REFERENCES/USAGE**:
- Claims OutboxKafkaConfig used by OutboxEventProducer, ClaimUpdateConsumer, saga listeners
- Agent OutboxKafkaConfig used by OutboxEventProducer, AgentSagaResponseHandler

**WHICH ONE IS ACTIVE**: Both are active in their respective services

**FUNCTIONALLY DUPLICATE**: YES - Configuration pattern is nearly identical; agent-service is a subset

**CONFIDENCE**: HIGH

**RECOMMENDED ONE TO KEEP**: Consolidate to common-lib or keep as-is if domain separation is intentional

**REASON**: The agent-service OutboxKafkaConfig is clearly a subset of the claims-service version (only claim-update topics, no saga orchestration topics). The comment explicitly acknowledges this duplication. Both implement the same String/String producer/consumer pattern with identical configuration values. This could be consolidated into a shared configuration class in common-lib with topic-specific overrides per service.

---

### 3. Performance Logging

**AREA**: Performance Logging

**IMPLEMENTATION A**: `common-lib/src/main/java/com/claimassist/platform/common_lib/observability/PerformanceLogger.java`
- Single shared implementation in common-lib
- 91 lines
- Central performance logger with INFO/WARN/ERROR thresholds
- Category-specific threshold overrides
- Emits EventLogger performance events
- Used by all services

**REFERENCES/USAGE**:
- Used in customer-service (PolicyServiceImpl, CustomerService, AuthController, etc.)
- Used in claims-service (ClaimQueryServiceImpl, ClaimCommandServiceImpl, CacheService)
- Used in agent-service (AgentTurnPersistenceService, AgentGenerationServiceImpl, CacheService)
- Used by AOP aspects in common-lib (ExecutionTimeAspect, DatabaseExecutionTimeAspect, KafkaExecutionTimeAspect)

**WHICH ONE IS ACTIVE**: Single shared implementation is active

**FUNCTIONALLY DUPLICATE**: NO - No duplicates found

**CONFIDENCE**: HIGH

**RECOMMENDED ONE TO KEEP**: Keep the single shared implementation

**REASON**: Performance logging is correctly centralized in common-lib with no duplicate implementations across services.

---

### 4. Exception Handling

**AREA**: Exception Handling

**IMPLEMENTATION A**: `common-lib/src/main/java/com/claimassist/platform/common_lib/error/GlobalExceptionHandler.java`
- 304 lines
- Shared via SharedExceptionAutoConfiguration
- Covers Servlet/MVC apps (not reactive gateway)
- Handles common exceptions: BadRequestException, ResourceNotFoundException, ServiceUnavailableException, AuthenticationException, AccessDeniedException, Validation exceptions, Persistence exceptions, FeignException, CircuitBreaker exceptions, TimeoutException
- Generic Exception catch-all
- Emits EventLogger exception events
- Sensitive data sanitization

**IMPLEMENTATION B**: `customer-service/src/main/java/com/claimassist/platform/customer_service/exception/GlobalExceptionHandler.java`
- 382 lines
- @RestControllerAdvice with enhanced event logging
- Handles same exception types plus AuthenticationCredentialsNotFoundException
- Uses EnhancedApiError with additional fields (timestamp, correlationId, traceId, spanId, path, method)
- Security-focused exception events with username tracking
- Execution time tracking
- Comment notes: "SharedExceptionAutoConfiguration from common-lib is excluded in this service since customer-service provides its own enhanced exception handler with event logging"

**REFERENCES/USAGE**:
- Common-lib GlobalExceptionHandler auto-configured for all services except customer-service
- Customer-service GlobalExceptionHandler is explicitly local

**WHICH ONE IS ACTIVE**: Common-lib version active in claims-service, agent-service. Customer-service uses its enhanced local version.

**FUNCTIONALLY DUPLICATE**: NO - Customer-service intentionally provides an enhanced version

**CONFIDENCE**: HIGH

**RECOMMENDED ONE TO KEEP**: Keep both - customer-service has intentionally enhanced version

**REASON**: The customer-service GlobalExceptionHandler is explicitly documented as an enhanced version with additional features (EnhancedApiError, security event tracking, execution time). This is intentional differentiation, not accidental duplication.

---

### 5. Repository/Query Logic

**AREA**: Repository/Query Logic

**IMPLEMENTATION A**: `claims-service/src/main/java/com/claimassist/platform/claims_service/repository/OutboxEventRepository.java`
- 32 lines
- findBatchForPublishing with pessimistic write lock
- findFirstByAggregateIdAndEventType
- countStalePendingEvents
- countStaleFailedEvents
- countByStatus

**IMPLEMENTATION B**: `agent-service/src/main/java/com/claimassist/platform/agent_service/repository/OutboxEventRepository.java`
- 32 lines
- Identical method signatures and JPQL queries
- Same lock strategy
- Same stale event counting methods

**REFERENCES/USAGE**:
- Claims OutboxEventRepository used by OutboxEventPublisher
- Agent OutboxEventRepository used by OutboxEventPublisher

**WHICH ONE IS ACTIVE**: Both are active in their respective services

**FUNCTIONALLY DUPLICATE**: YES - Identical implementation

**CONFIDENCE**: HIGH

**RECOMMENDED ONE TO KEEP**: Consolidate to common-lib as a shared base repository

**REASON**: The OutboxEventRepository interfaces are identical (same methods, same queries, same lock strategy). The only difference is the package and entity reference. This could be moved to common-lib with a generic OutboxEvent entity interface or separate service-specific repositories extending a common base.

---

### 6. Mapper/Conversion Logic

**AREA**: Mapper/Conversion Logic

**IMPLEMENTATION A**: `customer-service/src/main/java/com/claimassist/platform/customer_service/mapper/PolicyMapper.java`
- MapStruct interface
- Maps Policy entity to PolicyResponse
- Specific mapping for coveragePlan.name and coveragePlan.productType

**IMPLEMENTATION B**: `claims-service/src/main/java/com/claimassist/platform/claims_service/mapper/ClaimMapper.java`
- MapStruct interface
- Maps Claim entity to ClaimResponse and ClaimSummaryResponse
- Specific mapping for role field

**IMPLEMENTATION C**: `agent-service/src/main/java/com/claimassist/platform/agent_service/mapper/AgentMapper.java`
- MapStruct interface
- Maps List<AgentMessage> to List<AgentMessageResponse>
- Auto-generated nested mapping

**REFERENCES/USAGE**:
- PolicyMapper used by PolicyServiceImpl
- ClaimMapper used by ClaimQueryServiceImpl
- AgentMapper used by agent-service

**WHICH ONE IS ACTIVE**: All three are active in their respective services

**FUNCTIONALLY DUPLICATE**: NO - Each mapper is service-specific

**CONFIDENCE**: HIGH

**RECOMMENDED ONE TO KEEP**: Keep all three

**REASON**: Each mapper handles different domain entities (Policy, Claim, AgentMessage) with different DTO structures. There is no functional overlap between these mappers.

---

### 7. Saga Recovery Implementations

**AREA**: Saga Recovery Implementations

**IMPLEMENTATION A**: `claims-service/src/main/java/com/claimassist/platform/claims_service/saga/SagaFailureRecoveryService.java`
- 137 lines
- Scheduled recovery scan for failed sagas
- Exponential backoff for retries
- Max recovery attempts limit
- Manual recovery trigger method
- Metrics integration

**REFERENCES/USAGE**:
- Used by ClaimSagaOrchestratorService
- Scheduled task runs periodically

**WHICH ONE IS ACTIVE**: Single implementation active in claims-service

**FUNCTIONALLY DUPLICATE**: NO - No duplicates found

**CONFIDENCE**: HIGH

**RECOMMENDED ONE TO KEEP**: Keep the single implementation

**REASON**: Saga recovery is only implemented in claims-service. Agent-service and customer-service do not have saga orchestration. No duplicates exist.

---

### 8. Utility Implementations

**AREA**: Utility Implementations

**IMPLEMENTATION A**: `common-lib/src/main/java/com/claimassist/platform/common_lib/observability/MDCUtility.java`
- MDC helper for correlation ID, trace ID, span ID, request ID
- 35 lines

**IMPLEMENTATION B**: `common-lib/src/main/java/com/claimassist/platform/common_lib/observability/LoggingHelper.java`
- Sensitive data masking utilities
- JSON escaping utilities
- 51 lines

**IMPLEMENTATION C**: `common-lib/src/main/java/com/claimassist/platform/common_lib/observability/ExceptionLoggingUtil.java`
- Exception logging helper with sanitization
- 30 lines

**IMPLEMENTATION D**: `agent-service/src/main/java/com/claimassist/platform/agent_service/llm/PromptUtils.java`
- LLM prompt constants
- 45 lines
- Service-specific insurance agent system prompt

**REFERENCES/USAGE**:
- MDCUtility, LoggingHelper, ExceptionLoggingUtil used across all services via common-lib
- PromptUtils used only in agent-service

**WHICH ONE IS ACTIVE**: All are active

**FUNCTIONALLY DUPLICATE**: NO - No duplicates found

**CONFIDENCE**: HIGH

**RECOMMENDED ONE TO KEEP**: Keep all

**REASON**: All utilities are in common-lib with distinct purposes (MDC management, sensitive data masking, exception logging). PromptUtils is service-specific to agent-service for LLM prompt management. No functional overlap exists.

---

## Summary of Findings

### HIGH CONFIDENCE DUPLICATES

1. **OutboxKafkaConfig** (claims-service vs agent-service)
   - Nearly identical Kafka configuration for outbox pattern
   - Agent-service is a subset (only claim-update topics)
   - Both implement same String/String producer/consumer pattern
   - Recommendation: Consider consolidating to common-lib or accept domain separation

2. **OutboxEventRepository** (claims-service vs agent-service)
   - Identical repository interface with same methods and queries
   - Same lock strategy and stale event counting
   - Recommendation: Consolidate to common-lib as shared base repository

### MEDIUM/LOW CONFIDENCE CANDIDATES

1. **CacheService** (claims-service vs agent-service)
   - Similar Cache-Aside pattern implementation
   - Different business domains (claims vs agent sessions)
   - Different cache keys and TTL values
   - Not a functional duplicate - domain separation is appropriate

2. **GlobalExceptionHandler** (common-lib vs customer-service)
   - Customer-service has intentionally enhanced version
   - Additional features: EnhancedApiError, security event tracking, execution time
   - Not a functional duplicate - intentional enhancement

### NO DUPLICATES FOUND

- Performance logging (single shared implementation in common-lib)
- Mapper/conversion logic (service-specific MapStruct mappers)
- Saga recovery (only in claims-service)
- Utility implementations (distinct purposes in common-lib, service-specific PromptUtils)

---

## Outdated Comments

No clearly outdated comments were encountered during this audit. All comments observed were accurate and current.

---

## Blockers

None identified.

---

## Next Resume Point

If consolidating duplicates in a future phase:
1. Start with OutboxEventRepository consolidation to common-lib
2. Evaluate OutboxKafkaConfig consolidation (may require domain-specific topic configuration)
3. Document any consolidation decisions in master_change_log.md

---

## Files Modified During This Phase

NONE - This was an audit-only phase.

---

## Files Created During This Phase

- `phase1_task6_progress.md` (this file)

---

## Final Status

AUDIT COMPLETE - All eight requested areas have been audited. Two high-confidence duplicates identified (OutboxKafkaConfig, OutboxEventRepository). No code changes were made in this phase.
