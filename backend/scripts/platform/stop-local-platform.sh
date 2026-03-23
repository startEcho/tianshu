#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/common.sh"

print_header "TianShu Local Platform Shutdown"

ensure_runtime_dirs
load_env_file

print_header "Frontend and Backend Services"
if [[ "${STOP_FRONTEND:-true}" == "true" ]]; then
  stop_managed_process "frontend"
else
  info "Leaving frontend running because STOP_FRONTEND=false"
fi
stop_managed_process "gateway-service"
stop_managed_process "lab-orchestration-service"
stop_managed_process "vulnerability-definition-service"
stop_managed_process "auth-service"

print_header "Ingress Controller Proxy"
stop_managed_process "minikube-ingress-proxy"
rm -f "$INGRESS_URL_FILE" "$RUNTIME_ENV_FILE"

print_header "Dynamic Lab Resources"
if minikube status >/dev/null 2>&1; then
  kubectl delete deployment,service,ingress,pod \
    -n default \
    -l instanceType=lab-environment \
    --ignore-not-found >/dev/null 2>&1 || true
  info "Deleted active lab Kubernetes resources"
else
  warn "Minikube is not running, skipping lab resource cleanup"
fi

if [[ "${STOP_MINIKUBE:-true}" == "true" ]] && minikube status >/dev/null 2>&1; then
  print_header "Minikube"
  minikube stop >/dev/null
  info "Minikube stopped"
fi

if [[ "${STOP_INFRA:-true}" == "true" ]]; then
  print_header "Infrastructure"
  docker_compose down >/dev/null
  info "Docker infrastructure stopped"
fi

print_header "Shutdown Complete"
printf 'Managed processes stopped.\n'
printf 'Use ./start.sh to bring the platform back up.\n'
