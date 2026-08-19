# ClaimAssist — Access Control: OCI IAM + Kubernetes RBAC (Task 6A-14)

DESIGN + REPOSITORY-SIDE RBAC FOUNDATION ONLY. Nothing here provisions OCI
resource, creates K3s, creates real users, pushes images, or deploys. Real
identities, policies, kubeconfigs, SSH keys and CI/CD are later tasks.

The user is the **only** infrastructure/configuration administrator.

---

## 1. RBAC audit (current, after 6A-14)

| Area | Before (post-6A-13) | After 6A-14 |
|------|---------------------|-------------|
| App ServiceAccounts | created, no bindings, token default-mounted | token **not mounted** (`automountServiceAccountToken: false`), no Roles/RoleBindings, no cluster-admin |
| Admin role | inert ClusterRole `*/*/*` (adminRbac.enabled=false) | kept, justified for single owner; **never** bound to an app SA |
| Team read-only | `claimassist-team-read` Role with a **`secrets: [list]`** grant | **security gap removed** — no Secrets; only get/list/watch non-secret resources |
| Bindings | none | templates present, all **inert** (default false) |
| Secrets in chart | `secrets.create:false`, placeholders only | unchanged; no credentials anywhere |

## 2. Kubernetes RBAC design

Conceptual identities (no real user bound yet):
- **A. infrastructure-admin** (ClusterRole `claimassist-admin`, full `*/*/*`).
  Justified for the single owner on a learning-tier K3s node (needs cross-namespace
  RBAC, PVCs, helm hooks, ingress/observability controllers). Bound ONLY to the
  owner (inert `adminBinding`, default false). Never a ServiceAccount.
- **B. team-readonly** (Role `claimassist-team-read`, namespace-scoped). Grants
  only `get/list/watch` on pods (+log/status), services, configmaps, endpoints,
  PVCs, deployments, statefulsets, replicasets, jobs, cronjobs, ingresses, HPA,
  PDB, events, metrics.k8s.io/pods. **No Secrets. No create/update/patch/delete.**
- **C. application ServiceAccounts** — one per app, `automountServiceAccountToken:
  false`, no permissions, no cluster-admin.

## 3. Namespace scope

Respect the locked environments: `claimassist-dev`, `claimassist-stage`,
`claimassist-prod`. Team read-only is namespace-scoped via per-environment
`RoleBinding` (role in the same namespace). No cluster-wide team permissions.
PROD is the strongest: team-read binding disabled by default and only ever
read-only/no-secrets.

## 4. ServiceAccount security

- No application needs the Kubernetes API at runtime (the fabric8 k8s-client in
  claims-service's build is unused — verified by search: no imports/usages).
- All four SAs: `automountServiceAccountToken: false`, no Roles/RoleBindings,
  no cluster-admin. Namespace-scoped (pod identity never crosses environments).

## 5. Secret protection

- Verify no real credentials committed: Helm values are placeholders/empty only;
  `secrets.create=false`; PostgreSQL/Keycloak/Redis/Kafka credentials are all
  empty in the chart, injected at deploy time. No DB/Keycloak/OCI/registry creds
  in the repo. No fake production passwords.

## 6. OCI IAM design

Project uses OCI Always Free A1 VM + K3s + OCI Container Registry (NOT OKE).

- **OWNER/ADMIN** (one identity): manage compute, VCN/networking, Container Registry,
  DNS, object storage/backups; manage IAM only where appropriate.
- **TEAM**: read-only where required; **no** infrastructure modification, **no** IAM
  policy change, **no** network change, **no** compute deletion, **no** registry
  deletion, **no** production-infrastructure write.
- Least privilege; prefer **compartment-scoped** policies. No tenancy-wide manage.
- Real users/policies are NOT created in this task; document with placeholders.

Example (compartment-scoped, placeholder tenancy `ocid1.tenancy..<T>`):
```
# Owner, claimassist-infra compartment
allow group CLAIMASSIST_ADMINS to manage all-resources in compartment claimassist-infra
# Team read-only at the same compartment
allow group CLAIMASSIST_TEAM to read all-resources in compartment claimassist-infra
# (no infra write / IAM / network-scoped manage for team)
```

## 7. OCI compartment design

```
claimassist
   └── claimassist-infra   (the A1 VM + K3s hosts + registry group)
```
Single compartment keeps it simple for free-tier and scopes access to the project.

## 8. OCI registry access model

- **OWNER**: manage repositories/images.
- **CI/CD**: dedicated service identity/token that can `push`/`pull` (workload, no
  management) — configured in Task 6A-15.
- **TEAM**: read-only if needed.
- No registry credentials in Git; none created in this task.

## 9. OCI SSH access model

- **OWNER**: SSH admin access to the A1 VM.
- **TEAM**: no SSH by default.
- No keys created or committed; VM not configured (Task 6A-14B).

## 10. Kubernetes admin access model

- **OWNER** → kubeconfig/admin credential → K3s (authenticated administrator).
- **TEAM** → restricted kubeconfig/context → read-only RoleBindings.
- No kubeconfigs created; Kubernetes API **not** exposed publicly; K3s not configured.

## 11. Helm ownership

- **Git is source of truth**; **Helm release is deployment state**.
- Flow: Git → GitHub Actions → Helm → Kubernetes.
- Developers do not manually modify production Helm releases; any manual emergency
  change must be **reconciled back to Git**.
- CI/CD not implemented in this task.

## 12. Admin vs Team matrix

| Resource | ADMIN | TEAM |
|----------|-------|------|
| Git repository | RW/Admin | Feature/PR |
| Protected branches | RW/Admin | NO |
| GitHub Actions | RW/Admin | NO |
| GitHub Secrets | RW/Admin | NO |
| OCI infrastructure | RW/Admin | Read-only |
| OCI IAM | Admin | NO |
| OCI Registry | Manage | Read-only |
| K8s namespaces | Manage | Read |
| K8s deployments | Manage | Read |
| K8s services | Manage | Read |
| K8s ConfigMaps | Manage | Read |
| K8s Secrets | Manage | NO |
| K8s RBAC | Manage | NO |
| K8s StatefulSets | Manage | Read |
| K8s PVCs | Manage | Read |
| Production deployment | Approve | NO |
| Helm releases | Manage | Read/observe |
| SSH to OCI VM | Admin | NO |

## 13. Audit model

Supports WHO / WHAT / WHEN / WHERE / RESULT for:
- OCI administrative actions → OCI Audit service (preserved as part of the final
  architecture).
- GitHub changes → GitHub audit log.
- Deployments / configuration changes / production approvals → GitHub Actions +
  environment approvals (Task 6A-15) + Observability (Task 6A-13) logs.
- Kubernetes actions → the chart enforces RBAC; **API-server audit logging is NOT
  enabled** (K3s not yet configured) and is documented as a 6A-14B/bootstrap item,
  NOT claimed as active.

## 14. Production protection

PROD has the strongest controls: no team direct write, no production Secret read,
no production RBAC write, no direct production deployment, no OCI infra write.
Production deployment will be CI → required checks → ADMIN approval → GitHub
Actions → Kubernetes (Task 6A-15). This task establishes only the RBAC/design
foundation.

## 15. FREE-tier assessment

Access control is free: OCI IAM + K3s RBAC + GitHub repo permissions +
GitHub Environments + GitHub Actions. No paid IAM/Kubernetes-management/OKE.

## 16. Local safety

docker-compose, `.env`, config-repo, Local Eureka/Config Server, local app config
are UNCHANGED. RBAC changes affect only the Helm/K8s chart (not used locally).