#!/bin/bash
# =============================================================================
# ClaimAssist - Deployed Image State Resolver
# =============================================================================
# Resolves the CURRENTLY DEPLOYED image tags for all four application services
# from the AUTHORITATIVE Kubernetes runtime state (Deployments), NOT from Helm
# user-supplied values.
#
# Usage:
#   resolve-deployed-images.sh <namespace> <registry> [--output-format=plain|json]
#
# Outputs (plain mode, default):
#   Space-separated list of image tags in the order:
#   api-gateway customer-service claims-service agent-service
#
# Outputs (json mode):
#   JSON object with service names as keys and SHA tags as values.
#
# Exit codes:
#   0: All four images resolved successfully
#   1: Error (missing Deployment, invalid image, etc.)
#
# Design principles:
#   1. Kubernetes Deployment state is the authoritative source of truth
#   2. Helm values are NEVER used for image resolution (they may be incomplete)
#   3. Images are validated against expected format: <registry>/<service>:<SHA>
#   4. Failed Helm revisions cannot corrupt state resolution
#   5. First-ever bootstrap and broken states fail explicitly with diagnostics
# =============================================================================

set -euo pipefail

NAMESPACE="${1:?Usage: resolve-deployed-images.sh <namespace> <registry> [--output-format=plain|json]}"
REGISTRY="${2:?Usage: resolve-deployed-images.sh <namespace> <registry> [--output-format=plain|json]}"
OUTPUT_FORMAT="${3:---output-format=plain}"

# Strip the --output-format= prefix if present
OUTPUT_FORMAT="${OUTPUT_FORMAT#--output-format=}"

EXPECTED_SERVICES=("api-gateway" "customer-service" "claims-service" "agent-service")

# Validate inputs
if [ -z "$REGISTRY" ]; then
  echo "::error::Registry parameter is required."
  exit 1
fi

if [ -z "$NAMESPACE" ]; then
  echo "::error::Namespace parameter is required."
  exit 1
fi

if [ "$OUTPUT_FORMAT" != "plain" ] && [ "$OUTPUT_FORMAT" != "json" ]; then
  echo "::error::Invalid output format: $OUTPUT_FORMAT (expected: plain or json)"
  exit 1
fi

echo "Resolving deployed images for namespace: $NAMESPACE"
echo "Expected registry: $REGISTRY"
echo "Output format: $OUTPUT_FORMAT"

# ---------------------------------------------------------------------------
# Validation functions
# ---------------------------------------------------------------------------

# Validate SHA format: 40-character hexadecimal (full Git SHA)
validate_sha() {
  local sha="$1"
  if [[ ! "$sha" =~ ^[a-fA-F0-9]{40}$ ]]; then
    echo "::error::Invalid SHA format: $sha (expected 40-character hexadecimal commit SHA)"
    return 1
  fi
  return 0
}

# Validate the complete image reference against expected structure
# Expected: <registry>/<service>:<SHA>
validate_image() {
  local image="$1"
  local expected_service="$2"
  local expected_registry="$3"

  # Extract registry (everything before first /)
  local image_registry="${image%%/*}"
  if [ "$image_registry" = "$image" ]; then
    echo "::error::Image missing registry: $image (expected format: ${expected_registry}/${expected_service}:<SHA>)"
    return 1
  fi

  # Extract service:tag part (everything after first /)
  local service_tag="${image#*/}"

  # Extract service name (everything before :)
  local image_service="${service_tag%%:*}"
  if [ "$image_service" = "$service_tag" ]; then
    echo "::error::Image missing tag: $image (expected format: ${expected_registry}/${expected_service}:<SHA>)"
    return 1
  fi

  # Extract tag (everything after last :)
  local image_tag="${service_tag##*:}"

  # Validate registry matches expected
  if [ "$image_registry" != "$expected_registry" ]; then
    echo "::error::Image registry mismatch for $expected_service: got '$image_registry', expected '$expected_registry'"
    return 1
  fi

  # Validate service name matches expected
  if [ "$image_service" != "$expected_service" ]; then
    echo "::error::Image service name mismatch: got '$image_service', expected '$expected_service'"
    return 1
  fi

  # Validate tag is not empty
  if [ -z "$image_tag" ]; then
    echo "::error::Image tag is empty for $expected_service (expected immutable SHA)"
    return 1
  fi

  # Reject known mutable/invalid tags
  case "$image_tag" in
    latest|1.0.0|dev|staging|prod)
      echo "::error::Image tag is not an immutable SHA for $expected_service: '$image_tag'"
      echo "::error::Expected a 40-character commit SHA. Mutable tags like 'latest', '1.0.0', 'dev' are rejected."
      return 1
      ;;
  esac

  # Validate SHA format (must be 40-char hex)
  if ! validate_sha "$image_tag"; then
    echo "::error::Image tag for $expected_service is not a valid commit SHA: '$image_tag'"
    return 1
  fi

  return 0
}

# ---------------------------------------------------------------------------
# Resolve deployed images from Kubernetes Deployments
# ---------------------------------------------------------------------------

RESOLVED_TAGS=()
RESOLVED_IMAGES=()
ERRORS=()

for service in "${EXPECTED_SERVICES[@]}"; do
  deployment_name="claimassist-${service}"

  echo ""
  echo "--- Resolving image for $service (deployment: $deployment_name) ---"

  # Check if Deployment exists
  if ! kubectl get deployment "$deployment_name" -n "$NAMESPACE" &>/dev/null; then
    ERRORS+=("$service: Deployment '$deployment_name' does not exist in namespace '$NAMESPACE'.")
    echo "::error::Deployment $deployment_name does not exist in namespace $NAMESPACE."
    echo "::error::Unable to resolve current deployed image for $service."
    echo "::error::Environment state is incomplete."
    echo "::error::Use the documented DEV bootstrap/recovery procedure (force_deploy=true)."
    continue
  fi

  # Get the current image from the Deployment spec
  # Using jsonpath to reliably extract the first container's image
  image=$(kubectl get deployment "$deployment_name" -n "$NAMESPACE" \
    -o jsonpath='{.spec.template.spec.containers[0].image}' 2>/dev/null)

  if [ -z "$image" ]; then
    ERRORS+=("$service: Deployment '$deployment_name' exists but has no container image in spec.template.spec.containers[0].image.")
    echo "::error::Deployment $deployment_name exists but container image is empty."
    continue
  fi

  echo "Current image for $service: $image"

  # Validate the image format
  if ! validate_image "$image" "$service" "$REGISTRY"; then
    ERRORS+=("$service: Deployed image validation failed. Image='$image'.")
    echo "::error::Deployed image validation failed for $service."
    continue
  fi

  # Extract the tag (SHA) from the validated image
  tag="${image##*:}"
  RESOLVED_TAGS+=("$tag")
  RESOLVED_IMAGES+=("$image")

  echo "Resolved tag for $service: $tag"
done

# ---------------------------------------------------------------------------
# Report results
# ---------------------------------------------------------------------------

echo ""

if [ ${#ERRORS[@]} -gt 0 ]; then
  echo "::error::Image-state resolution failed for ${#ERRORS[@]} service(s):"
  for err in "${ERRORS[@]}"; do
    echo "::error::  - $err"
  done
  echo ""
  echo "Resolution failed. The environment state is inconsistent."
  echo "Recovery options:"
  echo "  1. Use force_deploy=true to bootstrap all four services explicitly."
  echo "  2. Manually verify and fix the broken Deployment(s) in namespace $NAMESPACE."
  echo "  3. Check that Helm owns all four Deployments and no manual kubectl edits were made."
  exit 1
fi

if [ ${#RESOLVED_TAGS[@]} -ne ${#EXPECTED_SERVICES[@]} ]; then
  echo "::error::Expected ${#EXPECTED_SERVICES[@]} resolved tags but got ${#RESOLVED_TAGS[@]}."
  exit 1
fi

echo ""
echo "All deployed images resolved successfully:"
for i in "${!EXPECTED_SERVICES[@]}"; do
  echo "  ${EXPECTED_SERVICES[$i]}: ${RESOLVED_TAGS[$i]}"
done

# Output in the requested format
echo ""
case "$OUTPUT_FORMAT" in
  json)
    echo "{"
    for i in "${!EXPECTED_SERVICES[@]}"; do
      comma=""
      if [ "$i" -lt $(( ${#EXPECTED_SERVICES[@]} - 1 )) ]; then
        comma=","
      fi
      echo "  \"${EXPECTED_SERVICES[$i]}\": \"${RESOLVED_TAGS[$i]}\"$comma"
    done
    echo "}"
    ;;
  plain|*)
    echo "${RESOLVED_TAGS[*]}"
    ;;
esac

exit 0
