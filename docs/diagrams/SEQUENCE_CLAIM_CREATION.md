# Sequence Diagram — Claim Creation Flow

This sequence diagram shows the implemented claim creation flow using the transactional outbox and saga orchestration.

Last Updated: August 4, 2026

---

Mermaid sequence diagram

```mermaid
sequenceDiagram
  participant Client
  participant API as API Gateway
  participant Claims as Claims Service
  participant Outbox as Outbox (DB)
  participant Publisher as OutboxPublisher
  participant Kafka
  participant Agent as Agent Service
  participant Saga as Saga Orchestrator
  participant Mongo as MongoDB (Read Model)

  Client->>API: POST /api/claims (Authorization: Bearer...)
  API->>Claims: Forward request
  Claims->>PG: Begin transaction
  Claims->>PG: INSERT claims (claims table)
  Claims->>Outbox: INSERT outbox_events (same transaction)
  PG-->>Claims: Commit transaction
  Claims-->>API: 201 Created

  Note over Publisher,Kafka: Scheduled publisher polls DB
  Publisher->>Outbox: SELECT PENDING events
  Publisher->>Kafka: Publish event(s)
  Kafka-->>Publisher: ACK
  Publisher->>Outbox: UPDATE event status = PUBLISHED

  Kafka->>Agent: claim-update-request-event (consume)
  Agent->>Agent: Evaluate claim (fraud score, ML)
  Agent->>Outbox: Write response event to outbox (same transaction)
  Agent->>PG: Commit

  Publisher->>Outbox: Publish agent response to Kafka
  Kafka->>Claims: claim-update-response-event (consume)
  Claims->>Saga: Handle step result / advance saga
  Claims->>Mongo: Update read model (async handler)

  Note over Saga: Saga completes or triggers compensation if needed
```

Notes:
- The diagram reflects the transactional outbox pattern: events are written to the database in the same database transaction as domain changes and a separate publisher publishes them to Kafka.
- Consumers write response events via their own outbox which the publisher will also publish.
- Read model updates are applied asynchronously from Kafka consumers to MongoDB.

