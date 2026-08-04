# Environment setup — Insurance AI Platform

This document describes how to run the project locally, how the DEV / PROD deployments are organized, and how to validate important functionality. Treat this repository as the single source of truth — the instructions below document only what is implemented in the repository.

Paths and files referenced in this document use absolute paths relative to the repository root. Example repository root: `D:\Mayur\claimsassist\insurance-ai-platform`.

---

## Summary of implemented components
- Spring Boot microservices (Maven modules): `discovery-service`, `config-service`, `api-gateway`, `customer-service`, `claims-service`, `agent-service`, plus `common-lib`.
- Docker Compose infrastructure (local): Postgres, Redis, Kafka (single-node KRaft broker), Zipkin, Keycloak (+ its Postgres), MongoDB.
- Spring Cloud Config Server (git-backed) — `config-service` (local-config-repo provided for local development).
- Eureka discovery server — `discovery-service`.
- Transactional outbox + Kafka-based eventing, saga orchestration (implemented — see docs).
- Helm chart `helm/claimassist/` and Kubernetes manifests under `k8s/` for cluster deployments.
- GitHub Actions deploy workflows under `.github/workflows/` for CI/CD.
- Actuator endpoints (health, readiness/liveness, prometheus) present on services.

Components that are NOT provided in `docker-compose.yml` but are available as Helm features for Kubernetes:
- Prometheus + Grafana (the Helm chart can enable and configure these; they are not in `docker-compose.yml`).

---

## Prerequisites — software & versions
- Git
- Java 21 (JDK 21) — repository modules target Java 21 (see root `pom.xml`).
- Maven 3.8+ or compatible
- Docker Engine and Docker Compose (Compose V2 is fine). On Windows use Docker Desktop.
- kubectl and Helm (for DEV/PROD Kubernetes steps):
  - kubectl (v1.27 or later recommended)
  - Helm 3.12+ (workflows use Helm 3.12.0 in examples)
- Optional: an IDE (IntelliJ IDEA recommended) with Maven support.
- PowerShell (Windows) or a POSIX shell on macOS/Linux — examples below use PowerShell where appropriate.

Note: exact versions used in CI are referenced in the workflows (see `.github/workflows/deploy.yml` and `.github/workflows/deploy.yaml`).

---

## Clone repository
PowerShell:

```powershell
Set-Location C:\dev
git clone <REPO_URL> claimassist
Set-Location 'C:\dev\claimassist'   # or the path you cloned into
git checkout main
```

Replace `<REPO_URL>` with your remote URL.

---

## Local environment setup — high level
1. Start local infrastructure using `docker-compose.yml` (Postgres, Redis, Kafka, Zipkin, Keycloak, Mongo).
2. Prepare local config repository (see `local-config-repo/README.md`) and point `config-service` to it via `CONFIG_REPO_URI` or use the embedded file-based repo location.
3. Build or import microservice projects in your IDE (or use Maven to build/run). Services read configuration from the Config Server.
4. Start services (discovery, config, gateway, then business services) in the correct order or run them from IntelliJ with the variables described below.

---

## Docker Compose setup (local)
From the repository root run (PowerShell):

```powershell
cd D:\Mayur\claimsassist\insurance-ai-platform
docker compose up -d
```

What `docker-compose.yml` starts (implemented):
- `postgres` (image `postgres:16`) — default DB user `claimassist` / `claimassist`. The compose file runs `init-db.sql` which creates `customer_db`, `claims_db`, `agent_db`.
- `redis` (image `redis:7`) — port 6379.
- `kafka` (image `apache/kafka:3.8.0`) — single-node KRaft broker on 9092.
- `zipkin` (image `openzipkin/zipkin:3`) — tracing UI at `http://localhost:9411`.
- `keycloak-postgres` and `keycloak` (Keycloak 26) — Keycloak running with realm import from `realm-export.json`. Keycloak admin default credentials in compose: `KEYCLOAK_ADMIN=admin` / `admin`. Keycloak HTTP mapped to host port `8180`.
- `mongo` (image `mongo:6.0`) — MongoDB with `root` / `example` credentials.

Confirm containers are healthy:

```powershell
docker ps --format "table {{.Names}}	{{.Status}}"
docker compose ps
```

Common local ports:
- Postgres: 5432
- Redis: 6379
- Kafka: 9092
- Zipkin: 9411
- Keycloak: 8180
- MongoDB: 27017

If you need to inspect container logs:

```powershell
docker compose logs -f keycloak
docker compose logs -f claimassist-kafka
```

---

## Local config repository (Config Server)
The repository contains a `local-config-repo/` folder with sample service `*.yml` files.

Quick local setup (PowerShell):

```powershell
# create a local git-backed config repo
mkdir C:\claimassist-config
Copy-Item -Path .\local-config-repo\*.yml -Destination C:\claimassist-config
Set-Location C:\claimassist-config
git init
git add .
git commit -m "local config"

# before starting config-service (or when running from IDE)
$env:CONFIG_REPO_URI = "file:///C:/claimassist-config"
$env:GIT_USERNAME = "local"
$env:GIT_PASSWORD = "local"
```

Important:
- `local-config-repo/README.md` notes that `jwt.secret-key` and `internal.api.secret` must match across the config files for local runs.

Config Server default port: `8888` (see `local-config-repo/config-service.yml`). When clients start, they request configuration from `http://<config-service-host>:8888/<application>/<profile>`.

---

## Which infrastructure runs in Docker (local)
- Postgres (DBs)
- Keycloak (+ its own Postgres)
- Kafka (single broker)
- Redis
- Zipkin
- MongoDB

Prometheus/Grafana are not included in the Docker Compose file; they are available if you enable them via the Helm chart for Kubernetes deployments.

---

## Build & start Spring Boot services (IntelliJ)
Recommended approach: import the root `pom.xml` into IntelliJ as a Maven multi-module project.

Build:

```powershell
mvn -T 1C -DskipTests package
```

Run from IntelliJ (recommended for iteration):
1. Set the following environment variables in your Run Configuration before starting `config-service` if you are using the local file-based config repo:
   - `CONFIG_REPO_URI` = `file:///C:/claimassist-config` (or path you created)
   - `GIT_USERNAME` = `local`
   - `GIT_PASSWORD` = `local`
2. Services to run from the IDE (recommended start order below):
   - `discovery-service` (Eureka) — port 8761
   - `config-service` — port 8888 (config server)
   - `api-gateway` — gateway (routes, security)
   - `customer-service`
   - `claims-service`
   - `agent-service`

Notes on startup order and why:
- Start infrastructure (Docker Compose) first so Postgres / Kafka / Redis / Keycloak are available.
- Start `discovery-service` first so other services can register with Eureka.
- Start `config-service` after the config repo is prepared (it will attempt to connect to Git and to Eureka). If Config Server needs Eureka, make sure `discovery-service` is reachable at `http://localhost:8761`.
- Start `api-gateway` next (it depends on discovery/config for routing and security).
- Start domain services (customer, claims, agent) after gateway and config/discovery so they can fetch configuration and register.

Correct startup sequence (local recommended):
1. `docker compose up -d` (infrastructure)
2. `discovery-service` (IDE or `mvn spring-boot:run`)
3. `config-service` (IDE or `mvn spring-boot:run`)
4. `api-gateway` (IDE)
5. `customer-service`, `claims-service`, `agent-service` (IDE)

Shutdown order: reverse of startup — stop business services first, then gateway, then config/discovery, then bring down Docker Compose services.

Example shutdown (PowerShell):

```powershell
# Stop IDE-run services from IntelliJ (stop run configurations)
docker compose down
```

---

## How Config Server works (in this repo)
- `config-service` is a Spring Cloud Config Server (see `config-service/pom.xml`) that reads its configuration from a Git repository (for local development the repository is a file-based Git repo you create from `local-config-repo/`).
- Each microservice requests its configuration from `http://<config-service>:8888/<application>-<profile>` on startup. The `local-config-repo` contains `*.yml` files for each service.
- For local development you set `CONFIG_REPO_URI` to a `file:///` path (see `local-config-repo/README.md`) so `config-service` serves configuration from that local repo.

## How Discovery Server works (in this repo)
- `discovery-service` is a Eureka server (`spring-cloud-starter-netflix-eureka-server`) running on port `8761`. Services with the Eureka client enabled will register on startup; the API Gateway uses Eureka to discover backend services.
- The `discovery-service` exposes the Eureka UI at `http://localhost:8761` (default).

---

## Keycloak setup (local)
- Keycloak is started by Docker Compose and imports the realm file `realm-export.json` into the container (`docker-compose.yml` mounts it into Keycloak import path).
- Local mapping: Keycloak host is mapped to `http://localhost:8180` (compose mapping 8180:8080).

Default local admin credentials (compose):
- user: `admin`
- password: `admin`

Local demo user (from `realm-export.json`):
- username: `demo.customer`
- password: `Password123!`

To verify login:
1. Open the Keycloak admin console: `http://localhost:8180` and log in as `admin`/`admin`.
2. Visit the public client redirect or use the platform UI to perform login flows (example client `claimassist-customer-app` configured to use `http://localhost:3000/*`).

Note: in Kubernetes you will provide secrets and external Keycloak host differently (see Helm/Secrets section below).

---

## PostgreSQL setup (local)
- `docker-compose.yml` creates a Postgres container and uses the `init-db.sql` file to create three databases: `customer_db`, `claims_db`, `agent_db`.
- Default Postgres credentials (compose):
  - user: `claimassist`
  - password: `claimassist`

To connect locally (example):

```powershell
psql -h localhost -U claimassist -d customer_db -W
```

To inspect the outbox table (transactional outbox pattern) used by services that implement outbox:

```sql
SELECT * FROM outbox_events ORDER BY created_at DESC LIMIT 50;
```

Outbox table DDL is present in service migrations (example: `agent-service/target/classes/db/migration/V1__init_agent_schema.sql` contains `CREATE TABLE outbox_events ...`).

---

## MongoDB setup (local)
- MongoDB is started by Docker Compose. Credentials in `docker-compose.yml`:
  - root user: `root`
  - root password: `example`
- Connection string for local development: `mongodb://root:example@localhost:27017`.

If your services use Mongo for any domain data they will obtain the URI from Config Server configuration.

---

## Redis setup (local)
- Redis runs in Docker Compose and is available on `localhost:6379`.
- No password is configured in the compose file.

---

## Kafka setup (local)
- The compose file runs a single-node Kafka KRaft broker on `localhost:9092`.
- Topics used by the app (examples from docs): `claim-update-request-event`, `claim-update-response-event` and other saga-related topics and their DLT equivalents.

Verify a topic exists by starting a container shell for the Kafka container and using tools included in the image, or use a Kafka client (kcat/kafkacat). Example (docker exec):

```powershell
docker exec -it claimassist-kafka bash
# inside container: use kafka tools (path may vary)
kafka-topics.sh --bootstrap-server localhost:9092 --list
```

Producer/consumer troubleshooting: check `outbox_events` table for pending events and inspect `claimassist-kafka` container logs.

---

## Zipkin (distributed tracing)
- Zipkin runs in Docker Compose and is available at `http://localhost:9411`.
- Services are instrumented to report to Zipkin (see `pom.xml` entries for Zipkin reporter and micrometer tracing). Verify traces by sending traffic through the gateway and inspecting Zipkin UI.

---

## Health endpoint verification
All services expose Spring Boot Actuator endpoints. Common endpoints:
- Health: `http://localhost:<port>/actuator/health`
- Readiness/Liveness: `http://localhost:<port>/actuator/health/readiness` and `/actuator/health/liveness` (Kubernetes probes in manifests).
- Prometheus metrics: `http://localhost:<port>/actuator/prometheus`

Ports used by services (local defaults from configs):
- `discovery-service` — 8761
- `config-service` — 8888
- gateway / others — configured per application (see `local-config-repo` files)

Example curl check:

```powershell
curl http://localhost:8888/actuator/health
curl http://localhost:8761/actuator/health
curl http://localhost:8080/actuator/health   # example service
```

---

## Login verification
1. Ensure Keycloak container is up: `http://localhost:8180`.
2. Use `demo.customer` / `Password123!` to test login flows configured in `realm-export.json` or use Keycloak admin UI to view `claimassist-customer-app` client settings.

---

## CQRS verification
The project implements event-driven patterns and transactional outbox. To verify:
1. Create or update a claim via the API Gateway endpoint (send an authenticated request from a test client or Postman).
2. Verify a new row in the `outbox_events` table in the relevant Postgres database. Example query:

```sql
SELECT id, aggregate_id, event_type, status, created_at FROM outbox_events ORDER BY created_at DESC LIMIT 10;
```

3. Verify Kafka topic received the event (consumer or `kafka-console-consumer.sh`).

---

## Saga verification
The repository contains a saga orchestrator and DB table `claim_saga_orchestrations` (see `docs/developer/SAGA.md`). To verify a saga run:
1. Trigger the claim processing flow via the API.
2. Inspect the `claim_saga_orchestrations` table for new entries and status.
3. Inspect related `outbox_events` which contain commands / events for saga steps.

SQL example (adjust DB/schema name):

```sql
SELECT * FROM claim_saga_orchestrations ORDER BY created_at DESC LIMIT 10;
```

---

## Redis verification
1. Connect with `redis-cli` (or `docker exec -it claimassist-redis redis-cli`).
2. Run `PING` — expect `PONG`.

---

## Kafka verification
1. List topics inside the Kafka container:

```powershell
docker exec -it claimassist-kafka bash -c "kafka-topics.sh --bootstrap-server localhost:9092 --list"
```

2. Use a console consumer to read from a topic (inside container):

```bash
kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic claim-update-request-event --from-beginning
```

3. Produce a test message to a topic or use the application to produce events via outbox.

---

## Common local issues & fixes
- Keycloak realm not imported: ensure `realm-export.json` is present and Keycloak container has `--import-realm` command (compose file does this). Check Keycloak logs: `docker compose logs -f keycloak`.
- Config Server cannot read repo: set `CONFIG_REPO_URI` to a valid `file:///` URL and ensure local repo is a git repository with a `main` branch.
- Services fail to register with Eureka: confirm `discovery-service` is running on `http://localhost:8761` and that services point to that Eureka URL in their config.
- Outbox events not being published: check DB connectivity, ensure `outbox_events` contains pending rows, and check application logs for `OutboxPublisher` errors.
- Kafka connection issues: ensure `claimassist-kafka` container is running and listening on 9092. Check advertised listeners in compose (`KAFKA_ADVERTISED_LISTENERS`) — for local development the compose file advertises `localhost:9092`.

---

## Development (DEV) environment — deployment process
This section describes how the repository deploys to the DEV Kubernetes environment using Helm and GitHub Actions. The repository includes Kubernetes manifests under `k8s/` and a Helm chart at `helm/claimassist/`.

Configuration source
- Configuration for each environment is stored as Helm values files under `helm/claimassist/values-<env>.yaml` and in Config Server repositories used by services when running in Kubernetes.

Environment-specific configuration & secrets
- Environment values files: `helm/claimassist/values-dev.yaml`, `values-qa.yaml`, `values-uat.yaml`, `values-prod.yaml`.
- Secrets are expected to be managed in the cluster (e.g., Kubernetes Secrets or an external secret store). The repository does not include production secrets; GitHub Actions uses repository secrets (see `.github/workflows/*` which reference `secrets.KUBE_CONFIG_*` and `secrets.HELM_REPO_URL`).

ConfigMaps
- Charts create ConfigMaps for non-sensitive configuration where necessary; application configuration is expected to come from the Config Server in cluster or mounted via secrets/configmaps depending on your environment.

Helm deployment
- The CI workflows use Helm to upgrade/install releases. Two examples in the repository:
  - `.github/workflows/deploy.yml` — deploys the local chart path `./helm/claimassist` and supports `workflow_dispatch` with `environment` input.
  - `.github/workflows/deploy.yaml` — multi-stage workflow that deploys dev/qa/uat/prod using either the chart in a Helm repo or the local chart depending on build.

Kubernetes deployment & namespace
- The repository pre-defines namespaces in `k8s/namespaces.yaml`:
  - `claimassist-core`
  - `claimassist-processing`

Ingress
- The Helm values files include ingress configuration (`ingress.enabled`, `host`) for each environment. In-cluster ingress controller (NGINX or cloud provider) must be present for ingress objects to work.

HPA
- Horizontal Pod Autoscalers are defined in `k8s/hpa-*.yaml` and the Helm chart templates include autoscaling configuration controlled by values files.

Rolling deployment and zero-downtime
- Helm + Kubernetes deployments are configured with readiness/liveness probes and rolling update strategies so a `helm upgrade` results in rolling updates. The deployment templates include probes on `/actuator/health` and readiness/liveness paths.

CI/CD flow (high-level)
1. Push to `develop` / `main` triggers GitHub Actions workflows under `.github/workflows/*`.
2. Workflows set kubeconfig from secrets and run `helm upgrade --install ... -f ./helm/claimassist/values-<env>.yaml` (see workflow files for exact commands).
3. Workflows wait for rollout status and can perform rollback on failure (some jobs include rollback steps).

Service verification
- After deployment use `kubectl -n claimassist-core get pods` and `kubectl rollout status deployment/<name> -n claimassist-core` to confirm.

Monitoring & logging
- Services expose Prometheus metrics at `/actuator/prometheus` and can be scraped by Prometheus when enabled in the Helm chart.
- Zipkin for traces is supported by services; Prometheus/Grafana are provided by the Helm chart in cluster (not by local Compose).
- Logs are written to stdout/stderr; cluster logging/ELK is not provided in this repository.

Rollback process
- GitHub Actions workflows include rollback jobs or the team can run `helm rollback <release> <revision> -n <namespace>`.

---

## Production environment — deployment & operations notes
This repository provides Helm charts and Kubernetes manifests suitable for production deployment, but infrastructure (cluster, ingress, secrets management, monitoring stack) must be provisioned externally.

Infrastructure overview (what repository provides)
- `helm/claimassist/` — chart containing deployment, service, ingress, HPA templates.
- `k8s/` — YAML manifests for namespaces, HPAs, network policies and service manifests that can be used directly.

Configuration management
- Environment configuration is provided as `helm/claimassist/values-<env>.yaml`.
- Sensitive data (DB passwords, Keycloak admin secrets, OAuth client secrets) must be stored in Kubernetes Secrets or an external vault — not stored in this repository.

Secrets & ConfigMaps
- Do not store production secrets in Git. Use sealed secrets or an external secret manager. Helm templates can reference Kubernetes Secrets created out-of-band.

Helm release & Kubernetes deployment
- Use `helm upgrade --install` with the environment values file and proper secrets injected as Kubernetes Secrets or external providers.

Rolling updates & zero-downtime
- Deployments use readiness/liveness probes and rolling update strategies to avoid downtime. Ensure probes are correct for your environment and that preStop hooks (if any) allow graceful shutdown.

Monitoring & tracing
- Prometheus and Zipkin are supported by the chart. You must provision Prometheus and Grafana in your cluster or enable the chart's bundled monitoring when appropriate.

Logging
- The application logs to stdout; collect logs with your cluster logging stack (EFK/ELK/Cloud). The repository does not contain centralized logging configuration.

Backups & disaster recovery
- The repository does not provide backup tooling. For production you must implement Postgres backups (pg_dump/pgbackrest), persistent volume snapshotting, and MongoDB backups as appropriate.

Rollback strategy
- Use Helm revision rollback and/or restore DB snapshots depending on the nature of the failure. Have playbooks for data migrations and schema rollbacks.

Production verification checklist (minimal)
1. Helm release succeeds and `kubectl get pods -n claimassist-core` shows all pods ready.
2. Health endpoints respond OK.
3. Prometheus scrapes application metrics.
4. Zipkin traces appear for requests.
5. Authentication (Keycloak) is reachable and client logins succeed.

---

## Validation status
- I inspected the repository and documented only implemented features.
- Implemented and present in repo:
  - Docker Compose services: Postgres, Redis, Kafka, Zipkin, Keycloak (+ Postgres), MongoDB (`docker-compose.yml`).
  - Spring Cloud Config Server (`config-service`) and `local-config-repo/` samples.
  - Eureka discovery server (`discovery-service`).
  - Transactional outbox, saga scaffolding and DB migrations (see `agent-service/target/classes/db/migration/*.sql`).
  - Helm chart `helm/claimassist/` and environment values for local/dev/qa/uat/prod.
  - GitHub Actions workflows for deployment (`.github/workflows/deploy.yml` and `.github/workflows/deploy.yaml`).
  - Actuator endpoints and Prometheus metrics in application modules (pom dependencies and k8s probes show actuator endpoints).

- Not implemented in the repository (explicitly):
  - Local `docker-compose.yml` does not run Prometheus or Grafana — those are available via Helm for Kubernetes but are not started by default for local development.
  - Centralized log aggregator (ELK/EFK) and automated backup scripts are not included.

---

If you want, I can additionally:
- Produce a short PowerShell script that automates local startup (compose up, create config repo, set environment variables) and verifies health endpoints.
- Create a PR that adds a `WINDOWS_LOCAL_SETUP.md` with one-click setup steps.

---

End of file.

