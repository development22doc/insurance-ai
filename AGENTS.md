# ClaimAssist Frontend — Agent Rules

## 1. Repository

This file is located in the ClaimAssist repository root.

Treat the directory containing this file as:

REPOSITORY_ROOT

Do NOT assume or hardcode an absolute filesystem path.

The project must work on:

* Windows
* macOS
* Linux
* every developer's local machine
* CI/CD environments in the future

Use repository-relative paths wherever possible.

## 2. Frontend Location

Create the frontend as a top-level directory:

REPOSITORY_ROOT/
└── claimassist-frontend/

The frontend must NOT be created inside an existing backend service.

Expected structure:

REPOSITORY_ROOT/
├── existing backend services/
├── existing infrastructure/
├── AGENTS.md
├── FRONTEND_BACKEND_MAP.md
├── BACKEND_DISCOVERY_SUMMARY.md
└── claimassist-frontend/

All frontend source code must remain under:

claimassist-frontend/

## 3. Backend Is READ-ONLY

Existing backend code is the source of truth.

Backend services must NOT be modified during frontend implementation.

Do not:

* modify Java backend code
* modify controllers
* modify DTOs
* modify services
* modify repositories
* modify database schemas
* modify business logic
* rename backend files
* refactor backend code
* change existing authentication behavior

You MAY read backend code extensively to understand the APIs.

## 4. Backend Source of Truth

The frontend must expose only functionality actually supported by the existing ClaimAssist backend.

Never invent:

* API endpoints
* request fields
* response fields
* roles
* permissions
* claim statuses
* business rules
* AI capabilities
* document capabilities

If functionality is unclear, inspect the backend.

If it cannot be established, mark it:

UNKNOWN — REQUIRES VERIFICATION

## 5. API Architecture

Use the existing ClaimAssist API Gateway.

Do not bypass the API Gateway unless the existing architecture explicitly requires it.

The browser must never depend on Kubernetes-internal DNS names.

Do not hardcode developer-specific IP addresses.

Do not hardcode OCI-specific URLs into source code.

Use environment/configuration appropriate for each environment.

## 6. Authentication

Inspect the existing Keycloak/OAuth/OIDC implementation before implementing frontend authentication.

Never assume the token format or authentication flow.

Do not:

* store access tokens in localStorage by default
* store refresh tokens in localStorage
* expose client secrets
* bypass backend authorization
* manufacture roles in frontend code

Use the existing authentication architecture and an industry-standard browser-safe approach.

Backend authorization remains the security boundary.

## 7. Frontend Scope

Implement only functionality supported by the backend.

Target areas:

PUBLIC

* Home
* Products
* Claims
* About
* Contact
* Login
* Register

CUSTOMER

* Dashboard
* Policies
* Policy Details
* Claims
* Claim Details
* Documents
* Notifications
* Profile

CLAIM

* File Claim
* Claim Wizard
* Document Upload
* AI Analysis
* Review
* Submit
* Claim Tracking
* Claim Details

OPERATIONS

* Dashboard
* Claims
* Claim Queue
* Claim Workspace
* AI Assessment
* Surveyor Assignment if supported
* Decision

ADMIN

* Dashboard
* Users
* RBAC
* Products
* AI
* Fraud/Risk
* Audit

Only implement functionality supported by the backend.

## 8. Digit Insurance UX Reference

Use the current Digit Insurance website as a UX-quality reference.

Reference:

https://www.godigit.com/

Study:

* product discovery
* navigation
* CTA hierarchy
* clean layouts
* typography
* cards
* whitespace
* simple forms
* claims discoverability
* policy/document access
* responsive behavior
* mobile UX

Do NOT copy:

* Digit branding
* Digit logo
* proprietary assets
* exact text
* exact UI
* proprietary illustrations

Create an original ClaimAssist design.

## 9. Testing

Every major frontend workflow must remain independently testable through Postman.

Frontend:

Browser
→ Existing API Gateway
→ Existing backend

Postman:

Postman
→ Existing API Gateway
→ Existing backend

Both must use the same API contracts.

Maintain:

ClaimAssist.postman_collection.json

and an appropriate DEV environment file/template.

Never commit credentials.

## 10. DEV Infrastructure

Target environment:

OCI K3s DEV

Namespace:

claimassist-dev

Reuse existing:

* K3s
* Ingress
* API Gateway
* networking

Do NOT introduce:

* OCI Load Balancer
* second API Gateway
* new ingress controller
* forward proxy
* additional NGINX
* additional reverse proxy

unless inspection proves one is technically required.

No domain is required.

No CI/CD is required for the current DEV implementation.

Target additional infrastructure cost:

$0

## 11. Configuration

Never hardcode:

* developer filesystem paths
* developer usernames
* localhost assumptions that are not configurable
* OCI IP addresses
* domains
* credentials
* tokens
* secrets

Use:

* environment variables
* `.env.example`
* Helm values
* Kubernetes ConfigMaps/Secrets where appropriate

Never expose secrets in client-side code.

Remember that Vite/React frontend environment variables are generally public once bundled. Never put secrets in them.

## 12. Hardware

The current development environment may use:

* Windows
* limited RAM
* CPU-only Ollama
* local coding models

Do not make architecture dependent on a specific developer machine.

Keep agent context focused.

## 13. Execution

Work phase-by-phase.

For each phase:

1. Inspect relevant code.
2. Explain intended changes.
3. Implement only that phase.
4. Build.
5. Lint.
6. Typecheck.
7. Test.
8. Fix errors.
9. Report results.
10. STOP.

Do not automatically continue to the next phase.

## 14. Cross-Developer Compatibility

The project must work for another developer after cloning the repository.

Do not require:

* the same filesystem path
* the same Windows username
* the same IDE
* the same machine
* the same local IP
* the same installed global tools beyond documented prerequisites

Document required setup steps.

Prefer:

* relative paths
* environment variables
* portable npm scripts
* Docker
* Helm values
* Kubernetes configuration

## 15. Code Quality

Use:

* TypeScript
* reusable components
* typed API client
* responsive design
* accessibility
* validation
* loading states
* empty states
* error states
* secure authentication
* maintainable architecture

## 16. No Fake Functionality

Do not create permanent mock APIs or fake backend responses.

Temporary UI mocks may only be used when explicitly necessary during isolated UI development.

Remove temporary mocks before integration validation.

## 17. Core Rule

Correctness and portability are more important than speed.

The ClaimAssist frontend must work against the existing backend without modifying backend business logic and without depending on any individual developer's machine-specific filesystem paths or configuration.
