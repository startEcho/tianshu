#!/usr/bin/env bash
set -euo pipefail

BLUE='\033[0;34m'
NC='\033[0m'

print_header() {
  printf "\n${BLUE}========== %s ==========${NC}\n\n" "$1"
}

print_header "Minikube"
minikube status || true

print_header "Kubernetes Resources"
kubectl get deployment,service,ingress,pod -A
