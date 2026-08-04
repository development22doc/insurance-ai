# Component Diagram — ClaimAssist AI Platform

This file contains simple, code-based architecture diagrams (Mermaid) that reflect the implemented system. Diagrams are intentionally lightweight and reference only implemented components.

Last Updated: August 4, 2026

---

Mermaid component diagram (system overview)

```mermaid
graph LR
  subgraph Clients
    Client[Client (Web / Mobile)]
  end

  subgraph Gateway
    API[API Gateway\n(port 8080)]
  end

  subgraph Services
    CS[Customer Service\n(port 8081)]
    CL[Claims Service\n(port 8082)]
    AG[Agent Service\n(port 8083)]
    CFG[Config Service\n(port 8888)]
    DISC[Discovery Service\n(port 8761)]
  end

  subgraph Infra
    PG[PostgreSQL\n(5432)]
    MG[MongoDB\n(27017)]
    RD[Redis\n(6379)]
    KF[Kafka\n(9092)]
    KC[Keycloak\n(8180)]
    ZP[Zipkin\n(9411)]
  end

  Client -->|HTTPS / JWT| API
  API --> CS
  API --> CL
  API --> AG

  CL -->|JDBC| PG
  CS -->|JDBC| PG
  AG -->|JDBC| PG

  CL -->|Outbox → Kafka| KF
  AG -->|Consumes| KF
  CL -->|Publishes read-model events| KF
  any --> MG

  CL -->|Cache| RD
  CS -->|Cache| RD

  API -->|Validate tokens| KC
  Services -->|Service discovery| DISC

  Services -->|Traces| ZP
  Infra -->|Storage & messaging| PG
```

Notes:
- Diagram intentionally avoids implementation details (class names) and focuses on deployed components and ports.
- For sequence-level flows see `docs/diagrams/SEQUENCE_CLAIM_CREATION.md`.

