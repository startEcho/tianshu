#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/common.sh"

ensure_runtime_dirs

print_header "Runtime Files"
printf 'Environment file: %s\n' "$ENV_FILE"
printf 'Runtime state:    %s\n' "$RUNTIME_DIR"
printf 'Frontend dir:     %s\n' "$FRONTEND_DIR"
if [[ -f "$RUNTIME_ENV_FILE" ]]; then
  printf '\nRuntime env:\n'
  cat "$RUNTIME_ENV_FILE"
fi

print_header "Managed Processes"
for name in frontend gateway-service lab-orchestration-service vulnerability-definition-service auth-service minikube-ingress-proxy; do
  url="$(service_health_url "$name" 2>/dev/null || true)"
  if pid_running "$name"; then
    printf '%-34s running (pid %s)\n' "$name" "$(cat "$(pid_file_for "$name")")"
  elif [[ -n "$url" ]] && http_up "$url"; then
    printf '%-34s external\n' "$name"
  else
    printf '%-34s stopped\n' "$name"
  fi
done

print_header "HTTP Health"
for name in auth-service vulnerability-definition-service lab-orchestration-service gateway-service frontend; do
  local_url=""
  local_url="$(service_health_url "$name")"
  if http_up "$local_url"; then
    printf '%-34s UP    %s\n' "$name" "$local_url"
  else
    printf '%-34s DOWN  %s\n' "$name" "$local_url"
  fi
done

print_header "Infrastructure"
docker_compose ps || true

print_header "Minikube"
minikube status || true
