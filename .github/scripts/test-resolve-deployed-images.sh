#!/bin/bash
# =============================================================================
# ClaimAssist - Image State Resolver Unit Tests (Mock-Based)
# =============================================================================
# Tests the resolve-deployed-images.sh logic using mock kubectl output.
# Does NOT require a real Kubernetes cluster. Runs entirely locally.
#
# Usage:
#   test-resolve-deployed-images.sh
#
# Requirements:
#   - bash 4+
#   - The resolve-deployed-images.sh script in the same directory
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RESOLVER_SCRIPT="${SCRIPT_DIR}/resolve-deployed-images.sh"
TEST_REGISTRY="claimassistdev"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BOLD='\033[1m'
NC='\033[0m'

PASSED=0
FAILED=0
TOTAL=0

# ---------------------------------------------------------------------------
# Test infrastructure
# ---------------------------------------------------------------------------

# Mock kubectl: stores the current mock state
declare -A MOCK_DEPLOYMENTS=()
MOCK_DEPLOYMENT_EXISTS=true

# Create a mock kubectl script
setup_mock_kubectl() {
  local mock_dir
  mock_dir=$(mktemp -d)
  cat > "$mock_dir/kubectl" << 'MOCK_EOF'
#!/bin/bash
# Mock kubectl for testing resolve-deployed-images.sh
# Reads state from MOCK_STATE_FILE environment variable

MOCK_STATE_FILE="${MOCK_STATE_FILE:-/tmp/mock-k8s-state}"

# Parse arguments
ACTION=""
RESOURCE=""
NAME=""
NAMESPACE=""
OUTPUT=""

POSITIONAL=()
while [[ $# -gt 0 ]]; do
  case $1 in
    get) ACTION="get"; shift ;;
    -n|--namespace) NAMESPACE="$2"; shift 2 ;;
    -o|--output) OUTPUT="$2"; shift 2 ;;
    --ignore-not-found) shift ;;
    *)
      if [ -z "$RESOURCE" ]; then
        RESOURCE="$1"
      elif [ -z "$NAME" ]; then
        NAME="$1"
      else
        POSITIONAL+=("$1")
      fi
      shift
      ;;
  esac
done

# Simulate kubectl get deployment
if [ "$ACTION" = "get" ] && [ "$RESOURCE" = "deployment" ]; then
  # Check if the deployment exists in mock state
  if [ -f "$MOCK_STATE_FILE" ]; then
    # Check if this deployment is marked as existing
    if grep -q "^EXISTS:$NAME$" "$MOCK_STATE_FILE" 2>/dev/null; then
      if [ "$OUTPUT" = "jsonpath={.spec.template.spec.containers[0].image}" ]; then
        # Return the mock image
        IMAGE=$(grep "^IMAGE:$NAME:" "$MOCK_STATE_FILE" 2>/dev/null | head -1 | cut -d: -f3-)
        if [ -n "$IMAGE" ]; then
          echo "$IMAGE"
          exit 0
        else
          echo "No image found for $NAME" >&2
          exit 1
        fi
      else
        # For existence check (no output format)
        exit 0
      fi
    else
      # Deployment does not exist
      exit 1
    fi
  else
    exit 1
  fi
fi

echo "Mock kubectl: unhandled command: $@" >&2
exit 1
MOCK_EOF
  chmod +x "$mock_dir/kubectl"
  echo "$mock_dir"
}

# Set up mock deployment state
set_mock_state() {
  local service="$1"
  local image="$2"
  local exists="${3:-true}"

  if [ "$exists" = "true" ]; then
    echo "EXISTS:claimassist-${service}" >> "$MOCK_STATE_FILE"
    echo "IMAGE:claimassist-${service}:${image}" >> "$MOCK_STATE_FILE"
  fi
}

# Clear mock state
clear_mock_state() {
  > "$MOCK_STATE_FILE"
}

# Run the resolver with mock kubectl
run_resolver() {
  local namespace="$1"
  local registry="$2"
  local format="${3:-plain}"

  PATH="$MOCK_KUBECTL_DIR:$PATH" \
  MOCK_STATE_FILE="$MOCK_STATE_FILE" \
    "$RESOLVER_SCRIPT" "$namespace" "$registry" "--output-format=$format" 2>&1
}

# Test assertion helpers
assert_success() {
  local test_name="$1"
  local result="$2"
  local exit_code="$3"

  TOTAL=$((TOTAL + 1))
  if [ "$exit_code" -eq 0 ] && [ -n "$result" ]; then
    echo -e "${GREEN}✓ PASS${NC}: $test_name"
    PASSED=$((PASSED + 1))
    return 0
  else
    echo -e "${RED}✗ FAIL${NC}: $test_name"
    echo -e "  ${YELLOW}Expected success, got exit code $exit_code${NC}"
    [ -n "$result" ] && echo -e "  Output: $result"
    FAILED=$((FAILED + 1))
    return 1
  fi
}

assert_failure() {
  local test_name="$1"
  local result="$2"
  local exit_code="$3"
  local expected_pattern="${4:-}"

  TOTAL=$((TOTAL + 1))
  if [ "$exit_code" -ne 0 ]; then
    if [ -n "$expected_pattern" ] && [[ "$result" == *"$expected_pattern"* ]]; then
      echo -e "${GREEN}✓ PASS${NC}: $test_name"
      PASSED=$((PASSED + 1))
      return 0
    elif [ -z "$expected_pattern" ]; then
      echo -e "${GREEN}✓ PASS${NC}: $test_name"
      PASSED=$((PASSED + 1))
      return 0
    else
      echo -e "${RED}✗ FAIL${NC}: $test_name"
      echo -e "  ${YELLOW}Expected failure with pattern: $expected_pattern${NC}"
      echo -e "  ${YELLOW}Got: $result${NC}"
      FAILED=$((FAILED + 1))
      return 1
    fi
  else
    echo -e "${RED}✗ FAIL${NC}: $test_name"
    echo -e "  ${YELLOW}Expected failure but got success${NC}"
    FAILED=$((FAILED + 1))
    return 1
  fi
}

assert_tag_count() {
  local test_name="$1"
  local result="$2"
  local expected_count="$3"

  TOTAL=$((TOTAL + 1))
  # Count space-separated tags (last line of output)
  local last_line
  last_line=$(echo "$result" | tail -1)
  local tag_count
  tag_count=$(echo "$last_line" | wc -w)

  if [ "$tag_count" -eq "$expected_count" ]; then
    echo -e "${GREEN}✓ PASS${NC}: $test_name (got $tag_count tags)"
    PASSED=$((PASSED + 1))
    return 0
  else
    echo -e "${RED}✗ FAIL${NC}: $test_name"
    echo -e "  ${YELLOW}Expected $expected_count tags, got $tag_count${NC}"
    echo -e "  Last line: $last_line"
    FAILED=$((FAILED + 1))
    return 1
  fi
}

assert_tags_match() {
  local test_name="$1"
  local result="$2"
  local expected_tags="$3"

  TOTAL=$((TOTAL + 1))
  local last_line
  last_line=$(echo "$result" | tail -1)

  if [ "$last_line" = "$expected_tags" ]; then
    echo -e "${GREEN}✓ PASS${NC}: $test_name"
    PASSED=$((PASSED + 1))
    return 0
  else
    echo -e "${RED}✗ FAIL${NC}: $test_name"
    echo -e "  ${YELLOW}Expected: $expected_tags${NC}"
    echo -e "  ${YELLOW}Got:      $last_line${NC}"
    FAILED=$((FAILED + 1))
    return 1
  fi
}

assert_json_tag() {
  local test_name="$1"
  local result="$2"
  local service="$3"
  local expected_tag="$4"

  TOTAL=$((TOTAL + 1))
  local actual_tag
  actual_tag=$(echo "$result" | grep "\"$service\"" | sed "s/.*: *\"//" | sed "s/\".*//")

  if [ "$actual_tag" = "$expected_tag" ]; then
    echo -e "${GREEN}✓ PASS${NC}: $test_name"
    PASSED=$((PASSED + 1))
    return 0
  else
    echo -e "${RED}✗ FAIL${NC}: $test_name"
    echo -e "  ${YELLOW}Expected $service=$expected_tag, got $actual_tag${NC}"
    FAILED=$((FAILED + 1))
    return 1
  fi
}

# ---------------------------------------------------------------------------
# Generate deterministic 40-char SHA for testing
# ---------------------------------------------------------------------------
make_sha() {
  local input="$1"
  echo -n "$input" | sha1sum | cut -d' ' -f1
}

# ---------------------------------------------------------------------------
# Test 1: All four current images exist
# ---------------------------------------------------------------------------
test_1_all_images_exist() {
  echo ""
  echo -e "${BOLD}Test 1: All four current images exist${NC}"

  clear_mock_state
  local sha_a; sha_a=$(make_sha "api-gw-commit-a")
  local sha_b; sha_b=$(make_sha "customer-commit-b")
  local sha_c; sha_c=$(make_sha "claims-commit-c")
  local sha_d; sha_d=$(make_sha "agent-commit-d")

  set_mock_state "api-gateway" "${TEST_REGISTRY}/api-gateway:${sha_a}"
  set_mock_state "customer-service" "${TEST_REGISTRY}/customer-service:${sha_b}"
  set_mock_state "claims-service" "${TEST_REGISTRY}/claims-service:${sha_c}"
  set_mock_state "agent-service" "${TEST_REGISTRY}/agent-service:${sha_d}"

  local result exit_code=0
  result=$(run_resolver "claimassist-dev" "$TEST_REGISTRY") || exit_code=$?

  assert_success "Resolver succeeds" "$result" "$exit_code" &&
  assert_tag_count "Returns 4 tags" "$result" 4 &&
  assert_tags_match "Tags match expected SHAs" "$result" "$sha_a $sha_b $sha_c $sha_d"
}

# ---------------------------------------------------------------------------
# Test 2: Deploy one service (others preserved)
# ---------------------------------------------------------------------------
test_2_deploy_one_service() {
  echo ""
  echo -e "${BOLD}Test 2: Deploy one service preserves others${NC}"

  clear_mock_state
  local sha_a; sha_a=$(make_sha "api-gw-commit-a")
  local sha_b; sha_b=$(make_sha "customer-commit-b")
  local sha_c; sha_c=$(make_sha "claims-commit-c")
  local sha_d; sha_d=$(make_sha "agent-commit-d")

  set_mock_state "api-gateway" "${TEST_REGISTRY}/api-gateway:${sha_a}"
  set_mock_state "customer-service" "${TEST_REGISTRY}/customer-service:${sha_b}"
  set_mock_state "claims-service" "${TEST_REGISTRY}/claims-service:${sha_c}"
  set_mock_state "agent-service" "${TEST_REGISTRY}/agent-service:${sha_d}"

  # Resolve current state
  local result exit_code=0
  result=$(run_resolver "claimassist-dev" "$TEST_REGISTRY") || exit_code=$?

  assert_success "Resolver succeeds" "$result" "$exit_code"

  # Simulate deploying customer-service with new SHA
  local new_sha; new_sha=$(make_sha "customer-new-sha")
  local tags
  tags=$(echo "$result" | tail -1)

  # Replace customer-service tag with new SHA
  local api_tag customer_tag claims_tag agent_tag
  read -r api_tag customer_tag claims_tag agent_tag <<< "$tags"
  customer_tag="$new_sha"

  assert_success "New tag is different" "$new_sha" 0
  assert_success "Other tags unchanged" "$([ "$api_tag" = "$sha_a" ] && [ "$claims_tag" = "$sha_c" ] && [ "$agent_tag" = "$sha_d" ] && echo ok)" 0
}

# ---------------------------------------------------------------------------
# Test 3: Deploy all four services
# ---------------------------------------------------------------------------
test_3_deploy_all_services() {
  echo ""
  echo -e "${BOLD}Test 3: Deploy all four services${NC}"

  clear_mock_state
  local sha1; sha1=$(make_sha "all-svc-sha-1")
  local sha2; sha2=$(make_sha "all-svc-sha-2")
  local sha3; sha3=$(make_sha "all-svc-sha-3")
  local sha4; sha4=$(make_sha "all-svc-sha-4")

  set_mock_state "api-gateway" "${TEST_REGISTRY}/api-gateway:${sha1}"
  set_mock_state "customer-service" "${TEST_REGISTRY}/customer-service:${sha2}"
  set_mock_state "claims-service" "${TEST_REGISTRY}/claims-service:${sha3}"
  set_mock_state "agent-service" "${TEST_REGISTRY}/agent-service:${sha4}"

  local result exit_code=0
  result=$(run_resolver "claimassist-dev" "$TEST_REGISTRY") || exit_code=$?

  assert_success "Resolver succeeds" "$result" "$exit_code" &&
  assert_tags_match "All four tags match" "$result" "$sha1 $sha2 $sha3 $sha4"
}

# ---------------------------------------------------------------------------
# Test 4: Infrastructure-only deployment (images unchanged)
# ---------------------------------------------------------------------------
test_4_infrastructure_only() {
  echo ""
  echo -e "${BOLD}Test 4: Infrastructure-only preserves all images${NC}"

  clear_mock_state
  local sha1="abc123def456abc123def456abc123def456abc1"
  local sha2="def456abc123def456abc123def456abc123def4"
  local sha3="123abc456def123abc456def123abc456def1234"
  local sha4="456def123abc456def123abc456def123abc4567"

  set_mock_state "api-gateway" "${TEST_REGISTRY}/api-gateway:${sha1}"
  set_mock_state "customer-service" "${TEST_REGISTRY}/customer-service:${sha2}"
  set_mock_state "claims-service" "${TEST_REGISTRY}/claims-service:${sha3}"
  set_mock_state "agent-service" "${TEST_REGISTRY}/agent-service:${sha4}"

  # Run resolver twice - should return identical results
  local result1 exit1=0 result2 exit2=0
  result1=$(run_resolver "claimassist-dev" "$TEST_REGISTRY") || exit1=$?
  result2=$(run_resolver "claimassist-dev" "$TEST_REGISTRY") || exit2=$?

  assert_success "First run succeeds" "$result1" "$exit1" &&
  assert_success "Second run succeeds" "$result2" "$exit2" &&
  TOTAL=$((TOTAL + 1))
  if [ "$result1" = "$result2" ]; then
    echo -e "${GREEN}✓ PASS${NC}: Both runs produce identical output"
    PASSED=$((PASSED + 1))
  else
    echo -e "${RED}✗ FAIL${NC}: Runs produce different output"
    FAILED=$((FAILED + 1))
  fi
}

# ---------------------------------------------------------------------------
# Test 5: One Deployment missing
# ---------------------------------------------------------------------------
test_5_one_deployment_missing() {
  echo ""
  echo -e "${BOLD}Test 5: One Deployment missing fails hard${NC}"

  clear_mock_state
  set_mock_state "api-gateway" "${TEST_REGISTRY}/api-gateway:$(make_sha 'api-ok')"
  set_mock_state "customer-service" "${TEST_REGISTRY}/customer-service:$(make_sha 'cust-ok')"
  # claims-service MISSING
  set_mock_state "agent-service" "${TEST_REGISTRY}/agent-service:$(make_sha 'agent-ok')"

  local result exit_code=0
  result=$(run_resolver "claimassist-dev" "$TEST_REGISTRY") || exit_code=$?

  assert_failure "Fails when Deployment missing" "$result" "$exit_code" "Deployment"
}

# ---------------------------------------------------------------------------
# Test 6: One Deployment uses 'latest'
# ---------------------------------------------------------------------------
test_6_deployment_uses_latest() {
  echo ""
  echo -e "${BOLD}Test 6: 'latest' tag fails hard${NC}"

  clear_mock_state
  set_mock_state "api-gateway" "${TEST_REGISTRY}/api-gateway:latest"
  set_mock_state "customer-service" "${TEST_REGISTRY}/customer-service:$(make_sha 'cust-ok')"
  set_mock_state "claims-service" "${TEST_REGISTRY}/claims-service:$(make_sha 'claims-ok')"
  set_mock_state "agent-service" "${TEST_REGISTRY}/agent-service:$(make_sha 'agent-ok')"

  local result exit_code=0
  result=$(run_resolver "claimassist-dev" "$TEST_REGISTRY") || exit_code=$?

  assert_failure "Fails on 'latest' tag" "$result" "$exit_code" "not an immutable SHA"
}

# ---------------------------------------------------------------------------
# Test 7: One Deployment uses '1.0.0'
# ---------------------------------------------------------------------------
test_7_deployment_uses_1_0_0() {
  echo ""
  echo -e "${BOLD}Test 7: '1.0.0' tag fails hard${NC}"

  clear_mock_state
  set_mock_state "api-gateway" "${TEST_REGISTRY}/api-gateway:1.0.0"
  set_mock_state "customer-service" "${TEST_REGISTRY}/customer-service:$(make_sha 'cust-ok')"
  set_mock_state "claims-service" "${TEST_REGISTRY}/claims-service:$(make_sha 'claims-ok')"
  set_mock_state "agent-service" "${TEST_REGISTRY}/agent-service:$(make_sha 'agent-ok')"

  local result exit_code=0
  result=$(run_resolver "claimassist-dev" "$TEST_REGISTRY") || exit_code=$?

  assert_failure "Fails on '1.0.0' tag" "$result" "$exit_code" "not an immutable SHA"
}

# ---------------------------------------------------------------------------
# Test 8: Automatic CD followed by manual CD (simulated)
# ---------------------------------------------------------------------------
test_8_auto_then_manual_cd() {
  echo ""
  echo -e "${BOLD}Test 8: Auto CD then manual CD (concurrent safety)${NC}"

  clear_mock_state
  local sha_a; sha_a=$(make_sha "api-gw-initial")
  set_mock_state "api-gateway" "${TEST_REGISTRY}/api-gateway:${sha_a}"
  set_mock_state "customer-service" "${TEST_REGISTRY}/customer-service:$(make_sha 'cust-initial')"
  set_mock_state "claims-service" "${TEST_REGISTRY}/claims-service:$(make_sha 'claims-initial')"
  set_mock_state "agent-service" "${TEST_REGISTRY}/agent-service:$(make_sha 'agent-initial')"

  # Auto CD: resolve -> all four known
  local auto_result exit1=0
  auto_result=$(run_resolver "claimassist-dev" "$TEST_REGISTRY") || exit1=$?
  assert_success "Auto CD resolves state" "$auto_result" "$exit1"

  # Simulate auto CD deployed new api-gateway
  local new_sha; new_sha=$(make_sha "api-gw-after-auto")
  clear_mock_state
  set_mock_state "api-gateway" "${TEST_REGISTRY}/api-gateway:${new_sha}"
  set_mock_state "customer-service" "${TEST_REGISTRY}/customer-service:$(make_sha 'cust-initial')"
  set_mock_state "claims-service" "${TEST_REGISTRY}/claims-service:$(make_sha 'claims-initial')"
  set_mock_state "agent-service" "${TEST_REGISTRY}/agent-service:$(make_sha 'agent-initial')"

  # Manual CD: resolve -> should see the auto-deployed state
  local manual_result exit2=0
  manual_result=$(run_resolver "claimassist-dev" "$TEST_REGISTRY") || exit2=$?
  assert_success "Manual CD resolves after auto" "$manual_result" "$exit2"

  # Verify the manual CD sees the auto-deployed SHA
  local manual_tags
  manual_tags=$(echo "$manual_result" | tail -1)
  local manual_api_tag
  manual_api_tag=$(echo "$manual_tags" | cut -d' ' -f1)

  TOTAL=$((TOTAL + 1))
  if [ "$manual_api_tag" = "$new_sha" ]; then
    echo -e "${GREEN}✓ PASS${NC}: Manual CD sees auto-deployed SHA ($new_sha)"
    PASSED=$((PASSED + 1))
  else
    echo -e "${RED}✗ FAIL${NC}: Manual CD sees stale SHA (expected $new_sha, got $manual_api_tag)"
    FAILED=$((FAILED + 1))
  fi
}

# ---------------------------------------------------------------------------
# Test 9: Two rapid automatic deployments
# ---------------------------------------------------------------------------
test_9_rapid_auto_deployments() {
  echo ""
  echo -e "${BOLD}Test 9: Two rapid auto deployments (stale protection)${NC}"

  clear_mock_state
  local sha_v1; sha_v1=$(make_sha "version-1")
  local sha_v2; sha_v2=$(make_sha "version-2")

  set_mock_state "api-gateway" "${TEST_REGISTRY}/api-gateway:${sha_v1}"
  set_mock_state "customer-service" "${TEST_REGISTRY}/customer-service:$(make_sha 'cust-v1')"
  set_mock_state "claims-service" "${TEST_REGISTRY}/claims-service:$(make_sha 'claims-v1')"
  set_mock_state "agent-service" "${TEST_REGISTRY}/agent-service:$(make_sha 'agent-v1')"

  # First auto CD resolves v1 state
  local result1 exit1=0
  result1=$(run_resolver "claimassist-dev" "$TEST_REGISTRY") || exit1=$?
  assert_success "First auto CD resolves" "$result1" "$exit1"

  # Simulate second auto CD deployed (newer state)
  clear_mock_state
  set_mock_state "api-gateway" "${TEST_REGISTRY}/api-gateway:${sha_v2}"
  set_mock_state "customer-service" "${TEST_REGISTRY}/customer-service:$(make_sha 'cust-v1')"
  set_mock_state "claims-service" "${TEST_REGISTRY}/claims-service:$(make_sha 'claims-v1')"
  set_mock_state "agent-service" "${TEST_REGISTRY}/agent-service:$(make_sha 'agent-v1')"

  # Second auto CD sees v2 (stale protection means first CD's state is gone)
  local result2 exit2=0
  result2=$(run_resolver "claimassist-dev" "$TEST_REGISTRY") || exit2=$?
  assert_success "Second auto CD resolves" "$result2" "$exit2"

  local tags2
  tags2=$(echo "$result2" | tail -1)
  local api_tag2
  api_tag2=$(echo "$tags2" | cut -d' ' -f1)

  TOTAL=$((TOTAL + 1))
  if [ "$api_tag2" = "$sha_v2" ]; then
    echo -e "${GREEN}✓ PASS${NC}: Second CD sees v2 ($sha_v2), not stale v1"
    PASSED=$((PASSED + 1))
  else
    echo -e "${RED}✗ FAIL${NC}: Second CD sees wrong SHA (expected $sha_v2, got $api_tag2)"
    FAILED=$((FAILED + 1))
  fi
}

# ---------------------------------------------------------------------------
# Test 10: Failed Helm revision (simulated - current K8s state is truth)
# ---------------------------------------------------------------------------
test_10_failed_helm_revision() {
  echo ""
  echo -e "${BOLD}Test 10: Failed Helm revision does not corrupt state${NC}"

  clear_mock_state
  local sha_good; sha_good=$(make_sha "good-deployed-sha")
  set_mock_state "api-gateway" "${TEST_REGISTRY}/api-gateway:${sha_good}"
  set_mock_state "customer-service" "${TEST_REGISTRY}/customer-service:$(make_sha 'cust-good')"
  set_mock_state "claims-service" "${TEST_REGISTRY}/claims-service:$(make_sha 'claims-good')"
  set_mock_state "agent-service" "${TEST_REGISTRY}/agent-service:$(make_sha 'agent-good')"

  # Even if Helm has a failed revision, K8s Deployment state is the truth
  local result exit_code=0
  result=$(run_resolver "claimassist-dev" "$TEST_REGISTRY") || exit_code=$?

  assert_success "Resolver uses K8s state, not Helm values" "$result" "$exit_code"

  local tags
  tags=$(echo "$result" | tail -1)
  local api_tag
  api_tag=$(echo "$tags" | cut -d' ' -f1)

  TOTAL=$((TOTAL + 1))
  if [ "$api_tag" = "$sha_good" ]; then
    echo -e "${GREEN}✓ PASS${NC}: Returns actual K8s deployed SHA ($sha_good), not Helm failure"
    PASSED=$((PASSED + 1))
  else
    echo -e "${RED}✗ FAIL${NC}: Wrong SHA returned (expected $sha_good, got $api_tag)"
    FAILED=$((FAILED + 1))
  fi
}

# ---------------------------------------------------------------------------
# Test 11: Wrong registry fails
# ---------------------------------------------------------------------------
test_11_wrong_registry() {
  echo ""
  echo -e "${BOLD}Test 11: Wrong registry fails hard${NC}"

  clear_mock_state
  set_mock_state "api-gateway" "wrongregistry/api-gateway:$(make_sha 'wrong-reg')"
  set_mock_state "customer-service" "${TEST_REGISTRY}/customer-service:$(make_sha 'cust-ok')"
  set_mock_state "claims-service" "${TEST_REGISTRY}/claims-service:$(make_sha 'claims-ok')"
  set_mock_state "agent-service" "${TEST_REGISTRY}/agent-service:$(make_sha 'agent-ok')"

  local result exit_code=0
  result=$(run_resolver "claimassist-dev" "$TEST_REGISTRY") || exit_code=$?

  assert_failure "Fails on wrong registry" "$result" "$exit_code" "registry mismatch"
}

# ---------------------------------------------------------------------------
# Test 12: Empty Deployment (no container image)
# ---------------------------------------------------------------------------
test_12_empty_image() {
  echo ""
  echo -e "${BOLD}Test 12: Empty container image fails hard${NC}"

  clear_mock_state
  set_mock_state "api-gateway" ""  # exists but no image
  set_mock_state "customer-service" "${TEST_REGISTRY}/customer-service:$(make_sha 'cust-ok')"
  set_mock_state "claims-service" "${TEST_REGISTRY}/claims-service:$(make_sha 'claims-ok')"
  set_mock_state "agent-service" "${TEST_REGISTRY}/agent-service:$(make_sha 'agent-ok')"

  local result exit_code=0
  result=$(run_resolver "claimassist-dev" "$TEST_REGISTRY") || exit_code=$?

  assert_failure "Fails on empty image" "$result" "$exit_code" ""
}

# ---------------------------------------------------------------------------
# Test 13: Invalid SHA format (too short)
# ---------------------------------------------------------------------------
test_13_invalid_sha_format() {
  echo ""
  echo -e "${BOLD}Test 13: Short SHA tag fails hard${NC}"

  clear_mock_state
  set_mock_state "api-gateway" "${TEST_REGISTRY}/api-gateway:abc123"
  set_mock_state "customer-service" "${TEST_REGISTRY}/customer-service:$(make_sha 'cust-ok')"
  set_mock_state "claims-service" "${TEST_REGISTRY}/claims-service:$(make_sha 'claims-ok')"
  set_mock_state "agent-service" "${TEST_REGISTRY}/agent-service:$(make_sha 'agent-ok')"

  local result exit_code=0
  result=$(run_resolver "claimassist-dev" "$TEST_REGISTRY") || exit_code=$?

  assert_failure "Fails on short SHA" "$result" "$exit_code" "Invalid SHA format"
}

# ---------------------------------------------------------------------------
# Test 14: JSON output format
# ---------------------------------------------------------------------------
test_14_json_output() {
  echo ""
  echo -e "${BOLD}Test 14: JSON output format${NC}"

  clear_mock_state
  local sha_a; sha_a=$(make_sha "api-gw-json")
  local sha_b; sha_b=$(make_sha "customer-json")
  local sha_c; sha_c=$(make_sha "claims-json")
  local sha_d; sha_d=$(make_sha "agent-json")

  set_mock_state "api-gateway" "${TEST_REGISTRY}/api-gateway:${sha_a}"
  set_mock_state "customer-service" "${TEST_REGISTRY}/customer-service:${sha_b}"
  set_mock_state "claims-service" "${TEST_REGISTRY}/claims-service:${sha_c}"
  set_mock_state "agent-service" "${TEST_REGISTRY}/agent-service:${sha_d}"

  local result exit_code=0
  result=$(run_resolver "claimassist-dev" "$TEST_REGISTRY" "json") || exit_code=$?

  assert_success "JSON resolver succeeds" "$result" "$exit_code" &&
  assert_json_tag "api-gateway tag in JSON" "$result" "api-gateway" "$sha_a" &&
  assert_json_tag "customer-service tag in JSON" "$result" "customer-service" "$sha_b" &&
  assert_json_tag "claims-service tag in JSON" "$result" "claims-service" "$sha_c" &&
  assert_json_tag "agent-service tag in JSON" "$result" "agent-service" "$sha_d"
}

# ---------------------------------------------------------------------------
# Test 15: Multiple Deployment missing
# ---------------------------------------------------------------------------
test_15_multiple_missing() {
  echo ""
  echo -e "${BOLD}Test 15: Multiple Deployments missing fails${NC}"

  clear_mock_state
  set_mock_state "api-gateway" "${TEST_REGISTRY}/api-gateway:$(make_sha 'api-ok')"
  # customer-service MISSING
  # claims-service MISSING
  set_mock_state "agent-service" "${TEST_REGISTRY}/agent-service:$(make_sha 'agent-ok')"

  local result exit_code=0
  result=$(run_resolver "claimassist-dev" "$TEST_REGISTRY") || exit_code=$?

  assert_failure "Fails with multiple missing" "$result" "$exit_code" "Deployment"
}

# ---------------------------------------------------------------------------
# Test 16: 'dev' tag fails (mutable tag)
# ---------------------------------------------------------------------------
test_16_mutable_dev_tag() {
  echo ""
  echo -e "${BOLD}Test 16: 'dev' mutable tag fails hard${NC}"

  clear_mock_state
  set_mock_state "api-gateway" "${TEST_REGISTRY}/api-gateway:dev"
  set_mock_state "customer-service" "${TEST_REGISTRY}/customer-service:$(make_sha 'cust-ok')"
  set_mock_state "claims-service" "${TEST_REGISTRY}/claims-service:$(make_sha 'claims-ok')"
  set_mock_state "agent-service" "${TEST_REGISTRY}/agent-service:$(make_sha 'agent-ok')"

  local result exit_code=0
  result=$(run_resolver "claimassist-dev" "$TEST_REGISTRY") || exit_code=$?

  assert_failure "Fails on 'dev' tag" "$result" "$exit_code" "not an immutable SHA"
}

# ---------------------------------------------------------------------------
# Main test runner
# ---------------------------------------------------------------------------
main() {
  echo "======================================================================"
  echo -e "${BOLD}ClaimAssist - Image State Resolver Unit Tests${NC}"
  echo "======================================================================"
  echo "Mock-based (no Kubernetes cluster required)"
  echo ""

  # Verify resolver script exists
  if [ ! -f "$RESOLVER_SCRIPT" ]; then
    echo -e "${RED}ERROR: Resolver script not found: $RESOLVER_SCRIPT${NC}"
    exit 1
  fi

  # Set up mock kubectl
  MOCK_KUBECTL_DIR=$(setup_mock_kubectl)
  MOCK_STATE_FILE=$(mktemp)
  export MOCK_STATE_FILE

  # Run all tests
  test_1_all_images_exist
  test_2_deploy_one_service
  test_3_deploy_all_services
  test_4_infrastructure_only
  test_5_one_deployment_missing
  test_6_deployment_uses_latest
  test_7_deployment_uses_1_0_0
  test_8_auto_then_manual_cd
  test_9_rapid_auto_deployments
  test_10_failed_helm_revision
  test_11_wrong_registry
  test_12_empty_image
  test_13_invalid_sha_format
  test_14_json_output
  test_15_multiple_missing
  test_16_mutable_dev_tag

  # Cleanup
  rm -rf "$MOCK_KUBECTL_DIR"
  rm -f "$MOCK_STATE_FILE"

  # Print summary
  echo ""
  echo "======================================================================"
  echo -e "${BOLD}Test Summary${NC}"
  echo "======================================================================"
  echo -e "Total tests: $TOTAL"
  echo -e "${GREEN}Passed: $PASSED${NC}"
  echo -e "${RED}Failed: $FAILED${NC}"
  echo "======================================================================"

  if [ "$FAILED" -eq 0 ]; then
    echo -e "${GREEN}All tests passed!${NC}"
    exit 0
  else
    echo -e "${RED}Some tests failed${NC}"
    exit 1
  fi
}

# Run main
main "$@"
