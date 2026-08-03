# Architecture Documentation

**Version:** 1.0.0  
**Last Updated:** 2026-08-04  
**Scope:** System architecture, component interactions, deployment topology

---

## 📐 Architecture Overview

```mermaid
graph TB
    Client["Client Applications"]
    Gateway["API Gateway<br/>Port 8080"]
    
    subgraph Services ["Microservices"]
        CustSvc["Customer Service<br/>Port 8081"]
        ClaimSvc["Claims Service<br/>Port 8082"]
        AgentSvc["Agent Service<br/>Port 8083"]
    end
    
    subgraph Infrastructure ["Core Infrastructure"]
        ConfigSrv["Config Server<br/>Port 8888"]
        DiscoverySrv["Discovery Service<br/>Port 8761"]
        Keycloak["Keycloak<br/>Port 8180"]
    end
    
    subgraph Data ["Data Layer"]
        PgSQL["PostgreSQL<br/>Write DB"]
        Mongo["MongoDB<br/>Read DB"]
        Redis["Redis<br/>Cache"]
    end
    
    subgraph Events ["Event Layer"]
        Kafka["Apache Kafka<br/>Message Broker"]
        Zipkin["Zipkin<br/>Tracing"]
    end
    
    Client -->|HTTP/REST| Gateway
    Gateway -->|Routes| CustSvc
    Gateway -->|Routes| ClaimSvc
    Gateway -->|Routes| AgentSvc
    
    CustSvc -->|JWT Validation| Keycloak
    ClaimSvc -->|JWT Validation| Keycloak
    AgentSvc -->|JWT Validation| Keycloak
    
    CustSvc -->|Config| ConfigSrv
    ClaimSvc -->|Config| ConfigSrv
    AgentSvc -->|Config| ConfigSrv
    
    CustSvc -->|Register| DiscoverySrv
    ClaimSvc -->|Register| DiscoverySrv
    AgentSvc -->|Register| DiscoverySrv
    
    CustSvc -->|Read/Write| PgSQL
    ClaimSvc -->|Read/Write| PgSQL
    AgentSvc -->|Read| PgSQL
    
    CustSvc -->|Query| Mongo
    ClaimSvc -->|Query| Mongo
    
    CustSvc -->|Cache| Redis
    ClaimSvc -->|Cache| Redis
    
    CustSvc -->|Publish| Kafka
    ClaimSvc -->|Publish| Kafka
    AgentSvc -->|Publish| Kafka
    
    CustSvc -->|Consume| Kafka
    ClaimSvc -->|Consume| Kafka
    AgentSvc -->|Consume| Kafka
    
    CustSvc -->|Traces| Zipkin
    ClaimSvc -->|Traces| Zipkin
    AgentSvc -->|Traces| Zipkin
    
    style Client fill:#e1f5ff
    style Gateway fill:#fff3e0
    style Services fill:#e8f5e9
    style Infrastructure fill:#fce4ec
    style Data fill:#f3e5f5
    style Events fill:#fff9c4
```

---

## 🔄 Request Flow: End-to-End

```mermaid
sequenceDiagram
    participant Client
    participant Gateway as API Gateway
    participant Auth as Keycloak
    participant Service as Domain Service
    participant DB as PostgreSQL
    participant Cache as Redis
    participant Kafka
    participant Outbox as Transactional<br/>Outbox
    participant Zipkin
    
    Client->>Gateway: 1. HTTP Request
    Gateway->>Gateway: 2. Validate JWT
    Gateway->>Auth: 3. Verify Token (JWKS)
    Auth-->>Gateway: 4. Token Valid ✓
    Gateway->>Service: 5. Route Request
    
    Service->>Cache: 6. Check Cache
    alt Cache Hit
        Cache-->>Service: Return Cached
    else Cache Miss
        Service->>DB: 7. Query Data
        DB-->>Service: Return Data
        Service->>Cache: 8. Update Cache
    end
    
    Service->>Service: 9. Process Business Logic
    
    Service->>DB: 10. Begin Transaction
    Service->>DB: 11. Write Data
    Service->>Outbox: 12. Write Event
    DB->>DB: 13. Commit
    
    Service->>Kafka: 14. Publish Event
    Kafka-->>Service: 15. Ack
    
    Service-->>Gateway: 16. Response
    Gateway-->>Client: 17. HTTP 200 ✓
    
    par Async Processing
        Zipkin->>Zipkin: 18. Trace Stored
        Kafka->>Kafka: 19. Consumer Processes
    end
```

---

## 📨 CQRS Command Flow

```mermaid
graph LR
    Client["Client"]
    Gateway["API Gateway"]
    CommandCtrl["@RestController<br/>Command Endpoint"]
    CommandDTO["Command DTO"]
    CommandSvc["Command Service"]
    Handler["Command Handler"]
    Repo["Repository"]
    DB["PostgreSQL"]
    Outbox["Outbox Event"]
    Kafka["Kafka Producer"]
    
    Client -->|POST /commands| Gateway
    Gateway -->|Route| CommandCtrl
    CommandCtrl -->|Parse| CommandDTO
    CommandDTO -->|Validate| CommandSvc
    CommandSvc -->|Handle| Handler
    
    Handler -->|1. Begin TX| DB
    Handler -->|2. Execute Business Logic| Handler
    Handler -->|3. Update State| Repo
    Repo -->|Write| DB
    Handler -->|4. Create Outbox Event| Outbox
    Outbox -->|Write| DB
    DB -->|Commit| DB
    
    Outbox -->|Async Publisher| Kafka
    Kafka -->|Publish| Kafka
    
    Handler -->|Response| CommandCtrl
    CommandCtrl -->|200 OK| Client
```

---

## 📖 CQRS Query Flow

```mermaid
graph LR
    Client["Client"]
    Gateway["API Gateway"]
    QueryCtrl["@RestController<br/>Query Endpoint"]
    QueryDTO["Query DTO"]
    QuerySvc["Query Service"]
    Handler["Query Handler"]
    Cache["Redis Cache"]
    Repo["Repository"]
    Mongo["MongoDB<br/>Read Model"]
    
    Client -->|GET /queries| Gateway
    Gateway -->|Route| QueryCtrl
    QueryCtrl -->|Parse| QueryDTO
    QueryDTO -->|Validate| QuerySvc
    QuerySvc -->|Handle| Handler
    
    Handler -->|Check| Cache
    
    alt Cache Hit
        Cache -->|Return| Handler
    else Cache Miss
        Handler -->|Read| Mongo
        Mongo -->|Data| Handler
        Handler -->|Cache Result| Cache
    end
    
    Handler -->|Response| QueryCtrl
    QueryCtrl -->|200 OK| Client
```

---

## 🎭 Saga Orchestration Flow

```mermaid
graph TB
    Request["Claim Submission Request"]
    
    subgraph SagaOrchestrator ["Saga State Machine"]
        Init["INITIATED"]
        Validate["VALIDATING"]
        Process["PROCESSING"]
        Complete["COMPLETED"]
        Compensate["COMPENSATING"]
        Failed["FAILED"]
    end
    
    subgraph Topics ["Kafka Topics"]
        ReqTopic["orchestration-request"]
        CmdTopic["step-command"]
        RespTopic["step-result"]
        CompTopic["orchestration-result"]
    end
    
    subgraph Steps ["Execution Steps"]
        Step1["Validate Claim"]
        Step2["Create Outbox Event"]
        Step3["Publish Event"]
        Step4["Process Async"]
    end
    
    Request -->|1. Publish| ReqTopic
    ReqTopic -->|2. Receive| Init
    Init -->|3. Transition| Validate
    Validate -->|4. Emit| CmdTopic
    CmdTopic -->|5. Execute| Step1
    Step1 -->|6. Result| RespTopic
    RespTopic -->|7. Receive| Process
    Process -->|Success| Complete
    Process -->|Failure| Compensate
    Compensate -->|Undo Changes| Step4
    Complete -->|8. Emit| CompTopic
    Failed -->|8. Emit| CompTopic
    
    style Init fill:#e3f2fd
    style Complete fill:#c8e6c9
    style Failed fill:#ffcdd2
    style Compensate fill:#ffe0b2
```

---

## 🔐 Authentication & Authorization Flow

```mermaid
sequenceDiagram
    participant Client
    participant Gateway
    participant Keycloak
    participant JWT as JWT<br/>Parser
    participant Service
    participant Auth as Authorization<br/>Filter
    
    Client->>Keycloak: 1. Login (username/password)
    Keycloak->>Keycloak: 2. Validate Credentials
    Keycloak-->>Client: 3. Access Token (JWT)
    
    Client->>Gateway: 4. API Request<br/>(Bearer Token)
    
    Gateway->>JWT: 5. Extract & Decode JWT
    JWT->>Keycloak: 6. Verify Signature (JWKS)
    Keycloak-->>JWT: 7. Valid ✓
    
    JWT->>JWT: 8. Extract Claims<br/>- user_id<br/>- realm_access.roles<br/>- resource_access.roles
    
    Gateway->>Auth: 9. Check Authorization<br/>(roles, permissions)
    
    alt Authorized
        Auth-->>Gateway: 10. Proceed ✓
        Gateway->>Service: 11. Forward Request<br/>(SecurityContext + Claims)
        Service->>Auth: 12. Authorize Action
        Auth-->>Service: 13. Granted ✓
        Service-->>Gateway: 14. Response
        Gateway-->>Client: 15. 200 OK
    else Not Authorized
        Auth-->>Gateway: 10. Deny
        Gateway-->>Client: 15. 403 Forbidden
    end
```

---

## 💾 Transactional Outbox Flow

```mermaid
graph LR
    Request["Write Request"]
    TX["Transaction Boundary"]
    Write["Write Business<br/>Data"]
    Outbox["Write to<br/>Outbox Table"]
    Commit["Commit"]
    Publisher["Outbox<br/>Publisher<br/>Scheduled"]
    Kafka["Kafka<br/>Topic"]
    Consumer["Event<br/>Consumer"]
    ReadModel["Update Read<br/>Model"]
    Cache["Invalidate<br/>Cache"]
    
    Request -->|Begin| TX
    TX -->|1. Write| Write
    Write -->|PostgreSQL| Write
    TX -->|2. Write| Outbox
    Outbox -->|Same TX| Outbox
    TX -->|3. Commit| Commit
    
    Commit -->|Success| Publisher
    Publisher -->|Async<br/>Polling| Publisher
    Publisher -->|Read Unpublished| Outbox
    Outbox -->|4. Publish| Kafka
    Kafka -->|5. Ack| Publisher
    Publisher -->|6. Mark Published| Outbox
    
    Kafka -->|7. Consume| Consumer
    Consumer -->|8. Update| ReadModel
    ReadModel -->|MongoDB| ReadModel
    Consumer -->|9. Invalidate| Cache
    Cache -->|Redis| Cache
```

---

## 🔄 Kafka Message Flow

```mermaid
graph TB
    Producer["Claim Service<br/>Producer"]
    OutboxPub["Outbox Publisher"]
    Topic["Kafka Topic"]
    DLT["Dead Letter Topic"]
    Consumer["Claims/Agent Service<br/>Consumer"]
    Process["Process Message"]
    Success["Update State"]
    Failed["Write to DLT"]
    Retry["Retry with<br/>Backoff"]
    MaxRetry["Max Retries<br/>Exceeded"]
    
    Producer -->|Send| OutboxPub
    OutboxPub -->|Idempotent Delivery<br/>Enable: true| Topic
    Topic -->|Partition by<br/>Claim ID| Consumer
    
    Consumer -->|1. Receive| Process
    
    alt Success
        Process -->|2. Process| Success
        Success -->|Update State| Consumer
        Consumer -->|3. Commit Offset| Topic
    else Failure
        Process -->|Retry Count < 3| Retry
        Retry -->|Backoff: 1s→2s→4s| Topic
        Retry -->|Retry Exceeded| MaxRetry
        MaxRetry -->|Write| DLT
        DLT -->|Manual Investigation| Failed
    end
```

---

## 🎯 Redis Cache Flow

```mermaid
graph TB
    Request["Query Request"]
    Service["Service Layer"]
    Cache["Redis Cache"]
    DB["PostgreSQL"]
    
    Request -->|1. Request Data| Service
    Service -->|2. Check Cache<br/>Key: claim:status:{claimId}| Cache
    
    alt Hit
        Cache -->|3. Found| Service
        Service -->|4. Return Cached<br/>TTL: 30s| Service
    else Miss
        Cache -->|3. Not Found| Service
        Service -->|4. Query| DB
        DB -->|5. Return| Service
        Service -->|6. Cache Result<br/>TTL: 30s| Cache
        Cache -->|7. Stored| Cache
        Service -->|8. Return| Service
    end
    
    Service -->|Response| Request
```

---

## 🐳 Deployment Architecture - Kubernetes

```mermaid
graph TB
    subgraph Ingress ["Ingress Controller"]
        IngCtrl["nginx-ingress"]
    end
    
    subgraph LoadBalancer ["Load Balancer"]
        LB["Service LoadBalancer<br/>api-gateway"]
    end
    
    subgraph Services ["Kubernetes Pods"]
        GW["API Gateway<br/>Replicas: 2-3"]
        CS["Customer Service<br/>Replicas: 2-3"]
        CLS["Claims Service<br/>Replicas: 2-3"]
        AS["Agent Service<br/>Replicas: 2-3"]
    end
    
    subgraph Autoscaling ["HPA"]
        HPA["Horizontal Pod<br/>Autoscaler<br/>CPU: 70%<br/>Memory: 80%"]
    end
    
    subgraph Storage ["Persistent Storage"]
        PV["PostgreSQL PVC"]
        MV["MongoDB PVC"]
        KV["Kafka PVC"]
    end
    
    subgraph ConfigMaps ["ConfigMaps & Secrets"]
        CM["Config Maps"]
        Secrets["Kubernetes Secrets"]
    end
    
    Internet["Internet"]
    
    Internet -->|Port 443| Ingress
    Ingress -->|Route| LB
    LB -->|Port 8080| GW
    
    GW -->|Service DNS| CS
    GW -->|Service DNS| CLS
    GW -->|Service DNS| AS
    
    CS -->|ConfigMap| CM
    CLS -->|ConfigMap| CM
    AS -->|ConfigMap| CM
    
    CS -->|Secret| Secrets
    CLS -->|Secret| Secrets
    AS -->|Secret| Secrets
    
    HPA -->|Scale| GW
    HPA -->|Scale| CS
    HPA -->|Scale| CLS
    HPA -->|Scale| AS
    
    CS -->|Mount| PV
    CLS -->|Mount| PV
    AS -->|Mount| KV
    
    style GW fill:#fff3e0
    style CS fill:#e8f5e9
    style CLS fill:#e8f5e9
    style AS fill:#e8f5e9
    style HPA fill:#f3e5f5
```

---

## 🔗 Service-to-Service Communication

```mermaid
graph LR
    Caller["Claims Service<br/>(Caller)"]
    Circuit["Circuit Breaker<br/>(Resilience4j)"]
    Feign["Feign Client"]
    OAuth["OAuth2<br/>Client Credentials"]
    Discovery["Service Discovery<br/>(Eureka)"]
    Target["Customer Service<br/>(Target)"]
    
    Caller -->|1. Call via| Feign
    Feign -->|2. Resolve DNS| Discovery
    Discovery -->|3. Return Instances| Feign
    Feign -->|4. Circuit Status| Circuit
    
    alt Circuit Closed (Normal)
        Feign -->|5. Send Request| OAuth
        OAuth -->|6. Get Token| OAuth
        Feign -->|7. Add Authorization<br/>Bearer Token| Feign
        Feign -->|8. Call Service| Target
        Target -->|9. Response| Feign
        Feign -->|10. Record Success| Circuit
    else Circuit Open (Failed)
        Circuit -->|Fail Fast<br/>After 50% failures| Feign
        Feign -->|Retry with Backoff| Feign
    end
    
    Feign -->|11. Return to| Caller
```

---

## 📊 Component Interaction Matrix

| Source | Target | Protocol | Purpose | Security |
|--------|--------|----------|---------|----------|
| Client | Gateway | HTTPS | Public API | JWT Bearer |
| Gateway | Services | HTTP | Internal routing | Correlation ID |
| Services | Keycloak | HTTPS | Token verification | Client credentials |
| Services | PostgreSQL | TCP | Write operations | Connection pooling |
| Services | MongoDB | TCP | Read operations | Connection pooling |
| Services | Redis | TCP | Caching | Password protected |
| Services | Kafka | TCP | Event publishing | None (internal) |
| Services | Discovery | HTTP | Service registration | None (internal) |
| Services | Config Server | HTTP | Config retrieval | Git auth |
| Zipkin | Services | HTTP | Trace collection | None (internal) |

---

## 🎯 Deployment Topology - Helm

```mermaid
graph TB
    HelmChart["Helm Chart"]
    
    subgraph Templates ["templates/"]
        Deployment["deployment.yaml"]
        Service["service.yaml"]
        ConfigMap["configmap.yaml"]
        Secret["secret.yaml"]
        HPA["hpa.yaml"]
        PVC["pvc.yaml"]
    end
    
    subgraph Values ["values.yaml variants"]
        ValuesLocal["values-local.yaml"]
        ValuesDev["values-dev.yaml"]
        ValuesQA["values-qa.yaml"]
        ValuesUAT["values-uat.yaml"]
        ValuesProd["values-prod.yaml"]
    end
    
    HelmChart -->|Contains| Templates
    HelmChart -->|Contains| Values
    
    ValuesLocal -->|Local: 1 replica| Deployment
    ValuesDev -->|Dev: 2 replicas| Deployment
    ValuesQA -->|QA: 3 replicas| Deployment
    ValuesUAT -->|UAT: 3 replicas| Deployment
    ValuesProd -->|Prod: 4 replicas| Deployment
    
    Deployment -->|Creates| Service
    Deployment -->|Uses| ConfigMap
    Deployment -->|Uses| Secret
    Deployment -->|Managed by| HPA
    Deployment -->|Uses| PVC
```

---

## 📈 Data Flow - Claim Submission

```mermaid
graph LR
    A["1. Client<br/>Submits Claim"]
    B["2. Gateway<br/>Validates JWT"]
    C["3. Claims Service<br/>Receives Request"]
    D["4. Business Logic<br/>Validation"]
    E["5. Save to<br/>PostgreSQL"]
    F["6. Create Outbox<br/>Event"]
    G["7. Commit Transaction"]
    H["8. Outbox Publisher<br/>Polls Event"]
    I["9. Publish to<br/>Kafka"]
    J["10. Consumers<br/>Process Event"]
    K["11. Update Read<br/>Models"]
    L["12. Invalidate<br/>Cache"]
    
    A -->|JWT| B
    B -->|Route| C
    C -->|Validate| D
    D -->|Transactional| E
    E -->|Same TX| F
    F -->|Atomic| G
    G -->|Async Poll| H
    H -->|Idempotent| I
    I -->|Partition| J
    J -->|Update| K
    K -->|Async| L
```

---

## 🔒 Security Layers

```mermaid
graph TB
    Client["Client"]
    
    Layer1["Layer 1: HTTPS/TLS<br/>Encrypted Transport"]
    Layer2["Layer 2: API Gateway<br/>Rate Limiting"]
    Layer3["Layer 3: JWT Validation<br/>Signature Verification"]
    Layer4["Layer 4: Authorization<br/>Role-Based Access"]
    Layer5["Layer 5: Service<br/>Business Logic"]
    Layer6["Layer 6: Database<br/>Connection Pooling"]
    
    Client -->|Secure Channel| Layer1
    Layer1 -->|Validate| Layer2
    Layer2 -->|Check Token| Layer3
    Layer3 -->|Verify Roles| Layer4
    Layer4 -->|Execute| Layer5
    Layer5 -->|Encrypted Connection| Layer6
    
    style Layer1 fill:#ffcdd2
    style Layer2 fill:#ffcdd2
    style Layer3 fill:#ffcdd2
    style Layer4 fill:#ffcdd2
    style Layer5 fill:#c8e6c9
    style Layer6 fill:#c8e6c9
```

---

**Next:** Review specific guides for detailed implementation of each component.
