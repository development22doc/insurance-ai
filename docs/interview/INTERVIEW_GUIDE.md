# Interview Guide — ClaimAssist AI Platform

**Purpose:** Help interviewers and candidates discuss the implemented system. All questions and model answers are limited to what is actually implemented in this repository — do not assume unimplemented features.

Last Updated: August 4, 2026

---

How to use

- Interviewers: pick 4–6 questions (1 architecture, 1 data, 1 messaging, 1 resilience/security, 1 coding/design) for a 45–60 minute interview.
- Candidates: use this to prepare by reading the referenced developer docs in `docs/developer/` and by inspecting the code under the service folders.

Reference docs (do not duplicate):
- `docs/developer/PROJECT_OVERVIEW.md`
- `docs/developer/ARCHITECTURE.md`
- `docs/developer/KAFKA.md`
- `docs/developer/DATABASE.md`
- `docs/developer/SECURITY.md`
- `docs/developer/SAGA.md`
- `docs/developer/RESILIENCE.md`

---

Interview Questions (with model answers and pointers to the repo)

1) High-level architecture (system design)
- Question: Describe the high-level architecture of ClaimAssist and how requests flow from client to final claim state.
- Model answer (short): API Gateway accepts requests, validates JWTs via Keycloak, routes to service (Claims/Customer). Claims Service persists claim to PostgreSQL and writes an OutboxEvent in the same transaction. An OutboxPublisher publishes events to Kafka. Agent Service consumes evaluation requests and responds; saga orchestrator coordinates long-running multi-step claim flows. Observability is via Prometheus metrics, Zipkin tracing, and structured JSON logs. (See `docs/developer/PROJECT_OVERVIEW.md` and `docs/developer/ARCHITECTURE.md`)
- Follow-ups: Ask candidate to explain why transactional outbox is used and trade-offs compared to distributed transactions.

2) Data modeling & CQRS
- Question: Explain how CQRS is implemented in this system and where read models live.
- Model answer: Writes are performed against PostgreSQL (normalized). Read models are stored in MongoDB and synchronized asynchronously by consuming Kafka events. This keeps write paths strongly consistent and read paths optimized for queries. (See `docs/developer/DATABASE.md` and `docs/developer/PROJECT_OVERVIEW.md`)

3) Messaging and reliability
- Question: How are Kafka topics, DLTs and idempotency handled?
- Model answer: The project uses multiple topics (claim-update-request-event, claim-update-response-event, saga-related topics). Each producer writes events via a transactional outbox pattern (outbox_events table) and an OutboxPublisher polls and sends to Kafka. Consumers use a processed_events table to ensure idempotency and manual offset commit for reliability. Dead-letter topics (topic.DLT) are used for unrecoverable messages. (See `docs/developer/KAFKA.md`)

4) Saga orchestration
- Question: Describe the claim processing saga and compensation strategy when a step fails.
- Model answer: The ClaimSagaOrchestrator tracks saga state in `claim_saga_orchestrations` and queues per-step commands via outbox events. Steps include CREATE_CLAIM → APPROVE_CLAIM → PAYMENT → NOTIFICATION. On payment failure the orchestrator queues a COMPENSATION step (refund/rollback) and ensures idempotency of compensations. Timeouts and recovery attempts are implemented. (See `docs/developer/SAGA.md`)

5) Resilience and fault tolerance
- Question: What resilience patterns are implemented and how do they combine?
- Model answer: Resilience4j circuit breakers, retries with exponential backoff, time limiters (timeouts), bulkheads (thread-pool isolation), and rate limiters. The recommended sequence is rate limiter → timeout → bulkhead → circuit breaker → retry → fallback. Health indicators and metrics are exposed via Actuator and Prometheus. (See `docs/developer/RESILIENCE.md`)

6) Security
- Question: How does service-to-service authentication work?
- Model answer: Services use OAuth2 client credentials against Keycloak. Access tokens are JWTs; services validate signatures against Keycloak JWKS and use roles for RBAC. Secrets are expected to be stored in Kubernetes Secrets. (See `docs/developer/SECURITY.md`)

7) Coding challenge (short take-home or pair-programming)
- Task: In 60–90 minutes, implement a small utility that inspects the outbox table and reports counts by status and age (PENDING > 1h). Provide a short design and pseudocode rather than changing repository code.
- Evaluation criteria: design clarity, transaction-safety awareness (do not mark or delete events), handling large tables (pagination), and how to integrate with alerting.
- Hint: Candidates should use pagination queries (LIMIT/OFFSET or keyset) and avoid long-running transactions.

8) Debugging / operational question
- Question: The Outbox backlog grows (many PENDING events). Describe how you'd investigate and remediate using the repository artifacts.
- Model answer: Steps: check OutboxPublisher and Kafka connectivity logs; verify Kafka brokers reachable; confirm producer configs (timeouts/retries); inspect DB locks; query `outbox_events` for oldest pending; check consumer groups and Kafka topic lags; consider manually publishing small batch to test; increase publisher logging and monitor Prometheus metrics for outbox backlog. (See `docs/developer/KAFKA.md` and `docs/developer/DATABASE.md`)

---

Scoring rubric (suggested)
- System design: 20
- Core patterns (event-driven, outbox, saga): 20
- Data modeling & consistency: 15
- Resilience & operations: 15
- Security & auth: 10
- Coding / debugging: 20

---

Notes for interviewers
- Keep questions grounded in the repository: ask for file/class names and where behavior is implemented.
- For backend-engineer roles emphasize CQRS, Kafka, saga and transactional outbox.
- For SRE/Platform roles emphasize deployment, observability, and HPA / resource configuration in `k8s/` and `helm/`.

---

Files created or referenced: this guide only references existing docs in `docs/developer/`. Do not add implementation code as part of interview tasks.

