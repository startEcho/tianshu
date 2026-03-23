#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/common.sh"

INSTANCE_ID="${1:?Usage: scripts/api/terminate-lab.sh <instance-id>}"

authorized_api_call "DELETE" "/labs/${INSTANCE_ID}" >/dev/null
echo "Termination requested for ${INSTANCE_ID}"
