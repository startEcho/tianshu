#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
MODE="local"

if [[ "${1:-}" == "--mode" ]]; then
  MODE="${2:-local}"
  shift 2 || true
elif [[ "${1:-}" == "local" || "${1:-}" == "k8s" ]]; then
  MODE="$1"
  shift || true
fi

case "$MODE" in
  local)
    exec "${ROOT_DIR}/scripts/platform/stop-local-platform.sh" "$@"
    ;;
  k8s)
    exec "${ROOT_DIR}/scripts/k8s/stop-minikube-deploy.sh" "$@"
    ;;
  *)
    echo "Usage: ./stop.sh [--mode local|k8s]"
    exit 1
    ;;
esac
