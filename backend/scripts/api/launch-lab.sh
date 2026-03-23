#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/common.sh"

VULNERABILITY_ID="${1:?Usage: scripts/api/launch-lab.sh <vulnerability-id> [idempotency-key]}"
IDEMPOTENCY_KEY="${2:-${IDEMPOTENCY_KEY:-launch-$(uuidgen 2>/dev/null || date +%s%N)}}"

response="$(authorized_api_call \
  "POST" \
  "/labs" \
  "$(jq -n --arg vulnerabilityId "$VULNERABILITY_ID" '{vulnerabilityId: $vulnerabilityId}')" \
  "Idempotency-Key: ${IDEMPOTENCY_KEY}")"
print_json "$response"
