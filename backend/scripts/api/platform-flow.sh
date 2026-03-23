#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/common.sh"

USERNAME="${1:-admin}"
PASSWORD="${2:-Admin123456}"

echo "== login =="
"$SCRIPT_DIR/login.sh" "$USERNAME" "$PASSWORD"

echo
echo "== current user =="
"$SCRIPT_DIR/me.sh"

echo
echo "== definitions =="
definitions_json="$("$SCRIPT_DIR/definitions.sh")"
printf '%s\n' "$definitions_json"

definition_id="$(jq -r '.[0].id // empty' <<<"$definitions_json")"
if [[ -n "$definition_id" ]]; then
  echo
  echo "== launch lab =="
  launch_json="$("$SCRIPT_DIR/launch-lab.sh" "$definition_id")"
  printf '%s\n' "$launch_json"
else
  echo
  echo "No definitions available, skipping lab launch."
fi

echo
echo "== lab list =="
"$SCRIPT_DIR/labs.sh"

echo
echo "== roles =="
if ! "$SCRIPT_DIR/roles.sh"; then
  echo "Role listing is not available for this account."
fi

echo
echo "== users =="
if ! "$SCRIPT_DIR/users.sh"; then
  echo "User listing is not available for this account."
fi
