# ClaimAssist DEV Onboarding — Tailscale VPN

> **This is the ONLY supported team development architecture.** SSH tunnels are
> deprecated. All local development connects through Tailscale VPN to the shared
> OCI K3s infrastructure via NodePorts.

## Prerequisites

- Windows 10/11 with PowerShell 5.1+
- [Tailscale](https://tailscale.com/download) installed and signed in
- IntelliJ IDEA (Community or Ultimate)
- Java 21+ JDK
- Maven
- kubectl (optional, for K8s debugging)

## Step 1: Join the Tailscale Network

1. Install Tailscale from https://tailscale.com/download
2. Sign in with the team account (ask admin for invite link)
3. Verify connectivity to the DEV node:
   ```powershell
   Test-NetConnection -ComputerName vnic.taild219f3.ts.net -Port 30432
   ```
   Expected: `TcpTestSucceeded: True`

## Step 2: Get the Kubeconfig

Ask the admin for the kubeconfig file (preferred), or generate it as a one-time setup:
```powershell
# ONE-TIME ONLY: Copy kubeconfig from the K3s server.
# This requires SSH access — ask your admin to provide the file directly if SSH is not available.
scp opc@vnic.taild219f3.ts.net:/etc/rancher/k3s/k3s.yaml ~/.kube/claimassist-oci-dev.yaml
# Edit the file: replace 127.0.0.1 with vnic.taild219f3.ts.net:6443
```

Save to: `$env:USERPROFILE\.kube\claimassist-oci-dev.yaml`

> **Note:** SSH is only used for this one-time kubeconfig copy. All ongoing development
> uses Tailscale VPN exclusively — no SSH tunnels are required.

## Step 3: Verify Cluster Access

```powershell
kubectl get pods -n claimassist-dev --kubeconfig="$env:USERPROFILE\.kube\claimassist-oci-dev.yaml"
```

Expected: 4 app pods (api-gateway, customer-service, claims-service, agent-service) + infra pods Running.

## Step 4: Access DEV Services

All services are accessible via Tailscale at `vnic.taild219f3.ts.net`:

| Service         | Tailscale URL                                           | Port |
|-----------------|--------------------------------------------------------|------|
| PostgreSQL      | `vnic.taild219f3.ts.net:30432`                         | 30432|
| Redis           | `vnic.taild219f3.ts.net:30379`                         | 30379|
| Keycloak        | `http://vnic.taild219f3.ts.net:30080`                  | 30080|
| Kafka           | `vnic.taild219f3.ts.net:30092`                         | 30092|
| API Gateway     | (via kubectl port-forward or Ingress)                  | 8080 |

### Database Access (DBeaver / psql)

```
Host: vnic.taild219f3.ts.net
Port: 30432
Database: claimassist_claims (or claimassist_customer / claimassist_agent)
User: claimassist
Password: (ask admin)
```

### Keycloak Admin Console

```
URL: http://vnic.taild219f3.ts.net:30080/admin
Realm: claimassist-dev
Admin: admin / (ask admin)
```

### Kafka (Console / kafka-ui)

Kafka is available at `vnic.taild219f3.ts.net:30092`. Use Kafka UI via port-forward:
```powershell
kubectl port-forward svc/kafka-ui -n claimassist-observability 8080:8080 --kubeconfig="$env:USERPROFILE\.kube\claimassist-oci-dev.yaml"
# Then open http://localhost:8080
```

## Step 5: IntelliJ Run Configurations

The repository includes pre-configured IntelliJ run configurations in `.run/`. After cloning:

1. Open the project in IntelliJ IDEA
2. IntelliJ will detect the `.run/*.run.xml` files automatically
3. Go to **Run > Edit Configurations** and verify you see:
   - `Claims Service (local-k8s)` — port 8082
   - `Customer Service (local-k8s)` — port 8081
   - `Agent Service (local-k8s)` — port 8083
   - `API Gateway (local-k8s)` — port 8080

4. **Set the PostgreSQL password** (required — not included in run configs for security):
   - Go to **Run > Edit Configurations > Claims Service (local-k8s) > Environment variables**
   - Add: `POSTGRES_PASSWORD=<ask admin for password>`
   - Repeat for Customer Service and Agent Service

5. **Update `CLAIMASSIST_DEV_ID`** in each run config to your identifier (e.g., your initials)
   - This isolates your Kafka consumer groups from other developers

6. Run any service with the green play button

All services connect through Tailscale NodePorts — no SSH tunnels needed.

### Alternative: Script-based startup

```powershell
.\scripts\start-services-local.ps1 -Profile local-k8s
```

This loads `.env.local-k3s` and starts all four services via Maven.

## Step 6: Run the Validation Script

```powershell
.\scripts\validate-dev-infra.ps1
```

Expected: 47/47 PASS.

## Troubleshooting

### Pod not starting
```powershell
kubectl describe pod <pod-name> -n claimassist-dev --kubeconfig="$env:USERPROFILE\.kube\claimassist-oci-dev.yaml"
kubectl logs <pod-name> -n claimassist-dev --kubeconfig="$env:USERPROFILE\.kube\claimassist-oci-dev.yaml"
```

### Eureka/Config Server references in logs
This should NOT happen. If it does, check that the latest Helm chart is deployed:
```powershell
helm list -n claimassist-dev --kubeconfig="$env:USERPROFILE\.kube\claimassist-oci-dev.yaml"
```

### Tailscale connectivity issues
```powershell
tailscale status
Test-NetConnection -ComputerName vnic.taild219f3.ts.net -Port 30432
```

## Key Commands

```powershell
# Set kubeconfig for the session
$env:KUBECONFIG="$env:USERPROFILE\.kube\claimassist-oci-dev.yaml"

# Quick pod status
kubectl get pods -n claimassist-dev

# Check service health
kubectl exec deploy/claimassist-api-gateway -n claimassist-dev -- wget -qO- http://localhost:8080/actuator/health

# View logs
kubectl logs deploy/claimassist-claims-service -n claimassist-dev --tail=100

# Helm upgrade (after chart changes)
helm upgrade claimassist-dev infrastructure/helm/claimassist -f infrastructure/helm/claimassist/values-dev.yaml --namespace claimassist-dev --set postgresql.credentials.postgresUsername=claimassist --set postgresql.credentials.postgresPassword=<password> --set keycloak.credentials.adminClientSecret=<secret> --set keycloak.postgresql.credentials.postgresPassword=<password> --set image.tag=<tag> --set image.registry=claimassistdev --kubeconfig="$env:USERPROFILE\.kube\claimassist-oci-dev.yaml" --force-conflicts
```
