#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/common.sh"

print_header "TianShu Local Platform Startup"

require_command curl
require_command docker
require_command jq
require_command kubectl
require_command minikube
require_command npm

ensure_runtime_dirs
load_env_file
ensure_java_17

print_header "Build Artifacts"
if [[ "${SKIP_BACKEND_BUILD:-false}" != "true" ]]; then
  info "Building backend jars"
  build_backend
else
  warn "Skipping backend build because SKIP_BACKEND_BUILD=true"
fi

if [[ "${SKIP_FRONTEND_BUILD:-false}" != "true" ]]; then
  info "Building frontend"
  build_frontend
else
  warn "Skipping frontend build because SKIP_FRONTEND_BUILD=true"
fi

print_header "Infrastructure"
docker_compose up -d
wait_for_docker_exec "tianshu-postgres" "PostgreSQL" "pg_isready -U ${POSTGRES_USER:-tianshu}"
wait_for_docker_exec "tianshu-redis" "Redis" "redis-cli ping | grep -q PONG"
wait_for_docker_exec "tianshu-rabbitmq" "RabbitMQ" "rabbitmq-diagnostics -q ping"
wait_for_http "http://127.0.0.1:8848/nacos/v1/console/health/readiness" "Nacos"
wait_for_http "http://127.0.0.1:9090/-/healthy" "Prometheus"
wait_for_http "http://127.0.0.1:3001/api/health" "Grafana"
wait_for_http "http://127.0.0.1:9411/health" "Zipkin"

print_header "Nacos Configuration"
./scripts/nacos/publish-local-configs.sh

print_header "Minikube"
if minikube status >/dev/null 2>&1; then
  info "Minikube is already running"
else
  info "Starting Minikube"
  minikube start --driver="${MINIKUBE_DRIVER:-docker}"
fi

kubectl config use-context minikube >/dev/null 2>&1 || true
minikube addons enable ingress >/dev/null 2>&1 || true
wait_for_pods_ready "app.kubernetes.io/component=controller" "ingress-nginx"

print_header "Ingress Controller URL"
PLATFORM_INGRESS_BASE_URL="${PLATFORM_INGRESS_BASE_URL:-$(start_ingress_proxy)}"
write_runtime_env "$PLATFORM_INGRESS_BASE_URL"
info "Lab access base URL: ${PLATFORM_INGRESS_BASE_URL}"
info "Gateway base URL: $(platform_gateway_base_url)"

print_header "Backend Services"
start_managed_process \
  "auth-service" \
  "cd \"${ROOT_DIR}\" && set -a && source \"${ENV_FILE}\" && set +a && export JAVA_HOME=\"${JAVA_HOME}\" && export PATH=\"${JAVA_HOME}/bin:\$PATH\" && exec java -jar platform-services/auth-service/target/auth-service.jar --spring.profiles.active=nacos" \
  "$(service_health_url auth-service)" \
  "auth-service"

start_managed_process \
  "vulnerability-definition-service" \
  "cd \"${ROOT_DIR}\" && set -a && source \"${ENV_FILE}\" && set +a && export JAVA_HOME=\"${JAVA_HOME}\" && export PATH=\"${JAVA_HOME}/bin:\$PATH\" && exec java -jar platform-services/vulnerability-definition-service/target/vulnerability-definition-service.jar --spring.profiles.active=nacos" \
  "$(service_health_url vulnerability-definition-service)" \
  "vulnerability-definition-service"

start_managed_process \
  "lab-orchestration-service" \
  "cd \"${ROOT_DIR}\" && set -a && source \"${ENV_FILE}\" && set +a && export PLATFORM_INGRESS_BASE_URL=\"${PLATFORM_INGRESS_BASE_URL}\" && export JAVA_HOME=\"${JAVA_HOME}\" && export PATH=\"${JAVA_HOME}/bin:\$PATH\" && exec java -jar platform-services/lab-orchestration-service/target/lab-orchestration-service.jar --spring.profiles.active=nacos" \
  "$(service_health_url lab-orchestration-service)" \
  "lab-orchestration-service"

start_managed_process \
  "gateway-service" \
  "cd \"${ROOT_DIR}\" && set -a && source \"${ENV_FILE}\" && set +a && export JAVA_HOME=\"${JAVA_HOME}\" && export PATH=\"${JAVA_HOME}/bin:\$PATH\" && exec java -jar platform-services/gateway-service/target/gateway-service.jar --spring.profiles.active=nacos" \
  "$(service_health_url gateway-service)" \
  "gateway-service"

print_header "Frontend"
start_frontend_server

print_header "Platform Readiness"
wait_for_platform_readiness

print_header "Vulnerability Image Audit"
audit_seed_vulnerability_images

print_header "Platform Ready"
printf 'Status:   %s\n' "Core services, authenticated APIs, and frontend operator routes are ready"
printf 'Frontend: %s\n' "http://127.0.0.1:3000"
printf 'Gateway:  %s\n' "$(platform_gateway_base_url)"
printf 'Grafana:  %s\n' "http://127.0.0.1:3001"
printf 'Prometheus: %s\n' "http://127.0.0.1:9090"
printf 'Zipkin:   %s\n' "http://127.0.0.1:9411"
printf 'Nacos:    %s\n' "http://127.0.0.1:8848/nacos"
printf 'Lab Base: %s\n' "${PLATFORM_INGRESS_BASE_URL}"
printf '\nDefault accounts:\n'
printf '  admin / Admin123456\n'
printf '  trainer / Trainer123456\n'
printf '  student / Student123456\n'
printf '\nLogs: %s\n' "${LOG_DIR}"
printf 'Runtime state: %s\n\n' "${RUNTIME_DIR}"
