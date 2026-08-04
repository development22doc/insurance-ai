# CQRS - Command Query Responsibility Segregation

**Version:** 1.0.0  
**Last Updated:** August 4, 2026

---

## Overview

CQRS separates read and write operations into different models, optimized for their respective use cases.

**Write Model:** PostgreSQL (normalized, transactional)
**Read Model:** MongoDB (denormalized, optimized for queries)

---

## Architecture

### Write Flow (Commands)

```
Client Request
    ↓
API Gateway (validation)
    ↓
Claims Service (command handler)
    ↓
Claim Aggregate (domain logic)
    ↓
OutboxEvent (event creation)
    ↓
PostgreSQL Transaction
    └─ Write Claim
    └─ Write OutboxEvent (atomic)
    ↓
Transaction Commit
    ↓
OutboxPublisher (async)
    ↓
Kafka (event publishing)
```

### Read Flow (Queries)

```
Client Request
    ↓
API Gateway (validation)
    ↓
Claims Service (query handler)
    ↓
Redis Cache (check)
    ├─ Cache Hit → Return (fast)
    └─ Cache Miss:
        ↓
        MongoDB Read Model (optimized for queries)
        ↓
        Redis (populate cache)
        ↓
        Return Result
```

---

## Write Model (PostgreSQL)

### Normalized Schema

**Purpose:** Strong consistency, ACID compliance, event sourcing

**Example - Claim Write Model:**
```sql
-- Normalized tables
claims
claim_parties
claim_documents
claim_status_history
outbox_events
claim_saga_orchestrations
```

**Characteristics:**
- ✓ Transactional consistency
- ✓ Event sourcing via OutboxEvent
- ✓ Saga state tracking
- ✓ Idempotency tracking
- ✗ Not optimized for reads
- ✗ Requires joins for queries

### Command Processing

**Example: CreateClaimCommand**

```java
@Transactional
public ClaimResponse createClaim(CreateClaimRequest request, String userId) {
    // 1. Validate command
    PolicyDto policy = policyService.getPolicy(request.policyId);
    if (policy == null) throw new PolicyNotFoundException();
    
    // 2. Create aggregate
    Claim claim = Claim.create(
        UUID.randomUUID(),
        request.policyId,
        request.claimType,
        request.amount,
        userId
    );
    
    // 3. Create outbox event (same transaction)
    OutboxEvent event = OutboxEvent.create(
        UUID.randomUUID(),
        claim.getClaimId(),
        "CLAIM",
        "CLAIM_CREATED",
        1,
        convertToJson(new ClaimCreatedEvent(claim)),
        Clock.systemDefaultZone().instant()
    );
    
    // 4. Persist both (atomic)
    claimRepository.save(claim);
    outboxEventRepository.save(event);
    
    // 5. Return result
    return new ClaimResponse(
        claim.getClaimId(),
        claim.getPolicyId(),
        claim.getStatus(),
        claim.getAmount()
    );
    // Transaction commits here (both written or both rolled back)
}
```

**Key Aspects:**
- Single transaction for aggregate + event
- OutboxEvent is the source of truth for events
- Clients don't wait for async processing
- Idempotency handled in read model

---

## Read Model (MongoDB)

### Denormalized Schema

**Purpose:** Fast queries, pre-computed results, optimized for reads

**Example - Claim Read Model:**
```json
{
  "_id": ObjectId(),
  "claim_id": "550e8400-e29b-41d4-a716-446655440000",
  "policy_id": "660e8400-e29b-41d4-a716-446655440001",
  "customer_id": "770e8400-e29b-41d4-a716-446655440002",
  "customer_name": "John Doe",      # Denormalized
  "policy_holder_email": "john@ex.com",  # Denormalized
  "claim_type": "MEDICAL",
  "status": "UNDER_REVIEW",
  "amount": 5000.00,
  "parties": [
    {
      "type": "CLAIMANT",
      "name": "John Doe",
      "email": "john@example.com"
    },
    {
      "type": "BENEFICIARY",
      "name": "Jane Doe",
      "email": "jane@example.com"
    }
  ],
  "documents": [
    {
      "type": "INVOICE",
      "file_name": "invoice.pdf",
      "uploaded_at": ISODate("2026-08-04T10:30:00Z"),
      "file_size_bytes": 256000
    }
  ],
  "evaluation": {
    "agent_score": 0.85,
    "fraud_risk_level": "LOW",
    "evaluated_at": ISODate("2026-08-04T10:31:00Z"),
    "evaluator": "agent-service"
  },
  "status_history": [
    {
      "status": "INITIATED",
      "changed_at": ISODate("2026-08-04T10:30:00Z"),
      "changed_by": "api-gateway"
    },
    {
      "status": "UNDER_REVIEW",
      "changed_at": ISODate("2026-08-04T10:31:00Z"),
      "changed_by": "agent-service"
    }
  ],
  "created_at": ISODate("2026-08-04T10:30:00Z"),
  "updated_at": ISODate("2026-08-04T10:31:00Z"),
  
  "_indexes": {
    "claim_id": 1,
    "policy_id": 1,
    "customer_id": 1,
    "status": 1,
    "created_at": -1,
    "evaluation.fraud_risk_level": 1
  }
}
```

**Characteristics:**
- ✓ Optimized for queries
- ✓ Pre-computed/denormalized data
- ✓ Single document for full claim info
- ✓ Fast aggregations
- ✗ Eventual consistency (lag from write)
- ✗ Data duplication

---

### Query Handler

**Example: GetClaimQuery**

```java
@Transactional(readOnly = true)
public ClaimReadModel getClaimReadModel(String claimId) {
    // 1. Check Redis cache
    ClaimReadModel cached = redisTemplate
        .opsForValue()
        .get("claim:" + claimId);
    
    if (cached != null) {
        return cached; // Cache hit (sub-millisecond)
    }
    
    // 2. Query MongoDB read model
    ClaimReadModel model = mongoTemplate
        .findById(claimId, ClaimReadModel.class, "claims");
    
    if (model != null) {
        // 3. Populate Redis cache (TTL: 60s)
        redisTemplate.opsForValue()
            .set("claim:" + claimId, model, Duration.ofSeconds(60));
        return model;
    }
    
    throw new ClaimNotFoundException();
}

// Query all claims by customer
public List<ClaimReadModel> listClaimsByCustomer(String customerId) {
    return mongoTemplate.find(
        Query.query(Criteria.where("customer_id").is(customerId))
            .with(Sort.by(Sort.Direction.DESC, "created_at"))
            .limit(50),
        ClaimReadModel.class,
        "claims"
    );
}

// Query claims by status
public List<ClaimReadModel> listClaimsByStatus(String status) {
    return mongoTemplate.find(
        Query.query(Criteria.where("status").is(status))
            .limit(100),
        ClaimReadModel.class,
        "claims"
    );
}

// Aggregate query - fraud analysis
public List<FraudAnalysisReport> getFraudAnalysis() {
    return mongoTemplate.aggregate(
        newAggregation(
            match(Criteria.where("evaluation.fraud_risk_level").is("HIGH")),
            group("evaluation.fraud_risk_level")
                .count().as("total_claims")
                .sum("amount").as("total_amount"),
            sort(Sort.by(Sort.Direction.DESC, "total_claims"))
        ),
        "claims",
        FraudAnalysisReport.class
    ).getMappedResults();
}
```

**Query Pattern:**
```
1. Try Redis (cache)
2. If miss, query MongoDB
3. Update Redis cache
4. Return result
```

---

## Synchronization: Write → Read

### Event Flow

```
1. Command Handler (PostgreSQL)
   └─ Creates OutboxEvent
   
2. OutboxPublisher (scheduled, every 2s)
   └─ SELECT * FROM outbox_events WHERE status='PENDING'
   └─ Publish to Kafka topic
   └─ UPDATE status='PUBLISHED'
   
3. Kafka Topic (event stream)
   └─ claim-created-event (or other domain events)
   
4. Read Model Updater Listener
   └─ Consume from Kafka
   └─ Transform event data
   └─ Update MongoDB read model
   └─ Invalidate Redis cache
   └─ Commit Kafka offset
```

### Event Handler Example

```java
@Component
public class ClaimEventHandler {
    
    @KafkaListener(topics = "claim-created-event")
    public void handleClaimCreated(
        @Payload ClaimCreatedEvent event,
        @Header(KafkaHeaders.RECEIVED_PARTITION_ID) int partition,
        @Header(KafkaHeaders.OFFSET) long offset) {
        
        try {
            // Check idempotency
            if (processedEventRepository.existsById(event.getEventId())) {
                return; // Already processed
            }
            
            // Transform to read model
            ClaimReadModel readModel = new ClaimReadModel(
                event.getClaimId(),
                event.getPolicyId(),
                event.getCustomerId(),
                event.getCustomerName(),
                event.getClaimType(),
                "INITIATED",
                event.getAmount(),
                new ArrayList<>(),
                event.getCreatedAt()
            );
            
            // Update MongoDB
            mongoTemplate.save(readModel, "claims");
            
            // Invalidate Redis cache
            redisTemplate.delete("claim:" + event.getClaimId());
            
            // Record processing
            processedEventRepository.save(
                new ProcessedEvent(event.getEventId())
            );
            
        } catch (Exception e) {
            logger.error("Failed to handle ClaimCreatedEvent", e);
            // Error handling: DLT or retry logic
        }
    }
    
    @KafkaListener(topics = "claim-status-updated-event")
    public void handleClaimStatusUpdated(
        @Payload ClaimStatusUpdatedEvent event) {
        
        // Update read model with new status
        mongoTemplate.update(ClaimReadModel.class)
            .matching(Query.query(
                Criteria.where("claim_id").is(event.getClaimId())))
            .apply(new Update()
                .set("status", event.getNewStatus())
                .set("updated_at", Instant.now())
                .push("status_history", new StatusHistoryEntry(
                    event.getOldStatus(),
                    event.getNewStatus(),
                    event.getChangedAt(),
                    event.getChangedBy())))
            .first();
        
        // Invalidate cache
        redisTemplate.delete("claim:" + event.getClaimId());
    }
}
```

---

## Consistency Model

### Eventual Consistency

**Timeline:**
```
T0: User creates claim (POST /claims)
    ↓
T0: Command handler writes to PostgreSQL + OutboxEvent
    └─ Return response to client
    └─ Client sees claim exists
    
T0-T2: OutboxPublisher publishes to Kafka
    └─ Typically within 2 seconds
    
T2-T5: Event handler updates MongoDB
    └─ Typically within 2-5 seconds
    └─ Read model now consistent
    
T5+: Cache is invalidated
    └─ Subsequent reads get latest data
```

**Max Consistency Gap:** ~5 seconds (typically much faster)

### Handling Race Conditions

**Scenario: User creates claim and immediately queries**

```
Solution 1: Query Write Model (PostgreSQL)
- If reading immediately after write, read from PostgreSQL
- Return from write model for consistency

Solution 2: Distributed Cache
- Write model writes claim to Redis cache
- Return Redis-based response
- Read model updates cache eventually

Solution 3: Accept Eventual Consistency
- Document max consistency gap (5s)
- For UI, show write-time data
- Update from read model when available
```

**Implementation:**
```java
// Return from write model for fresh data
@Transactional(readOnly = true)
public ClaimResponse getClaimImmediatelyAfterCreate(String claimId) {
    // Read from PostgreSQL (strong consistency)
    Claim claim = claimRepository.findById(claimId)
        .orElseThrow();
    
    return new ClaimResponse(
        claim.getClaimId(),
        claim.getPolicyId(),
        claim.getStatus(),
        claim.getAmount()
    );
}
```

---

## Benefits & Tradeoffs

### Benefits

✅ **Scalability:** Read and write models scale independently
✅ **Performance:** Reads optimized separately from writes
✅ **Flexibility:** Different storage engines for different needs
✅ **Clear Separation:** Simpler to reason about each model
✅ **Event Sourcing:** Full event history available

### Tradeoffs

⚠️ **Eventual Consistency:** Lag between write and read models
⚠️ **Complexity:** More moving parts to manage
⚠️ **Data Duplication:** Denormalization in read model
⚠️ **Synchronization Issues:** Event loss scenarios
⚠️ **Testing:** More scenarios to test

---

## Best Practices

1. **Keep Models Simple:** One write model, one read model per aggregate
2. **Use Events:** Events are the source of truth
3. **Handle Failures:** Plan for event handler failures
4. **Cache Strategically:** Redis cache between PostgreSQL and client
5. **Monitor Lag:** Track synchronization lag between models
6. **Idempotency:** All event handlers must be idempotent
7. **Versioning:** Version events for schema evolution

---


