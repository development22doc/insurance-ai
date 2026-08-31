# Team Local Observability Setup

Each developer runs ClaimAssist services locally in IntelliJ and ships logs, metrics, and traces to K8s observability via Tailscale.

## Prerequisites

1. **Tailscale** installed and connected to the team network
2. **Java 21** and **Maven** installed
3. Access to the `claimassist-observability` K8s namespace

## Quick Start (5 minutes)

### 1. Find your Tailscale IP

```bash
tailscale ip -4
# Output: 100.x.x.x
```

### 2. Set your developer name (environment variable)

In IntelliJ, go to **Run > Edit Configurations** and add this to each service's environment variables:

```
CLAIMASSIST_DEVELOPER_NAME=yourname
```

Or set it system-wide in your shell profile (`~/.bashrc`, `~/.zshrc`, or Windows System Environment Variables):

```bash
export CLAIMASSIST_DEVELOPER_NAME=yourname
```

### 3. Register in the team developer registry

Edit `infrastructure/monitoring/developers.yml` and add your entry:

```yaml
- targets:
    - "100.x.x.x:8080"
    - "100.x.x.x:8081"
    - "100.x.x.x:8082"
    - "100.x.x.x:8083"
  labels:
    developer: yourname
    environment: local
    source: intellij
    cluster: claimassist-local
```

### 4. Apply to Kubernetes

```bash
kubectl -n claimassist-observability apply -f infrastructure/monitoring/developers.yml
```

### 5. Run services in IntelliJ

Start any service with the `local` Spring profile (default for your local run configs). The `server.address=0.0.0.0` setting ensures Tailscale can reach your services.

### 6. Verify

**Prometheus** (scraping):
- Open http://100.114.133.69:30900/targets
- Look for your developer name in the `intellij-local-services` job targets
- All 4 services should show `UP`

**Grafana** (dashboards):
- Open http://100.114.133.69:30300 (admin/admin)
- Go to **ClaimAssist Platform Overview (K8s)**
- Use the **Developer** dropdown to filter to your name
- Use the **Service** dropdown to filter to a specific service

**Loki** (logs):
- In the same Grafana dashboard, the **Service Logs** panel filters by `developer=~"$developer"`
- Or go to **Explore > Loki** and query:
  ```
  {environment="local", developer="yourname", service="api-gateway"}
  ```

**Zipkin** (traces):
- Open http://100.114.133.69:30941
- Traces are labeled with your developer name

## Architecture

```
Developer Laptop (Windows)
  ┌─────────────────────────────────────────────┐
  │ IntelliJ (local profile)                    │
  │  api-gateway:8080  customer-svc:8081        │
  │  claims-svc:8082    agent-svc:8083          │
  │                                              │
  │  Metrics → Prometheus (Tailscale)            │
  │  Logs    → Loki (Tailscale)                  │
  │  Traces  → Zipkin (Tailscale)                │
  └─────────────────────────────────────────────┘
              │ Tailscale VPN
              ▼
  K8s Node (100.114.133.69)
  ┌─────────────────────────────────────────────┐
  │ Prometheus :30900 (file_sd_configs)          │
  │ Loki       :30310 (HTTP push)               │
  │ Zipkin     :30941 (HTTP POST)               │
  │ Grafana    :30300 (dashboards)               │
  └─────────────────────────────────────────────┘
```

## How It Works

| Signal  | Protocol | Path on K8s | How services reach it |
|---------|----------|-------------|----------------------|
| Metrics | Prometheus scrape | `:30900` | Prometheus uses `file_sd_configs` to read `developer-registry.yml` from a ConfigMap. Each entry has a `developer` label. |
| Logs    | HTTP push to Loki | `:30310/loki/api/v1/push` | Logback `LokiHttpAppender` ships JSON logs with `developer` label from `CLAIMASSIST_DEVELOPER_NAME` env var. |
| Traces  | HTTP POST to Zipkin | `:30941/api/v2/spans` | Spring Cloud Sleuth sends spans to the K8s Zipkin instance. |

## Updating Your Entry

If your Tailscale IP changes (e.g., reconnecting to VPN):

1. Get new IP: `tailscale ip -4`
2. Edit `infrastructure/monitoring/developers.yml`
3. Apply: `kubectl -n claimassist-observability apply -f infrastructure/monitoring/developers.yml`
4. Prometheus auto-reloads within 30 seconds

## Disabling Without Removing

Set `enabled: false` in your entry to stop Prometheus scraping without removing your config:

```yaml
- targets:
    - "100.x.x.x:8080"
    # ...
  labels:
    developer: yourname
    environment: local
    source: intellij
    cluster: claimassist-local
    enabled: "false"
```

Note: You'll need to add an `action: drop` relabel_config in the Prometheus scrape job to honor this label, or simply comment out your targets.

## Troubleshooting

### Services not appearing in Prometheus targets

1. Verify Tailscale is connected: `tailscale status`
2. Check your IP is correct in `developers.yml`
3. Verify services bind to `0.0.0.0` (not `127.0.0.1`): check `application-local.yaml` has `server.address: 0.0.0.0`
4. Check Prometheus targets page: http://100.114.133.69:30900/targets

### Logs not appearing in Loki

1. Verify `CLAIMASSIST_DEVELOPER_NAME` env var is set
2. Check Loki is reachable: `curl http://100.114.133.69:30310/ready`
3. Check Grafana Explore for your developer label

### Traces not appearing in Zipkin

1. Verify `ZIPKIN_ENDPOINT` is set to `http://100.114.133.69:30941/api/v2/spans`
2. Check Zipkin UI: http://100.114.133.69:30941

## Files Changed for Multi-Developer Support

| File | Purpose |
|------|---------|
| `infrastructure/monitoring/developers.yml` | Team registry (Git-tracked) |
| `infrastructure/kubernetes/observability/prometheus.yaml` | K8s Prometheus with `file_sd_configs` |
| `infrastructure/kubernetes/observability/grafana.yaml` | K8s Grafana dashboards with developer/service filters |
| `logback-spring.xml` | Root Logback config with Loki appender |
| `common-lib/src/main/resources/logback-spring.xml` | Shared Logback config with Loki appender |
| `{service}/src/main/resources/application-local.yaml` | Per-service local config (server.address, developer tag) |
| `infrastructure/monitoring/grafana/provisioning/dashboards/claimassist-overview.json` | Docker Compose dashboard |
