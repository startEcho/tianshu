#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/common.sh"

USERNAME="${1:-admin}"
PASSWORD="${2:-Admin123456}"

response="$(api_call "POST" "/auth/login" "$(jq -n --arg username "$USERNAME" --arg password "$PASSWORD" '{username: $username, password: $password}')")"
save_session "$response"
print_json "$response"
echo "Session stored in $SESSION_FILE"
