# Database Documentation

**Version:** 1.0.0  
**Last Updated:** August 4, 2026

---

## Overview

The insurance AI platform uses a multi-database strategy:

- **PostgreSQL 16** - Transactional data, event sourcing, saga state
- **MongoDB 6.0** - Read models, audit logs, denormalized views
- **Redis 7** - Distributed cache, sessions

---

## PostgreSQL (Transactional Database)

### Connection Details

**Local Development:**
- **Host:** localhost
- **Port:** 5432
- **Username:** claimassist
- **Password:** claimassist

**Connection String:**
```
jdbc:postgresql://localhost:5432/claims_db
```

### Databases

#### 1. claims_db
Core claims processing database

#### 2. customer_db
Customer and policy information

#### 3. keycloak
Keycloak authentication provider

### Schema: claims_db

#### claims Table
```sql
CREATE TABLE claims (
    claim_id UUID PRIMARY KEY,
    policy_id UUID NOT NULL,
    claim_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'INITIATED',
    amount DECIMAL(12, 2) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

-- Indices
CREATE INDEX idx_claims_policy_id ON claims(policy_id);
CREATE INDEX idx_claims_status ON claims(status);
CREATE INDEX idx_claims_created_at ON claims(created_at DESC);
```

**Fields:**
- `claim_id` - Unique claim identifier (UUID)
- `policy_id` - Reference to customer service policy
- `claim_type` - MEDICAL, AUTO, PROPERTY, etc.
- `status` - INITIATED, UNDER_REVIEW, APPROVED, REJECTED, PAID
- `amount` - Claim amount in currency
- `description` - Claim details
- `created_at` - Claim creation timestamp
- `updated_at` - Last modification timestamp
- `created_by` - User who created claim
- `updated_by` - User who last modified

---

#### claim_parties Table
```sql
CREATE TABLE claim_parties (
    party_id UUID PRIMARY KEY,
    claim_id UUID NOT NULL REFERENCES claims(claim_id) ON DELETE CASCADE,
    party_type VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    relationship VARCHAR(50),
    contact_email VARCHAR(255),
    contact_phone VARCHAR(20),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indices
CREATE INDEX idx_claim_parties_claim_id ON claim_parties(claim_id);
CREATE INDEX idx_claim_parties_type ON claim_parties(party_type);
```

**Fields:**
- `party_id` - Unique party identifier
- `claim_id` - Reference to claim
- `party_type` - CLAIMANT, BENEFICIARY, WITNESS, DEFENDANT
- `name` - Person's full name
- `relationship` - Relationship to claimant
- `contact_email` - Email address
- `contact_phone` - Phone number

---

#### claim_documents Table
```sql
CREATE TABLE claim_documents (
    document_id UUID PRIMARY KEY,
    claim_id UUID NOT NULL REFERENCES claims(claim_id) ON DELETE CASCADE,
    document_type VARCHAR(50) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(1024) NOT NULL,
    file_size_bytes BIGINT,
    mime_type VARCHAR(100),
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    uploaded_by VARCHAR(255)
);

-- Indices
CREATE INDEX idx_claim_documents_claim_id ON claim_documents(claim_id);
CREATE INDEX idx_claim_documents_type ON claim_documents(document_type);
```

**Fields:**
- `document_id` - Unique document identifier
- `claim_id` - Reference to claim
- `document_type` - INVOICE, RECEIPT, POLICY, PRESCRIPTION, etc.
- `file_name` - Original filename
- `file_path` - Storage path
- `file_size_bytes` - Document size
- `mime_type` - Content type
- `uploaded_at` - Upload timestamp
- `uploaded_by` - User who uploaded

---

#### claim_status_history Table
```sql
CREATE TABLE claim_status_history (
    history_id UUID PRIMARY KEY,
    claim_id UUID NOT NULL REFERENCES claims(claim_id) ON DELETE CASCADE,
    old_status VARCHAR(50),
    new_status VARCHAR(50) NOT NULL,
    status_reason TEXT,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    changed_by VARCHAR(255)
);

-- Indices
CREATE INDEX idx_claim_status_history_claim_id ON claim_status_history(claim_id);
CREATE INDEX idx_claim_status_history_changed_at ON claim_status_history(changed_at DESC);
```

**Purpose:** Audit trail for claim status changes

**Fields:**
- `history_id` - Unique history entry identifier
- `claim_id` - Reference to claim
- `old_status` - Previous status
- `new_status` - New status
- `status_reason` - Reason for change
- `changed_at` - Timestamp of change
- `changed_by` - User making change

---

#### outbox_events Table
```sql
CREATE TABLE outbox_events (
    event_id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    event_version INTEGER DEFAULT 1,
    payload TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER DEFAULT 0,
    last_retry_at TIMESTAMP,
    published_at TIMESTAMP,
    error_message TEXT,
    
    CONSTRAINT check_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

-- Indices for efficient polling
CREATE INDEX idx_outbox_status ON outbox_events(status, created_at);
CREATE INDEX idx_outbox_published_at ON outbox_events(published_at);
CREATE INDEX idx_outbox_aggregate_id ON outbox_events(aggregate_id);
```

**Purpose:** Transactional Outbox for reliable event publishing

**Fields:**
- `event_id` - Unique event identifier
- `aggregate_id` - ID of aggregate that created event (claim_id, saga_id, etc.)
- `aggregate_type` - Type of aggregate (CLAIM, SAGA, etc.)
- `event_type` - CLAIM_CREATED, CLAIM_APPROVED, etc.
- `event_version` - Event schema version
- `payload` - Event data as JSON
- `created_at` - Event creation timestamp
- `status` - PENDING, PUBLISHED, FAILED
- `retry_count` - Number of publish attempts
- `last_retry_at` - Last retry timestamp
- `published_at` - Successfully published timestamp
- `error_message` - Error details if failed

**Publishing Logic:**
```
OutboxPublisher scheduled task (every 2 seconds):
1. SELECT * FROM outbox_events WHERE status = 'PENDING' LIMIT 100
2. FOR EACH event:
   a. Publish to Kafka
   b. UPDATE status = 'PUBLISHED'
3. Commit transaction
4. IF publish fails:
   a. Increment retry_count
   b. Calculate backoff: min(base * multiplier^retry_count, max)
   c. On max retries: status = 'FAILED'
   d. Insert into dead_letter_queue
```

---

#### claim_saga_orchestrations Table
```sql
CREATE TABLE claim_saga_orchestrations (
    saga_id UUID PRIMARY KEY,
    claim_id UUID NOT NULL REFERENCES claims(claim_id) ON DELETE CASCADE,
    saga_type VARCHAR(100) NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    state VARCHAR(50) NOT NULL,
    steps_completed INTEGER DEFAULT 0,
    total_steps INTEGER,
    timeout_at TIMESTAMP,
    recovery_attempts INTEGER DEFAULT 0,
    last_recovery_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    
    CONSTRAINT check_state CHECK (state IN 
        ('INITIATED', 'IN_PROGRESS', 'COMPLETED', 
         'COMPENSATED', 'FAILED', 'TIMED_OUT'))
);

-- Indices
CREATE INDEX idx_saga_claim_id ON claim_saga_orchestrations(claim_id);
CREATE INDEX idx_saga_state ON claim_saga_orchestrations(state);
CREATE INDEX idx_saga_timeout_at ON claim_saga_orchestrations(timeout_at);
CREATE INDEX idx_saga_updated_at ON claim_saga_orchestrations(updated_at);
```

**Purpose:** Saga orchestration state tracking

**Fields:**
- `saga_id` - Unique saga instance identifier
- `claim_id` - Associated claim
- `saga_type` - CLAIM_PROCESSING, PAYMENT, etc.
- `action_type` - CREATE_CLAIM, APPROVE_CLAIM, etc.
- `state` - Current state in state machine
- `steps_completed` - Number of steps executed
- `total_steps` - Expected total steps
- `timeout_at` - Saga timeout timestamp (180s from start)
- `recovery_attempts` - Number of recovery attempts
- `last_recovery_at` - Last recovery attempt timestamp
- `created_at` - Saga start time
- `updated_at` - Last update time
- `completed_at` - Saga completion time

**State Transitions:**
```
INITIATED
  ↓
IN_PROGRESS
  ├─→ COMPLETED (all steps successful)
  ├─→ COMPENSATED (compensation triggered)
  ├─→ FAILED (unrecoverable error)
  └─→ TIMED_OUT (180s timeout exceeded)
```

---

#### processed_events Table
```sql
CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(event_id, aggregate_id, event_type)
);

-- Indices
CREATE INDEX idx_processed_events_aggregate_id ON processed_events(aggregate_id);
CREATE INDEX idx_processed_events_processed_at ON processed_events(processed_at);
```

**Purpose:** Idempotency tracking for event consumers

**Logic:**
```
On receiving event from Kafka:
1. SELECT * FROM processed_events WHERE event_id = ?
2. IF found:
   a. Skip processing (already processed)
   b. Commit offset
3. ELSE:
   a. INSERT INTO processed_events
   b. Process event
   c. UPDATE domain model
   d. Commit transaction
   e. Commit Kafka offset
```

---

### Schema: customer_db

#### customers Table
```sql
CREATE TABLE customers (
    customer_id UUID PRIMARY KEY,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    date_of_birth DATE,
    email VARCHAR(255) UNIQUE,
    phone VARCHAR(20),
    kyc_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    kyc_verified_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indices
CREATE INDEX idx_customers_email ON customers(email);
CREATE INDEX idx_customers_kyc_status ON customers(kyc_status);
```

#### policies Table
```sql
CREATE TABLE policies (
    policy_id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customers(customer_id),
    policy_type VARCHAR(50) NOT NULL,
    coverage_amount DECIMAL(12, 2) NOT NULL,
    premium DECIMAL(10, 2) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    start_date DATE NOT NULL,
    end_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indices
CREATE INDEX idx_policies_customer_id ON policies(customer_id);
CREATE INDEX idx_policies_status ON policies(status);
CREATE INDEX idx_policies_policy_id ON policies(policy_id);
```

---

## MongoDB (Read Models & Audit)

### Connection Details

**Local Development:**
- **Host:** localhost
- **Port:** 27017
- **Username:** root
- **Password:** example

**Connection String:**
```
mongodb://root:example@localhost:27017/claims_read_model?authSource=admin
```

### Database: claims_read_model

#### claims Collection
```json
{
  "_id": ObjectId("507f1f77bcf86cd799439011"),
  "claim_id": "550e8400-e29b-41d4-a716-446655440000",
  "policy_id": "660e8400-e29b-41d4-a716-446655440001",
  "customer_id": "770e8400-e29b-41d4-a716-446655440002",
  "customer_name": "John Doe",
  "claim_type": "MEDICAL",
  "status": "UNDER_REVIEW",
  "amount": 5000.00,
  "parties": [
    {
      "type": "CLAIMANT",
      "name": "John Doe",
      "email": "john@example.com"
    }
  ],
  "documents": [
    {
      "type": "INVOICE",
      "file_name": "invoice.pdf",
      "uploaded_at": ISODate("2026-08-04T10:30:00Z")
    }
  ],
  "evaluation": {
    "agent_score": 0.85,
    "fraud_risk_level": "LOW",
    "evaluated_at": ISODate("2026-08-04T10:31:00Z")
  },
  "created_at": ISODate("2026-08-04T10:30:00Z"),
  "updated_at": ISODate("2026-08-04T10:31:00Z"),
  
  "indexes": [
    "claim_id",
    "policy_id",
    "customer_id",
    "status",
    "created_at"
  ]
}
```

**Purpose:** Denormalized read model optimized for queries

**Synchronization:** MongoDB updated asynchronously when PostgreSQL OutboxEvent published to Kafka

---

#### claim_events Collection
```json
{
  "_id": ObjectId(),
  "claim_id": "550e8400-e29b-41d4-a716-446655440000",
  "event_type": "CLAIM_CREATED",
  "event_id": "880e8400-e29b-41d4-a716-446655440003",
  "timestamp": ISODate("2026-08-04T10:30:00Z"),
  "user_id": "user@example.com",
  "event_data": {
    "amount": 5000.00,
    "claim_type": "MEDICAL",
    "policy_id": "660e8400-e29b-41d4-a716-446655440001"
  }
}
```

**Purpose:** Immutable event log for auditing

---

## Redis (Caching)

### Connection Details

**Local Development:**
- **Host:** localhost
- **Port:** 6379

### Cache Strategy

#### Customer Cache
```
Key Pattern: customer:{customer_id}
TTL: 300 seconds
Value: JSON serialized Customer object
Invalidation: On customer update
```

#### Policy Cache
```
Key Pattern: policy:{policy_id}
TTL: 600 seconds
Value: JSON serialized Policy object
Invalidation: On policy update, customer update
```

#### Claim Cache
```
Key Pattern: claim:{claim_id}
TTL: 60 seconds
Value: JSON serialized Claim object + status
Invalidation: On claim status update
```

#### Status Cache
```
Key Pattern: claim:status:{claim_id}
TTL: 30 seconds
Value: Current claim status string
Invalidation: On claim update
```

#### Lookup Table Cache
```
Key Pattern: lookup:{table_name}
TTL: 3600 seconds
Value: JSON array of lookup values
Examples:
  lookup:claim-types → ["MEDICAL", "AUTO", "PROPERTY"]
  lookup:party-types → ["CLAIMANT", "BENEFICIARY", "WITNESS"]
```

### Cache Configuration

```yaml
spring.redis:
  host: localhost
  port: 6379
  timeout: 2000ms
  password: null
  jedis:
    pool:
      max-active: 20
      max-idle: 10
      min-idle: 5
      max-wait: -1ms
```

### Cache Hit/Miss Handling

```java
// Cache hit: Return from Redis (fast)
claim = redisTemplate.opsForValue().get("claim:" + claimId)
if (claim != null) return claim;

// Cache miss: Query database and populate cache
claim = claimRepository.findById(claimId)
  .orElseThrow();
redisTemplate.opsForValue()
  .set("claim:" + claimId, claim, Duration.ofSeconds(60));
return claim;

// Cache invalidation on update
claimRepository.save(claim);
redisTemplate.delete("claim:" + claimId);
redisTemplate.delete("claim:status:" + claimId);
```

---

## Database Migrations

### Flyway Configuration

```yaml
spring.flyway:
  enabled: true
  locations: classpath:db/migration
  baseline-on-migrate: true
  baseline-version: 0
  validate-on-migrate: true
```

### Migration Files

**Location:** `claims-service/src/main/resources/db/migration/`

**Naming Convention:** `V{version}__{description}.sql`

**Example:**
```
V1__init_claims_schema.sql
V2__add_outbox_events_table.sql
V3__add_saga_orchestration_table.sql
```

### Execution Order

1. **Service startup:** Flyway runs all pending migrations
2. **Version tracking:** Stored in `flyway_schema_history` table
3. **Validation:** On every startup (configured)
4. **Rollback:** Not recommended (use V{n+1} with undo logic)

---

## Performance Considerations

### Connection Pooling

```yaml
spring.datasource:
  hikari:
    maximum-pool-size: 20
    minimum-idle: 5
    connection-timeout: 30000
    idle-timeout: 600000
    max-lifetime: 1800000
    auto-commit: true
```

### Index Strategy

**Primary Indexes (auto-created):**
- All PRIMARY KEYs and UNIQUE constraints

**Performance Indexes:**
- `outbox_events(status, created_at)` - For polling queries
- `claims(policy_id)` - For policy lookup
- `claims(status, created_at)` - For filtered queries
- `claim_saga_orchestrations(timeout_at)` - For timeout recovery

### Query Optimization

**N+1 Problem Prevention:**
- Use JPA `@Query` with `JOIN FETCH` for relationships
- Consider `@EntityGraph` for complex queries
- Batch queries using IN clauses

**Pagination:**
```sql
SELECT * FROM claims
WHERE status = 'UNDER_REVIEW'
ORDER BY created_at DESC
LIMIT 20 OFFSET 0;
```

---

## Backup & Recovery

### PostgreSQL Backup

```bash
# Full backup
pg_dump -U claimassist -h localhost claims_db > claims_db_backup.sql

# Restore
psql -U claimassist -h localhost claims_db < claims_db_backup.sql

# Compressed backup (production)
pg_dump -U claimassist -h localhost claims_db | gzip > claims_db_backup.sql.gz
```

### MongoDB Backup

```bash
# Full backup
mongodump --uri mongodb://root:example@localhost:27017/claims_read_model \
  --out=/backups/mongodb

# Restore
mongorestore --uri mongodb://root:example@localhost:27017 \
  /backups/mongodb
```

### Kubernetes PVC Backup

```bash
# Create snapshot
kubectl exec -n claimassist-infra postgres-pod -- \
  pg_dump claims_db > backup.sql

# Restore from PVC
kubectl cp postgres-pod:/backup.sql ./backup.sql -n claimassist-infra
```

---

## Troubleshooting

### Connection Failures

**Error:** `Connection refused`
```
Solution:
1. Verify PostgreSQL container is running
2. Check if port 5432 is exposed
3. Verify credentials
4. Check firewall rules
```

### Slow Queries

```sql
-- Find slow queries
SELECT query, calls, total_time, mean_time 
FROM pg_stat_statements 
WHERE mean_time > 100 
ORDER BY mean_time DESC;

-- Create missing index
CREATE INDEX idx_claim_status ON claims(status);
```

### Outbox Event Backlog

```sql
-- Check outbox status
SELECT status, COUNT(*) FROM outbox_events GROUP BY status;

-- If many PENDING events:
1. Check OutboxPublisher logs
2. Verify Kafka connectivity
3. Check database lock contention
4. Manually trigger publisher

-- Check for stuck events
SELECT * FROM outbox_events 
WHERE status = 'PENDING' 
AND created_at < NOW() - INTERVAL '1 hour';
```

---

## Summary

- **PostgreSQL:** Transactional consistency, saga state, event sourcing
- **MongoDB:** Read models, audit logs, denormalized queries
- **Redis:** Cache layer, sub-second query latency

The multi-database approach enables:
- Strong consistency for transactions (PostgreSQL)
- Fast reads (Redis cache)
- Flexible document storage (MongoDB)
- Separation of concerns (write/read models)


