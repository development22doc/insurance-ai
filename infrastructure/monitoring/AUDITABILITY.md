# ClaimAssist — Auditability & Observability Model (Task 6A-13)

This document states, **honestly**, what CAN and CANNOT be audited in the FREE OCI
architecture. The ClaimAssist platform does **not** pretend that every subsystem
offers identical CRUD auditing. It defines six distinct audit concerns:

| Concern | Mechanism | Nature |
|---------|-----------|--------|
| A. Application audit | EventLogger → `event.*` structured logs | Log-based, env-labeled, in Loki |
| B. Database audit | Application audit events + PG metadata | **NOT** forensic DB auditing |
| C. Kafka event traceability | EventLogger KAFKA + outbox trace context | Event-level, producer/consumer linkage |
| D. Redis observability | Micrometer redis metrics + health | Cache behaviour only, **no** audit trail |
| E. Keycloak audit | Keycloak native events + admin-console | Native identity/security events |
| F. Kubernetes/Admin audit | K8s API audit log + RBAC | Documented, constrained by free-tier access |

---

## 1. LOGGING ARCHITECTURE (shared by all concerns)

- `common-lib` ships `logback-spring.xml` (root) with a **JSON** console encoder
  (logstash-logback-encoder) emitting fields: `timestamp, version, logger, thread,
  level, message, exception` plus MDC keys `correlationId, requestId, traceId, spanId`.
- MDC correlation/request/trace/span ids are populated by
  `CorrelationIdFilter` + `MDCUtility` and the Micrometer tracing instrumentation.
- `DefaultEventLogger` writes to logger names derived from `LogCategories`
  (e.g. a dedicated logger per event category) so logback can route them.
- In Kubernetes, the pod container stdout (the JSON stream) is shipped by
  **Promtail → Loki → Grafana**, labeled by `environment` (= namespace) and
  `service`. `correlationId/traceId/spanId/level` are extracted to queryable labels.
- Audit logs carry the `environment` label from the pod's namespace. **No
  environment's logs are written into another environment's stream.**

## 2. CORRELATION / TRACEABILITY

- **Correlation/request id**: `CorrelationIdFilter` (business-level, greppable)
  automatically propagated across Feign/RestTemplate/WebClient
  (`FeignCorrelationRequestInterceptor`, `RestTemplateCorrelationInterceptor`,
  `WebClientCorrelationFilter`).
- **Trace/span id**: Micrometer Tracing (Sampler) + Zipkin. Trace ids are also
  echoed in response headers and MDC, and captured in JSON logs.
- **Kafka**: producer/consumer `observation-enabled` (spring.kafka) + outbox
  trace-context migrations (`V7__add_outbox_trace_context.sql`,
  `V5__add_outbox_trace_context.sql`) carry correlation/trace into events and give
  producer→consumer linkage.

## 3. APPLICATION AUDIT (A) — what is captured

`EventLogger` (`EventType`: REQUEST, BUSINESS, SECURITY, DATABASE, CACHE, KAFKA,
PERFORMANCE, EXCEPTION), invoked across:
- Auth flows: `AuthController` (authorize/callback/refresh/logout),
  `CustomerSignupService`, `KeycloakUserProvisioningService`.
- Customer lifecycle: `CustomerService` CRUD + compensation events.
- Claims: `ClaimCommandServiceImpl` (claim creation / status changes),
  `CustomerServiceGateway`.
- Agent: `AgentTurnPersistenceService`, `AgentGenerationServiceImpl`,
  `AgentSagaResponseHandler`.
- Gateway security: `SecurityEventsListener`, `RateLimitResponseFilterConfiguration`.

Canonical audit fields (where applicable): `eventType/event`, `service`,
`application`, `correlationId`, `traceId`, `spanId`, `timestamp`, `durationMs`,
`result`, `actor`/`state`, safe metadata. **Secrets are never stored in audit
records** — see Security findings.

## 4. DATABASE AUDIT (B) — honest status

- The authoritative "who changed what" trail is the **application audit event**
  emitted by the business service at the moment of the write, tagged with
  actor + timestamp + entity id (where recorded) + correlation/trace id.
- **There is NO PostgreSQL forensic audit**: no `pg_audit`/`pgaudit` extension, no
  trigger-based audit tables, no logical replication. On the FREE tier, Hibernate
  owns schema (`ddl-auto=none`, schema owned by Flyway) and per-statement auditing
  would require an extension + architectural change that was deliberately **not**
  made (out of scope for 6A-13, and cost/resource-heavy).
- I.e. **"who INSERT/UPDATE/DELETE'd a DB row" can be answered only at the
  application layer**, via the business/security audit events that accompany the
  write. Direct SQL/psql DML is NOT attributable.
- Database metadata (e.g. `xmin`, audit columns) provides data lineage, not actor
  attribution.

## 5. KAFKA EVENT TRACEABILITY (C)

- Kafka is event-based, not a CRUD database. Traceability is **per-event**, not
  CRUD-audit:
  - event/correlation/trace id in the outbox (+ consumer headers)
  - producer service + consumer service/consumer-group
  - timestamp, aggregate/entity id where available
- Micrometer records producer/consumer metrics (rate, lag, error) → Prometheus.
- **No credentials, tokens, or sensitive payloads are logged.** Payload bodies are
  not written to audit logs.

## 6. REDIS OBSERVABILITY / AUDIT (D) — explicit limitation

- Redis is a **cache / ephemeral** resource. It has **no business-data audit
  history**. The platform does not claim otherwise.
- Auditable / observable: cache **hit/miss** (via Spring Cache/micrometer), Redis
  **health** (`RedisHealthIndicator`), **operations**, **memory**, and **failures**
  → Prometheus + readiness.
- **Redis persistence is intentionally NOT enabled** (RDB `--save ""`, AOF off,
  no PVC). It is added purely as a cache; privilege loss is acceptable; the
  authoritative audit trail for cached data is the underlying business operation /
  source of truth (PostgreSQL + application audit).

## 7. KEYCLOAK AUDIT (E)

- Uses **Keycloak's native event / audit capabilities**: realm import enables
  standard login/failed-login/logout/token and admin events; the Keycloak admin
  console has event retention (database-backed, in its dedicated PostgreSQL).
- Admin configuration / user / client / role changes are captured by Keycloak's
  own admin-event store (not by a custom implementation).
- The internal `claimassist-admin-service` confidential client (client_credentials /
  user management API) traffic is reflected in Keycloak events.
- **No custom Keycloak audit re-implementation was created.** Keycloak admin is
  INTERNAL ONLY (no NodePort/LoadBalancer/Ingress); admin credentials are never
  committed and never logged.
- Documented retention: Keycloak default event settings; operator should raise
  retention if longer history is needed (out of scope to configure blindly).

## 8. KUBERNETES / ADMIN AUDIT (F) — honest constraint

- Operators need ADMIN = modify configuration/deployments/secrets as authorized;
  others = read-only. This is enforced by RBAC in a later task (6A-14); 6A-13 does
  **not** grant any admin rights. The observability stack uses only read-only
  ClusterRoles (Prometheus: list/watch pods; Promtail: read pod logs).
- **API-server audit logging**: on the free K3s tier this requires enabling the
  `kube-apiserver` audit policy + log backend, which is **not currently active** and
  cannot be validated from this repository (no reachable cluster; K3s not installed).
  This is documented as a **limitation, not claimed as enabled**. When API server
  audit is enabled, it is the authoritative record of who created/modified
  deployments, secrets, etc.
- Application-level evidence of admin/configuration actions is available via
  application SECURITY/BUSINESS audit events where the app records them; Kubernetes
  object-level attribution currently relies on RBAC + the (future) API-server audit
  log.

## 9. ENVIRONMENT ISOLATION

- K8s namespaces = environments: `claimassist-dev`, `claimassist-stage`,
  `claimassist-prod`. Each has isolated PostgreSQL/Redis/Kafka/Keycloak endpoints
  (no cross-environment DB/Redis/Kafka/issuer references in the Helm chart).
- Shared observability stores metrics/logs for all three but **tags every
  series/line with `environment`** (sourced from the namespace). Filters use
  `environment="$environment"`, so data is never presented/mixed across environments.

## 10. SECURITY / LOG-HYGIENE

- `CorrelationIdFilter` masks the `Authorization` header (scheme + `***`), the
  `Cookie` header (entirely) and JWT/token-like values.
- **Task 6A-13 fix**: the same filter previously logged the raw request *query
  string*; OAuth `code`/tokens in query were at risk. It now redacts credential
  keys (`code`, `token`, `secret`, `password`, `authorization`, `credential`,
  `api_key`, `client_secret`, `code_verifier`, `code_challenge`, etc.).
- Business/security audit events do not log authorization codes, JWTs, refresh
  tokens, client secrets, DB credentials, or Keycloak admin passwords.

## 11. RESOURCE / COST ASSESSMENT (FREE OCI)

- The observability stack is deployed as **ONE shared instance** across the three
  namespaces (not per-environment), to stay inside the single Always-Free node
  budget. Requests ≈ Prometheus 100m/256Mi, Grafana 100m/256Mi, Loki 100m/256Mi,
  Promtail(DS) 25m/64Mi, Zipkin 100m/256Mi.
- Loki retention is capped (14d) and Prometheus retention at 7d; no managed / paid
  services are used. This is a conscious reduction to keep the A1 footprint
  reasonable. Operators can raise retention at a documented cost to disk.

## 12. VALIDATION STATUS

- Helm lint/template (DEV/STAGE/PROD) — pass (see master_change_log.md).
- Kubernetes **live** validation: **NOT AVAILABLE** — no reachable cluster; K3s was
  NOT installed per locked instructions. Manifests are static-rendered and reviewed,
  and are intended to be applied once a cluster exists.