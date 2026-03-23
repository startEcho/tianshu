#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/common.sh"

REFRESH_TOKEN="$(require_refresh_token)"

response="$(api_call "POST" "/auth/refresh" "$(jq -n --arg refreshToken "$REFRESH_TOKEN" '{refreshToken: $refreshToken}')")"
save_session "$response"
print_json "$response"
echo "Session refreshed and stored in $SESSION_FILE"
