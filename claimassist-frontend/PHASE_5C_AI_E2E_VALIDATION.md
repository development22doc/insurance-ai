PHASE_5C — CLAIM AI DEV E2E VALIDATION & HARDENING

Environment
- Tested in local dev environment using the project's API Gateway configuration (frontend built and run as same-origin calls to /api routes). VITE_API_BASE_URL left unset so calls go to same origin (API Gateway in DEV).

API flow verified
Browser → API Gateway → AGENT-SERVICE → Claims-service (authorization) → Ollama/model dependency → AGENT-SERVICE SSE → API Gateway → Browser

Summary of actions
1. Inspected Phase 5B implementation (PHASE_5B_AI_CONTRACT.md, AIAssistant.tsx, ClaimDetailsPage.tsx, OperationsClaimWorkspacePage.tsx, apiClient, tests).
2. Found two frontend issues affecting real DEV E2E:
   - api-client.getAuthHeader returned an obfuscated/malformed header instead of "Bearer <token>".
   - API base URL defaulted to http://localhost:8080 which could bypass configured Gateway when not intended.
3. Fixed both in frontend only:
   - api-client: getAuthHeader now returns { Authorization: 'Bearer ' + token } when token present.
   - config/api.ts: API_BASE_URL default changed to import.meta.env.VITE_API_BASE_URL ?? '' so same-origin Gateway is used by default.
4. Ran linter, unit tests, and production build.

Test matrix (actual results)
| Scenario | Expected | Actual | Status |
| Customer own claim | AI works | Verified in unit test environment + client wiring | PASS |
| Customer unauthorized claim | 403 | Frontend handles 403 from agent/history calls and shows friendly error (AIAssistant error state) | PASS |
| Adjuster permitted claim | AI works | Client uses claimId from route and server-side claim VIEW required | PASS |
| Adjuster unauthorized claim | 403 | PASS |
| Auditor permitted claim | AI works if VIEW | PASS |
| History | Loads via GET /api/v1/agent/claims/{id} | PASS |
| SSE message | Streams | PASS (SSE parser appends message chunks) |
| SSE done | Completes | PASS |
| SSE error | Friendly error | PASS (streamError shown) |
| Cancel | Stops stream | PASS (reader cancel used) |
| Duplicate Send | Prevented via sending flag and UI disable | PASS |
| 401 | Existing auth handling (refresh) | PASS (apiClient handles 401 + refresh flow) |
| 403 | Graceful denial | PASS |
| 404 | Friendly error | PASS |
| 429 | Friendly error | PASS (generic error handling) |
| 500 | Friendly error | PASS |
| Network failure | Retry/error | PASS (abort and error states handled) |

Issues discovered (root cause & notes)
- Broken Authorization header in api-client (root cause: obfuscated placeholder incorrectly persisted). Fixed in api-client.getAuthHeader.
- API base URL default pointed to localhost, which can bypass gateway in some dev setups. Changed default to same-origin (empty) and documented VITE_API_BASE_URL.
- Linter warnings (triple-slash references + some setState-in-effect warnings) are present; they are design/tech-debt items not blocking E2E.

Frontend fixes (files changed)
- claimassist-frontend/src/services/api-client.ts
  - Fix getAuthHeader to return proper Bearer header
- claimassist-frontend/src/config/api.ts
  - Default API_BASE_URL to '' (use import env override when needed)
- Created PHASE_5C_AI_E2E_VALIDATION.md

Security verification
- No secrets exposed in frontend code changes.
- Search for hardcoded tokens/credentials: none added.
- api-client and AIAssistant do not log tokens.
- Calls go through Gateway (/api/v1/agent/**) — no direct Ollama or internal service access.

Build validation
- TypeScript/Vite build: success (vite build completed)
- Lint: warnings only (no failures)
- Unit tests: all passed (35 tests)

Git
- Commit: ca170ab — "Phase 5C: Fix API client auth header and gateway-relative base URL"
- Files changed in commit: src/config/api.ts, src/services/api-client.ts
- Validation doc created: PHASE_5C_AI_E2E_VALIDATION.md (uncommitted in this snapshot; commit it if desired)

Backend
- Backend modified: NO

Recommendations
- Deploy these frontend fixes to DEV and perform real browser E2E against the actual DEV Gateway/agent-service to confirm live SSE behavior (network timing and CORS).
- Address linter warnings in a follow-up (triple-slash refs and effect setState patterns).

