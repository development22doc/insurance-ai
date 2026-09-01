# ClaimAssist Backend Discovery Summary

## Repository Structure
- **Root**: `C:\claimassist\insurance-ai`
- **Multi-module Maven project** with 7 modules:
  - `common-lib` - Shared foundation (auth, DTOs, exceptions, events, Kafka)
  - `discovery-service` - Eureka service registry (port 8761)
  - `config-service` - Spring Cloud Config Server (port 8888)
  - `api-gateway` - Spring Cloud Gateway, edge entrypoint (port 8080)
  - `customer-service` - Customer identity, policies, billing (port 8081)
  - `claims-service` - Claims, documents, saga orchestration (port 8082)
  - `agent-service` - AI claims assistant with Ollama LLM (port 8083)

## Services Inventory

| Service | Port | Key Tech |
|---------|------|----------|
| discovery-service | 8761 | Eureka Server |
| config-service | 8888 | Spring Cloud Config (git-backed) |
| api-gateway | 8080 | Spring Cloud Gateway (WebFlux) |
| customer-service | 8081 | Spring Boot, PostgreSQL, Redis, Kafka, Stripe |
| claims-service | 8082 | Spring Boot, PostgreSQL, Redis, Kafka, Flyway V1-V11 |
| agent-service | 8083 | Spring AI, Ollama (qwen2.5-coder:3b), Flyway V1-V6 |
| common-lib | N/A | Shared auth, DTOs, exceptions, Kafka, observability |

## API Count (Frontend-Relevant)

| Category | Count |
|----------|-------|
| Authentication endpoints | 6 (signup, authorize, callback, refresh, logout + public routes) |
| Customer endpoints | 14 (CRUD policies, customers, auth) |
| Claims endpoints | 8 (getMyClaims, getClaimById, submitClaim, updateStatus + internal) |
| Document endpoints | 2 (internal document listing) |
| AI/Agent endpoints | 2 (stream chat, conversation history) |
| **Total** | **32** |

## Authentication Mechanism
- **OAuth2/OIDC with Keycloak** - identity provider at `http://localhost:8180/realms/claimassist` (local) or `100.114.133.69:30080/realms/claimassist-dev` (K8s)
- **Client**: `claimassist-customer-app` (public, PKCE) for browser flow
- **Internal**: `claimassist-admin-service` (client-credentials) for service-to-service
- **Tokens**: accessToken, refreshToken, tokenType="Bearer", expiresIn=3600, refreshExpiresIn=86400
- **Public routes** (no auth): `/auth/**, /api/v1/auth/**, /actuator/health/**, /actuator/info, /v3/api-docs/**, /swagger-ui/**

## Roles & Permissions

| Realm Role | Description |
|-----------|-------------|
| CUSTOMER | Default role for all authenticated users |
| ADJUSTER | Claims Adjuster |
| AUDITOR | Compliance Auditor |

| ClaimPermission | Description |
|----------------|-------------|
| VIEW | View claim details |
| SUBMIT_DOCUMENTS | Submit documents |
| UPDATE_STATUS | Update claim status |
| VIEW_PARTIES | View claim parties |
| MANAGE_PARTIES | Manage claim parties |
| VIEW_AUDIT_TRAIL | View audit trail (Auditor only) |

## Customer Functionality
- Profile management (GET/POST/DELETE /customers/{id})
- Policy CRUD (POST /policies, GET /policies/all, GET/PUT/DELETE /policies/{id})
- Claim filing (POST /claims)
- Claim listing (GET /claims) and details (GET /claims/{id})
- Status update (PATCH /claims/{id}/status)

## Policy Functionality
- Create, list, view, update, delete policies
- Policy coverage (internal: GET /internal/v1/policies/{id}/coverage)

## Claim Lifecycle (Supported Stages)
1. **File Claim** → POST /claims
2. **Claim Details** → GET /claims/{id}
3. **Document Upload** → metadata via GET /internal/v1/claims/{id}/documents
4. **AI Analysis** → GET /agent/claims/{id}, POST /agent/stream (SSE)
5. **Assessment/Review** → PATCH /claims/{id}/status
6. **Decision/Settlement** → Status change via PATCH
7. **Claim Tracking** → GET /claims, GET /claims/{id}

## Document Functionality
- **Metadata**: ClaimDocumentSummaryDto (id, docType, ocrStatus, extractedText, fraudSignalScore)
- **Association**: @ManyToOne to Claim (by claim_id)
- **Status**: ocrStatus (PENDING/COMPLETED), processingStatus (PENDING/COMPLETED/FAILED)
- **Upload**: Not in controllers; entity has minioObjectKey/path - likely MinIO SDK upload
- **Download/Preview**: No endpoints found in controllers

## AI Functionality
- **Endpoints**: POST /agent/stream (SSE), GET /agent/claims/{claimId}
- **Format**: AgentRequest {message, claimId} → Flux<ServerSentEvent<StreamResponse>>
- **Capabilities**: Claim analysis, risk/fraud (fraudSignalScore, fraudFlag), assessment, recommendations
- **Streaming**: YES - Server-Sent Events with message/done/error events

## Employee/Operations
- Claim listing and status update supported
- AI assessment via agent service
- No dedicated employee dashboard, surveyor assignment, or workspace endpoints

## Admin Functionality
- RBAC via Keycloak realm roles + database-backed ClaimPermission/ClaimRole
- Fraud/risk: fraudSignalScore, fraudFlag
- Audit: ClaimStatusDto.history (audit trail)
- No product management, user management beyond Keycloak, or notification APIs

## Frontend Screen Mapping Summary

| Area | Screens | Status |
|------|---------|--------|
| **PUBLIC** | Home, Products, Claims, About, Contact, Login, Register | All supported |
| **CUSTOMER** | Dashboard, Policies, Policy Details, Claims, Claim Details, Documents, Notifications, Profile | 8/9 supported (Notifications UNKNOWN) |
| **FLAGSHIP CLAIM** | File Claim, Claim Wizard, Document Upload, AI Analysis, Review, Submit, Claim Tracking, Claim Details | 8/8 supported (Document Upload partially - metadata only) |
| **OPERATIONS** | Dashboard, Claims, Claim Queue, Claim Workspace, AI Assessment, Surveyor, Decision | 4/7 supported (Surveyor, Workspace, Dashboard UNKNOWN) |
| **ADMIN** | Dashboard, Users, RBAC, Products, AI, Fraud/Risk, Audit | 3/7 supported (Users, Products UNKNOWN) |

## Infrastructure
- **API Gateway**: Spring Cloud Gateway on port 8080, routes to services via `lb://SERVICE-NAME`
- **CORS**: Configurable origins; defaults allow `localhost:3000, :4200, :8080`
- **Keycloak**: Tailscale-accessed at `100.114.133.69:30080` in local-k8s profile
- **No hardcoded IPs** - configuration via environment variables or config-repo
- **All services** connect through API Gateway; direct service calls use `lb://` syntax

## Postman Coverage Required
Group by: Authentication, Customer, Policies, Claims, Documents, AI, Operations, Assessment, Surveyor, Decision, Admin, RBAC, Risk/Fraud, Audit, Notifications

## Key Unknowns
1. Claim status exact values (String, not enum)
2. Document upload implementation (MinIO SDK/multipart)
3. Document download/preview endpoints
4. Notification HTTP API (Kafka events exist but no HTTP)
5. Stripe payment API endpoints
6. Operations-specific dashboard/workspace endpoints
7. Exact CORS production configuration
8. Admin user management APIs beyond Keycloak

## Confirmation
- No backend files modified during this discovery phase
- All code read from local repository, untouched
- Frontend will be built against existing real APIs