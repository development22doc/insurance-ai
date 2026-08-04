# Saga Pattern - Distributed Transactions

**Version:** 1.0.0  
**Last Updated:** August 4, 2026

---

## Overview

The Saga pattern manages distributed transactions across multiple microservices without traditional ACID transactions. Uses choreography + orchestration approach with event-driven coordination.

---

## Saga State Machine

### States

```
INITIATED
  ↓ (first step queued)
IN_PROGRESS
  ├─→ COMPLETED (all steps successful)
  ├─→ COMPENSATED (compensation executed)
  ├─→ FAILED (unrecoverable error)
  └─→ TIMED_OUT (180 second timeout)
```

### Transitions

```
INITIATED → IN_PROGRESS (automatic on first step)
IN_PROGRESS → COMPLETED (all steps successful)
IN_PROGRESS → FAILED (step failure, no compensation)
IN_PROGRESS → COMPENSATED (step failure, compensation executed)
IN_PROGRESS → TIMED_OUT (timeout after 180s, automatic recovery)
```

---

## Claim Processing Saga

### Flow Diagram

```
Saga Start (INITIATED)
    ↓
Step 1: CREATE_CLAIM
├─ Claims Service creates claim in PostgreSQL
├─ Publishes ClaimCreated event
└─ Moves to IN_PROGRESS
    ↓
Step 2: APPROVE_CLAIM
├─ Claims Service receives update request
├─ Validates status transition
├─ Updates claim status
└─ Publishes ClaimApproved event
    ↓
Step 3: PAYMENT
├─ Agent Service processes payment
├─ If Success → Publishes PaymentCompleted
└─ If Failure → Triggers Compensation
    ├─→ COMPENSATION step activated
    ├─→ ROLLBACK step for payment
    └─→ Saga state → COMPENSATED
    ↓
Step 4: NOTIFICATION
├─ Sends notification to customer
└─ Saga → COMPLETED
    ↓
Saga End (COMPLETED or COMPENSATED)
```

---

## Data Model

### ClaimSagaOrchestration Entity

```java
@Entity
@Table(name = "claim_saga_orchestrations")
public class ClaimSagaOrchestration {
    
    @Id
    private String sagaId;
    
    @Column(name = "claim_id")
    private String claimId;
    
    @Column(name = "saga_type")
    private String sagaType; // CLAIM_PROCESSING
    
    @Column(name = "action_type")
    private String actionType; // CREATE_CLAIM, APPROVE_CLAIM
    
    @Enumerated(EnumType.STRING)
    @Column(name = "state")
    private SagaState state; // INITIATED, IN_PROGRESS, etc.
    
    @Column(name = "steps_completed")
    private Integer stepsCompleted;
    
    @Column(name = "total_steps")
    private Integer totalSteps;
    
    @Column(name = "timeout_at")
    private Instant timeoutAt; // Now + 180 seconds
    
    @Column(name = "recovery_attempts")
    private Integer recoveryAttempts;
    
    @Column(name = "last_recovery_at")
    private Instant lastRecoveryAt;
    
    @Column(name = "created_at")
    private Instant createdAt;
    
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    @Column(name = "completed_at")
    private Instant completedAt;
}
```

---

## Orchestrator Service

### Orchestration Logic

```java
@Service
@Transactional
public class ClaimSagaOrchestratorService {
    
    // Define steps in order
    private static final List<String> SAGA_STEPS = Arrays.asList(
        "CREATE_CLAIM",
        "APPROVE_CLAIM",
        "PAYMENT",
        "NOTIFICATION"
    );
    
    private static final String COMPENSATION_STEP = "COMPENSATION";
    private static final String ROLLBACK_STEP = "ROLLBACK";
    
    private static final int SAGA_TIMEOUT_SECONDS = 180;
    
    /**
     * Initiate saga for claim processing
     */
    public void initiateSaga(String claimId, String actionType) {
        // 1. Check if saga already exists (idempotency)
        Optional<ClaimSagaOrchestration> existing = sagaRepository
            .findByClaimIdAndActionType(claimId, actionType);
        
        if (existing.isPresent()) {
            logger.info("Saga already exists for claim {}", claimId);
            return; // Already initiated
        }
        
        // 2. Create saga
        ClaimSagaOrchestration saga = new ClaimSagaOrchestration();
        saga.setSagaId(UUID.randomUUID().toString());
        saga.setClaimId(claimId);
        saga.setSagaType("CLAIM_PROCESSING");
        saga.setActionType(actionType);
        saga.setState(SagaState.INITIATED);
        saga.setStepsCompleted(0);
        saga.setTotalSteps(SAGA_STEPS.size());
        saga.setTimeoutAt(Instant.now().plus(SAGA_TIMEOUT_SECONDS, 
            ChronoUnit.SECONDS));
        saga.setRecoveryAttempts(0);
        saga.setCreatedAt(Instant.now());
        saga.setUpdatedAt(Instant.now());
        
        sagaRepository.save(saga);
        
        // 3. Queue first step
        queueNextStep(saga);
    }
    
    /**
     * Process saga step result
     */
    @Transactional
    public void handleStepResult(String sagaId, 
            String stepName, 
            String result,
            Map<String, Object> resultData) {
        
        ClaimSagaOrchestration saga = sagaRepository
            .findById(sagaId)
            .orElseThrow(() -> new SagaNotFoundException());
        
        // 1. Validate saga state
        if (saga.getState() != SagaState.IN_PROGRESS) {
            logger.warn("Saga {} not in progress, ignoring result", sagaId);
            return;
        }
        
        // 2. Check timeout
        if (Instant.now().isAfter(saga.getTimeoutAt())) {
            saga.setState(SagaState.TIMED_OUT);
            sagaRepository.save(saga);
            handleSagaTimeout(saga);
            return;
        }
        
        // 3. Process result
        if ("SUCCESS".equals(result)) {
            saga.setStepsCompleted(saga.getStepsCompleted() + 1);
            
            // If all steps done, complete saga
            if (saga.getStepsCompleted() >= saga.getTotalSteps()) {
                saga.setState(SagaState.COMPLETED);
                saga.setCompletedAt(Instant.now());
                sagaRepository.save(saga);
                handleSagaCompletion(saga);
            } else {
                // Queue next step
                queueNextStep(saga);
            }
        } else if ("FAILURE".equals(result)) {
            // Start compensation
            startCompensation(saga);
        }
        
        saga.setUpdatedAt(Instant.now());
        sagaRepository.save(saga);
    }
    
    /**
     * Queue next step for execution
     */
    private void queueNextStep(ClaimSagaOrchestration saga) {
        saga.setState(SagaState.IN_PROGRESS);
        sagaRepository.save(saga);
        
        String nextStep = SAGA_STEPS.get(saga.getStepsCompleted());
        
        // Create step command event
        Map<String, Object> stepCommand = Map.of(
            "saga_id", saga.getSagaId(),
            "step_name", nextStep,
            "claim_id", saga.getClaimId(),
            "attempt", 1
        );
        
        // Publish via outbox
        sagaOutboxPublisher.enqueueEvent(
            saga.getSagaId(),
            "CLAIM_SAGA_STEP_COMMAND",
            stepCommand
        );
    }
    
    /**
     * Start compensation (rollback) flow
     */
    private void startCompensation(ClaimSagaOrchestration saga) {
        saga.setState(SagaState.IN_PROGRESS);
        saga.setStepsCompleted(SAGA_STEPS.size()); // Mark regular steps done
        
        // Queue compensation step
        Map<String, Object> compensationCommand = Map.of(
            "saga_id", saga.getSagaId(),
            "step_name", COMPENSATION_STEP,
            "claim_id", saga.getClaimId(),
            "failed_step", "PAYMENT"
        );
        
        sagaOutboxPublisher.enqueueEvent(
            saga.getSagaId(),
            "CLAIM_SAGA_STEP_COMMAND",
            compensationCommand
        );
        
        metrics.incrementSagaCompensation();
    }
    
    /**
     * Handle saga timeout (automatic recovery)
     */
    @Scheduled(fixedDelay = 15000) // Every 15 seconds
    public void handleTimeoutAndRecovery() {
        // 1. Find timed-out sagas
        List<ClaimSagaOrchestration> timedOut = sagaRepository
            .findByStateAndTimeoutAtBefore(
                SagaState.IN_PROGRESS,
                Instant.now()
            );
        
        for (ClaimSagaOrchestration saga : timedOut) {
            logger.warn("Saga {} timed out", saga.getSagaId());
            
            // 2. Update state
            saga.setState(SagaState.TIMED_OUT);
            saga.setCompletedAt(Instant.now());
            sagaRepository.save(saga);
            
            // 3. Attempt recovery
            if (saga.getRecoveryAttempts() < 3) {
                saga.setRecoveryAttempts(saga.getRecoveryAttempts() + 1);
                saga.setLastRecoveryAt(Instant.now());
                saga.setState(SagaState.IN_PROGRESS);
                saga.setTimeoutAt(Instant.now().plus(SAGA_TIMEOUT_SECONDS, 
                    ChronoUnit.SECONDS));
                sagaRepository.save(saga);
                
                // Re-queue the failed step
                queueNextStep(saga);
                metrics.incrementSagaRecovery();
            } else {
                // Max recovery attempts exceeded
                alertingService.alert("Saga max recovery exceeded: " + 
                    saga.getSagaId());
                metrics.incrementSagaFailed();
            }
        }
    }
    
    /**
     * Handle successful saga completion
     */
    private void handleSagaCompletion(ClaimSagaOrchestration saga) {
        logger.info("Saga {} completed for claim {}", 
            saga.getSagaId(), saga.getClaimId());
        
        // Update claim status to PAID
        Claim claim = claimRepository.findById(saga.getClaimId())
            .orElseThrow();
        claim.setStatus(ClaimStatus.PAID);
        claimRepository.save(claim);
        
        // Publish completion event
        sagaOutboxPublisher.enqueueEvent(
            saga.getSagaId(),
            "CLAIM_SAGA_ORCHESTRATION_RESULT",
            Map.of(
                "saga_id", saga.getSagaId(),
                "claim_id", saga.getClaimId(),
                "result", "COMPLETED",
                "duration_ms", Duration.between(
                    saga.getCreatedAt(), 
                    Instant.now()
                ).toMillis()
            )
        );
        
        metrics.incrementSagaCompleted();
    }
}
```

---

## Step Processor Service

### Step Execution

```java
@Service
@Transactional
public class ClaimSagaStepProcessorService {
    
    /**
     * Process individual saga step
     */
    public void executeStep(String sagaId, String stepName, 
            Map<String, Object> stepData) {
        
        try {
            logger.info("Executing step {} for saga {}", stepName, sagaId);
            
            String result;
            Map<String, Object> resultData = new HashMap<>();
            
            switch (stepName) {
                case "CREATE_CLAIM":
                    result = processCreateClaim(sagaId, stepData, resultData);
                    break;
                case "APPROVE_CLAIM":
                    result = processApproveClaim(sagaId, stepData, resultData);
                    break;
                case "PAYMENT":
                    result = processPayment(sagaId, stepData, resultData);
                    break;
                case "NOTIFICATION":
                    result = processNotification(sagaId, stepData, resultData);
                    break;
                case "COMPENSATION":
                    result = processCompensation(sagaId, stepData, resultData);
                    break;
                default:
                    throw new UnknownStepException(stepName);
            }
            
            // Publish result
            publishStepResult(sagaId, stepName, result, resultData);
            
        } catch (Exception e) {
            logger.error("Step {} failed for saga {}", stepName, sagaId, e);
            publishStepResult(sagaId, stepName, "FAILURE", 
                Map.of("error", e.getMessage()));
        }
    }
    
    private String processCreateClaim(String sagaId, 
            Map<String, Object> stepData,
            Map<String, Object> resultData) {
        // Extract claim data
        String claimId = (String) stepData.get("claim_id");
        
        // Create claim (already created before saga started)
        // This step validates creation
        Claim claim = claimRepository.findById(claimId)
            .orElseThrow();
        
        claim.setStatus(ClaimStatus.INITIATED);
        claimRepository.save(claim);
        
        resultData.put("claim_id", claimId);
        return "SUCCESS";
    }
    
    private String processApproveClaim(String sagaId, 
            Map<String, Object> stepData,
            Map<String, Object> resultData) {
        String claimId = (String) stepData.get("claim_id");
        
        Claim claim = claimRepository.findById(claimId)
            .orElseThrow();
        
        claim.setStatus(ClaimStatus.UNDER_REVIEW);
        claimRepository.save(claim);
        
        resultData.put("claim_id", claimId);
        return "SUCCESS";
    }
    
    private String processPayment(String sagaId, 
            Map<String, Object> stepData,
            Map<String, Object> resultData) {
        String claimId = (String) stepData.get("claim_id");
        BigDecimal amount = new BigDecimal(stepData.get("amount").toString());
        
        try {
            // Call external payment service
            PaymentResult payment = paymentService.processPayment(
                claimId, 
                amount
            );
            
            if (payment.isSuccessful()) {
                resultData.put("payment_id", payment.getTransactionId());
                resultData.put("amount", amount);
                return "SUCCESS";
            } else {
                // Payment failed → triggers compensation
                resultData.put("error", payment.getErrorMessage());
                return "FAILURE";
            }
        } catch (Exception e) {
            logger.error("Payment processing failed", e);
            return "FAILURE";
        }
    }
    
    private String processNotification(String sagaId, 
            Map<String, Object> stepData,
            Map<String, Object> resultData) {
        String claimId = (String) stepData.get("claim_id");
        
        // Send async notification (fire-and-forget)
        notificationService.sendClaimApprovedNotification(claimId);
        
        resultData.put("claim_id", claimId);
        return "SUCCESS";
    }
    
    private String processCompensation(String sagaId, 
            Map<String, Object> stepData,
            Map<String, Object> resultData) {
        String claimId = (String) stepData.get("claim_id");
        String failedStep = (String) stepData.get("failed_step");
        
        if ("PAYMENT".equals(failedStep)) {
            // Refund the payment
            Claim claim = claimRepository.findById(claimId)
                .orElseThrow();
            
            try {
                paymentService.refundPayment(claimId, claim.getAmount());
                claim.setStatus(ClaimStatus.REJECTED);
                claimRepository.save(claim);
                resultData.put("refund_status", "SUCCESS");
                return "SUCCESS";
            } catch (Exception e) {
                logger.error("Refund failed", e);
                resultData.put("refund_status", "FAILED");
                return "FAILURE";
            }
        }
        
        return "SUCCESS";
    }
    
    private void publishStepResult(String sagaId, String stepName, 
            String result, Map<String, Object> resultData) {
        
        sagaOutboxPublisher.enqueueEvent(
            sagaId,
            "CLAIM_SAGA_STEP_RESULT",
            Map.of(
                "saga_id", sagaId,
                "step_name", stepName,
                "result", result,
                "result_data", resultData,
                "timestamp", Instant.now()
            )
        );
    }
}
```

---

## Kafka Listeners

### Orchestration Listener

```java
@Component
public class ClaimSagaOrchestrationListener {
    
    @KafkaListener(topics = "claim-saga-orchestration-request-event")
    public void handleOrchestrationRequest(
        @Payload OrchestrationRequestEvent event,
        Acknowledgment ack) {
        
        try {
            String sagaId = event.getSagaId();
            String claimId = event.getClaimId();
            String action = event.getAction();
            
            logger.info("Starting saga {} for claim {} with action {}", 
                sagaId, claimId, action);
            
            // Initiate saga
            orchestratorService.initiateSaga(claimId, action);
            
            // Commit offset
            ack.acknowledge();
            
        } catch (Exception e) {
            logger.error("Error handling orchestration request", e);
        }
    }
}
```

### Step Result Listener

```java
@Component
public class ClaimSagaStepResultListener {
    
    @KafkaListener(topics = "claim-saga-step-result-event")
    public void handleStepResult(
        @Payload StepResultEvent event,
        Acknowledgment ack) {
        
        try {
            String sagaId = event.getSagaId();
            String stepName = event.getStepName();
            String result = event.getResult();
            
            // Update orchestrator
            orchestratorService.handleStepResult(
                sagaId, 
                stepName, 
                result,
                event.getResultData()
            );
            
            ack.acknowledge();
            
        } catch (Exception e) {
            logger.error("Error handling step result", e);
        }
    }
}
```

---

## Compensation Logic

### Failure & Rollback

```
Step PAYMENT fails:
    ↓
Orchestrator detects failure
    ↓
Queue COMPENSATION step
    ↓
StepProcessor refunds payment
    ↓
Update claim status to REJECTED
    ↓
Publish saga result (COMPENSATED)
```

### Idempotent Compensation

```java
// Check if compensation already done
if (compensationRepository.existsBySagaId(sagaId)) {
    return; // Already compensated
}

// Execute compensation
compensation();

// Record completion
compensationRepository.save(new Compensation(sagaId, Instant.now()));
```

---

## Metrics & Monitoring

```java
@Component
public class SagaMetricsService {
    
    private final MeterRegistry meterRegistry;
    
    public void incrementSagaStarted() {
        meterRegistry.counter("saga.started").increment();
    }
    
    public void incrementSagaCompleted() {
        meterRegistry.counter("saga.completed").increment();
    }
    
    public void incrementSagaFailed() {
        meterRegistry.counter("saga.failed").increment();
    }
    
    public void incrementSagaCompensated() {
        meterRegistry.counter("saga.compensated").increment();
    }
    
    public void incrementSagaTimedOut() {
        meterRegistry.counter("saga.timed_out").increment();
    }
    
    public void recordSagaLatency(long durationMs) {
        meterRegistry.timer("saga.latency").record(durationMs, TimeUnit.MILLISECONDS);
    }
}
```

---

## Best Practices

1. **Idempotency:** All steps must be idempotent
2. **Timeout Handling:** Always define timeouts
3. **Compensation:** Plan compensation for each step
4. **Events:** Use events for step coordination
5. **Monitoring:** Track saga state transitions
6. **Testing:** Test happy path and failure scenarios
7. **Documentation:** Document step ordering and compensation

---


