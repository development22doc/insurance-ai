# MongoDB Usage Analysis Report
**Generated:** August 4, 2026  
**Project:** insurance-ai-platform  
**Status:** ⚠️ DECLARED BUT NOT IMPLEMENTED

---

## Executive Summary

**MongoDB is configured in the infrastructure but NOT currently implemented in the application code.**

- ✅ **Infrastructure:** MongoDB is fully configured in `docker-compose.yml`
- ✅ **Documentation:** CQRS architecture documented to use MongoDB for read models
- ❌ **Dependencies:** NO MongoDB Spring Data dependencies in any service
- ❌ **Implementation:** NO actual code using MongoDB annotations or repositories
- 🟡 **Current Implementation:** Uses PostgreSQL (write model) + Redis (cache only)

---

## MongoDB in Infrastructure

### Docker Compose Configuration
**File:** `docker-compose.yml` (Lines 133-150)

```yaml
mongo:
  image: mongo:6.0
  container_name: claimassist-mongo
  restart: unless-stopped
  environment:
    MONGO_INITDB_ROOT_USERNAME: root
    MONGO_INITDB_ROOT_PASSWORD: example
  ports:
    - "27017:27017"
  volumes:
    - mongodb_data:/data/db
  networks:
    - backend
  healthcheck:
    test: ["CMD-SHELL", "mongosh --quiet --eval \"db.adminCommand('ping')\" || exit 1"]
    interval: 10s
    timeout: 5s
    retries: 5
```

**Status:** ✅ Ready to use (accessible at `localhost:27017`)

---

## MongoDB in Architecture Documentation

### Documented CQRS Architecture
**File:** `docs/developer/CQRS.md`

#### Intended Read Model Design (Lines 144-221)
```
Read Model Purpose: Fast queries, pre-computed results, optimized for reads
Storage: MongoDB (denormalized, optimized for queries)
Cache Layer: Redis (sub-millisecond access)
```

#### Example Documented Read Model Schema
```json
{
  "_id": ObjectId(),
  "claim_id": "UUID",
  "policy_id": "UUID",
  "customer_id": "UUID",
  "customer_name": "Denormalized",
  "policy_holder_email": "Denormalized",
  "status": "UNDER_REVIEW",
  "parties": [...],
  "documents": [...],
  "evaluation": {...},
  "status_history": [...],
  "created_at": ISODate(),
  "updated_at": ISODate()
}
```

#### Documented Event Synchronization Flow (Lines 301-395)
```
Write → OutboxEvent (PostgreSQL)
       ↓
    OutboxPublisher (every 2s)
       ↓
    Kafka Topic
       ↓
    Read Model Event Handler (Listener)
       ↓
    MongoDB Update
    Redis Cache Invalidation
```

---

## Current Actual Implementation

### What IS Used for Read Service

#### 1. **PostgreSQL (Write Model)**
- **Purpose:** Strong consistency, ACID compliance, event sourcing
- **Status:** ✅ Actively used by all services
- **Dependencies:** 
  - `spring-boot-starter-data-jpa` (all services)
  - `org.postgresql:postgresql:*` (all services)
- **Usage:** All query operations currently read from PostgreSQL

#### 2. **Redis (Cache Layer)**
- **Purpose:** Fast in-memory caching for frequently accessed data
- **Status:** ✅ Actively used for read operations
- **Dependencies:** 
  - `spring-boot-starter-data-redis` (claims-service, others)
  - `redis.clients:jedis`
- **Implementation:** `CacheService.java` (245 lines)
- **Cache Strategy:** Cache-Aside pattern with TTLs:
  - Customer data: 5 minutes
  - Policy data: 10 minutes
  - Claim status: 1 minute
  - Lookup cache: 1 hour

#### 3. **Kafka (Event Stream)**
- **Purpose:** Event publishing and async processing
- **Status:** ✅ Actively configured (docker-compose)
- **Dependencies:** `spring-kafka` (all services)
- **Usage:** Event publishing from OutboxEvent

### What is NOT Used

#### MongoDB Dependencies
**Search Result:** NO MongoDB dependencies found in any `pom.xml`

```
Searched 8 services' pom.xml files:
- ✅ claims-service/pom.xml - NO MongoDB
- ✅ customer-service/pom.xml - NO MongoDB
- ✅ agent-service/pom.xml - NO MongoDB
- ✅ api-gateway/pom.xml - NO MongoDB
- ✅ discovery-service/pom.xml - NO MongoDB
- ✅ config-service/pom.xml - NO MongoDB
- ✅ common-lib/pom.xml - NO MongoDB
- ✅ root pom.xml - NO MongoDB
```

#### MongoDB Code Usage
**Search Result:** NO MongoDB annotations or repositories found in source code

```
No matches found for:
- @Document (Spring Data MongoDB annotation)
- @MongoField (Field annotation)
- MongoRepository (Interface)
- MongoTemplate (Template class)
- @Transactional with MongoDB operations
```

---

## Current Read Service Flow

### Actual Implementation (Current State)

```
Client Request (Query)
    ↓
API Gateway
    ↓
Claims Service / Customer Service
    ↓
CacheService.get(key)
    ├─ Cache HIT: Return from Redis (sub-ms)
    └─ Cache MISS:
        ↓
        JpaRepository.findById() or .findAll()
        (Query PostgreSQL directly)
        ↓
        CacheService.set(key, value, TTL)
        (Update Redis cache)
        ↓
        Return Result
```

**Database accessed for reads:** PostgreSQL only  
**Cache used:** Redis for frequently accessed data  
**Consistency:** Strong (reading from PostgreSQL directly)  
**Latency:** Milliseconds for cache hits, ~10-50ms for DB queries

---

## Where MongoDB is NOT Used for Read Service

### 1. **Claims Service Reads**
- Query handlers use JpaRepository
- All claim queries go directly to PostgreSQL
- Redis cache sits in front for performance

### 2. **Customer Service Reads**
- Customer data queries use JpaRepository
- No MongoDB read model implemented
- Redis cache for customer information

### 3. **Agent Service Reads**
- Agent evaluation data stored in PostgreSQL
- No separate MongoDB read model
- Results cached in Redis

---

## Services Configuration Analysis

### claims-service
**File:** `claims-service/src/main/java/com/claimassist/platform/claims_service/`

**Query Implementation:**
- `CacheService.java` (245 lines) - Redis cache operations
- `ClaimController.java` - REST endpoints
- Direct JPA repository queries for data retrieval

**No MongoDB code found**

### customer-service
**File:** `customer-service/src/main/java/.../`

**Query Implementation:**
- Direct JPA repository usage
- Redis caching for customer lookups
- No MongoDB integration

**No MongoDB code found**

### agent-service
**File:** `agent-service/src/main/java/.../`

**Query Implementation:**
- Evaluation results stored in PostgreSQL
- Redis cache for agent scores
- No MongoDB read model

**No MongoDB code found**

---

## Documentation vs. Reality Gap

### Documented Architecture (docs/developer/CQRS.md)
```
Write Model: PostgreSQL ✅ (Implemented)
Read Model: MongoDB ❌ (Documented but NOT Implemented)
Cache: Redis ✅ (Implemented)
Event Stream: Kafka ✅ (Implemented)
```

### Event Handler (Documented in CQRS.md)
```java
// Lines 327-395 show MongoDB usage in event handlers:
mongoTemplate.save(readModel, "claims");           // ❌ NOT IMPLEMENTED
mongoTemplate.findById(claimId, ...);              // ❌ NOT IMPLEMENTED
mongoTemplate.aggregate(...);                       // ❌ NOT IMPLEMENTED
```

**Status:** Documentation shows intended design but code does not implement it.

---

## Current Read Service Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      Client Request                         │
└────────────────────────┬────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────┐
│                    API Gateway                              │
│            (Validation & Routing)                           │
└────────────────────────┬────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────┐
│              Claims/Customer/Agent Service                  │
│                 (Business Logic)                            │
└────────────────────────┬────────────────────────────────────┘
                         ↓
              ┌──────────────────────┐
              │   CacheService       │
              │   (Redis Cache)      │
              │  - Hit: Return (✅)  │
              │  - Miss: Continue    │
              └──────────────────────┘
                         ↓
         ┌───────────────────────────────┐
         │   JpaRepository              │
         │   (PostgreSQL Direct Access) │
         │   - Query Write Model        │
         │   - Return Results ✅        │
         └───────────────────────────────┘
                         ↓
         ┌───────────────────────────────┐
         │   MongoDB (docker-compose)   │
         │   ❌ NOT CONNECTED            │
         │   ❌ NOT USED                 │
         └───────────────────────────────┘
```

---

## Findings Summary

| Component | Status | Details |
|-----------|--------|---------|
| **MongoDB Container** | ✅ Ready | Running in docker-compose, port 27017 |
| **MongoDB Documentation** | ✅ Complete | Detailed in CQRS.md with examples |
| **MongoDB Dependencies** | ❌ Missing | No spring-data-mongodb in pom.xml |
| **MongoDB Code** | ❌ Absent | No @Document, MongoRepository, MongoTemplate usage |
| **Read Service** | ✅ Working | Uses PostgreSQL + Redis cache |
| **Consistency** | ✅ Strong | Direct PostgreSQL queries (not eventual) |
| **Performance** | ✅ Good | Redis cache for hot data (sub-ms access) |

---

## Recommendations

### Option 1: Implement MongoDB Read Model (Full CQRS)
**Effort:** High | **Timeline:** 2-3 sprints

1. Add `spring-boot-starter-data-mongodb` dependency
2. Create `@Document` read model classes
3. Implement `MongoRepository` interfaces
4. Create Kafka listeners for event handlers
5. Update query methods to read from MongoDB
6. Implement eventual consistency handling
7. Add monitoring for synchronization lag

**Benefits:**
- Optimized read performance (denormalized data)
- Scalable read and write models independently
- Full CQRS implementation as documented

### Option 2: Keep Current Architecture (PostgreSQL + Redis)
**Effort:** Low | **Timeline:** Immediate

1. Remove MongoDB from docker-compose.yml (or keep for future)
2. Update CQRS.md to reflect actual implementation
3. Document that read model = PostgreSQL queries + Redis cache
4. Continue with strong consistency model

**Benefits:**
- Simpler architecture with fewer moving parts
- Strong consistency (no eventual consistency gaps)
- Easier to reason about data flow
- Reduced operational complexity

### Option 3: Hybrid Approach (Phased Implementation)
**Effort:** Medium | **Timeline:** 3-4 sprints

**Phase 1 (Current):** Keep PostgreSQL + Redis  
**Phase 2:** Add MongoDB for heavy-load queries (claims dashboard, analytics)  
**Phase 3:** Migrate complex queries to MongoDB read model  
**Phase 4:** Full CQRS with complete separation  

---

## Conclusion

**MongoDB is currently NOT used in the read service path.** All read operations flow through:
1. **Redis cache** (fast path for cached data)
2. **PostgreSQL** (source of truth for all data)

The architecture documentation describes an intended CQRS design with MongoDB, but the application currently implements a simpler **PostgreSQL-centric model with Redis caching**.

**For production deployments:**
- Remove MongoDB from docker-compose.yml unless implementing read models
- Update documentation to match actual implementation
- OR implement full CQRS with MongoDB to enable future scalability

---

**Report Generated:** August 4, 2026  
**Project:** insurance-ai-platform v1.0.0

