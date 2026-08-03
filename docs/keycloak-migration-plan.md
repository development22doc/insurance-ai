# Keycloak Migration Plan

This is the file-by-file plan for the migration described in
`docs/current-security-analysis.md`, and reflects what was actually executed.
Each entry: **Package / Class / Current responsibility / Action / Reason.**

Key architectural decisions this plan makes (expanded on in
`README-KEYCLOAK.md`):
1. The `userId` custom claim (sourced from a `legacy_user_id` Keycloak user
   attribute) replaces the platform's dependency on a custom principal —
   Keycloak's own `sub` (a UUID) is never used as the business user id.
2. `/auth/signup` and `/auth/login` keep their existing request/response
   contracts; `customer-service` becomes a thin proxy in front of Keycloak
   rather than a local credential store.
3. The claim-level RBAC system (`ClaimRole`/`ClaimPermission`/
   `SecurityExpressions`) is explicitly untouched — it is per-resource,
   database-backed authorization, orthogonal to *authentication*, which is
   all this migration changes.

---

## common-lib

| Package | Class | Current responsibility | Action | Reason |
|---|---|---|---|---|
| `common_lib.security` | `AuthUtil` | Generated/verified HMAC JWTs; exposed `getCurrentUser()`/`getCurrentUserId()` | **DELETE** | Both responsibilities become Keycloak's/Spring Security's job; replaced by `CurrentUserProvider` |
| `common_lib.security` | `JwtUserPrincipal` | Custom principal record placed on the `Authentication` | **DELETE** | Superseded by Spring Security's own `Jwt` principal (`JwtAuthenticationToken`) |
| `common_lib.security` | `JwtAuthFilter` | Serlvet filter: parse+verify custom JWT, build `Authentication` | **DELETE** | Replaced wholesale by Spring Security's OAuth2 Resource Server filter (`BearerTokenAuthenticationFilter`), wired via `.oauth2ResourceServer(...)` in each service's `SecurityFilterChain` |
| `common_lib.security` | `CurrentUserProvider` | Reads `userId`/username/name off the Keycloak `Jwt` on the `SecurityContext` | **CREATE** | Direct, call-site-compatible (`getCurrentUserId()` signature unchanged) replacement for `AuthUtil`'s user-lookup half |
| `common_lib.security` | `KeycloakJwtAuthenticationConverter` | Maps `realm_access`/`resource_access` roles to `ROLE_*` `GrantedAuthority`s | **CREATE** | Needed by `.oauth2ResourceServer(jwt -> jwt.jwtAuthenticationConverter(...))` in every servlet service |
| `common_lib.security` | `SharedSecurityAutoConfiguration` | Auto-config wiring `AuthUtil`, `JwtAuthFilter`, `InternalApiKeyFilter`, `CorrelationIdFilter`, and the Feign `RequestInterceptor` | **MODIFY** | Removed the two obsolete beans, added `currentUserProvider()`/`keycloakJwtAuthenticationConverter()` beans, and fixed the Feign interceptor to read the outbound bearer token from `Jwt.getTokenValue()` (previously read it from `Authentication.getCredentials()`, which the old `JwtAuthFilter` populated on purpose - Spring Security's own resource-server support leaves credentials empty) |
| `common_lib.security` | `InternalApiKeyFilter` | Service-to-service shared-secret check on `/internal/**` | **KEEP** | Orthogonal trust boundary, not part of user-facing auth; flagged in `KEYCLOAK_SETUP.md` §17 as a candidate for a *later* hardening pass (Keycloak client-credentials), out of scope here |
| `common_lib.observability` | `CorrelationIdFilter` | Request tracing | **KEEP** | Unrelated to auth |
| `common_lib.error` | `GlobalExceptionHandler` | Maps exceptions to `ApiError` JSON | **MODIFY** | Removed the `io.jsonwebtoken.JwtException` handler (dependency deleted); Keycloak/Resource-Server auth failures surface as `AuthenticationException` subtypes (e.g. `InvalidBearerTokenException`), already covered by the existing `AuthenticationException` handler |
| — | `pom.xml` | Declared `jjwt-api`/`jjwt-impl`/`jjwt-jackson` | **MODIFY** | Replaced with `spring-boot-starter-oauth2-resource-server` |

## api-gateway (reactive/WebFlux — architecturally different from the other three)

| Package | Class | Current responsibility | Action | Reason |
|---|---|---|---|---|
| `api_gateway.filter` | `GatewayJwtAuthFilter` | Hand-rolled `GlobalFilter`: check public-route allow-list, else require+verify a Bearer token | **DELETE** | Replaced by a real `SecurityWebFilterChain` |
| `api_gateway.service` | `JwtGatewayService` | Own `jjwt`-based HMAC verification; token parsing for rate-limiter keying | **DELETE** | Verification replaced by Spring Security's reactive resource server; rate-limiter keying replaced by reading the already-validated `Jwt` off `ReactiveSecurityContextHolder` |
| `api_gateway.config` | `GatewaySecurityConfig` | Reactive OAuth2 Resource Server `SecurityWebFilterChain`: same public-route allow-list (from `SecurityProperties`), custom JSON error bodies matching the old shape | **CREATE** | The gateway had **no** `SecurityWebFilterChain` at all before - this is new code, not a converted config, since there was nothing to convert |
| `api_gateway.config` | `RateLimiterConfig` | `KeyResolver` (user id else IP) + `RedisRateLimiter` bean | **MODIFY** | `userKeyResolver()` rewritten to read the `Jwt` from `ReactiveSecurityContextHolder` instead of calling the deleted `JwtGatewayService.extractUserIdOrNull()` |
| `api_gateway.properties` | `SecurityProperties` | Externalized public-route allow-list | **KEEP** | Still the single source of truth for public routes, now consumed by `GatewaySecurityConfig` instead of `GatewayJwtAuthFilter` |
| `api_gateway.error` | `GatewayExceptionHandler` | Catch-all `ErrorWebExceptionHandler` for uncaught exceptions | **KEEP** | Auth rejections are now handled inline by `GatewaySecurityConfig`'s own entry point/access-denied handler (which never throw further), so this class's scope is unchanged |
| — | `pom.xml` | Declared `jjwt-*` | **MODIFY** | Replaced with `spring-boot-starter-oauth2-resource-server` + `spring-boot-starter-security` |

## claims-service

| Package | Class | Current responsibility | Action | Reason |
|---|---|---|---|---|
| `claims_service.security` | `ClaimsSecurityConfig` | `SecurityFilterChain`: stateless, `CorrelationIdFilter`→`InternalApiKeyFilter`→`JwtAuthFilter`, `/actuator/**` public | **MODIFY** | `addFilterBefore(jwtAuthFilter, ...)` replaced with `.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtAuthenticationConverter)))`; public-route list unchanged |
| `claims_service.security` | `SecurityExpressions` | Per-claim RBAC (`hasPermission`, `hasPermissionForUser`) reading the caller's id via `AuthUtil.getCurrentUserId()` | **MODIFY** (call site only) | `AuthUtil` → `CurrentUserProvider`, mechanical rename; the RBAC logic itself is untouched |
| `claims_service.service.query.impl` | `ClaimQueryServiceImpl` | Reads current user id via `AuthUtil` for query-scoping | **MODIFY** (call site only) | Same rename |
| `claims_service.controller` | `ClaimController` | Reads current user id via `AuthUtil` | **MODIFY** (call site only) | Same rename |
| `claims_service.controller` | `InternalClaimsController` | Reads current user id via `AuthUtil` (fully-qualified reference) | **MODIFY** (call site only) | Same rename |
| `claims_service.messaging` | `ClaimUpdateConsumer` | Reads acting user id from the Kafka event payload, not from `AuthUtil`/`SecurityContext` | **KEEP** | No `SecurityContext` exists on a listener thread either way - unrelated to which auth mechanism is in use |
| `common_lib` (enums) | `ClaimRole`, `ClaimPermission` | Per-claim role → permission mapping | **KEEP** | Explicitly out of scope - not a Keycloak role, DB-backed, keyed off `getCurrentUserId()` regardless of mechanism |

## agent-service

| Package | Class | Current responsibility | Action | Reason |
|---|---|---|---|---|
| `agent_service.security` | `AgentSecurityConfig` | Same shape as `ClaimsSecurityConfig` | **MODIFY** | Same OAuth2 Resource Server swap |
| `agent_service.security` | `SecurityExpressions` (agent-service's own) | Thin proxy delegating to claims-service over the network | **KEEP** | Unaffected - it never touched JWTs directly |
| `agent_service.service.impl` | `AgentGenerationServiceImpl` | Reads current user id via `AuthUtil` | **MODIFY** (call site only) | Rename to `CurrentUserProvider` |
| `agent_service.service.impl` | `AgentQueryServiceImpl` | Reads current user id via `AuthUtil` | **MODIFY** (call site only) | Rename to `CurrentUserProvider` |

## customer-service (the only service with a login/signup flow)

| Package | Class | Current responsibility | Action | Reason |
|---|---|---|---|---|
| `customer_service.security` | `CustomerSecurityConfig` | `SecurityFilterChain` + `PasswordEncoder` bean + `AuthenticationManager` bean | **MODIFY** | Filter chain swapped to `.oauth2ResourceServer(...)`; `PasswordEncoder`/`AuthenticationManager` beans **removed** - no local credential store remains to authenticate against |
| `customer_service.service.impl` | `CustomerUserDetailsService` | `UserDetailsService` backing the local `AuthenticationManager` | **DELETE** | No local `AuthenticationManager` remains to back |
| `customer_service.entity` | `Customer` | Entity that also `implements UserDetails`, holds `password` | **MODIFY** | No longer implements `UserDetails`; `password` field removed from the Java mapping (column kept in the DB for one release - see the Flyway note below); added `keycloakId` |
| `customer_service.controller` | `AuthController` | `/auth/signup`, `/auth/login`, using `AuthenticationManager`/`PasswordEncoder`/`AuthUtil.generateAccessToken` | **MODIFY** | Rewritten to: (signup) save the `Customer` row → provision the matching Keycloak user via `KeycloakUserProvisioningService` → log the new user in via `KeycloakTokenService`; (login) proxy `KeycloakTokenService` then look up the local row for `customerId`/`fullName`. Request/response DTOs (`LoginRequest`, `SignupRequest`, `AuthResponse`) are **unchanged** |
| `customer_service.controller` | `PolicyController` | Reads current user id via `AuthUtil` | **MODIFY** (call site only) | Rename to `CurrentUserProvider` |
| `customer_service.controller` | `InternalCustomerController` | Reads current user id via `AuthUtil` | **MODIFY** (call site only) | Rename to `CurrentUserProvider` |
| `customer_service.service` | `KeycloakUserProvisioningService` | Creates a Keycloak user at signup via the Admin REST API, tagging `legacy_user_id` | **CREATE** | This is what replaces "hash the password and INSERT a row" as the credential-creation step |
| `customer_service.service` | `KeycloakTokenService` | Proxies the Resource Owner Password Credentials grant against Keycloak's token endpoint | **CREATE** | This is what keeps `/auth/login`'s existing JSON contract working without every client switching to Authorization Code flow |
| `customer_service.config` | `KeycloakProperties` | `@ConfigurationProperties("keycloak")` record: server URL, realm, both client ids/secrets | **CREATE** | Config surface for the two services above |
| `customer_service` (resources) | `db/migration/V2__keycloak_migration.sql` | — | **CREATE** | Relaxes the now-unused `password` column's `NOT NULL` constraint (kept, not dropped, as a rollback safety net - see the file's own comment) and adds `keycloak_id` |
| `customer_service.repository` | `CustomerRepository` | `findByUsername` | **KEEP** | Unaffected |

## Configuration (Spring Cloud Config: `local-config-repo/*.yml`)

| File | Change | Reason |
|---|---|---|
| `customer-service.yml` | Removed `jwt.secret-key`; added `spring.security.oauth2.resourceserver.jwt.issuer-uri` and the full `keycloak.*` block | Only service that needs the admin/token client config in addition to the issuer-uri every service needs |
| `claims-service.yml` | Removed `jwt.secret-key`; added `spring.security.oauth2.resourceserver.jwt.issuer-uri` | Resource-server-only - no signup/login here |
| `agent-service.yml` | Same as claims-service.yml | Same reasoning |
| `api-gateway.yml` | Removed `jwt.secret-key`; added `spring.security.oauth2.resourceserver.jwt.issuer-uri` under the **existing** `spring:` block (not a second one - YAML doesn't merge duplicate top-level keys) | Reactive resource server, same property name as the servlet services |
| `internal.api.secret` (all four) | **KEPT, unchanged** | Separate trust boundary (`InternalApiKeyFilter`), not part of this migration |

## New top-level files (Steps 7 & 8)

| File | Purpose |
|---|---|
| `docker-compose.yml` | **MODIFIED** (extended, not replaced) - added `keycloak-postgres` and `keycloak` services, importing `realm-export.json` on first boot |
| `realm-export.json` | **CREATE** - the `claimassist` realm: roles (`CUSTOMER`/`ADJUSTER`/`AUDITOR` + default-role composite), the `userId-claim` client scope + protocol mapper, the two OAuth2 clients (`claimassist-customer-app`, `claimassist-admin-service`), and a demo user |
| `application-keycloak.yml` | **CREATE** - documentation copy of every Keycloak property in use (the values that actually take effect live in `local-config-repo`, consistent with how every other setting in this platform is externalized) |
| `README-KEYCLOAK.md` | **CREATE** - one-page orientation to the migration |
| `KEYCLOAK_SETUP.md` | **CREATE** - the full setup/runbook/troubleshooting guide (Step 9) |
| `docs/current-security-analysis.md` | **CREATE** (Step 1, delivered earlier) |
| `docs/keycloak-migration-plan.md` | **CREATE** (this file) |

## Explicitly out of scope (documented, not silently ignored)

- **Claim-level RBAC** (`ClaimRole`, `ClaimPermission`, both services'
  `SecurityExpressions`) - per-resource, database-backed, orthogonal to
  authentication.
- **`InternalApiKeyFilter`'s shared secret** - a separate service-to-service
  trust boundary; a candidate for a future Keycloak client-credentials pass
  (see `KEYCLOAK_SETUP.md` §17) but not required for this migration to be
  correct.
- **Moving browser clients to Authorization Code + PKCE** - the migration
  keeps the Resource Owner Password Credentials grant specifically to
  preserve the existing `/auth/login` contract; flagged as a recommended
  follow-up, not done here, since it would change the client-facing contract
  this migration was explicitly asked to preserve.
