PHASE 5B — CLAIM AI ASSISTANT CONTRACT

VERIFIED FROM BACKEND

Gateway endpoints (gateway maps /api/v1/agent/** -> AGENT-SERVICE):

1) POST /api/v1/agent/stream
   - Purpose: Start an SSE AI assistant stream for a claim-scoped question
   - Content-Type: application/json
   - Accept: text/event-stream
   - Request body: { "message": string, "claimId": number }
   - Authentication: Bearer JWT (Keycloak) propagated by API Gateway
   - Authorization: server-side check -> security.canAccessClaim(#claimId) -> claimsServiceGateway.checkPermission(..., ClaimPermission.VIEW)
   - Response: text/event-stream (SSE) with data: JSON payload per StreamResponse

   SSE event payload (StreamResponse JSON expected inside "data:" lines):
   {
     "text": string,            // token or chunk text when eventType == "message"
     "eventType": "message"|"done"|"error",
     "requestId": string,      // correlator
     "done": boolean,          // optional
     "errorCode": string|null  // when eventType == "error"
   }
   - Event semantics: "message" events may contain partial assistant text that must be appended; "done" ends the stream; "error" reports server-side errors.

2) GET /api/v1/agent/claims/{claimId}
   - Purpose: Retrieve AI conversation history for a claim
   - Method: GET
   - Response: AgentMessageResponse[] (list of messages with fields such as id, role ('USER'|'ASSISTANT'), content, createdAt, tokensUsed, events)
   - Authentication: Bearer JWT
   - Authorization: same claim-level VIEW permission

AUTHORIZATION / ROLE MATRIX (VERIFIED)
- Access to both streaming and history: SUPPORTED if and only if authenticated user has ClaimPermission.VIEW for target claim (server-enforced).
- CUSTOMER (own claim): SUPPORTED when claims-service grants VIEW for that claim.
- ADJUSTER: SUPPORTED for permitted claims (per claim-party permissions).
- AUDITOR: SUPPORTED only if claims-service assigns VIEW.
- ADMIN / SUPPORT: No blanket bypass in agent-service. Access requires claim-level VIEW. (ADMIN role exists in realm? See Phase 5A report.)
- Unauthorized user: NOT SUPPORTED (403 expected).

AI TOOLS & PERMISSION BEHAVIOR
- Tools are registered server-side (ToolRegistry). Some tools require higher claim permissions (e.g., PROPOSE_CLAIM_UPDATE -> ClaimPermission.UPDATE_STATUS).
- Tool invocation/permission checks are enforced server-side via ToolExecutionGuard. Frontend must not attempt to pass tool permissions.
- Frontend must only send (message, claimId). The server decides which tools may be invoked.

GATEWAY / CORS / STREAMING
- Gateway routes /api/v1/agent/** to AGENT-SERVICE. CORS configured in gateway and supports SSE Accept header. Frontend must call Gateway paths (no direct service IPs).

ERRORS
- Common HTTP errors: 400, 401, 403, 404, 429, 500. SSE may emit error events with eventType="error".

FRONTEND INTEGRATION POINTS
- Integrate assistant into Claim Details and Operations Claim Workspace. Use claimId from route/context.
- Use existing apiClient.stream() which posts JSON and returns ReadableStream for SSE parsing.

UNSUPPORTED (NOT PROVIDED BY BACKEND)
- No browser APIs for AI model configuration, prompt management, provider/Ollama configuration, model switching, thresholds, or system-wide AI admin.
- No gateway admin routes for AI configuration.

VALIDATION
- Verified by inspecting agent-service controller, DTOs, ToolRegistry, security expressions, and API Gateway config in repository.

NOTES
- Server enforces claim-scoped authorization; frontend should be UX-gated only. Handle 403 gracefully.
- Do not expose any service credentials, internal endpoints, or attempt to call internal /internal/** services directly.

