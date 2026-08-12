# Phase 1.1 — Repository Inventory Progress

## Task Overview
**READ-ONLY TASK** - Repository structure inspection and module identification

## Current Status
**COMPLETED**

## Git Status
- **Branch**: application/optimizations
- **Deleted files**: Multiple guide and progress files (COPILOT_HANDOFF.md, LOCAL_*_GUIDE.md, phase*_progress.md, policy_*_progress.md)
- **Untracked files**: phase1_task1_progress.md
- **Modified files**: None

## Master Change Log - Last Task Review
The last task in master_change_log.md was "Customer Service Policy CRUD Verification" (PARTIALLY VERIFIED):
- Fixed PolicyServiceImpl.java duplicate code defect
- Customer service running on port 8081
- Database empty preventing full runtime verification
- Architecture, DTOs, security, cache integration verified

## Maven Modules Identified

### Root POM Structure
- **Parent**: claimassist-ai-platform (version 1.0.0)
- **Java Version**: 21
- **Spring Boot**: 3.5.6
- **Spring Cloud**: 2025.0.0

### Modules (7 total)
1. **common-lib** - Shared library module
   - Packaging: jar
   - Purpose: Shared Keycloak/OAuth2 auth, filters, correlation-id, exception handling, DTOs, saga events
   - Dependencies: Spring Web, Security, OAuth2 Resource Server, Kafka, Resilience4j, Micrometer Tracing, Lombok

2. **discovery-service** - Eureka service registry
   - Port: 8761
   - Purpose: Zero business logic, standard Spring Cloud Eureka server
   - Dependencies: Eureka Server, Actuator, Micrometer, common-lib

3. **config-service** - Spring Cloud Config Server
   - Port: 8888
   - Purpose: Git-backed configuration centralization
   - Dependencies: Config Server, Eureka Client, Actuator, Micrometer, common-lib

4. **api-gateway** - API Gateway
   - Port: 8080
   - Purpose: Single public entrypoint, JWT validation, Redis rate limiting, service routing
   - Stack: WebFlux (reactive)
   - Dependencies: Gateway, Eureka Client, Config, Redis Reactive, Resilience4j, OAuth2 Resource Server, common-lib

5. **customer-service** - Customer domain service
   - Port: 8081
   - Purpose: Customer identity, policies, coverage plans, premium billing
   - Dependencies: Web, Data JPA, Security, Cache, Redis, OAuth2, Eureka, Config, PostgreSQL, Flyway, Stripe, MapStruct, common-lib

6. **claims-service** - Claims domain service
   - Port: 8082
   - Purpose: Claims, documents, parties, claim-update saga
   - Dependencies: Cache, Redis, Kafka, Resilience4j, Web, Data JPA, Security, Validation, Eureka, OpenFeign, Config, MinIO, Kubernetes Client, PostgreSQL, H2, Flyway, MapStruct, common-lib

7. **agent-service** - AI claims assistant service
   - Port: 8083
   - Purpose: AI tool-calling, streaming, claim-update saga producer
   - Dependencies: Resilience4j, Kafka, Web, OAuth2, Data JPA, Security, Validation, Eureka, OpenFeign, Config, Redis, PostgreSQL, H2, Flyway, Spring Retry, MapStruct, common-lib
   - Note: Spring AI model starter disabled for local startup

## Spring Services Identified

### Main Application Classes
1. AgentServiceApplication.java
2. ApiGatewayApplication.java
3. ClaimsServiceApplication.java
4. ConfigServiceApplication.java
5. CustomerServiceApplication.java
6. DiscoveryServiceApplication.java

### Service Types
- **Infrastructure Services**: discovery-service, config-service
- **Gateway Service**: api-gateway
- **Business Services**: customer-service, claims-service, agent-service

## Common Module Structure

### common-lib Package Structure
- **dto**: ClaimDocumentSummaryDto, ClaimStatusDto, PolicyCoverageDto
- **enums**: AgentEventStatus, AgentEventType, ClaimPermission, ClaimRole, ClaimStatus, MessageRole, OutboxStatus, SagaActionType, SagaOrchestrationStatus, SagaStepType
- **error**: ApiError, BadRequestException, ClaimStateTransitionException, GlobalExceptionHandler, ResourceNotFoundException, ServiceUnavailableException, SharedExceptionAutoConfiguration
- **event**: ClaimSagaOrchestrationRequestEvent, ClaimSagaOrchestrationResultEvent, ClaimSagaStepCommandEvent, ClaimSagaStepResultEvent, ClaimUpdateRequestEvent, ClaimUpdateResponseEvent
- **observability**: CorrelationIdFilter, DatabaseExecutionTimeAspect, ExceptionLoggingAspect, ExecutionTimeAspect, FeignClientTimingBeanPostProcessor, FeignCorrelationRequestInterceptor, KafkaExecutionTimeAspect, PerformanceLogger, various logging utilities
- **security**: CorsConfigurationHandler, CurrentUserProvider, KeycloakJwtAuthenticationConverter, SecureCookieConfiguration, SecurityHeadersFilter, ServiceClientCredentialsTokenProvider, SharedSecurityAutoConfiguration

## Major Source/Config Directories

### Source Code Structure
Each service follows standard Maven structure:
- `src/main/java/com/claimassist/platform/{service_name}/`
- `src/main/resources/`
- `src/test/` (where present)

### Service-Specific Packages
- **agent-service**: ai, cache, client, config, consumer, controller, dto, entity, health, llm
- **api-gateway**: config, error, filter, properties
- **customer-service**: config, controller, dto, entity, exception, health, mapper, repository, security, service
- **claims-service**: cache, client, config, consumer, controller, cqrs, dto, entity, health, mapper, messaging

### Configuration Files
- **Root level**: application-keycloak.yml, docker-compose.yml, .env, logback-spring.xml
- **config-repo**: agent-service.yml, api-gateway.yml, application.yml, claims-service.yml, config-service.yml, customer-service.yml, discovery-service.yml
- **Service-level**: Each service has application.yaml, application-local.yaml, application-dev.yaml, application-prod.yaml

### Infrastructure Directories
- **infrastructure/docker**: keycloak, postgres, docker-compose.local.yml
- **infrastructure/monitoring**: grafana, loki, prometheus, promtail, README.md

### Database Migrations (Flyway)
- **customer-service**: V1__init_customer_schema.sql, V2__keycloak_migration.sql, V3__add_refresh_tokens_table.sql
- **claims-service**: V1__init_claims_schema.sql, V2__add_claims_reliability.sql, V3__add_document_processing.sql, V4__enhance_outbox_reliability.sql, V5__add_saga_orchestrator_tables.sql, V6__add_saga_recovery_tracking.sql, V7__add_outbox_trace_context.sql
- **agent-service**: V1__init_agent_schema.sql, V2__add_session_tracking.sql, V3__add_event_processing.sql, V4__enhance_outbox_reliability.sql, V5__add_outbox_trace_context.sql
- **infrastructure/docker/postgres**: init-db.sql

### Scripts
- **scripts**: run-customer-service-local.ps1, .gitkeep

## Architecture Patterns Identified

### Distributed System Patterns
- **Service Discovery**: Eureka (discovery-service)
- **Configuration Management**: Spring Cloud Config (config-service)
- **API Gateway**: Spring Cloud Gateway (api-gateway)
- **CQRS**: Command/Query separation in claims-service
- **Saga Pattern**: Claim update saga orchestration
- **Outbox Pattern**: Outbox table for reliable event publishing
- **Circuit Breaker**: Resilience4j
- **Caching**: Redis-based caching
- **Event-Driven**: Kafka messaging
- **Observability**: Micrometer, Zipkin, Prometheus, Grafana

### Security
- **OAuth2/OIDC**: Keycloak integration
- **JWT**: Resource server validation
- **Zero-trust**: Internal service authentication

## Key Technologies
- **Language**: Java 21
- **Build**: Maven
- **Framework**: Spring Boot 3.5.6, Spring Cloud 2025.0.0
- **Database**: PostgreSQL (primary), H2 (testing)
- **Cache**: Redis
- **Messaging**: Apache Kafka
- **Security**: Keycloak, OAuth2 Resource Server
- **Observability**: Micrometer, Zipkin, Prometheus, Grafana, Loki, Promtail
- **API Documentation**: SpringDoc OpenAPI
- **Object Mapping**: MapStruct
- **Database Migration**: Flyway
- **Resilience**: Resilience4j
- **File Storage**: MinIO
- **Containerization**: Docker Compose

## Files Modified
**NONE** - This was a read-only task

## Files Created
- **phase1_task1_progress.md** - This progress tracking file

## Blockers
**NONE**

## Final Status
**SUCCESS** - Repository inventory completed. All Maven modules, Spring services, common modules, and major source/config directories identified and documented.

## Next Steps
This task is complete. The next phase would involve specific analysis or modifications based on the identified structure.
