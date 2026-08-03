# Insurance AI Platform - Complete Project Documentation

**Version:** 1.0.0  
**Last Updated:** 2026-08-04  
**Platform:** Spring Boot 3.5.6 | Java 21 | PostgreSQL 16 | MongoDB | Kafka | Redis

---

## 📋 Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [System Components](#system-components)
3. [Technology Stack](#technology-stack)
4. [Business Capabilities](#business-capabilities)
5. [Quick Start](#quick-start)
6. [Documentation Structure](#documentation-structure)

---

## Architecture Overview

The Insurance AI Platform is a production-grade, event-driven microservices system for claims processing with AI capabilities. It implements enterprise patterns including CQRS, Saga Orchestration, and Transactional Outbox for reliable distributed processing.

### Key Principles

- **Microservices:** Independently deployable domain services
- **CQRS:** Separate read and write models (PostgreSQL writes, MongoDB reads ready)
- **Event-Driven:** Kafka-based asynchronous communication
- **Saga Pattern:** Distributed transaction orchestration with compensation
- **Security-First:** OAuth2, JWT, role-based access control
- **Observable:** Structured logging, distributed tracing, metrics
- **Reliable:** Transactional Outbox, idempotency, circuit breakers

---

## System Components

### Core Microservices

| Service | Port | Purpose | Key Technologies |
|---------|------|---------|------------------|
| **API Gateway** | 8080 | Public entry point, routing, rate limiting | Spring Cloud Gateway (Reactive) |
| **Customer Service** | 8081 | Customer management, policies, authentication | Spring Data JPA, OAuth2, PKCE |
| **Claims Service** | 8082 | Claims processing, documents, saga orchestration | Saga Pattern, Transactional Outbox |
| **Agent Service** | 8083 | AI agent, LLM integration, recommendations | OpenAI/OpenRouter API, Feign clients |

### Infrastructure Services

| Service | Port | Purpose |
|---------|------|---------|
| **Config Server** | 8888 | Git-backed centralized configuration |
| **Discovery Service** | 8761 | Eureka service registry |
| **Keycloak** | 8180 | Identity provider (OAuth2, JWT) |
| **PostgreSQL** | 5432 | Write database (claims, customers, policies) |
| **MongoDB** | 27017 | Read database (CQRS model) |
| **Kafka** | 9092 | Message broker |
| **Redis** | 6379 | Caching layer |
| **Zipkin** | 9411 | Distributed tracing |

---

## Technology Stack

### Backend
- **Framework:** Spring Boot 3.5.6
- **Java Version:** 21 (LTS)
- **Build Tool:** Maven 3.9.8
- **JPA Provider:** Hibernate (Jakarta EE)

### Databases
- **Primary (Write):** PostgreSQL 16 with HikariCP connection pooling
- **Secondary (Read):** MongoDB (CQRS-ready)
- **Migrations:** Flyway (versioned SQL migrations)

### Messaging & Events
- **Message Broker:** Apache Kafka
- **Patterns:** Transactional Outbox, Idempotency, Dead Letter Topics

### Security
- **Identity Provider:** Keycloak (OAuth2/OIDC)
- **JWT:** RS256 signed, role-based access
- **Service-to-Service:** Client Credentials Flow (OAuth2)

### Caching
- **Cache Provider:** Redis
- **Patterns:** Cache-Aside, TTL-based invalidation
- **Serialization:** Jackson JSON

### Observability
- **Logging:** Structured JSON, Correlation ID, MDC
- **Tracing:** Zipkin with Micrometer Tracing
- **Metrics:** Prometheus via Micrometer
- **Health Checks:** Spring Boot Actuator

### Deployment
- **Containerization:** Docker (multi-stage builds, Alpine JRE)
- **Orchestration:** Kubernetes (1.28+)
- **Package Manager:** Helm 3
- **CI/CD:** GitHub Actions

---

## Business Capabilities

### Customer Management
- User registration and authentication
- Policy lifecycle management
- Customer profile management
- OAuth2-based authorization

### Claims Processing
- Claim submission and tracking
- Document upload and management
- Status updates and notifications
- AI-powered claim recommendations

### Claim Workflows
- State-driven saga orchestration
- Automatic and manual status updates
- Compensation and rollback handling
- Timeout and failure recovery

### AI Integration
- Conversational AI using LLM
- Claim recommendation engine
- Document analysis support
- Multi-turn conversation handling

---

## Quick Start

### Local Development (Docker Compose)

```bash
# Clone repository
git clone <repository>
cd insurance-ai-platform

# Start full stack
docker-compose up -d

# Verify services
curl http://localhost:8080/health

# Access UIs
# - API Gateway: http://localhost:8080
# - Zipkin: http://localhost:9411
# - Keycloak: http://localhost:8180
```

### First API Call

```bash
# 1. Get Keycloak token
TOKEN=$(curl -s -X POST http://localhost:8180/realms/claimassist/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=claimassist-customer-app&grant_type=password&username=user&password=password" | jq -r '.access_token')

# 2. Call API with JWT
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/claims

# 3. View trace in Zipkin
# http://localhost:9411/zipkin/
```

---

## Documentation Structure

Comprehensive documentation is available in the following guides:

### Setup Guides
- **[Local Setup Guide](./setup-local.md)** - Development environment setup
- **[Development Setup Guide](./setup-dev.md)** - Dev environment configuration
- **[QA Setup Guide](./setup-qa.md)** - QA environment deployment
- **[UAT Setup Guide](./setup-uat.md)** - UAT environment setup
- **[Production Setup Guide](./setup-production.md)** - Production deployment

### Technical Guides
- **[CQRS Guide](./guide-cqrs.md)** - Command Query Responsibility Segregation pattern
- **[Saga Guide](./guide-saga.md)** - Distributed transaction orchestration
- **[Kafka Guide](./guide-kafka.md)** - Event streaming and messaging
- **[Redis Guide](./guide-redis.md)** - Caching strategy and patterns
- **[Security Guide](./guide-security.md)** - Authentication, authorization, secrets

### Deployment & Operations
- **[Docker Guide](./guide-docker.md)** - Container building and optimization
- **[Kubernetes Guide](./guide-kubernetes.md)** - K8s deployment patterns
- **[Helm Guide](./guide-helm.md)** - Helm chart usage and values
- **[CI/CD Guide](./guide-cicd.md)** - GitHub Actions pipeline
- **[Deployment Guide](./guide-deployment.md)** - Production deployment process
- **[Operations Runbook](./runbook-operations.md)** - Day-2 operations

### Developer Resources
- **[API Reference](./reference-api.md)** - Complete API documentation
- **[Architecture Diagrams](./architecture.md)** - System architecture visuals
- **[Functional Flows](./flows-functional.md)** - Business process flows
- **[Troubleshooting Guide](./guide-troubleshooting.md)** - Problem diagnosis
- **[Debugging Guide](./guide-debugging.md)** - Debug techniques and breakpoints

### Monitoring & Operations
- **[Monitoring Guide](./guide-monitoring.md)** - Metrics, logs, alerts
- **[End-to-End Flows](./flows-e2e.md)** - Complete request lifecycle

---

## Getting Help

### Documentation
All guides are located in `/docs` directory with standard format:
- **setup-*.md** - Environment setup guides
- **guide-*.md** - Technical implementation guides
- **runbook-*.md** - Operational procedures
- **reference-*.md** - API and component reference
- **flows-*.md** - Business and technical flows

### Postman Collection
Complete Postman collection with all APIs:
- **Local environment** - http://localhost:8080
- **Dev environment** - dev.claimassist.io
- **QA environment** - qa.claimassist.io
- **Production environment** - api.claimassist.io

Import `insurance-ai-platform.postman_collection.json` and environment files.

### Support Resources
- GitHub Issues: Bug reports and feature requests
- Architecture Decision Records: Design rationale
- Runbooks: Operational procedures

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2026-08-04 | Initial production release |

---

**Next Step:** Start with [Local Setup Guide](./setup-local.md) for development or [Production Setup Guide](./setup-production.md) for production deployment.
