# ClaimAssist Frontend-Backend Mapping

## Executive Summary

This document maps all backend capabilities to intended frontend screens. The ClaimAssist backend is a 7-module Spring Cloud microservices platform with Keycloak OAuth2/OIDC authentication, consisting of:

- **customer-service**: Customer identity, policies, billing (Stripe)
- **claims-service**: Claims, documents, saga orchestration
- **agent-service**: AI claims assistant with Ollama LLM
- **common-lib**: Shared foundation library (auth, DTOs, events, exceptions)
- **api-gateway**: Spring Cloud Gateway with JWT validation, rate limiting, CORS
- **discovery-service**: Eureka service registry
- **config-service**: Spring Cloud Config Server (git-backed)

All services use OAuth2 Resource Server validation with Keycloak. The platform supports three environments: local (Docker compose), local-k8s (Tailscale to OCI K3s), and K8s production.

---

## Repository/Service Inventory

| Service | Port | Purpose | Key Features |
|---------|------|---------|--------------|
| `discovery-service` | 8761 | Eureka service registry | Service registry, zero business logic, TLS disabled |
| `config-service` | 8888 | Spring Cloud Config Server | Git-backed configuration, native/k8s profiles |
| `api-gateway` | 8080 | API Gateway | Spring Cloud Gateway, JWT validation, rate limiting, CORS, circuit breaking |
| `customer-service` | 8081 | Customer identity & policies | PostgreSQL, Redis, Kafka, Stripe, Keycloak OAuth2 PKCE |
| `claims-service` | 8082 | Claims, documents, saga | PostgreSQL, Redis, Kafka, Flyway (V1-V11), Outbox pattern |
| `agent-service` | 8083 | AI claims assistant | Spring AI + Ollama (qwen2.5-coder:3b), cache, Flyway (V1-V6) |
| `common-lib` | N/A | Shared foundation | Keycloak auth, correlation ID, exceptions, DTOs, Kafka, Kafka, logging |

---

## API Inventory

### Authentication APIs

| Service | Method | URL | Purpose | Auth Req | Role/Permission |
|---------|--------|-----|---------|----------|-----------------|
| customer-service | POST | `/auth/signup` | Customer registration | None | None |
| customer-service | GET | `/auth/authorize` | OAuth2 authorize start | None (public) | None |
| customer-service | GET | `/auth/callback` | OAuth2 callback / token exchange | None (public) | None |
| customer-service | POST | `/auth/refresh` | Refresh access token | Refresh token | None |
| customer-service | POST | `/auth/logout` | Logout / revoke refresh token | Refresh token | None |
| gateway | GET | `/auth/**, /api/v1/auth/**, /actuator/health/**, /actuator/info, /v3/api-docs/**, /swagger-ui/**, /swagger-ui.html` | Public routes | None (public) | None |

**Auth Response Structure** (AuthController):
```json
{
  "accessToken": "jwt",
  "refreshToken": "jwt",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "refreshExpiresIn": 86400,
  "scope": "openid profile email",
  "idToken": "jwt",
  "customerId": 123,
  "fullName": "John Doe"
}
```

### Customer APIs

| Service | Method | URL | Purpose | Auth Req | Role/Permission | Path Params | Query Params | Request Body | Response |
|---------|--------|-----|---------|----------|----------------|-------------|--------------|--------------|----------|
| customer-service | POST | `/customers/{customerId}` | Update customer profile | JWT | User (own id only) | customerId | - | UpdateCustomerRequest { fullName } | CustomerResponse { id, username, fullName, kycStatus } |
| customer-service | DELETE | `/customers/{customerId}` | Delete customer account | JWT | User (own id only) | customerId | - | - | 204 No Content |
| customer-service | GET | `/policies/all` | Get all customer policies | JWT | User | - | - | - | List<PolicyResponse> |
| customer-service | GET | `/policies/{policyId}` | Get policy by ID | JWT | User | policyId | - | - | PolicyResponse |
| customer-service | POST | `/policies` | Create new policy | JWT | User | - | - | PolicyCreateRequest { coveragePlanId, effectiveDate, renewalDate } | PolicyResponse |
| customer-service | PUT | `/policies/{policyId}` | Update policy | JWT | User | policyId | - | PolicyUpdateRequest { status, renewalDate } | PolicyResponse |
| customer-service | DELETE | `/policies/{policyId}` | Delete policy | JWT | User | policyId | - | - | 204 No Content |
| customer-service | GET | `/internal/v1/policies/{policyId}/coverage` | Get policy coverage (internal) | JWT or Service | User or Service | policyId | X-User-Id (optional) | - | PolicyCoverageDto |

### Policy DTOs

**PolicyResponse**: id, policyNumber, status, coveragePlanName, productType, effectiveDate, renewalDate

**PolicyCreateRequest**: coveragePlanId (Required), effectiveDate (Required), renewalDate

**PolicyUpdateRequest**: status (Optional), renewalDate (Optional)

### Claims APIs

| Service | Method | URL | Purpose | Auth Req | Role/Permission | Path Params | Query Params | Request Body | Response |
|---------|--------|-----|---------|----------|----------------|-------------|--------------|--------------|----------|
| claims-service | GET | `/claims` | Get current user's claims | JWT | User | - | - | - | List<ClaimSummaryResponse> |
| claims-service | GET | `/claims/{id}` | Get claim by ID | JWT | User | id | - | - | ClaimSummaryResponse |
| claims-service | POST | `/claims` | Submit new claim | JWT | User | - | - | ClaimRequest { policyId, incidentType, incidentDate, estimatedAmountCents } | ClaimResponse { id, claimNumber, status, incidentType } |
| claims-service | PATCH | `/claims/{id}/status` | Update claim status | JWT | User (via @security) | id | - | UpdateClaimStatusRequest { status, note } | ClaimSummaryResponse |
| claims-service | GET | `/internal/v1/claims/{claimId}/status` | Get claim status (internal) | JWT or Service | User or Service | claimId | - | - | ClaimStatusDto |
| claims-service | GET | `/internal/v1/claims/{claimId}/documents` | Get claim documents | JWT or Service | User or Service | claimId | - | - | List<ClaimDocumentSummaryDto> |
| claims-service | GET | `/internal/v1/claims/{claimId}/permissions/check` | Check claim permission | JWT or Service | User or Service | claimId | permission (ClaimPermission) | - | boolean |

**ClaimRequest**: policyId (Required), incidentType (Required), incidentDate (Required, not future), estimatedAmountCents (Optional)

**ClaimResponse**: id, claimNumber, status, incidentType

**ClaimSummaryResponse**: id, claimNumber, policyId, incidentType, status, estimatedAmountCents, approvedAmountCents, role, incidentDate, createdAt

**UpdateClaimStatusRequest**: status (Required), note (Optional)

**ClaimStatusDto**: claimId, policyId, claimNumber, status, incidentType, estimatedAmountCents, approvedAmountCents, history [StatusHistoryEntry { fromStatus, toStatus, changedBy, changedAt }]

**ClaimDocumentSummaryDto**: documentId, docType, ocrStatus, extractedText, fraudSignalScore

**ClaimPermission**: VIEW, SUBMIT_DOCUMENTS, UPDATE_STATUS, VIEW_PARTIES, MANAGE_PARTIES, VIEW_AUDIT_TRAIL

**ClaimRole**: POLICYHOLDER (VIEW, SUBMIT_DOCUMENTS, VIEW_PARTIES), ADJUSTER (VIEW, SUBMIT_DOCUMENTS, UPDATE_STATUS, VIEW_PARTIES, MANAGE_PARTIES), AUDITOR (VIEW, VIEW_PARTIES, VIEW_AUDIT_TRAIL)

### Claim Document APIs

| Service | Method | URL | Purpose | Auth Req | Role/Permission | Path Params | Query Params | Request Body | Response |
|---------|--------|-----|---------|----------|----------------|-------------|--------------|--------------|----------|
| claims-service | GET | `/internal/v1/claims/{claimId}/documents` | Get claim documents | JWT or Service | User or Service | claimId | - | - | List<ClaimDocumentSummaryDto> |
| InternalClaimsController | - | `/internal/v1/claims/{claimId}/documents` | Internal document listing | JWT or Service | User or Service | claimId | - | - | List<ClaimDocumentSummaryDto> |

**ClaimDocument entity**: id, claim, path, minioObjectKey, docType (PHOTO/POLICE_REPORT/MEDICAL_BILL/REPAIR_ESTIMATE), ocrStatus (PENDING/COMPLETED), extractedText, fraudSignalScore, processingStatus (PENDING/COMPLETED/FAILED), processingError, fraudFlag, uploadedAt, processedAt, createdAt

### Agent/AI APIs

| Service | Method | URL | Purpose | Auth Req | Role/Permission | Path Params | Query Params | Request Body | Response |
|---------|--------|----- |----------|----------|----------------|-------------|--------------|--------------|----------|
| agent-service | POST | `/agent/stream` | Stream AI response (SSE) | JWT | User | claimId | - | AgentRequest { message, claimId } | Flux<ServerSentEvent<StreamResponse>> |
| agent-service | GET | `/agent/claims/{claimId}` | Get conversation history | JWT | User | claimId | - | - | List<AgentMessageResponse> |

**AgentRequest**: message (Required), claimId (Required)

**AgentMessageResponse**: id, role (MessageRole), content, tokensUsed, createdAt, events [AgentEventResponse]

**StreamResponse**: text, eventType (message/done/error), requestId, done (boolean), errorCode (String)

**AgentMessageResponse.eventType**: message, done, error

**AgentEventResponse**: id, type, status, sequenceOrder, content, sagaId, proposedStatus

Agent SSE Events stream:
- `message` events: token chunks
- `done` event: terminal event with done=true
- `error` event: error with errorCode and error message

### Internal Service APIs (client-credentials)

These endpoints use client-credentials flow (internal-service Keycloak client), not user JWTs:

| Service | Method | URL | Purpose | Auth |
|---------|--------|-----|---------|------|
| customer-service | Various | `/internal/v1/policies/{policyId}/coverage` | Internal policy coverage | Service token (client-credentials) |
| claims-service internal endpoints | - | `/internal/v1/claims/{claimId}/status`, `/documents`, `/permissions/check` | Internal claim operations | Service token |

---

### Document Functionality

| Capability | Status | Details |
|-----------|--------|---------|
| Upload | PARTIALLY | ClaimDocument entity has path/minioObjectKey; actual upload not in controllers - likely multipart/form-data or MinIO SDK usage |
| Download | UNKNOWN | No download endpoints found in controllers; likely MinIO browser access |
| View/Preview | UNKNOWN | No view/preview endpoints; OCR status indicates async processing |
| Metadata | IMPLEMENTED | ClaimDocumentResponseDto includes: id, docType, ocrStatus, extractedText, fraudSignalScore, ocrStatus, uploadedAt, processingStatus |
| Claim Association | IMPLEMENTED | ClaimDocument has @ManyToOne to Claim, mapped by claim_id |
| Policy Association | UNKNOWN | Not explicitly documented |
| Validation | IMPLEMENTED | docType validation (PHOTO/POLICE_REPORT/MEDICAL_BILL/REPAIR_ESTIMATE), ocrStatus defaults to PENDING |
| File Types | UNKNOWN | Not explicitly documented in controllers |
| File Size | UNKNOWN | Not documented in controllers |
| Document Status | IMPLEMENTED | ocrStatus (PENDING/COMPLETED), processingStatus (PENDING/COMPLETED/FAILED), processingError, fraudSignalScore, fraudFlag |

### AI Functionality

| Capability | Status | Details |
|-----------|----------|---------|
| AI endpoints | IMPLEMENTED | `/agent/stream` (SSE) and `/agent/claims/{claimId}` |
| Request format | IMPLEMENTED | AgentRequest { message, claimId } |
| Response format | SSE streaming | Flux<ServerSentEvent<StreamResponse>> with message/done/error events |
| Claim analysis | IMPLEMENTED | Agent can analyze claim conversations |
| Document analysis | UNKNOWN | Not explicitly documented in controllers |
| Assessment | IMPLEMENTED | Agent provides assessment via conversation |
| Risk/fraud | IMPLEMENTED | fraudSignalScore and fraudFlag on ClaimDocument |
| Recommendations | IMPLEMENTED | Agent can provide recommendations via conversation |
| Processing status | IMPLEMENTED | processingStatus on ClaimDocument: PENDING/COMPLETED/FAILED |
| Asynchronous processing | IMPLEMENTED | Kafka outbox pattern, document-processing worker pool (K8s) |
| Streaming | IMPLEMENTED | /agent/stream uses Server-Sent Events |

### Employee/Operations Functionality

| Capability | Status | Details |
|-----------|----------|---------|
| Employee dashboard | UNKNOWN | No dedicated employee/service controller found |
| Claim queue | PARTIALLY | Claims listing with filters/pagination possible |
| Search | UNKNOWN | No dedicated search endpoints found |
| Filters | UNKNOWN | No dedicated filter endpoints found |
| Pagination | IMPLEMENTED | Customer policies use Pageable, claims use pagination |
| Assignment | UNKNOWN | No assignment endpoints found |
| Claim workspace | UNKNOWN | No workspace endpoints found |
| AI assessment | IMPLEMENTED | Agent/AI assessment via /agent/stream |
| Surveyor assignment | UNKNOWN | No surveyor assignment endpoints found |
| Approval | PARTIALLY | Claim status update via PATCH /{id}/status |
| Rejection | PARTIALLY | Status change can reject/deny claims |
| Request information | IMPLEMENTED | GET /claims, GET /claims/{id} |
| Claim status changes | IMPLEMENTED | PATCH /{id}/status with UpdateClaimStatusRequest |

### Admin Functionality

| Capability | Status | Details |
|-----------|----------|---------|
| Dashboard | UNKNOWN | No admin-specific endpoints |
| Users | PARTIALLY | Keycloak admin console; no custom user management APIs |
| RBAC | IMPLEMENTED | Keycloak realm roles (CUSTOMER, ADJUSTER, AUDITOR) + database-backed ClaimRole/ClaimPermission |
| Products | UNKNOWN | No product management APIs |
| AI | IMPLEMENTED | Agent service AI capabilities |
| Fraud/Risk | PARTIALLY | fraudSignalScore/fraudFlag on claims |
| Audit | PARTIALLY | Claim audit trail via ClaimStatusDto.history, VIEW_AUDIT_TRAIL permission |
| Notifications | UNKNOWN | Not documented in controllers |
| RBAC/Permissions | IMPLEMENTED | Keycloak realm roles + database-backed ClaimPermission/ClaimRole system |

---

## Authentication

### Mechanism

**OAuth2/OIDC with Keycloak** is the authentication mechanism across the entire platform.

- **Identity Provider**: Keycloak at `http://localhost:8180/realms/claimassist` (local) or `http://100.114.133.69:30080/realms/claimassist-dev` (K8s/local-k8s)
- **Realm**: `claimassist` (local) / `claimassist-dev` (K8s)

### Customer App (Public Flow)

- **Client**: `claimassist-customer-app` (public client, PKCE enabled)
- **Flow**: Authorization Code with PKCE
- **Redirect URIs**: `http://localhost:8080/customer/auth/callback`, `http://localhost:3000/*`
- **Scope**: openid, profile, email, userId-claim

### Token Response

```json
{
  "accessToken": "jwt",
  "refreshToken": "jwt", 
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "refreshExpiresIn": 86400,
  "scope": "openid profile email",
  "idToken": "jwt",
  "customerId": 123,
  "fullName": "John Doe"
}

- **accessToken**: JWT, expires in 6000 seconds (100 min) per Keycloak realm config
- **refreshToken**: Used to get new access tokens
- **expiresIn**: 3600 seconds (1 hour) per AuthResponse
- **refreshExpiresIn**: 86400 seconds (24 hours) per AuthResponse
- **tokenType**: "Bearer"
- **expiresIn**: 3600 (1 hour) per Keycloak realm accessTokenLifespan=6000 (actually ~100 min, likely rounded)

### Internal Service Calls (client-credentials)

- **Client**: `claimassist-admin-service` (confidential client)
- **Flow**: client-credentials
- **Token URI**: `http://localhost:8180/realms/claimassist/protocol/openid-connect/token`
- **Client ID**: `claimassist-admin-service`
- **Client Secret**: `local-dev-admin-client-secret` (local) / env var (K8s)

### Authorization

- **Realm Roles**: CUSTOMER (default), ADJUSTER, AUDITOR
- **Fine-grained Authorization**: Database-backed ClaimRole/ClaimPermission system in claims-service
- **@PreAuthorize**: Used on claim status update: `@security.canUpdateStatus(#id)`
- **CurrentUserProvider**: Extracts userId from JWT "userId" claim (legacy_user_id), not Keycloak "sub"

### Authorization Headers

- **Authorization**: `Bearer <jwt>` - Standard JWT authorization header
- **X-User-Id**: Optional header for internal calls (InternalCustomerController)

### Authentication Error Responses

| Status | Response | Description |
|--------|----------|-------------|
| 401 | ApiError { status, message, timestamp, correlationId } | Missing or invalid bearer token |
| 403 | ApiError { status, message, timestamp, correlationId } | Access denied: Insufficient permissions |
| 401 | "Invalid credentials" | AuthenticationException handler |

---

## Authorization

### Roles

| Role | Description | Default Assigned |
|------|-------------|------------------|
| CUSTOMER | Default role for customers | Yes (all authenticated users) |
| ADJUSTER | Claims Adjuster | No (assigned as needed) |
| AUDITOR | Compliance Auditor | No (assigned as needed) |
| default-roles-claimassist | Composite: CUSTOMER | Yes (default composite role) |

### Permissions (ClaimPermission enum)

| Permission | Description | Granted To |
|-----------|-------------|------------|
| VIEW | View claim details | POLICYHOLDER, ADJUSTER, AUDITOR |
| SUBMIT_DOCUMENTS | Submit documents to claim | POLICYHOLDER, ADJUSTER |
| UPDATE_STATUS | Update claim status | ADJUSTER only |
| VIEW_PARTIES | View claim parties | POLICYHOLDER, ADJUSTER, AUDITOR |
| MANAGE_PARTIES | Manage claim parties | ADJUSTER only |
| VIEW_AUDIT_TRAIL | View full audit trail | AUDITOR only |

### ClaimRole Permissions

| ClaimRole | Permissions |
|-----------|-------------|
| POLICYHOLDER | VIEW, SUBMIT_DOCUMENTS, VIEW_PARTIES |
| ADJUSTER | VIEW, SUBMIT_DOCUMENTS, UPDATE_STATUS, VIEW_PARTIES, MANAGE_PARTIES |
| AUDITOR | VIEW, VIEW_PARTIES, VIEW_AUDIT_TRAIL |

---

## Customer Capabilities

| Capability | API | Status |
|-----------|-----|--------|
| Profile management | GET/POST/DELETE /customers/{id} | IMPLEMENTED |
| Policy creation | POST /policies | IMPLEMENTED |
| Policy listing | GET /policies/all | IMPLEMENTED |
| Policy details | GET /policies/{id} | IMPLEMENTED |
| Policy update | PUT /policies/{id} | IMPLEMENTED |
| Policy delete | DELETE /policies/{id} | IMPLEMENTED |
| Claim filing | POST /claims | IMPLEMENTED |
| Claim listing | GET /claims | IMPLEMENTED |
| Claim details | GET /claims/{id} | IMPLEMENTED |
| Claim status update | PATCH /claims/{id}/status | IMPLEMENTED |
| Document upload | GET /internal/v1/claims/{id}/documents | PARTIALLY (metadata only) |
| Notifications | UNKNOWN | No notification APIs found |
| Profile update | PATCH /customers/{id} | IMPLEMENTED |

---

## Policy Capabilities

| Capability | API | Status |
|-----------|-----|--------|
| Create policy | POST /policies | IMPLEMENTED |
| List policies | GET /policies/all | IMPLEMENTED |
| Get policy by ID | GET /policies/{id} | IMPLEMENTED |
| Update policy | PUT /policies/{id} | IMPLEMENTED |
| Delete policy | DELETE /policies/{id} | IMPLEMENTED |
| Policy coverage (internal) | GET /internal/v1/policies/{id}/coverage | IMPLEMENTED (internal) |

---

## Claim Lifecycle

The claim lifecycle supported by the backend:

1. **File Claim** → POST /claims (SubmitClaimCommand)
   - Requires: policyId, incidentType, incidentDate, estimatedAmountCents (optional)
   - Creates claim with initial status (likely "SUBMITTED" or "OPEN")

2. **Select Policy** → Associated with existing policy via policyId

3. **Claim Creation** → POST /claims creates the claim record

4. **Incident Details** → Captured in ClaimRequest.incidentType and incidentDate

5. **Document Upload** → GET /internal/v1/claims/{id}/documents (metadata); actual upload likely via frontend + MinIO

6. **AI Analysis** → GET /agent/claims/{claimId} (conversation history), POST /agent/stream (streaming AI analysis)

7. **Assessment** → Agent/AI conversation provides assessment; processingStatus on ClaimDocument tracks progress

8. **Surveyor** → UNKNOWN - no surveyor assignment endpoints found

9. **Decision** → Status update via PATCH /claims/{id}/status with UpdateClaimStatusRequest
   - Statuses likely: SUBMITTED, IN_REVIEW, APPROVED, REJECTED, CLOSED

10. **Settlement/Payment** → UNKNOWN - no payment endpoints found

11. **Claim Tracking** → GET /claims (listing), GET /claims/{id} (details)

### Claim Statuses

Based on the code, claim statuses are strings. The actual status values are not explicitly enumerated in the DTOs but are represented as `String status`. Common insurance claim statuses likely include:
- SUBMITTED
- IN_REVIEW
- IN_PROGRESS
- APPROVED
- REJECTED
- CLOSED
- PENDING

---

## Document Functionality

| Capability | Implementation |
|-----------|---------------|
| Upload | ClaimDocument entity stores minioObjectKey and path ("<claimId>/<path>"); actual upload not in controllers - likely multipart/form-data upload to MinIO |
| Download | No download endpoints in controllers; likely MinIO browser access or pre-signed URLs |
| View/Preview | No view/preview endpoints; ocrStatus indicates async processing (PENDING → COMPLETED) |
| Metadata | ClaimDocumentSummaryDto: id, docType, ocrStatus, extractedText, fraudSignalScore |
| Claim Association | @ManyToOne to Claim, mapped by claim_id |
| Policy Association | Not explicitly documented |
| Validation | docType enum: PHOTO, POLICE_REPORT, MEDICAL_BILL, REPAIR_ESTIMATE |
| File Types | Not explicitly documented in controllers |
| File Size | Not documented in controllers |
| Document Status | ocrStatus: PENDING/COMPLETED; processingStatus: PENDING/COMPLETED/FAILED; processingError; fraudSignalScore; fraudFlag |

---

## AI Functionality

| Capability | Implementation |
|-----------|---------------|
| AI endpoints | POST /agent/stream (SSE), GET /agent/claims/{claimId} |
| Request format | AgentRequest { message, claimId } |
| Response format | SSE: Flux<ServerSentEvent<StreamResponse>> |
| Stream events | message (token chunks), done (terminal), error (with errorCode) |
| Claim analysis | Agent can analyze claim-related questions |
| Document analysis | Not explicitly documented in controllers |
| Assessment | Agent provides assessment via conversation |
| Risk/fraud | fraudSignalScore (Double) and fraudFlag (boolean) on ClaimDocument |
| Recommendations | Agent can provide recommendations via conversation |
| Processing status | processingStatus on ClaimDocument: PENDING/COMPLETED/FAILED |
| Asynchronous processing | Kafka outbox pattern, document-processing worker pool (K8s) |
| Streaming | YES - /agent/stream uses Server-Sent Events |
| Errors | StreamResponse.error() with errorCode and message |

---

## Employee/Operations Capabilities

| Capability | API | Status |
|-----------|-----|--------|
| Claim queue listing | GET /claims (customer) / claims listing (operations) | IMPLEMENTED |
| Search/filter | UNKNOWN | No dedicated search/filter controllers |
| Pagination | IMPLEMENTED (Pageable on policies) | IMPLEMENTED |
| Claim workspace | UNKNOWN | No workspace endpoints |
| AI assessment | GET /agent/claims/{claimId}, POST /agent/stream | IMPLEMENTED |
| Surveyor assignment | UNKNOWN | No endpoints |
| Approval | PATCH /claims/{id}/status | IMPLEMENTED |
| Rejection | Status change via status field | IMPLEMENTED |
| Claim status changes | PATCH /claims/{id}/status | IMPLEMENTED |
| Claim status changes (operations) | PATCH /claims/{id}/status with @security.canUpdateStatus | IMPLEMENTED |

---

## Admin Functionality

| Capability | API | Status |
|-----------|-----|--------|
| User management | Keycloak admin console | PARTIALLY (no custom APIs) |
| RBAC | Keycloak realm roles + ClaimPermission | IMPLEMENTED |
| Product management | UNKNOWN | No APIs |
| AI | Agent service capabilities | IMPLEMENTED |
| Fraud/Risk | fraudSignalScore, fraudFlag | PARTIALLY |
| Audit | Claim audit trail, VIEW_AUDIT_TRAIL | PARTIALLY |
| Notifications | UNKNOWN | No APIs |
| RBAC/Permissions | Keycloak + database-backed system | IMPLEMENTED |
| Notifications | UNKNOWN | No notification APIs found |

---

## RBAC

| Layer | Mechanism | Details |
|-------|-----------|---------|
| Keycloak Realm | Realm roles: CUSTOMER, ADJUSTER, AUDITOR | Assigned at login, checked via @PreAuthorize hasRole('ROLE_X') |
| Fine-grained | Database-backed ClaimPermission/ClaimRole | Keyed off claim ID and userId, not just global roles |
| @PreAuthorize | Used on claim status update: `@security.canUpdateStatus(#id)` | Custom SpEL expression in claims-service |
| ClaimRole | POLICYHOLDER/ADJUSTER/AUDITOR with specific permission sets | Database-backed, checked via hasPermission() method |

---

## Risk/Fraud

| Capability | Implementation |
|-----------|---------------|
| Fraud signal | fraudSignalScore (Double) on ClaimDocument - score from document processing |
| Fraud flag | fraudFlag (boolean) on ClaimDocument - indicates suspected fraud |
| Risk assessment | Not explicitly documented as separate endpoint; agent may provide risk assessment via conversation |
| Risk/fraud endpoints | UNKNOWN | No dedicated risk/fraud APIs |

---

## Audit

| Capability | Implementation |
|-----------|---------------|
| Audit trail | ClaimStatusDto.history: List<StatusHistoryEntry> with {fromStatus, toStatus, changedBy, changedAt} |
| VIEW_AUDIT_TRAIL permission | Allows viewing full audit history without modify capability |
| Audit endpoints | PATCH /claims/{id}/status (updates status with note) |
| Audit endpoints (operations) | PATCH /internal/v1/claims/{id}/status | IMPLEMENTED |

---

## Notifications

| Capability | Status |
|-----------|--------|
| Notification APIs | UNKNOWN - no notification APIs found in controllers |
| Kafka events | ClaimSagaOrchestrationRequestEvent, ClaimSagaOrchestrationResultEvent etc. in common-lib | Event publishing exists but no HTTP notification endpoints |

---

## Payments

| Capability | Status |
|-----------|--------|
| Stripe integration | Configured in customer-service (stripeClient) | IMPLEMENTED at service level but no payment API endpoints in controllers |
| Payment endpoints | UNKNOWN | No payment collection/payout APIs in controllers |

---

## Frontend Screen Mapping

### PUBLIC Screens

| Screen | Supported | Partially Supported | Not Supported | Unknown |
|--------|-----------|---------------------|---------------|---------|
| Home | YES (likely static) | - | - | - |
| Products | YES (GET /policies, GET /policies/{id}) | - | - | - |
| Claims | YES (GET /claims, POST /claims) | - | - | - |
| About | YES (likely static) | - | - | - |
| Contact | YES (likely static) | - | - | - |
| Login | YES (OAuth2 flow: /auth/authorize, /auth/callback) | - | - | - |
| Register | YES (/auth/signup) | - | - | - |

### CUSTOMER Screens

| Screen | Supported | Partially Supported | Not Supported | Unknown |
|--------|-----------|---------------------|---------------|---------|
| Dashboard | YES (claims + policies listing) | - | - | - |
| Policies | YES (GET /policies/all, GET /policies/{id}) | - | - | - |
| Policy Details | YES (GET /policies/{id}) | - | - | - |
| Claims | YES (GET /claims, GET /claims/{id}) | - | - | - |
| Claim Details | YES (GET /claims/{id}) | - | - | - |
| Documents | PARTIALLY (GET /internal/v1/claims/{id}/documents metadata only) | - | - | - |
| Notifications | UNKNOWN | - | - | - |
| Profile | YES (GET/POST /customers/{id}) | - | - | - |

### FLAGSHIP CLAIM Screens

| Screen | Supported | Partially Supported | Not Supported | Unknown |
|--------|-----------|---------------------|---------------|---------|
| File Claim | YES (POST /claims) | - | - | - |
| Claim Wizard | PARTIALLY (POST /claims with policy/incident data) | - | - | - |
| Document Upload | PARTIALLY (GET document metadata; actual upload not in controllers) | - | - | - |
| AI Analysis | YES (GET /agent/claims/{id}, POST /agent/stream) | - | - | - |
| Review | PARTIALLY (PATCH /claims/{id}/status) | - | - | - |
| Submit | YES (POST /claims) | - | - | - |
| Claim Tracking | YES (GET /claims, GET /claims/{id}) | - | - | - |
| Claim Details | YES (GET /claims/{id}) | - | - | - |

### OPERATIONS Screens

| Screen | Supported | Partially Supported | Not Supported | Unknown |
|--------|-----------|---------------------|---------------|---------|
| Dashboard | UNKNOWN | - | - | - |
| Claims | YES (GET /claims) | - | - | - |
| Claim Queue | PARTIALLY (GET /claims with pagination/filtering) | - | - | - |
| Claim Workspace | UNKNOWN | - | - | - |
| AI Assessment | YES (GET /agent/claims/{id}, POST /agent/stream) | - | - | - |
| Surveyor | UNKNOWN | - | - | - |
| Decision | PARTIALLY (PATCH /claims/{id}/status) | - | - | - |

### ADMIN Screens

| Screen | Supported | Partially Supported | Not Supported | Unknown |
|--------|-----------|---------------------|---------------|---------|
| Dashboard | UNKNOWN | - | - | - |
| Users | PARTIALLY (Keycloak admin only) | - | - | - |
| RBAC | IMPLEMENTED (Keycloak + ClaimPermission) | - | - | - |
| Products | UNKNOWN | - | - | - |
| AI | IMPLEMENTED (Agent service) | - | - | - |
| Fraud/Risk | PARTIALLY (fraudSignalScore, fraudFlag) | - | - | - |
| Audit | PARTIALLY (audit trail via status history) | - | - | - |

---

## Screen → API Mapping

### PUBLIC

| Screen | APIs |
|--------|-------|
| Home | Static (no API) |
| Products | GET /policies/all, GET /policies/{id} |
| Claims | GET /claims, POST /claims, GET /claims/{id} |
| About | Static |
| Contact | Static |
| Login | GET /auth/authorize, GET /auth/callback, POST /auth/refresh, POST /auth/logout |
| Register | POST /auth/signup |

### CUSTOMER

| Screen | APIs |
|--------|-------|
| Dashboard | GET /claims, GET /policies/all |
| Policies | GET /policies/all, GET /policies/{id}, POST /policies, PUT /policies/{id}, DELETE /policies/{id} |
| Policy Details | GET /policies/{id} |
| Claims | GET /claims, GET /claims/{id} |
| Claim Details | GET /claims/{id} |
| Documents | GET /internal/v1/claims/{id}/documents |
| Notifications | - (UNKNOWN) |
| Profile | GET /customers/{id}, PATCH /customers/{id} |

### FLAGSHIP CLAIM

| Screen | APIs |
|--------|-------|
| File Claim | POST /claims (ClaimRequest) |
| Claim Wizard | POST /claims (ClaimRequest with policyId, incidentType, incidentDate, estimatedAmountCents) |
| Document Upload | GET /internal/v1/claims/{id}/documents (metadata) |
| AI Analysis | GET /agent/claims/{id}, POST /agent/stream |
| Review | PATCH /claims/{id}/status (UpdateClaimStatusRequest) |
| Submit | POST /claims |
| Claim Tracking | GET /claims, GET /claims/{id} |
| Claim Details | GET /claims/{id} |

### OPERATIONS

| Screen | APIs |
|--------|-------|
| Dashboard | - (UNKNOWN) |
| Claims | GET /claims |
| Claim Queue | GET /claims (with pagination) |
| Claim Workspace | - (UNKNOWN) |
| AI Assessment | GET /agent/claims/{id}, POST /agent/stream |
| Surveyor | - (UNKNOWN) |
| Decision | PATCH /claims/{id}/status |

### ADMIN

| Screen | APIs |
|--------|-------|
| Dashboard | - (UNKNOWN) |
| Users | - (Keycloak admin console) |
| RBAC | Keycloak realm roles |
| Products | - (UNKNOWN) |
| AI | GET /agent/claims/{id}, POST /agent/stream |
| Fraud/Risk | - (fraudSignalScore on documents) |
| Audit | GET claim status with history |

---

## API → Screen Mapping

| API | Screens |
|-----|---------|
| GET /policies/all | Customer: Dashboard, Policies |
| GET /policies/{id} | Customer: Policy Details, Admin: Audit |
| POST /policies | Customer: Create Policy |
| PUT /policies/{id} | Customer: Update Policy, Admin: Update |
| DELETE /policies/{id} | Customer: Delete Policy |
| GET /claims | Customer: Dashboard, Operations: Claims, Claim Tracking |
| POST /claims | Customer: File Claim, Flagship: File Claim |
| GET /claims/{id} | Customer: Claim Details, Flagship: Claim Details, Operations: Decision |
| PATCH /claims/{id}/status | Customer: Review, Operations: Decision, Admin: Decision |
| GET /auth/authorize | Public: Login |
| GET /auth/callback | Public: Login callback |
| POST /auth/signup | Public: Register |
| POST /auth/refresh | Customer: Token refresh |
| POST /auth/logout | Customer: Logout |
| GET /agent/claims/{id} | Flagship: AI Analysis, Operations: AI Assessment |
| POST /agent/stream | Flagship: AI Analysis, Operations: AI Assessment |
| GET /internal/v1/claims/{id}/documents | Customer: Documents, Flagship: Document Upload |
| GET /policies/{policyId}/coverage | Internal: Policy coverage |
| GET /customers/{id} | Customer: Profile |
| PATCH /customers/{id} | Customer: Profile Update |

---

## Postman Test Inventory

### Authentication
- signup (POST /auth/signup)
- authorize (GET /auth/authorize) - redirects
- callback (GET /auth/callback)
- refresh (POST /auth/refresh)
- logout (POST /auth/logout)
- me (GET /customers/me or similar user info)

### Customer
- getMyPolicies (GET /policies/all)
- createPolicy (POST /policies)
- getPolicyById (GET /policies/{id})
- updatePolicy (PUT /policies/{id})
- deletePolicy (DELETE /policies/{id})
- updateCustomer (PATCH /customers/{id})
- deleteCustomer (DELETE /customers/{id})

### Claims
- getMyClaims (GET /claims)
- getClaimById (GET /claims/{id})
- submitClaim (POST /claims)
- updateClaimStatus (PATCH /claims/{id}/status)
- getClaimStatus (GET /internal/v1/claims/{id}/status)
- getClaimDocuments (GET /internal/v1/claims/{id}/documents)

### Documents
- listClaimDocuments (GET /internal/v1/claims/{id}/documents)

### AI/Agent
- startConversation (GET /agent/claims/{claimId})
- streamResponse (POST /agent/stream)

### Operations
- (Similar to claims but potentially with different filters/pagination)

### Admin
- (Keycloak admin API calls)

---

## Infrastructure/Connectivity

### Existing Ingress/Gateway

- **API Gateway**: Spring Cloud Gateway on port 8080
- **Routes**:
  - `/customer/**` → customer-service (8081)
  - `/auth/**` → customer-service (8081)
  - `/claims/**` → claims-service (8082)
  - `/agent/**` → agent-service (8083)
  - `/policies/**` → customer-service (8081)
  - `/api/v1/...` → versioned routes with StripPrefix

### CORS Configuration

- **Allowed origins** (from SecurityProperties):
  - Default: `http://localhost:3000`, `http://localhost:4200`, `http://localhost:8080`
  - Configurable via `cors.allowed-origins` property or `CORS_ALLOWED_ORIGINS` env var
- **Allowed methods**: GET, POST, PUT, DELETE, OPTIONS, PATCH
- **Allowed headers**: Content-Type, Authorization, X-Requested-With, Correlation-ID, Accept, Origin
- **Exposed headers**: Authorization, Content-Type, Correlation-ID
- **Credentials**: Supported only with explicit origins (not *)

### DEV Access Path

- **Local (Docker compose)**: All services accessible via Docker networking
- **Local-k8s (Tailscale)**: Services at `100.114.133.69:<NodePort>` through Tailscale VPN
- **Keycloak**: `http://100.114.133.69:30080/realms/claimassist-dev`
- **API Gateway**: `http://localhost:8080` (local) or `http://100.114.133.69:30080` (K8s via Tailscale)

### Ports (Local Docker Compose)

| Service | Port | NodePort (K8s) |
|---------|------|----------------|
| API Gateway | 8080 | - |
| Customer Service | 8081 | - |
| Claims Service | 8082 | - |
| Agent Service | 8083 | - |
| Config Service | 8888 | - |
| Discovery Service | 8761 | - |
| PostgreSQL | 5432 | - |
| Redis | 6379 | - |
| Kafka | 9092 | - |
| Keycloak | 8180 | 30080 (Tailscale) |

### Important Notes for Frontend

- **Do not hardcode OCI IPs** - use environment variables or configuration
- **Use environment variables** for: Keycloak URL, service base URLs
- **CORS must allow** the frontend origin (typically `http://localhost:3000` or production domain)
- **All API calls go through API Gateway** (port 8080) - do not call services directly
- **Authentication**: Obtain JWT from Keycloak, send as `Authorization: Bearer <token>` header
- **Public routes** (no auth): `/auth/**, /api/v1/auth/**, /actuator/health/**, /actuator/info, /v3/api-docs/**, /swagger-ui/`
- **All other routes require valid JWT**

---

## Unsupported Functionality

The following backend capabilities have NO corresponding frontend or are not implemented:

1. **Document actual upload** - ClaimDocument entity exists with minioObjectKey/path but no upload controller endpoint found
2. **Document download/preview** - No download/preview endpoints in controllers
3. **Surveyor assignment** - No surveyor assignment APIs found
4. **Payment collection/payout** - Stripe integrated at service level but no payment APIs in controllers
5. **Notifications** - No notification HTTP endpoints; Kafka events exist but no HTTP API
6. **Employee dashboard** - No dedicated employee/operations controllers found
7. **Product management** - No product management APIs
8. **Admin user management** - Only Keycloak admin console; no custom user management APIs
9. **Search/filter endpoints** - No dedicated search or filter controllers found
10. **Claim status enumeration** - Status is String, no enum defined in DTOs

---

## Unknowns Requiring Verification

1. **Actual claim status values** - Status is String in DTOs; actual values not enumerated
2. **Document upload implementation** - How files are uploaded to MinIO (multipart form? SDK?).
3. **Document download/preview** - How users download/view documents
4. **Notification delivery mechanism** - Kafka events exist but no HTTP notification API
5. **Stripe payment integration details** - Configured but no payment APIs found
6. **Claim lifecycle exact statuses** - Full list of possible claim statuses not documented
7. **Operations-specific APIs** - Employee/operations dashboard endpoints
8. **Admin-specific APIs** - Beyond Keycloak console
9. **Exact CORS configuration in production** - Default origins may differ
10. **ClaimPermission exact grant rules** - How permissions are enforced at database level

---

## Confirmation - Backend Not Modified

This phase is discovery only. No backend files were modified. All code read from the local repository remains unchanged.

**Verification**: Git status should show no modifications to existing backend service files.