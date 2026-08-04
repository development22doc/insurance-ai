# ClaimAssist AI Platform

A microservices-based insurance claims processing platform built with Spring Boot 3.5.6 and running on Java 21.

## Overview

ClaimAssist AI Platform is an enterprise-grade application for managing insurance claims with intelligent document processing, fraud detection, and automated workflows. The platform is designed for Kubernetes deployment with comprehensive observability, security, and scalability features.

## Architecture

### Core Services

- **API Gateway** (`api-gateway`): Entry point for all client requests with routing, rate limiting, and request validation
- **Customer Service** (`customer-service`): Manages customer profiles, accounts, and customer-related operations
- **Claims Service** (`claims-service`): Core claims processing engine with document management and workflow orchestration
- **Agent Service** (`agent-service`): Intelligent agent for claims processing with ML-based fraud detection
- **Config Service** (`config-service`): Spring Cloud Config server for centralized configuration management
- **Discovery Service** (`discovery-service`): Eureka service registry for service discovery

### Infrastructure Services

- **PostgreSQL**: Primary data store for transactional data
- **Redis**: Caching and session management
- **Kafka**: Event streaming and asynchronous messaging
- **Keycloak**: OAuth2/OIDC authentication and authorization
- **MongoDB**: Document and audit log storage
- **Zipkin**: Distributed tracing and observability
- **Prometheus**: Metrics collection
- **Grafana**: Metrics visualization

## Technology Stack

- **Language**: Java 21
- **Framework**: Spring Boot 3.5.6
- **Build Tool**: Maven 3.9
- **Containers**: Docker
- **Orchestration**: Kubernetes 1.27+
- **Package Manager**: Helm 3.12+
- **CI/CD**: GitHub Actions

## Quick Start

### Prerequisites

- Java 21
- Maven 3.9+
- Docker & Docker Compose
- Kubernetes cluster (for production)
- Helm 3.12+ (for production)

### Local Development

#### Using Docker Compose

```bash
docker-compose up -d
```

This will start all infrastructure services and the application services.

#### Using Maven

```bash
# Build all services
mvn clean package

# Start individual services
cd api-gateway && mvn spring-boot:run
cd customer-service && mvn spring-boot:run
cd claims-service && mvn spring-boot:run
cd agent-service && mvn spring-boot:run
cd discovery-service && mvn spring-boot:run
cd config-service && mvn spring-boot:run
```

### Kubernetes Deployment

#### Using Helm

```bash
# Add Helm repository (if applicable)
helm repo add claimassist-platform ./helm

# Install chart
helm install claimassist-platform ./helm/claimassist \
  -f ./helm/claimassist/values-dev.yaml \
  -n claimassist-core \
  --create-namespace

# Verify deployment
kubectl get pods -n claimassist-core
kubectl get services -n claimassist-core
```

#### Using kubectl

```bash
# Deploy namespaces
kubectl apply -f k8s/namespaces.yaml

# Deploy configurations
kubectl apply -f k8s/configmap-secrets.yaml

# Deploy services
kubectl apply -f k8s/services/

# Deploy network policies
kubectl apply -f k8s/network-policies.yaml
```

## Configuration

### Environment-Specific Values

- **Local Development**: `helm/claimassist/values-local.yaml`
- **Development**: `helm/claimassist/values-dev.yaml`
- **QA**: `helm/claimassist/values-qa.yaml`
- **UAT**: `helm/claimassist/values-uat.yaml`
- **Production**: `helm/claimassist/values-prod.yaml`

### Config Server

Central configuration is managed through Spring Cloud Config Server. Configuration files are located in `local-config-repo/`:

- `api-gateway.yml`
- `customer-service.yml`
- `claims-service.yml`
- `agent-service.yml`
- `discovery-service.yml`
- `config-service.yml`

## API Documentation

API documentation is available through Swagger/OpenAPI:

- API Gateway: `http://localhost:8080/swagger-ui.html`
- Customer Service: `http://localhost:8081/swagger-ui.html`
- Claims Service: `http://localhost:8082/swagger-ui.html`
- Agent Service: `http://localhost:8083/swagger-ui.html`

## Security

The platform implements:

- OAuth2 Resource Server with Keycloak
- Role-Based Access Control (RBAC)
- Zero-Trust Architecture with internal API secret validation
- Network Policies for service-to-service communication
- Encrypted credentials in Kubernetes Secrets
- Security scanning in CI/CD pipeline

## Monitoring & Observability

### Metrics

Prometheus metrics are available at:

- `http://localhost:8080/actuator/prometheus`
- `http://localhost:8081/actuator/prometheus`
- `http://localhost:8082/actuator/prometheus`
- `http://localhost:8083/actuator/prometheus`

### Tracing

Distributed tracing is available through Zipkin:

- `http://localhost:9411`

### Logging

Logs are structured in JSON format and can be sent to:

- ELK Stack (Elasticsearch, Logstash, Kibana)
- Datadog
- CloudWatch
- Any standard log aggregation platform

## Database Migrations

Database migrations are managed using Flyway. Migration files are located in:

- `claims-service/src/main/resources/db/migration/`
- `customer-service/src/main/resources/db/migration/`
- `agent-service/src/main/resources/db/migration/`

## Testing

### Unit Tests

```bash
mvn clean test
```

### Integration Tests

```bash
mvn clean verify
```

### Load Testing

Load testing configurations are available in the project for stress testing services.

## CI/CD Pipeline

GitHub Actions workflows automate:

1. **Build** (`.github/workflows/build.yml`): Compiles code and runs Maven builds
2. **Test** (`.github/workflows/test.yml`): Executes unit tests
3. **Security Scan** (`.github/workflows/security.yml`): Performs dependency checks and CVE scanning
4. **Docker Build** (`.github/workflows/docker.yml`): Builds and pushes Docker images
5. **Deploy** (`.github/workflows/deploy.yml`): Deploys to Kubernetes using Helm
6. **Rollback** (`.github/workflows/rollback.yml`): Enables rollback to previous releases

## Project Structure

```
insurance-ai-platform/
├── .github/
│   └── workflows/              # CI/CD Pipeline definitions
├── api-gateway/                # API Gateway service
├── claims-service/             # Claims processing service
├── customer-service/           # Customer management service
├── agent-service/              # AI Agent service
├── discovery-service/          # Eureka Service Registry
├── config-service/             # Spring Cloud Config Server
├── common-lib/                 # Shared libraries and utilities
├── helm/
│   └── claimassist/            # Helm chart for deployment
├── k8s/                        # Kubernetes manifests
│   ├── services/               # Service deployments
│   ├── observability/          # Monitoring stack
│   ├── claim-processing/       # Claims processing resources
│   └── network-policies.yaml   # Network security policies
├── local-config-repo/          # Spring Cloud Config files
├── docs/                       # Documentation
├── docker-compose.yml          # Local development environment
├── values*.yaml                # Helm values for different environments
└── pom.xml                     # Parent Maven POM

```

## Deployment Strategies

### Blue-Green Deployment

The platform supports blue-green deployments using Helm:

```bash
# Deploy to "blue" environment
helm upgrade --install claimassist-platform-blue ./helm/claimassist ...

# Test and validate
# Switch traffic to "blue" when ready
```

### Rolling Updates

Kubernetes rolling updates are configured with:

- Min unavailable: 1 pod
- Max surge: 1 pod
- Readiness probe checks before routing traffic

### Rollback

If deployment issues occur:

```bash
# Manual rollback
helm rollback claimassist-platform [REVISION]

# Automated rollback via GitHub Actions workflow
# Trigger the rollback workflow with target revision
```

## Troubleshooting

### Common Issues

#### Services not registering with Eureka

- Verify Eureka server is running: `http://localhost:8761`
- Check service configuration for correct eureka.client.service-url

#### Database connection failures

- Verify PostgreSQL is running
- Check database credentials in config files
- Ensure network connectivity

#### Kafka connection issues

- Verify Kafka broker is running and accessible
- Check bootstrap server configuration
- Ensure Kafka topics are created

#### Keycloak authentication failures

- Verify Keycloak server is running: `http://localhost:8180`
- Check OAuth2 client configuration
- Verify JWT token validity

## Maintenance

### Regular Tasks

- Review and update dependencies monthly
- Run security scans weekly
- Monitor disk space on PostgreSQL and MongoDB
- Backup databases regularly
- Review and optimize database indexes
- Monitor service performance metrics

### Scaling

Services are configured with HPA (Horizontal Pod Autoscaler):

```bash
# View HPA status
kubectl get hpa -n claimassist-core

# Manual scaling
kubectl scale deployment/claims-service --replicas=5 -n claimassist-core
```

## Contributing

1. Create a feature branch from `develop`
2. Make changes following the project's coding standards
3. Write or update tests as necessary
4. Submit a pull request for review
5. Ensure all CI/CD checks pass before merging

## License

Proprietary - All Rights Reserved

## Support

For support and questions, please contact the platform team.

## Versioning

Current Version: **1.0.0**

This project follows semantic versioning:

- **Major**: Incompatible API changes
- **Minor**: New functionality (backward compatible)
- **Patch**: Bug fixes

---

Last Updated: August 4, 2026

