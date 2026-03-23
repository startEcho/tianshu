#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/common.sh"

REFRESH_TOKEN="$(require_refresh_token)"

authorized_api_call "POST" "/auth/logout" "$(jq -n --arg refreshToken "$REFRESH_TOKEN" '{refreshToken: $refreshToken}')" >/dev/null
rm -f "$SESSION_FILE"
echo "Session cleared and logout completed."
