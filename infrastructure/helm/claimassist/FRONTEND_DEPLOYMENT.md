Frontend manual deployment instructions (DEV)

Overview
--------
This document describes the minimal manual steps to deploy the claimassist-frontend SPA into the existing claimassist Helm chart in namespace claimassist-dev. It intentionally keeps changes small: add a frontend service via Helm values override and a dev NodePort for external access. No backend Java changes are required.

Prerequisites
-------------
- Helm v3 installed
- Docker (or a container build process) to produce an image pushed to the registry referenced by values.image.registry in the chart (default: claimassistdev)
- kubeconfig: $env:TEMP\claimassist-oci-dev-tailscale.yaml
- Access to the cluster and namespace claimassist-dev

Files added
-----------
- claimassist-frontend/Dockerfile
- claimassist-frontend/nginx.conf
- infrastructure/helm/claimassist/templates/frontend-dev-service.yaml
- infrastructure/helm/claimassist/values-frontend-deploy.yaml

Build & push image
------------------
1. Build the image (replace TAG and set VITE_API_BASE_URL to your DEV Gateway host):

   # Option A: run from claimassist-frontend directory
   pushd claimassist-frontend
   $env:VITE_API_BASE_URL = "http://<DEV-GATEWAY-HOST>:30070"  # example: http://<DEV-GATEWAY-HOST>:30070
   docker build --build-arg VITE_API_BASE_URL="$env:VITE_API_BASE_URL" -t claimassistdev/claimassist-frontend:TAG .
   popd

   # Note: VITE_API_BASE_URL is a build-time variable (Vite embeds env at build). Set it to the DEV Gateway URL so the runtime bundle calls the Gateway.

2. Push to registry:
   docker push claimassistdev/claimassist-frontend:TAG

Prepare Helm values
-------------------
- Edit infrastructure/helm/claimassist/values-frontend-deploy.yaml and replace <REPLACE_WITH_IMAGE_TAG> with your image tag (TAG).

Render & verify template (DRY-RUN)
----------------------------------
# From repository root, render the chart with your local values file (DRY-RUN):
$REPO_ROOT = (Get-Location)
helm template claimassist-dev ./infrastructure/helm/claimassist \
  -n claimassist-dev \
  -f ./infrastructure/helm/claimassist/values-frontend-deploy.yaml \
  --kubeconfig "$env:TEMP\claimassist-oci-dev-tailscale.yaml" > rendered-frontend.yaml

# Inspect rendered-frontend.yaml to confirm only frontend resources are present.

Inspect rendered-frontend.yaml to confirm a Deployment and a NodePort Service claimassist-frontend-dev are present.

Helm upgrade (manual deploy)
----------------------------
# Manual install/upgrade (run only when ready)
helm upgrade --install claimassist-dev ./infrastructure/helm/claimassist \
  -n claimassist-dev \
  -f ./infrastructure/helm/claimassist/values-frontend-deploy.yaml \
  --kubeconfig "$env:TEMP\claimassist-oci-dev-tailscale.yaml"

Verify rollout
--------------
kubectl --kubeconfig "$env:TEMP\claimassist-oci-dev-tailscale.yaml" -n claimassist-dev rollout status deploy/claimassist-frontend
kubectl --kubeconfig "$env:TEMP\claimassist-oci-dev-tailscale.yaml" -n claimassist-dev get pods -l app.kubernetes.io/component=frontend

Test externally
---------------
- Node IP: use the cluster node IP or your DEV MagicDNS and the configured NodePort (devNodePort in values-frontend-deploy.yaml).
  Example (replace <NODE_IP> and <NODE_PORT>): http://<NODE_IP>:<NODE_PORT>/
- The SPA will be served; build the image with VITE_API_BASE_URL set to the DEV Gateway URL (e.g. http://<DEV-GATEWAY-HOST>:30070) so the browser bundle calls the existing API Gateway.

Notes on runtime vs build-time
-----------------------------
- Vite embeds env variables at build time. The Docker image must be built with the correct VITE_API_BASE_URL for the browser bundle to call the Gateway. The Dockerfile supports passing VITE_API_BASE_URL as a build-arg (see above). Alternatively, use a runtime index.html substitution approach (more complex) if you need a single image usable across environments.

Notes & Security
----------------
- The frontend NodePort is DEV-only. NodePort exposes the port on the node; it's suitable for DEV/Tailscale access but not production.
- No secrets are stored in the image or values file. VITE_API_BASE_URL is a public config and safe to set for DEV.
- The container image must not bake any secrets; build should inject only public config (VITE_API_BASE_URL).
- Ensure CORS / Keycloak redirect URIs include the Gateway origin used in DEV (the chart already configures Keycloak externalRedirectUris to the API Gateway NodePort). If you plan to serve the SPA from a different origin (NodePort) and want Keycloak to call the browser directly, you must add that frontend origin to keycloak.externalRedirectUris / externalWebOrigins in the Helm values BEFORE deploying Keycloak. Otherwise use the API Gateway-based flow (recommended) so Keycloak redirects to the Gateway callback URL configured in the chart.

Rollback
--------
helm rollback claimassist-dev <revision> -n claimassist-dev --kubeconfig "$env:TEMP\claimassist-oci-dev-tailscale.yaml"

Contact
-------
If you need assistance performing the manual helm deploy, provide kubeconfig or run the commands above and paste outputs for analysis.

Notes & Security
----------------
- The frontend NodePort is DEV-only. NodePort exposes the port on the node; it's suitable for DEV/Tailscale access but not production.
- No secrets are stored in the image or values file. VITE_API_BASE_URL is a public config and safe to set for DEV.
- Future CI/CD should build the image, push, and perform the same Helm upgrade command.

Rollback
--------
helm rollback claimassist-dev <revision> -n claimassist-dev --kubeconfig "$env:TEMP\claimassist-oci-dev-tailscale.yaml"

Contact
-------
If you need assistance performing the manual helm deploy, provide kubeconfig or run the commands above and paste outputs for analysis.
