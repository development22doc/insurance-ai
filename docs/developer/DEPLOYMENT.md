# Deployment Guide

**Version:** 1.0.0  
**Last Updated:** August 4, 2026

---

## Local Development

### Prerequisites

- Java 21 JDK
- Maven 3.9+
- Docker & Docker Compose
- PostgreSQL client (optional)
- Redis CLI (optional)

### Setup

```bash
# 1. Clone repository
git clone https://github.com/claimassist/insurance-ai-platform.git
cd insurance-ai-platform

# 2. Build all services
mvn clean package

# 3. Start infrastructure
docker-compose up -d

# 4. Wait for services to be ready (2-3 minutes)
# Check logs: docker-compose logs -f

# 5. Access services
# API Gateway: http://localhost:8080
# Swagger UI: http://localhost:8080/swagger-ui.html
# Keycloak: http://localhost:8180
# Zipkin: http://localhost:9411
# Eureka: http://localhost:8761
```

### Running Services Individually

```bash
# Terminal 1: Config Service
cd config-service && mvn spring-boot:run

# Terminal 2: Discovery Service
cd discovery-service && mvn spring-boot:run

# Terminal 3: Customer Service
cd customer-service && mvn spring-boot:run

# Terminal 4: Claims Service
cd claims-service && mvn spring-boot:run

# Terminal 5: Agent Service
cd agent-service && mvn spring-boot:run

# Terminal 6: API Gateway
cd api-gateway && mvn spring-boot:run
```

---

## Docker Deployment

### Build Images

```bash
# Build all services
mvn clean package

# For each service:
docker build -t claimassist/claims-service:1.0.0 ./claims-service
docker build -t claimassist/agent-service:1.0.0 ./agent-service
docker build -t claimassist/customer-service:1.0.0 ./customer-service
docker build -t claimassist/api-gateway:1.0.0 ./api-gateway
docker build -t claimassist/config-service:1.0.0 ./config-service
docker build -t claimassist/discovery-service:1.0.0 ./discovery-service
```

### Run with Docker Compose

```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f claims-service
docker-compose logs -f agent-service

# Stop services
docker-compose down

# Stop and remove volumes
docker-compose down -v
```

---

## Kubernetes Deployment

### Prerequisites

- Kubernetes cluster 1.27+
- kubectl configured
- Container registry access
- Helm 3.12+

### Namespace Setup

```bash
# Create namespaces
kubectl create namespace claimassist-core
kubectl create namespace claimassist-infra
kubectl create namespace claimassist-observability

# Label namespaces
kubectl label namespace claimassist-infra name=claimassist-infra
```

### ConfigMap & Secrets

```bash
# Create database secret
kubectl create secret generic database-credentials \
  --from-literal=username=claimassist \
  --from-literal=password=SecurePassword123 \
  -n claimassist-core

# Create Keycloak secret
kubectl create secret generic keycloak-clients \
  --from-literal=client-secret=AaBbCcDdEeFf123456 \
  -n claimassist-core

# Create ConfigMaps from files
kubectl create configmap claims-service-config \
  --from-file=local-config-repo/claims-service.yml \
  -n claimassist-core
```

### Using Helm

```bash
# Install Helm chart
helm install claimassist-platform ./helm/claimassist \
  -f ./helm/claimassist/values-dev.yaml \
  -n claimassist-core \
  --create-namespace

# Verify deployment
kubectl get pods -n claimassist-core
kubectl get services -n claimassist-core

# Check status
kubectl describe pod claims-service-xyz -n claimassist-core

# View logs
kubectl logs -f deployment/claims-service -n claimassist-core

# Upgrade chart
helm upgrade claimassist-platform ./helm/claimassist \
  -f ./helm/claimassist/values-dev.yaml \
  -n claimassist-core

# Rollback to previous version
helm rollback claimassist-platform 1 -n claimassist-core
```

### Manual kubectl Deployment

```bash
# Apply resources in order
kubectl apply -f k8s/namespaces.yaml
kubectl apply -f k8s/configmap-secrets.yaml
kubectl apply -f k8s/services/
kubectl apply -f k8s/network-policies.yaml
kubectl apply -f k8s/observability/

# Verify
kubectl get all -n claimassist-core
```

---

## Database Migrations

### PostgreSQL Initialization

```bash
# Initialize databases (runs on container start)
kubectl exec -it postgres-pod -n claimassist-infra -- \
  psql -U claimassist -d postgres -f /init-db.sql

# Verify databases created
kubectl exec -it postgres-pod -n claimassist-infra -- \
  psql -U claimassist -d postgres -c '\l'
```

### Flyway Migrations

Migrations run automatically on service startup:

```
1. Service starts
2. Flyway runs pending migrations
3. Version tracked in flyway_schema_history table
4. Service starts accepting requests
```

**Manual migration (if needed):**

```bash
# Baseline existing database
mvn flyway:baseline

# Run pending migrations
mvn flyway:migrate

# Check migration status
mvn flyway:info
```

---

## Verification Checklist

### Health Checks

```bash
# API Gateway
curl http://api-gateway:8080/actuator/health

# Claims Service
curl http://claims-service:8082/actuator/health/ready

# All services
for svc in api-gateway customer-service claims-service agent-service; do
  curl http://${svc}:8080/actuator/health
done
```

### Service Discovery

```bash
# Eureka
curl http://discovery-service:8761/eureka/v2/apps

# All registered services should appear
```

### Database Connectivity

```bash
# Connect to PostgreSQL
kubectl exec -it postgres-pod -- psql -U claimassist -d claims_db

# List tables
\dt

# Verify migrations applied
SELECT * FROM flyway_schema_history;
```

### Kafka Topics

```bash
# Connect to Kafka
kubectl exec -it kafka-pod -- kafka-topics \
  --bootstrap-server localhost:9092 --list

# Should see claim-* topics
```

---

## Production Deployment

### Pre-deployment Checklist

- [ ] All tests passing
- [ ] Security scan completed
- [ ] Load testing passed
- [ ] Backup strategy configured
- [ ] Monitoring alerts configured
- [ ] Incident response plan reviewed
- [ ] Rollback procedure tested
- [ ] Infrastructure capacity verified

### Production Configuration

**values-prod.yaml:**
```yaml
replicaCount: 3              # High availability
resources:
  requests:
    memory: "512Mi"
    cpu: "250m"
  limits:
    memory: "1024Mi"
    cpu: "500m"
    
autoscaling:
  enabled: true
  minReplicas: 3
  maxReplicas: 10
  targetCPUUtilization: 70
```

### Production Deployment

```bash
# 1. Build and test
mvn clean test

# 2. Build Docker images
mvn clean package
docker build -t claimassist/claims-service:1.0.0 ./claims-service

# 3. Push to registry
docker push claimassist/claims-service:1.0.0

# 4. Update image tags in values-prod.yaml
# 5. Deploy via Helm
helm upgrade claimassist-platform ./helm/claimassist \
  -f ./helm/claimassist/values-prod.yaml \
  -n claimassist-core

# 6. Verify deployment
kubectl rollout status deployment/claims-service -n claimassist-core

# 7. Run smoke tests
./run-smoke-tests.sh
```

---

## Monitoring Deployment

### Real-time Monitoring

```bash
# Watch pods
kubectl get pods -n claimassist-core -w

# Stream logs
kubectl logs -f deployment/claims-service -n claimassist-core

# Check resource usage
kubectl top nodes
kubectl top pods -n claimassist-core
```

### Metrics Dashboard

Access Prometheus: `http://prometheus:9090`
Access Grafana: `http://grafana:3000`

Key metrics to monitor:
- Request latency (P99)
- Error rate
- Pod restart count
- Database connection pool utilization
- Kafka consumer lag
- JVM memory usage

---

## Rollback Procedure

### Helm Rollback

```bash
# View revision history
helm history claimassist-platform -n claimassist-core

# Rollback to previous revision
helm rollback claimassist-platform 2 -n claimassist-core

# Verify rollback
kubectl rollout status deployment/claims-service -n claimassist-core
```

### Kubectl Rollback

```bash
# Check rollout history
kubectl rollout history deployment/claims-service -n claimassist-core

# Rollback to previous version
kubectl rollout undo deployment/claims-service -n claimassist-core

# Rollback to specific revision
kubectl rollout undo deployment/claims-service --to-revision=2 -n claimassist-core
```

---

## Troubleshooting

### Pod Startup Failures

```bash
# Check pod events
kubectl describe pod claims-service-xyz -n claimassist-core

# Check logs
kubectl logs claims-service-xyz -n claimassist-core

# Check configuration
kubectl get configmap -n claimassist-core
kubectl describe configmap claims-service-config -n claimassist-core
```

### CrashLoopBackOff

```
Cause: Pod keeps crashing on startup

Check:
1. Application logs for startup errors
2. Database connectivity
3. Environment variables
4. Resource limits

Solution:
1. Fix error in code or config
2. Rebuild image
3. Force pod restart: kubectl delete pod <pod-name>
```

### Pending Pods

```
Cause: Pod cannot be scheduled

Check:
1. Node resources: kubectl describe nodes
2. Pod resource requests
3. Node affinity rules
4. PVC availability

Solution:
1. Add nodes to cluster
2. Reduce resource requests
3. Check PVC provisioning
```

---

## Disaster Recovery

### Database Backup

```bash
# Backup PostgreSQL
kubectl exec postgres-pod -- \
  pg_dump -U claimassist claims_db | \
  gzip > backup_$(date +%Y%m%d).sql.gz

# Restore from backup
gunzip < backup_20260804.sql.gz | \
kubectl exec -i postgres-pod -- \
  psql -U claimassist claims_db
```

### MongoDB Backup

```bash
# Backup MongoDB
kubectl exec mongo-pod -- \
  mongodump --uri mongodb://root:example@localhost:27017 \
  --out /backup

# Restore MongoDB
kubectl exec mongo-pod -- \
  mongorestore --uri mongodb://root:example@localhost:27017 \
  /backup
```

---


