# Local Development Setup Guide

**Version:** 1.0.0  
**Last Updated:** 2026-08-04  
**Environment:** Windows / Linux / macOS  
**Estimated Setup Time:** 15-30 minutes

---

## 📋 Prerequisites

### System Requirements

- **OS:** Windows 10+ / macOS 11+ / Ubuntu 20.04+
- **RAM:** 8GB minimum, 16GB recommended
- **Disk:** 20GB free space
- **Docker:** 20.10+
- **Docker Compose:** 2.0+
- **Git:** 2.30+

### Software Installation

#### Windows

```powershell
# Using Chocolatey
choco install docker-desktop git

# Or download from:
# - Docker Desktop: https://www.docker.com/products/docker-desktop
# - Git: https://git-scm.com/download/win
```

#### macOS

```bash
# Using Homebrew
brew install --cask docker git

# Or download from:
# - Docker Desktop: https://www.docker.com/products/docker-desktop
# - Git: https://git-scm.com/download/mac
```

#### Linux (Ubuntu/Debian)

```bash
# Install Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# Add user to docker group
sudo usermod -aG docker $USER
newgrp docker

# Install Git
sudo apt-get install git

# Install Docker Compose
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose
```

### Verify Installation

```bash
docker --version      # Docker 20.10+
docker-compose --version  # Docker Compose 2.0+
git --version        # Git 2.30+
```

---

## 🚀 Quick Start (5 minutes)

### Step 1: Clone Repository

```bash
git clone <repository-url>
cd insurance-ai-platform
```

### Step 2: Start Services

```bash
docker-compose up -d
```

### Step 3: Wait for Services

```bash
# Check status (wait 30-60 seconds for all services to be healthy)
docker-compose ps

# Expected output:
# NAME          STATUS
# postgres      healthy
# mongo         healthy
# redis         healthy
# kafka         healthy
# keycloak      healthy
# config        healthy
# discovery     healthy
# api-gateway   healthy
# customer      healthy
# claims        healthy
# agent         healthy
```

### Step 4: Test APIs

```bash
# Health check
curl http://localhost:8080/health

# Get token
TOKEN=$(curl -s -X POST http://localhost:8180/realms/claimassist/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=claimassist-customer-app&grant_type=password&username=testuser&password=password" | jq -r '.access_token')

# Call API
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/customers/profile
```

---

## 📦 Complete Setup

### 1. Clone Repository

```bash
git clone <repository-url>
cd insurance-ai-platform
```

### 2. Configure Environment

```bash
# Copy environment template
cp .env.example .env

# Edit for local settings
nano .env
```

**Key Local Settings:**
```env
# Database
POSTGRES_USER=claimassist
POSTGRES_PASSWORD=claimassist
POSTGRES_DB=claims_db

# Keycloak
KEYCLOAK_ADMIN_PASSWORD=admin123

# Services
CONFIG_SERVER_URL=http://config:8888
EUREKA_SERVER_URL=http://discovery:8761/eureka/

# Kafka
KAFKA_BOOTSTRAP_SERVERS=kafka:9092

# Redis
REDIS_HOST=redis
REDIS_PORT=6379

# OpenAI (optional, set dummy key for local testing)
OPENAI_API_KEY=sk-test-dummy-key-for-local-development
```

### 3. Start Docker Compose Stack

```bash
# Start in background
docker-compose up -d

# Or start with logs visible (for debugging)
docker-compose up

# Tail logs for specific service
docker-compose logs -f claims-service

# Stop all services
docker-compose down

# Stop and remove volumes (full clean)
docker-compose down -v
```

### 4. Verify All Services Are Healthy

```bash
# Check service health
docker-compose ps

# Test each service
curl http://localhost:8080/health           # API Gateway
curl http://localhost:8081/health           # Customer Service
curl http://localhost:8082/health           # Claims Service
curl http://localhost:8083/health           # Agent Service
curl http://localhost:8761                  # Discovery Service UI
curl http://localhost:8888                  # Config Server
curl http://localhost:9411                  # Zipkin UI
```

---

## 🔑 Initial Configuration

### 1. Create Test Users in Keycloak

```bash
# Access Keycloak Admin Console
# http://localhost:8180/admin
# Username: admin
# Password: admin123

# Or use CLI commands
KEYCLOAK_TOKEN=$(curl -s -X POST http://localhost:8180/realms/master/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=admin-cli&username=admin&password=admin123&grant_type=password" | jq -r '.access_token')

# Create user
curl -X POST http://localhost:8180/admin/realms/claimassist/users \
  -H "Authorization: Bearer $KEYCLOAK_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "test@example.com",
    "firstName": "Test",
    "lastName": "User",
    "enabled": true,
    "emailVerified": true
  }'
```

### 2. Set User Password

```bash
# Get User ID
USER_ID=$(curl -s http://localhost:8180/admin/realms/claimassist/users?username=testuser \
  -H "Authorization: Bearer $KEYCLOAK_TOKEN" | jq -r '.[0].id')

# Set password
curl -X PUT http://localhost:8180/admin/realms/claimassist/users/$USER_ID/reset-password \
  -H "Authorization: Bearer $KEYCLOAK_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "type": "password",
    "value": "password",
    "temporary": false
  }'
```

---

## 🧪 Test First API Call

### Get Access Token

```bash
# Password Grant Flow
TOKEN=$(curl -s -X POST http://localhost:8180/realms/claimassist/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=claimassist-customer-app&grant_type=password&username=testuser&password=password" | jq -r '.access_token')

echo "Token: $TOKEN"
```

### Call Customer Service

```bash
# Get customer profile
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/customers/profile | jq

# Register customer
curl -s -X POST http://localhost:8080/api/customers/register \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "John",
    "lastName": "Doe",
    "email": "john@example.com",
    "password": "SecurePassword123!"
  }' | jq
```

### Call Claims Service

```bash
# Submit claim
curl -s -X POST http://localhost:8080/api/claims \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "policyId": "POL-2024-001",
    "claimType": "AUTO_ACCIDENT",
    "description": "Test claim",
    "incidentDate": "2026-08-04",
    "location": {
      "latitude": 37.7749,
      "longitude": -122.4194,
      "address": "Test St"
    },
    "claimants": [{
      "type": "INSURED",
      "name": "John Doe",
      "email": "john@example.com"
    }]
  }' | jq

# Get claims
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/claims | jq
```

---

## 🛠️ Development Workflow

### IDE Setup

#### VS Code

```bash
# Install extensions
# - Extension Pack for Java
# - Spring Boot Extension Pack
# - Docker
# - Kubernetes

# Open workspace
code insurance-ai-platform
```

#### IntelliJ IDEA

```bash
# Import as Maven project
File → Open → insurance-ai-platform/pom.xml

# Configure Spring Boot run configurations
Run → Edit Configurations → + → Spring Boot
```

#### Eclipse

```bash
# Import Maven project
File → Import → Existing Maven Projects → insurance-ai-platform
```

### Build Locally (Optional, for IDE Development)

```bash
# Set JAVA_HOME
export JAVA_HOME=/path/to/java-21  # or use system Java

# Build all modules
mvn clean package -DskipTests

# Run single service
java -jar claims-service/target/claims-service-1.0.0.jar

# Run with specific profile
java -jar -Dspring.profiles.active=local claims-service/target/claims-service-1.0.0.jar
```

### Live Coding with Docker Compose

For hot-reload during development:

```bash
# Terminal 1: Start services except claims-service
docker-compose up -d --scale claims-service=0

# Terminal 2: Run claims-service locally in IDE
mvn spring-boot:run -Dspring-boot.run.profiles=local -pl claims-service

# Now make code changes in IDE, Maven will auto-reload
```

---

## 📊 Accessing UIs

| Service | URL | Username | Password |
|---------|-----|----------|----------|
| **API Gateway** | http://localhost:8080 | - | - |
| **Keycloak** | http://localhost:8180/admin | admin | admin123 |
| **Zipkin** | http://localhost:9411 | - | - |
| **Eureka** | http://localhost:8761 | - | - |
| **PostgreSQL** | localhost:5432 | claimassist | claimassist |
| **MongoDB** | localhost:27017 | - | - |
| **Kafka** | localhost:9092 | - | - |
| **Redis** | localhost:6379 | - | - |

### Connect to PostgreSQL

```bash
# Using psql
psql -h localhost -U claimassist -d claims_db

# Or using pgAdmin (optional)
docker run -d \
  -e PGADMIN_DEFAULT_EMAIL=admin@example.com \
  -e PGADMIN_DEFAULT_PASSWORD=admin \
  -p 5050:80 \
  dpage/pgadmin4
```

### Connect to MongoDB

```bash
# Using MongoDB CLI
mongosh mongodb://localhost:27017

# Or using MongoDB Compass (GUI)
# Download: https://www.mongodb.com/products/tools/compass
# Connection: mongodb://localhost:27017
```

### View Kafka Topics

```bash
# List topics
docker-compose exec kafka kafka-topics --bootstrap-server kafka:9092 --list

# Create test topic
docker-compose exec kafka kafka-topics --bootstrap-server kafka:9092 \
  --create --topic test-topic --partitions 1 --replication-factor 1

# View topic messages
docker-compose exec kafka kafka-console-consumer --bootstrap-server kafka:9092 \
  --topic orchestration-request --from-beginning
```

---

## 🐛 Troubleshooting

### Services Won't Start

```bash
# Check logs
docker-compose logs <service-name>

# Common issues:
# 1. Port conflicts - check if ports are already in use
netstat -tulpn | grep -E ':8080|:8081|:8082|:5432'

# 2. Insufficient resources - increase Docker memory
# Docker Desktop → Preferences → Resources → Memory: 8GB+

# 3. Old containers/images - clean up
docker-compose down -v
docker system prune -a
docker-compose up -d
```

### Database Connection Issues

```bash
# Test PostgreSQL connection
docker-compose exec postgres psql -U claimassist -c "SELECT 1"

# Check database exists
docker-compose exec postgres psql -U claimassist -l

# Recreate databases
docker-compose down -v postgres
docker-compose up -d postgres
```

### Kafka Not Working

```bash
# Check Kafka health
docker-compose exec kafka kafka-broker-api-versions --bootstrap-server kafka:9092

# Check topic existence
docker-compose exec kafka kafka-topics --bootstrap-server kafka:9092 --list

# Recreate Kafka
docker-compose down -v kafka
docker-compose up -d kafka
```

### Keycloak Token Errors

```bash
# Check Keycloak is running
curl http://localhost:8180/health/live

# Check realm exists
curl http://localhost:8180/realms/claimassist

# Check token endpoint
curl -X POST http://localhost:8180/realms/claimassist/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=claimassist-customer-app&grant_type=password&username=testuser&password=password"
```

### Memory Issues

```bash
# Increase Docker resources (Docker Desktop GUI)
# Or via Docker Desktop config (~/.docker/config.json):
{
  "resources": {
    "memoryMiB": 8192,
    "cpus": 4
  }
}

# Restart Docker
docker-compose restart
```

---

## 📝 Common Development Tasks

### Add New Database Migration

```bash
# Create migration file
touch claims-service/src/main/resources/db/migration/V6__add_new_column.sql

# Write SQL
cat > claims-service/src/main/resources/db/migration/V6__add_new_column.sql << 'EOF'
ALTER TABLE claims ADD COLUMN new_column VARCHAR(255);
EOF

# Restart services
docker-compose restart claims-service
```

### View Service Logs in Real-Time

```bash
# Tail specific service
docker-compose logs -f claims-service

# Tail multiple services
docker-compose logs -f claims-service customer-service

# Show last 100 lines
docker-compose logs --tail=100 claims-service
```

### Debug Service Startup

```bash
# Start service with debug logging
docker-compose -f docker-compose.yml -f docker-compose.debug.yml up claims-service

# Or set log level
docker-compose exec claims-service curl -X POST localhost:8082/actuator/loggers/com.claimassist -H "Content-Type: application/json" -d '{"configuredLevel":"DEBUG"}'
```

### Run Tests

```bash
# Unit tests
mvn test -pl claims-service

# Integration tests (requires Docker)
mvn verify -pl claims-service

# Skip tests
mvn clean package -DskipTests
```

---

## ✅ Verification Checklist

- [ ] All services healthy (`docker-compose ps`)
- [ ] Can get Keycloak token
- [ ] Can call API Gateway with token
- [ ] Can view Zipkin traces
- [ ] Can access Keycloak admin console
- [ ] PostgreSQL has tables
- [ ] Kafka topics created
- [ ] Redis is responding
- [ ] Logs show no errors

---

**Next Steps:**
1. Read [Architecture Documentation](./architecture.md)
2. Explore [API Reference](./reference-api.md)
3. Try [Postman Collection](./insurance-ai-platform.postman_collection.json)
4. Review [CQRS Guide](./guide-cqrs.md)
