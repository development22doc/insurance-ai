# Kafka - Event Streaming

**Version:** 1.0.0  
**Last Updated:** August 4, 2026

---

## Overview

Kafka provides event-driven communication between microservices with guaranteed message delivery and topic partitioning for scalability.

**Kafka Broker:** Apache Kafka 3.8.0  
**Port:** 9092 (PLAINTEXT)  
**Partitions:** 3 per topic  
**Replication Factor:** 1 (dev), 3+ (production)

---

## Topics & Partitions

### Topic Configuration

All topics configured with:
- **Partitions:** 3 (allows parallelism across 3 consumer instances)
- **Replication Factor:** 1 (development), 3 (production)
- **Min In-Sync Replicas:** 1 (development), 2 (production)
- **Retention:** 7 days default
- **Cleanup Policy:** delete (default) or compact (for compacted topics)

### Topics

#### 1. claim-update-request-event
**Purpose:** Claim status update requests from Claims Service to Agent Service

**Producers:**
- Claims Service (via OutboxEvent)

**Consumers:**
- Agent Service (ClaimUpdateConsumer)

**Event Schema:**
```json
{
  "event_id": "uuid",
  "claim_id": "uuid",
  "old_status": "INITIATED",
  "new_status": "UNDER_REVIEW",
  "update_reason": "Sent for evaluation",
  "timestamp": "2026-08-04T10:30:00Z",
  "trace_id": "4e17d3a9c6b7f2d1"
}
```

**DLT:** `claim-update-request-event.DLT`

---

#### 2. claim-update-response-event
**Purpose:** Agent evaluation responses back to Claims Service

**Producers:**
- Agent Service (via OutboxEvent)

**Consumers:**
- Claims Service (AgentSagaResponseHandler)

**Event Schema:**
```json
{
  "event_id": "uuid",
  "claim_id": "uuid",
  "evaluation_score": 0.85,
  "fraud_risk_level": "LOW",
  "recommendation": "APPROVE",
  "timestamp": "2026-08-04T10:31:00Z"
}
```

**DLT:** `claim-update-response-event.DLT`

---

#### 3. claim-saga-orchestration-request-event
**Purpose:** Start saga orchestration for claim processing

**Producers:**
- Claims Service (via OutboxEvent)

**Consumers:**
- ClaimSagaOrchestratorService

**Event Schema:**
```json
{
  "saga_id": "uuid",
  "claim_id": "uuid",
  "action": "CREATE_CLAIM",
  "payload": { /* claim data */ },
  "timestamp": "2026-08-04T10:30:00Z"
}
```

**DLT:** `claim-saga-orchestration-request-event.DLT`

---

#### 4. claim-saga-step-command-event
**Purpose:** Individual saga step execution commands

**Producers:**
- ClaimSagaOrchestratorService

**Consumers:**
- ClaimSagaStepProcessorService

**Event Schema:**
```json
{
  "saga_id": "uuid",
  "step_id": "uuid",
  "step_name": "PAYMENT",
  "claim_id": "uuid",
  "step_data": { /* step-specific data */ },
  "attempt": 1,
  "timestamp": "2026-08-04T10:31:00Z"
}
```

**DLT:** `claim-saga-step-command-event.DLT`

---

#### 5. claim-saga-step-result-event
**Purpose:** Results from saga step execution

**Producers:**
- ClaimSagaStepProcessorService

**Consumers:**
- ClaimSagaOrchestratorService

**Event Schema:**
```json
{
  "saga_id": "uuid",
  "step_id": "uuid",
  "claim_id": "uuid",
  "result": "SUCCESS",
  "result_data": { /* step result */ },
  "timestamp": "2026-08-04T10:31:30Z"
}
```

**DLT:** `claim-saga-step-result-event.DLT`

---

#### 6. claim-saga-orchestration-result-event
**Purpose:** Final saga completion/failure result

**Producers:**
- ClaimSagaOrchestratorService

**Consumers:**
- External notification services
- Audit/compliance systems

**Event Schema:**
```json
{
  "saga_id": "uuid",
  "claim_id": "uuid",
  "action": "CREATE_CLAIM",
  "state": "COMPLETED",
  "result": "SUCCESS",
  "duration_ms": 3500,
  "compensation_triggered": false,
  "timestamp": "2026-08-04T10:34:00Z"
}
```

**DLT:** `claim-saga-orchestration-result-event.DLT`

---

## Producer Configuration

### General Settings

```yaml
spring.kafka.producer:
  bootstrap-servers: localhost:9092
  
  # Acknowledgment level (all = wait for all replicas)
  acks: all
  
  # Unlimited retries (with backoff)
  retries: 2147483647
  
  # Batch size and timing
  batch-size: 16384                    # 16KB
  linger-ms: 10                        # Wait up to 10ms to batch
  
  # Properties (advanced)
  properties:
    compression.type: snappy           # Compress batches
    delivery.timeout.ms: 120000        # 2 minute timeout
    request.timeout.ms: 30000          # 30 second request timeout
    retry.backoff.ms: 1000             # 1 second between retries
    enable.idempotence: true           # Exactly once semantics
    max.in.flight.requests.per.connection: 5
```

**Idempotency:**
- `enable.idempotence: true` prevents duplicate messages
- Producer automatically handles retries
- Requires `max.in.flight.requests.per.connection` ≤ 5

---

### Publishing via OutboxEvent

```java
@Transactional
public void publishEvent(String aggregateId, String eventType, Object payload) {
    // 1. Create event object
    OutboxEvent event = new OutboxEvent(
        UUID.randomUUID(),
        aggregateId,
        eventType,
        jsonMapper.writeValueAsString(payload),
        "PENDING",
        0,
        Instant.now()
    );
    
    // 2. Save to database (same transaction)
    outboxEventRepository.save(event);
    
    // Transaction commits → OutboxPublisher picks it up
}

// OutboxPublisher (runs every 2 seconds)
@Scheduled(fixedDelay = 2000, initialDelay = 2000)
public void publishOutboxEvents() {
    // 1. Query pending events
    List<OutboxEvent> pending = outboxEventRepository
        .findByStatusOrderByCreatedAtAsc(
            OutboxEventStatus.PENDING, 
            Pageable.ofSize(100)
        );
    
    for (OutboxEvent event : pending) {
        try {
            // 2. Publish to Kafka
            String topic = determineTopicFromEventType(event.getEventType());
            ProducerRecord<String, String> record = new ProducerRecord<>(
                topic,
                event.getAggregateId(),      // Key (for partitioning)
                event.getPayload()           // Value (event data)
            );
            
            KafkaFuture<RecordMetadata> future = kafkaTemplate
                .send(record)
                .get();
            
            // 3. Update status to PUBLISHED
            event.setStatus(OutboxEventStatus.PUBLISHED);
            event.setPublishedAt(Instant.now());
            outboxEventRepository.save(event);
            
        } catch (Exception e) {
            // 4. Handle failure with backoff retry
            event.incrementRetryCount();
            
            if (event.getRetryCount() < MAX_RETRIES) {
                // Calculate exponential backoff
                long backoffMs = calculateBackoff(event.getRetryCount());
                event.setNextRetryAt(Instant.now().plus(backoffMs, ChronoUnit.MILLIS));
                outboxEventRepository.save(event);
            } else {
                // Move to DLT after max retries
                event.setStatus(OutboxEventStatus.FAILED);
                event.setErrorMessage(e.getMessage());
                outboxEventRepository.save(event);
                publishToDLT(event);
            }
        }
    }
}
```

---

## Consumer Configuration

### General Settings

```yaml
spring.kafka.consumer:
  bootstrap-servers: localhost:9092
  
  # Consumer group for offset tracking
  group-id: claims-service-consumers
  
  # Offset management
  auto-offset-reset: earliest          # Start from beginning if no offset
  enable-auto-commit: false            # Manual commit for reliability
  
  # Performance tuning
  max-poll-records: 100                # Fetch 100 messages at a time
  fetch-min-bytes: 1024                # 1KB minimum
  fetch-max-wait-ms: 500               # Wait up to 500ms
  
  # Session management
  session-timeout-ms: 30000            # 30 second session
  heartbeat-interval-ms: 10000         # Heartbeat every 10s
  max-poll-interval-ms: 300000         # 5 minute poll interval
```

### Consumer Implementation

```java
@Component
public class ClaimUpdateConsumer {
    
    @KafkaListener(
        topics = "claim-update-request-event",
        groupId = "claims-service",
        concurrency = "3"  // 3 concurrent listeners (one per partition)
    )
    public void handleClaimUpdateRequest(
        @Payload ClaimUpdateRequestEvent event,
        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
        @Header(KafkaHeaders.RECEIVED_PARTITION_ID) int partition,
        @Header(KafkaHeaders.OFFSET) long offset,
        Acknowledgment acknowledgment) {
        
        try {
            logger.info("Processing event from partition {} offset {}", 
                partition, offset);
            
            // 1. Check idempotency
            if (processedEventRepository.existsById(event.getEventId())) {
                logger.info("Event {} already processed, skipping", 
                    event.getEventId());
                acknowledgment.acknowledge();
                return;
            }
            
            // 2. Authorization check
            if (!authorizationService.canUpdateClaim(event.getClaimId())) {
                throw new UnauthorizedException();
            }
            
            // 3. Validate domain state
            Claim claim = claimRepository.findById(event.getClaimId())
                .orElseThrow(() -> new ClaimNotFoundException());
            
            if (!claim.getStatus().canTransitionTo(event.getNewStatus())) {
                throw new InvalidStateTransitionException();
            }
            
            // 4. Update domain
            claim.updateStatus(event.getNewStatus(), event.getUpdateReason());
            
            // 5. Create response event
            OutboxEvent response = new OutboxEvent(
                UUID.randomUUID(),
                event.getClaimId(),
                "CLAIM_UPDATE_RESPONSE",
                jsonMapper.writeValueAsString(
                    new ClaimUpdateResponseEvent(
                        event.getEventId(),
                        claim.getClaimId(),
                        claim.getStatus()
                    )
                ),
                "PENDING",
                0,
                Instant.now()
            );
            
            // 6. Save in transaction
            claimRepository.save(claim);
            outboxEventRepository.save(response);
            
            // Record processing
            processedEventRepository.save(
                new ProcessedEvent(event.getEventId())
            );
            
            // 7. Commit offset (manual)
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            logger.error("Error processing ClaimUpdateRequest", e);
            // Don't commit offset - message goes to error topic
            // After max retries, goes to DLT
        }
    }
}
```

---

## Error Handling & Dead Letter Queues

### Error Handler Configuration

```java
@Configuration
public class KafkaErrorHandlerConfig {
    
    @Bean
    public CommonErrorHandler errorHandler(
        KafkaTemplate<String, String> kafkaTemplate) {
        
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
            new DeadLetterPublishingRecoverer(kafkaTemplate, 
                (record, ex) -> new TopicPartition(
                    record.topic() + ".DLT",  // .DLT suffix
                    record.partition()
                )
            ),
            new FixedBackOff(1000, 4)  // 1s backoff, 4 retries
        );
        
        errorHandler.addRetryableExceptions(
            TemporaryProcessingException.class
        );
        
        return errorHandler;
    }
}
```

### DLT (Dead Letter Topic)

**Processing Failures:**
```
1. Exception in listener
   ↓
2. Retry 1: 1 second wait
   ↓
3. Retry 2: 1 second wait
   ↓
4. Retry 3: 1 second wait
   ↓
5. Retry 4: 1 second wait
   ↓
6. Max retries exceeded → Publish to .DLT topic
```

**DLT Topics:**
```
Original: claim-update-request-event
DLT:      claim-update-request-event.DLT

Original: claim-saga-orchestration-request-event
DLT:      claim-saga-orchestration-request-event.DLT
```

**DLT Recovery:**
```java
@Component
public class DLTRecoveryService {
    
    @KafkaListener(topics = "claim-update-request-event.DLT")
    public void handleDLT(
        @Payload ClaimUpdateRequestEvent event,
        @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        
        logger.error("Message in DLT - Event: {}, Error: {}", 
            event.getEventId(), exceptionMessage);
        
        // 1. Log for manual intervention
        alertingService.sendAlert(
            "DLT Message received",
            "Topic: claim-update-request-event, " +
            "ClaimId: " + event.getClaimId()
        );
        
        // 2. Store for later replay
        dltRepository.save(new DLTMessage(
            event.getEventId(),
            "claim-update-request-event",
            exceptionMessage,
            Instant.now()
        ));
        
        // 3. Potential automated recovery:
        // - If temporary failure, retry
        // - If permanent failure, escalate
    }
}
```

---

## Performance & Optimization

### Throughput Optimization

```yaml
spring.kafka.producer:
  batch-size: 32768              # Larger batches (32KB)
  linger-ms: 100                 # Wait longer for batching
  buffer-memory: 67108864        # 64MB buffer
  
spring.kafka.consumer:
  fetch-min-bytes: 10240         # 10KB minimum
  fetch-max-wait-ms: 1000        # Wait up to 1 second
```

**Impact:** Higher latency but better throughput

### Latency Optimization

```yaml
spring.kafka.producer:
  batch-size: 16384              # Smaller batches
  linger-ms: 1                   # Send immediately
  compression-type: none         # No compression overhead
  
spring.kafka.consumer:
  fetch-min-bytes: 1             # Send as soon as available
  fetch-max-wait-ms: 0           # No wait
```

**Impact:** Lower latency but lower throughput

### Monitoring

```bash
# Topic lag (consumer lag)
kafka-consumer-groups --bootstrap-server localhost:9092 \
  --group claims-service-consumers \
  --describe

# Topic statistics
kafka-topics --bootstrap-server localhost:9092 \
  --topic claim-update-request-event \
  --describe
```

---

## Best Practices

1. **Use Keys:** Partition by aggregate ID for ordering
2. **Idempotent Consumers:** All handlers must be idempotent
3. **Error Handling:** Explicit error handler with DLT
4. **Manual Commit:** For reliability in transactional contexts
5. **Monitoring:** Track consumer lag and error rates
6. **Versioning:** Version events for schema evolution
7. **Cleanup:** Regular DLT cleanup procedure
8. **Documentation:** Document event schema in code

---

## Troubleshooting

### High Consumer Lag

```
Check:
1. Consumer speed: kafka-consumer-groups --describe
2. Producer speed: Monitor producer metrics
3. Processing time: Check handler log times
4. Partition count: May need more partitions

Solution:
1. Increase consumer concurrency
2. Increase batch size
3. Optimize handler logic
4. Add more partitions (up to 3 in dev)
```

### Messages Not Being Processed

```
Check:
1. Consumer group active: kafka-consumer-groups --list
2. Group coordinator: kafka-consumer-groups --describe
3. Broker connectivity: telnet localhost 9092
4. Topic exists: kafka-topics --list

Solution:
1. Restart consumer
2. Reset offset: kafka-consumer-groups --reset-offsets
3. Check network connectivity
```

---


