#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
RUNTIME_ENV_FILE="${ROOT_DIR}/.platform-runtime/platform.env"

if [[ -f "$RUNTIME_ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$RUNTIME_ENV_FILE"
  set +a
fi

GATEWAY_BASE_URL="${API_GATEWAY_BASE_URL:-${PLATFORM_GATEWAY_BASE_URL:-${NEXT_PUBLIC_API_GATEWAY_BASE_URL:-http://127.0.0.1:8080}}}"
GATEWAY_BASE_URL="${GATEWAY_BASE_URL%/}"
API_BASE_URL="${API_BASE_URL:-${GATEWAY_BASE_URL}/api/v1}"
SESSION_FILE="${SESSION_FILE:-$SCRIPT_DIR/.session.json}"

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing required command: $1" >&2
    exit 1
  }
}

require_command curl
require_command jq

print_json() {
  jq '.' <<<"$1"
}

save_session() {
  jq '.' <<<"$1" >"$SESSION_FILE"
}

require_session() {
  [[ -f "$SESSION_FILE" ]] || {
    echo "Session file not found: $SESSION_FILE" >&2
    echo "Run scripts/api/login.sh first." >&2
    exit 1
  }
}

session_value() {
  local key="$1"
  require_session
  jq -r "$key // empty" "$SESSION_FILE"
}

require_access_token() {
  local token
  token="$(session_value '.accessToken')"
  [[ -n "$token" ]] || {
    echo "Access token missing in $SESSION_FILE" >&2
    exit 1
  }
  printf '%s' "$token"
}

require_refresh_token() {
  local token
  token="$(session_value '.refreshToken')"
  [[ -n "$token" ]] || {
    echo "Refresh token missing in $SESSION_FILE" >&2
    exit 1
  }
  printf '%s' "$token"
}

api_call() {
  local method="$1"
  local endpoint="$2"
  local body="${3:-}"
  local extra_header="${4:-}"
  local curl_args=(
    --fail-with-body
    -sS
    -X "$method"
    -H "Content-Type: application/json"
  )

  if [[ -n "$extra_header" ]]; then
    curl_args+=(-H "$extra_header")
  fi

  if [[ -n "$body" ]]; then
    curl "${curl_args[@]}" "${API_BASE_URL}${endpoint}" --data "$body"
    return
  fi

  curl "${curl_args[@]}" "${API_BASE_URL}${endpoint}"
}

authorized_api_call() {
  local method="$1"
  local endpoint="$2"
  local body="${3:-}"
  local extra_header="${4:-}"
  local access_token
  access_token="$(require_access_token)"
  local curl_args=(
    --fail-with-body
    -sS
    -X "$method"
    -H "Authorization: Bearer ${access_token}"
    -H "Content-Type: application/json"
  )

  if [[ -n "$extra_header" ]]; then
    curl_args+=(-H "$extra_header")
  fi

  if [[ -n "$body" ]]; then
    curl "${curl_args[@]}" "${API_BASE_URL}${endpoint}" --data "$body"
    return
  fi

  curl "${curl_args[@]}" "${API_BASE_URL}${endpoint}"
}
