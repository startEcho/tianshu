#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

"${SCRIPT_DIR}/stop-local-platform.sh" "$@"
exec "${SCRIPT_DIR}/start-local-platform.sh" "$@"
