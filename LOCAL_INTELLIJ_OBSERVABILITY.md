# ClaimAssist IntelliJ Local Observability & Debugging Environment

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           DEVELOPER MACHINE (Windows)                            │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐        │
│  │ API Gateway  │  │ Customer Svc │  │ Claims Svc   │  │ Agent Svc    │        │
│  │    :8080     │  │    :8081     │  │    :8082     │  │    :8083     │        │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘        │
│         │                 │                 │                 │                 │
│         │  /actuator/prometheus              │                 │                 │
│         │  /actuator/health                  │                 │                 │
│         │  Structured JSON logs              │                 │                 │
│         │  Zipkin traces                     │                 │                 │
│         └──────────┬─────────────────────────┴─────────────────┘                 │
│                    ▼                                                             │
│         ┌─────────────────────┐                                                  │
│         │   GRAFANA ALLOY     │                                                  │
│         │  (Local Collector)  │                                                  │
│         └──────────┬──────────┘                                                  │
│                    │                                                              │
│         ┌──────────┴──────────┐                                                  │
│         ▼                     ▼                                                  │
│  ┌─────────────┐      ┌─────────────┐                                           │
│  │  Prometheus │      │    Loki     │                                           │
│  │  (Metrics)  │      │   (Logs)    │                                           │
│  └──────┬──────┘      └──────┬──────┘                                           │
│         │                    │                                                   │
│         └──────────┬─────────┘                                                   │
│                    ▼                                                              │
│         ┌─────────────────────┐                                                  │
│         │   SSH TUNNELS       │                                                  │
│         │  (oci-k8s-connect)  │                                                  │
│         └──────────┬──────────┘                                                  │
└─────────────────────┼────────────────────────────────────────────────────────────┘
                      ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              OCI K3s (claimassist-observability)                │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐        │
│  │ Prometheus   │  │    Loki      │  │   Zipkin     │  │   Grafana    │        │
│  │  :9090       │  │   :3100      │  │   :9411      │  │   :3000      │        │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘        │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘

Infrastructure Dependencies (via SSH tunnels):
- PostgreSQL: localhost:15432 → claimassist-postgresql:5432
- Redis:      localhost:16379 → claimassist-redis:6379
- Kafka:      localhost:9092  → claimassist-kafka:9092
- Keycloak:   localhost:18080 → claimassist-keycloak:8080
```

## Files Changed

### Application Configuration (Local Profile Only)
- `api-gateway/src/main/resources/application-local.yaml` - Updated ports, added tags
- `customer-service/src/main/resources/application-local.yaml` - Updated ports, added management config
- `claims-service/src/main/resources/application-local.yaml` - Updated ports, added management config
- `agent-service/src/main/resources/application-local.yaml` - Updated ports, added management config
- `config-repo/api-gateway.yml` - Added environment/source tags to Prometheus metrics

### Observability Infrastructure
- `infrastructure/monitoring/alloy/config.alloy` - **NEW** Grafana Alloy configuration
- `infrastructure/kubernetes/observability/grafana.yaml` - Added Zipkin datasource
- `infrastructure/monitoring/grafana/provisioning/datasources/datasources.yml` - Added Zipkin datasource

### Local Development Scripts
- `scripts/start-local-observability.ps1` - **NEW** Start Grafana Alloy collector
- `scripts/stop-local-observability.ps1` - **NEW** Stop Grafana Alloy collector
- `scripts/status-local-observability.ps1` - **NEW** Check observability status

### Local Logging
- `logback-local.xml` - **NEW** Local file logging config for Alloy collection

### IntelliJ Run Configurations
- `.idea/runConfigurations/API_Gateway_Local.xml`
- `.idea/runConfigurations/Customer_Service_Local.xml`
- `.idea/runConfigurations/Claims_Service_Local.xml`
- `.idea/runConfigurations/Agent_Service_Local.xml`
- `.idea/runConfigurations/Discovery_Service_Local.xml`
- `.idea/runConfigurations/Config_Service_Local.xml`

## Files NOT Changed (CI/CD Protection)

✅ **GitHub Actions Workflows** - No modifications
- `.github/workflows/ci.yml` - Unchanged
- `.github/workflows/cd.yml` - Unchanged
- `.github/workflows/docker-build.yaml` - Unchanged
- `.github/workflows/rollback.yml` - Unchanged

✅ **Helm Charts** - No modifications
- `infrastructure/helm/claimassist/` - All values.yaml and templates unchanged

✅ **Kubernetes Application Deployments** - No modifications
- Deployment manifests unchanged
- Service configurations unchanged
- No image tag changes

✅ **Production/Stage Configurations** - No modifications
- `infrastructure/helm/claimassist/values-prod.yaml` - Unchanged
- `infrastructure/helm/claimassist/values-stage.yaml` - Unchanged

## Quick Start Commands

### 1. Establish OCI SSH Tunnels (Required First)
```powershell
# Run as Administrator for hosts file management
Start-Process powershell -Verb RunAs -ArgumentList '-File .\scripts\oci-k8s-connect.ps1'
```

### 2. Start Local Observability Collector (Grafana Alloy)
```powershell
# In a separate terminal
.\scripts\start-local-observability.ps1
```

### 3. Start Local JVM Services (IntelliJ)
Either use IntelliJ run configurations (created above) or:
```powershell
# Option A: Using Maven (requires tunnels from step 1)
.\scripts\start-services-local.ps1 -Profile local

# Option B: IntelliJ Run Configurations
# Run: API Gateway (Local), Customer Service (Local), Claims Service (Local), Agent Service (Local)
# In order: Discovery → Config → Customer → Claims → Agent → API Gateway
```

### 4. Verify Everything Works
```powershell
# Check observability status
.\scripts\status-local-observability.ps1

# Check service health
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health

# Check metrics
curl http://localhost:8080/actuator/prometheus
curl http://localhost:8081/actuator/prometheus
curl http://localhost:8082/actuator/prometheus
curl http://localhost:8083/actuator/prometheus
```

### 5. Access Observability UIs
| Service | Local URL | Via Tunnel |
|---------|-----------|------------|
| Grafana | http://localhost:13000 | ✅ |
| Prometheus | http://localhost:19090 | ✅ |
| Zipkin | http://localhost:19411 | ✅ |
| Loki | http://localhost:13100 | ✅ |
| Kafka UI | http://localhost:18081 | ✅ |
| Keycloak | http://localhost:18080 | ✅ |

### 6. Stop Everything
```powershell
# Stop local services (Ctrl+C in start-services-local terminal)
# Stop observability collector
.\scripts\stop-local-observability.ps1

# Disconnect tunnels
.\scripts\oci-k8s-disconnect.ps1
```

## Key Configuration Details

### OCI Tunnel Ports (Mandatory)
| Service | Local Port | OCI Service |
|---------|------------|-------------|
| PostgreSQL | 15432 | claimassist-postgresql:5432 |
| Redis | 16379 | claimassist-redis:6379 |
| Kafka | 9092 | claimassist-kafka:9092 |
| Keycloak | 18080 | claimassist-keycloak:8080 |
| Kafka UI | 18081 | kafka-ui:8080 |
| Grafana | 13000 | grafana-service:3000 |
| Prometheus | 19090 | prometheus-service:9090 |
| Zipkin | 19411 | zipkin-service:9411 |
| Loki | 13100 | loki-service:3100 |
| K3s API | 16443 | k3s API:6443 |

### Local Service Ports
| Service | Port |
|---------|------|
| API Gateway | 8080 |
| Customer Service | 8081 |
| Claims Service | 8082 |
| Agent Service | 8083 |
| Discovery Service | 8761 |
| Config Service | 8888 |

### Prometheus Metric Labels (Low Cardinality)
All local metrics include:
- `environment=local`
- `source=intellij`
- `service=<service-name>`
- `application=<spring.application.name>`
- `host=dev-machine`
- `cluster=claimassist-dev`

**NOT included** (high cardinality - stay in logs/traces):
- `requestId`, `correlationId`, `traceId`, `spanId`
- `userId`, `claimId`, `customerId`

### Log Collection
- Services write JSON logs to `logs/<service-name>/*.log`
- Logback config: `logback-local.xml` (passed via `-Dlogging.config=logback-local.xml`)
- Alloy collects from `logs/*/*.log` and forwards to Loki
- Structured fields: `timestamp`, `level`, `logger`, `message`, `traceId`, `spanId`, `correlationId`, `requestId`

### Tracing
- Micrometer Tracing with 100% sampling (`tracing.sampling.probability=1.0`)
- Zipkin endpoint: `http://localhost:19411/api/v2/spans`
- Trace propagation via common-lib: `CorrelationIdFilter`, `RestTemplateCorrelationInterceptor`, `FeignCorrelationRequestInterceptor`, `WebClientCorrelationFilter`

## IntelliJ Debugging Workflow

1. **Set Breakpoint** in any service code
2. **Start Debug Session** using IntelliJ run configurations
3. **Make Request** via API Gateway (http://localhost:8080)
4. **Inspect in IntelliJ**: Variables, call stack, thread state
5. **Correlate in Grafana**:
   - Find trace in Zipkin (Grafana → Explore → Zipkin)
   - Jump to logs (Grafana → Explore → Loki, filter by traceId)
   - Check metrics (Grafana → Explore → Prometheus, filter by service/environment)
   - View Kafka messages (Kafka UI → http://localhost:18081)
   - Inspect Redis (Redis CLI → localhost:16379)
   - Verify PostgreSQL (psql → localhost:15432)

## Verification Checklist

- [ ] OCI tunnels established (all 10 ports)
- [ ] Grafana Alloy running (PID, health endpoint OK)
- [ ] All 4 services healthy (/actuator/health = UP)
- [ ] Metrics scraping (Alloy → Prometheus)
- [ ] Logs flowing (Alloy → Loki)
- [ ] Traces flowing (Services → Zipkin)
- [ ] Grafana dashboards show local data
- [ ] End-to-end request produces trace + logs + metrics
- [ ] CI/CD workflows unchanged (verified via git diff)
- [ ] No Helm/application deployment changes

## Rollback Procedure

```powershell
# 1. Stop local services and observability
.\scripts\stop-local-observability.ps1
.\scripts\oci-k8s-disconnect.ps1

# 2. Restore configuration files (if needed)
git -C D:\Mayur\claimsassist\insurance-ai-platform checkout -- \
  api-gateway/src/main/resources/application-local.yaml \
  customer-service/src/main/resources/application-local.yaml \
  claims-service/src/main/resources/application-local.yaml \
  agent-service/src/main/resources/application-local.yaml \
  config-repo/api-gateway.yml \
  infrastructure/kubernetes/observability/grafana.yaml \
  infrastructure/monitoring/grafana/provisioning/datasources/datasources.yml

# 3. Remove new files (if desired)
Remove-Item .idea/runConfigurations/*_Local.xml
Remove-Item logback-local.xml
Remove-Item scripts/start-local-observability.ps1
Remove-Item scripts/stop-local-observability.ps1
Remove-Item scripts/status-local-observability.ps1
Remove-Item -Recurse infrastructure/monitoring/alloy/
```

## Requirements

- **Grafana Alloy** on Windows: https://grafana.com/docs/alloy/latest/set-up/install/windows/
- **Java 21** (JAVA_HOME set)
- **Maven 3.9+** (for command-line startup)
- **kubectl** in PATH
- **SSH access** to OCI VM (key at `~/.ssh/claimassist-oci-dev` or `~/.ssh/github-actions-oci-dev`)
- **Administrator privileges** for hosts file modification (Kafka hostname)

## Notes

- This setup uses the existing `local` Spring profile (not `dev-local`, `intellij-dev`, etc.)
- Kubernetes logging remains stdout/stderr only (no file logging in pods)
- Local file logging is ONLY for IntelliJ development via `logback-local.xml`
- Alloy runs as a Windows process, not in Kubernetes
- No additional Prometheus/Grafana/Loki/Zipkin instances created locally
- CI/CD pipeline remains completely unchanged
