# Current Security Analysis — ClaimAssist AI Platform

**Stack:** Spring Boot 3.5.6, Spring Security 6.x, Spring Cloud Gateway (WebFlux/reactive), Java 21, Maven multi-module.
**Modules:** `common-lib`, `discovery-service`, `config-service`, `api-gateway`, `customer-service`, `claims-service`, `agent-service`.

This is an inventory of every authentication/authorization artifact found in the codebase, organized by category, as requested in Step 1. No code is changed in this step.

---

## 1. Custom JWT Infrastructure (`common-lib`)

All three business services (`customer-service`, `claims-service`, `agent-service`) share one JWT implementation via `common-lib`, auto-wired through `SharedSecurityAutoConfiguration`.

| Class | Package | Responsibility |
|---|---|---|
| `AuthUtil` | `common_lib.security` | Generates HMAC-signed JWTs (`jjwt`), verifies them, and exposes `getCurrentUser()` / `getCurrentUserId()` by reading `JwtUserPrincipal` off the `SecurityContextHolder`. Signing key comes from `jwt.secret-key` (shared symmetric secret across all services — see config below). Token TTL is hardcoded to 100 minutes. |
| `JwtUserPrincipal` | `common_lib.security` | Record: `userId, name, username, role, authorities`. This is the custom principal type placed into `Authentication.getPrincipal()`. |
| `JwtAuthFilter` | `common_lib.security` | `OncePerRequestFilter`. Reads `Authorization: Bearer`, verifies via `AuthUtil`, builds a `UsernamePasswordAuthenticationToken(principal, rawToken, authorities)`. **Important:** it stores the **raw token string as the credentials**, specifically so it can be re-forwarded on outbound Feign calls (see below). Auth failures are routed through `HandlerExceptionResolver` so they produce the app's standard `ApiError` JSON. |
| `InternalApiKeyFilter` | `common_lib.security` | Separate defense-in-depth filter, only active on `/internal/**` paths. Requires a shared-secret header `X-Internal-Api-Key`, constant-time compared. Not JWT-based — this is service-to-service, not user auth, but it's part of the trust boundary and needs an equivalent in the Keycloak world (likely: Keycloak client-credentials/service-account tokens, or keep as-is). |
| `SharedSecurityAutoConfiguration` | `common_lib.security` | `@AutoConfiguration` that wires `AuthUtil`, `JwtAuthFilter`, `InternalApiKeyFilter`, `CorrelationIdFilter` as beans for every consuming service, **and** registers a Feign `RequestInterceptor` that forwards the caller's raw JWT + the internal shared secret + the correlation id on every outbound internal call. This propagation mechanism is central to how identity flows between services and must be preserved (in OIDC form) after migration. |

**Dependency:** `jjwt-api`, `jjwt-impl`, `jjwt-jackson` in both `common-lib/pom.xml` and `api-gateway/pom.xml` (the gateway has its own separate, duplicate JWT-parsing code — see §3).

---

## 2. Per-Service Security Configs (`SecurityFilterChain`)

Each servlet-based service defines its own filter chain but all follow the identical shape: stateless sessions, CSRF disabled, CORS defaulted, then `CorrelationIdFilter → InternalApiKeyFilter → JwtAuthFilter` inserted before `UsernamePasswordAuthenticationFilter`.

| Class | Service | Public routes (`permitAll`) | Notes |
|---|---|---|---|
| `CustomerSecurityConfig` | customer-service | `/auth/signup`, `/auth/login`, `/webhooks/**`, `/actuator/**`, `/v3/api-docs/**`, `/swagger-ui/**` | Also defines the `PasswordEncoder` (`BCryptPasswordEncoder`) and exposes the framework `AuthenticationManager` bean — the **only** service that does, because it's the only one with a login/signup flow. `@EnableMethodSecurity`. |
| `ClaimsSecurityConfig` | claims-service | `/actuator/**` only | Everything else requires authentication. `@EnableMethodSecurity` (used by `@PreAuthorize("@security.canView(...)")` etc., see §6). |
| `AgentSecurityConfig` | agent-service | `/actuator/**` only | Same shape; also has `@EnableWebSecurity` explicitly (redundant with `@EnableMethodSecurity` + auto-config, harmless). |

None of these define a custom `AuthenticationProvider`. `CustomerSecurityConfig`'s `AuthenticationManager` is the **default** `DaoAuthenticationProvider`-backed manager Spring Security builds automatically from the `UserDetailsService` + `PasswordEncoder` beans present in that context (see §4) — there is no explicit custom `AuthenticationProvider` class anywhere in the codebase.

---

## 3. API Gateway (Spring Cloud Gateway, **reactive**/WebFlux)

The gateway is architecturally different from the three business services — it's reactive, not servlet-based, and does **not** reuse `common-lib`'s `JwtAuthFilter` (that class is a servlet `OncePerRequestFilter`, incompatible with WebFlux). Instead it has its own parallel JWT implementation:

| Class | Responsibility |
|---|---|
| `GatewayJwtAuthFilter` | `GlobalFilter, Ordered` (order `-1`, i.e. runs first). Checks `SecurityProperties.publicRoutes()` (Ant-pattern allow-list from `app.security.public-routes` config) and, for everything else, requires and validates a Bearer token via `JwtGatewayService`. Explicitly documented as **defense-in-depth, not the sole boundary** — every downstream service independently re-verifies the same JWT. |
| `JwtGatewayService` | Its own `jjwt`-based HMAC verification against `jwt.secret-key` (same shared secret as every other service). Also has `extractUserIdOrNull()`, used only for Redis-backed rate-limiter keying (`RateLimiterConfig`). |
| `SecurityProperties` | `@ConfigurationProperties(prefix = "app.security")` — the externalized public-route allow-list, sourced from Spring Cloud Config (`api-gateway.yml`). |

There is **no Spring Security filter chain / `SecurityWebFilterChain`** at the gateway at all — auth is handled entirely by this hand-rolled `GlobalFilter`, not `spring-security`. This has architectural implications for Step 3/6: migrating the gateway to OAuth2 Resource Server means introducing `spring-boot-starter-oauth2-resource-server` + a **reactive** `ReactiveJwtDecoder`/`SecurityWebFilterChain`, which is a different code path than the servlet-side resource-server config used in the three business services.

---

## 4. Login/Signup & UserDetailsService (customer-service only)

Only `customer-service` issues credentials — it's the platform's identity source. Nothing else has a login/signup endpoint.

| Class | Responsibility |
|---|---|
| `AuthController` (`/auth/signup`, `/auth/login`) | Signup: checks username uniqueness, BCrypt-encodes the password, saves `Customer`, mints a JWT via `AuthUtil.generateAccessToken`. Login: delegates credential checking to `AuthenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password))`, then mints a JWT from the resulting principal. |
| `CustomerUserDetailsService implements UserDetailsService` | `loadUserByUsername` → `CustomerRepository.findByUsername(...)`, throws `UsernameNotFoundException` if absent. |
| `Customer` entity | Implements `UserDetails` directly (the entity *is* the principal). `getAuthorities()` currently returns `List.of()` — **there are no granted authorities/roles on the Spring Security principal today**; all real authorization is claim-scoped RBAC done separately (§6), not role-based Spring Security authorities. |
| `LoginRequest`, `SignupRequest`, `AuthResponse` (DTOs) | Request/response contracts for `/auth/**`. `AuthResponse(token, customerId, fullName)` is the **API contract** every client (including any frontend) currently depends on — Step 3 needs to decide whether Keycloak's token response replaces this shape or whether customer-service keeps issuing a compatibility wrapper. |

`PasswordEncoder` (`BCryptPasswordEncoder`) is defined once, in `CustomerSecurityConfig`, and is the only password-hashing logic in the codebase.

---

## 5. SecurityContext / Custom Principal Usage

`JwtUserPrincipal` (via `AuthUtil.getCurrentUser()` / `getCurrentUserId()`) is the **only** way business code reads "who is the current user" — there is no use of Spring Security's `@AuthenticationPrincipal` or `Authentication` directly in controllers/services today. This means the migration's blast radius for "replace the principal type" touches every call site listed below.

Call sites of `AuthUtil.getCurrentUser()` / `getCurrentUserId()`:
- `claims-service`: `ClaimQueryServiceImpl`, `ClaimCommandServiceImpl`, `ClaimController`, `InternalClaimsController`, `SecurityExpressions` (claims), `ClaimUpdateConsumer` (Kafka listener — see note below)
- `agent-service`: `AgentGenerationServiceImpl`, `AgentQueryServiceImpl`
- `customer-service`: `AuthController` (to *build* the principal after login/signup, not read it)

**Special case — `ClaimUpdateConsumer`:** a Kafka listener thread has no HTTP request and therefore no populated `SecurityContextHolder`. It doesn't call `getCurrentUser()` — the acting user id is instead carried explicitly in the event payload (`ClaimUpdateRequestEvent.proposedByUserId`) and passed to `SecurityExpressions.hasPermissionForUser(claimId, userId, permission)`. This pattern is independent of the auth mechanism and should be **preserved as-is** in the Keycloak migration (there's no token to validate on a Kafka thread either way).

---

## 6. Authorization Logic / Role Mapping (claim-level RBAC)

This is the platform's real authorization model, and it is **not** based on Spring Security roles/authorities at all — it's a separate, per-claim relational RBAC system, evaluated via `@PreAuthorize` SpEL against a `@Component("security")` bean in each service:

| Class | Service | Responsibility |
|---|---|---|
| `SecurityExpressions` | claims-service | Source of truth. `hasPermission(claimId, permission)` reads the caller's id from `AuthUtil.getCurrentUserId()`, looks up their `ClaimRole` for that specific claim via `ClaimPartyRepository`, and checks `ClaimRole.hasPermission(permission)`. Also exposes `hasPermissionForUser(claimId, userId, permission)` for the Kafka-consumer case above. |
| `SecurityExpressions` | agent-service | Thin proxy — has no claim-party data locally, delegates to claims-service over the network via `ClaimsServiceGateway` (fails closed on error). |
| `ClaimRole` (enum, common-lib) | — | `POLICYHOLDER`, `ADJUSTER`, `AUDITOR`, each mapped to a fixed `EnumSet<ClaimPermission>`. |
| `ClaimPermission` (enum, common-lib) | — | `VIEW`, `SUBMIT_DOCUMENTS`, `UPDATE_STATUS`, `VIEW_PARTIES`, `MANAGE_PARTIES`, `VIEW_AUDIT_TRAIL`. |

**This is important for Step 3:** `ClaimRole` is a per-claim assignment stored in a database table (`ClaimPartyRepository`), not a global user role — so it is **not** a candidate for a Keycloak realm role / `JwtAuthenticationConverter` role mapping. Only *global* identity (who the user is) should move to Keycloak; this claim-scoped table-driven RBAC stays exactly as it is and continues to be keyed off the authenticated user's id, whatever mechanism produces that id.

---

## 7. Common Security Utilities

- `AuthUtil` (common-lib) — described in §1; doubles as both the JWT codec and the "get current user" utility. Under Keycloak, its "get current user" half is what needs a like-for-like replacement (reading `Jwt`/`AuthenticationPrincipal` claims instead of a custom principal); its "generate/verify JWT" half becomes unnecessary once Keycloak is the token issuer.
- `SharedSecurityAutoConfiguration` (common-lib) — the wiring point for all of the above; this is the single place that needs to change to swap `JwtAuthFilter` for an OAuth2 Resource Server configuration across all three servlet services at once.
- `CorrelationIdFilter` (common-lib, `observability` package) — unrelated to auth, just request tracing; unaffected by this migration, kept for context since it shares filter ordering with the security filters.
- `InternalApiKeyFilter` (common-lib) — service-to-service trust boundary, orthogonal to user-facing auth; out of scope for Keycloak user-token migration but worth flagging as a candidate for Keycloak client-credentials in a later hardening pass.

---

## 8. Configuration (shared secret, distributed via Spring Cloud Config)

`local-config-repo/*.yml` (served by `config-service` to every client service):

| File | Keys present |
|---|---|
| `customer-service.yml` | `jwt.secret-key`, `internal.api.secret` |
| `claims-service.yml` | `jwt.secret-key`, `internal.api.secret` |
| `agent-service.yml` | `jwt.secret-key`, `internal.api.secret` |
| `api-gateway.yml` | `jwt.secret-key` (no internal secret — gateway doesn't call `/internal/**`), plus `app.security.public-routes` (the Ant-pattern allow-list `GatewayJwtAuthFilter` reads) |

All four services currently share **the same symmetric `jwt.secret-key` value** — this is what allows any service to independently verify tokens minted by customer-service. Under Keycloak, this shared secret disappears entirely; every service instead trusts Keycloak's `issuer-uri` and validates signatures against Keycloak's published JWKS, asymmetrically, with no shared secret to distribute or rotate.

---

## 9. Summary Inventory (flat list, for cross-reference with the migration plan)

- **SecurityConfig classes:** `CustomerSecurityConfig`, `ClaimsSecurityConfig`, `AgentSecurityConfig` (no equivalent exists yet at the gateway — it uses a plain `GlobalFilter` instead of Spring Security).
- **JWT classes:** `AuthUtil`, `JwtUserPrincipal`, `JwtAuthFilter` (common-lib); `JwtGatewayService` (api-gateway, duplicate/parallel implementation).
- **Gateway filters:** `GatewayJwtAuthFilter` (auth), `InternalApiKeyFilter` (not gateway-level — only in business services), `CorrelationIdFilter` (non-auth).
- **AuthenticationManager usage:** `CustomerSecurityConfig` (bean definition), `AuthController` (usage in `/auth/login`).
- **PasswordEncoder usage:** `CustomerSecurityConfig` (bean), `AuthController` (encode on signup).
- **UserDetailsService implementations:** `CustomerUserDetailsService`.
- **AuthenticationProvider implementations:** none explicit — relies on Spring Security's default `DaoAuthenticationProvider` auto-configured from the `UserDetailsService` + `PasswordEncoder` beans.
- **Auth controllers / login-signup endpoints:** `AuthController` — `/auth/signup`, `/auth/login` (customer-service only).
- **JWT generation code:** `AuthUtil.generateAccessToken`.
- **JWT validation code:** `AuthUtil.verifyAccessToken`, `JwtGatewayService.validateToken`.
- **SecurityContext usage:** `AuthUtil.getCurrentUser()/getCurrentUserId()` (read), `JwtAuthFilter` (write).
- **Custom filters:** `JwtAuthFilter`, `InternalApiKeyFilter`, `CorrelationIdFilter` (common-lib, applied per-service); `GatewayJwtAuthFilter` (gateway, `GlobalFilter`).
- **Authorization logic / role mapping:** `SecurityExpressions` (claims-service, agent-service), `ClaimRole`, `ClaimPermission` — claim-scoped, DB-backed, independent of the authentication mechanism.
- **Common security utilities:** `AuthUtil`, `SharedSecurityAutoConfiguration`.

---

## 10. Key Constraints for the Migration Plan (Step 2)

1. **Two different runtime models to migrate**, not one: three servlet-based resource servers (customer/claims/agent) and one reactive gateway (WebFlux) with no Spring Security at all today.
2. **Identity propagation contract**: the Feign `RequestInterceptor` forwarding the raw bearer token between services must keep working unchanged — Keycloak-issued tokens forwarded the same way satisfy this for free, but it's worth calling out explicitly since it's easy to break.
3. **`/auth/signup` and `/auth/login` are a real product surface**, not just plumbing — `AuthResponse`'s shape is an API contract. Moving credential issuance to Keycloak (Authorization Code / Direct Access Grants) is a user-facing/API-contract decision, not just an internals swap, and needs an explicit choice in Step 2 (proxy Keycloak's token endpoint behind the existing `/auth/**` paths vs. redirecting clients to Keycloak directly vs. keeping customer-service as the user-registration system of record that also provisions the corresponding Keycloak user).
4. **Claim-level RBAC (`ClaimRole`/`ClaimPermission`) is out of scope for Keycloak role mapping** — it's per-resource, DB-backed data, not a global user role, and must be preserved unchanged, keyed off whatever field carries the user's stable id in the new token (`sub` claim vs. today's `userId` claim — needs an explicit mapping decision).
5. **`InternalApiKeyFilter` and the shared internal secret are a separate trust boundary** from user JWTs and are not required to change for this migration to succeed, though they're a reasonable follow-up (Keycloak client-credentials grants) once user-auth migration is stable.
6. **The gateway needs new code, not modified code** — there's no existing Spring Security filter chain there to convert; Step 3/6 will introduce one from scratch (reactive OAuth2 Resource Server), since `common-lib`'s servlet-based `JwtAuthFilter` cannot be reused on WebFlux.
