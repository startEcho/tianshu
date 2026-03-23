#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RUNTIME_DIR="${ROOT_DIR}/.platform-runtime"
PID_DIR="${RUNTIME_DIR}/pids"
LOG_DIR="${ROOT_DIR}/logs"
RUNTIME_ENV_FILE="${RUNTIME_DIR}/platform.env"
INGRESS_URL_FILE="${RUNTIME_DIR}/ingress-controller.url"
ENV_FILE="${TIANSHU_ENV_FILE:-${ROOT_DIR}/.env.infrastructure}"
FRONTEND_DIR="${TIANSHU_FRONTEND_DIR:-${ROOT_DIR}/../tian-shu-frontend}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_header() {
  printf "\n${BLUE}========== %s ==========${NC}\n\n" "$1"
}

info() {
  printf "${GREEN}[INFO]${NC} %s\n" "$1"
}

warn() {
  printf "${YELLOW}[WARN]${NC} %s\n" "$1"
}

error() {
  printf "${RED}[ERROR]${NC} %s\n" "$1" >&2
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    error "Missing required command: $1"
    exit 1
  }
}

ensure_runtime_dirs() {
  mkdir -p "$RUNTIME_DIR" "$PID_DIR" "$LOG_DIR"
}

ensure_env_file() {
  if [[ -f "$ENV_FILE" ]]; then
    return 0
  fi

  if [[ -f "${ROOT_DIR}/.env.infrastructure.example" ]]; then
    cp "${ROOT_DIR}/.env.infrastructure.example" "$ENV_FILE"
    info "Created ${ENV_FILE} from .env.infrastructure.example"
    return 0
  fi

  error "Missing ${ENV_FILE} and no example file was found."
  exit 1
}

load_env_file() {
  ensure_env_file
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
}

trim_trailing_slash() {
  local value="$1"
  printf '%s\n' "${value%/}"
}

platform_gateway_base_url() {
  trim_trailing_slash "${PLATFORM_GATEWAY_BASE_URL:-http://127.0.0.1:8080}"
}

platform_api_base_url() {
  printf '%s/api/v1\n' "$(platform_gateway_base_url)"
}

java_home_is_17() {
  local candidate="$1"
  [[ -n "$candidate" && -x "$candidate/bin/javac" ]] || return 1
  "$candidate/bin/javac" -version 2>&1 | grep -Eq '^javac 17([. ]|$)'
}

resolve_java_home() {
  if java_home_is_17 "${JAVA_HOME:-}"; then
    printf '%s\n' "$JAVA_HOME"
    return 0
  fi

  if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    local mac_home
    mac_home="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
    if java_home_is_17 "$mac_home"; then
      printf '%s\n' "$mac_home"
      return 0
    fi
  fi

  if command -v brew >/dev/null 2>&1; then
    local brew_home
    brew_home="$(brew --prefix openjdk@17 2>/dev/null || true)"
    if java_home_is_17 "$brew_home/libexec/openjdk.jdk/Contents/Home"; then
      printf '%s\n' "$brew_home/libexec/openjdk.jdk/Contents/Home"
      return 0
    fi
  fi

  local cellar_home
  cellar_home="$(find /opt/homebrew/Cellar/openjdk@17 -maxdepth 3 -type d -path '*/libexec/openjdk.jdk/Contents/Home' 2>/dev/null | sort | tail -n 1)"
  if java_home_is_17 "$cellar_home"; then
    printf '%s\n' "$cellar_home"
    return 0
  fi

  return 1
}

ensure_java_17() {
  local java_home
  java_home="$(resolve_java_home)" || {
    error "JDK 17 was not found. Install openjdk@17 or export JAVA_HOME to a JDK 17 home."
    exit 1
  }

  export JAVA_HOME="$java_home"
  export PATH="${JAVA_HOME}/bin:${PATH}"

  javac -version 2>&1 | grep -q "17" || {
    error "Resolved JAVA_HOME does not point to JDK 17: ${JAVA_HOME}"
    exit 1
  }
}

frontend_exists() {
  [[ -d "$FRONTEND_DIR" && -f "$FRONTEND_DIR/package.json" ]]
}

ensure_frontend_dir() {
  frontend_exists && return 0
  error "Frontend project not found at ${FRONTEND_DIR}. Override with TIANSHU_FRONTEND_DIR."
  exit 1
}

docker_compose() {
  docker compose --env-file "$ENV_FILE" -f "${ROOT_DIR}/docker-compose.infrastructure.yml" "$@"
}

http_up() {
  curl --connect-timeout 2 --max-time 4 -fsS "$1" >/dev/null 2>&1
}

http_status() {
  local url="$1"
  shift || true
  curl --connect-timeout 2 --max-time 6 -sS -o /dev/null -w '%{http_code}' "$@" "$url" 2>/dev/null || true
}

wait_for_http() {
  local url="$1"
  local description="$2"
  local attempts="${3:-60}"

  for ((i = 1; i <= attempts; i++)); do
    if http_up "$url"; then
      info "${description} is ready at ${url}"
      return 0
    fi
    sleep 2
  done

  error "${description} did not become ready: ${url}"
  return 1
}

wait_for_http_status() {
  local url="$1"
  local description="$2"
  local expected_status="${3:-200}"
  local attempts="${4:-60}"
  shift 4 || true
  local curl_args=("$@")

  for ((i = 1; i <= attempts; i++)); do
    local status
    status="$(http_status "$url" "${curl_args[@]}")"
    if [[ "$status" == "$expected_status" ]]; then
      info "${description} returned ${expected_status}"
      return 0
    fi
    sleep 2
  done

  error "${description} did not return ${expected_status}: ${url}"
  return 1
}

wait_for_docker_exec() {
  local container_name="$1"
  local description="$2"
  local command="$3"
  local attempts="${4:-60}"

  for ((i = 1; i <= attempts; i++)); do
    if docker exec "$container_name" sh -lc "$command" >/dev/null 2>&1; then
      info "${description} is ready"
      return 0
    fi
    sleep 2
  done

  error "${description} did not become ready"
  return 1
}

pid_file_for() {
  printf '%s/%s.pid\n' "$PID_DIR" "$1"
}

pid_running() {
  local pid_file
  pid_file="$(pid_file_for "$1")"
  [[ -f "$pid_file" ]] && kill -0 "$(cat "$pid_file")" 2>/dev/null
}

stop_managed_process() {
  local name="$1"
  local pid_file
  pid_file="$(pid_file_for "$name")"

  if [[ ! -f "$pid_file" ]]; then
    return 0
  fi

  local pid
  pid="$(cat "$pid_file")"

  if kill -0 "$pid" 2>/dev/null; then
    kill "$pid" 2>/dev/null || true
    for _ in {1..20}; do
      if ! kill -0 "$pid" 2>/dev/null; then
        rm -f "$pid_file"
        info "Stopped ${name}"
        return 0
      fi
      sleep 1
    done
    kill -9 "$pid" 2>/dev/null || true
  fi

  rm -f "$pid_file"
  info "Stopped ${name}"
}

start_managed_process() {
  local name="$1"
  local command="$2"
  local ready_url="${3:-}"
  local display_name="${4:-$1}"
  local pid_file
  local log_file

  pid_file="$(pid_file_for "$name")"
  log_file="${LOG_DIR}/${name}.console.log"

  if pid_running "$name"; then
    info "${display_name} is already running"
    return 0
  fi

  if [[ -n "$ready_url" ]] && http_up "$ready_url"; then
    warn "${display_name} is already available but not managed by this script"
    return 0
  fi

  : >"$log_file"
  (
    cd "$ROOT_DIR"
    nohup bash -lc "$command" >>"$log_file" 2>&1 &
    echo $! >"$pid_file"
  )

  sleep 1
  if ! pid_running "$name"; then
    error "${display_name} failed to start. See ${log_file}"
    tail -n 40 "$log_file" >&2 || true
    return 1
  fi

  if [[ -n "$ready_url" ]]; then
    wait_for_http "$ready_url" "$display_name"
  fi

  info "${display_name} started"
}

wait_for_pods_ready() {
  local label="$1"
  local namespace="$2"
  local attempts="${3:-120}"

  for ((i = 1; i <= attempts; i++)); do
    local total ready
    total="$(kubectl get pods -l "$label" -n "$namespace" --no-headers 2>/dev/null | wc -l | tr -d ' ')"
    if [[ "$total" -gt 0 ]]; then
      ready="$(
        kubectl get pods -l "$label" -n "$namespace" \
          -o jsonpath='{range .items[*]}{range .status.containerStatuses[*]}{.ready}{"\n"}{end}{end}' 2>/dev/null \
          | grep -c '^true$' || true
      )"
      if [[ "$ready" -ge "$total" ]]; then
        info "Pods ready for label ${label} in namespace ${namespace}"
        return 0
      fi
    fi
    sleep 2
  done

  error "Timed out waiting for pods with label ${label} in namespace ${namespace}"
  return 1
}

extract_ingress_proxy_url() {
  [[ -f "$INGRESS_URL_FILE" ]] || return 1
  grep -Eo 'http://127\.0\.0\.1:[0-9]+' "$INGRESS_URL_FILE" | head -n 1
}

start_ingress_proxy() {
  local name="minikube-ingress-proxy"
  local pid_file
  local log_file
  local url

  pid_file="$(pid_file_for "$name")"
  log_file="${LOG_DIR}/${name}.console.log"

  if pid_running "$name"; then
    url="$(extract_ingress_proxy_url || true)"
    if [[ -n "$url" ]]; then
      printf '%s\n' "$url"
      return 0
    fi
  fi

  rm -f "$INGRESS_URL_FILE"
  : >"$log_file"
  (
    cd "$ROOT_DIR"
    nohup minikube service ingress-nginx-controller -n ingress-nginx --url >"$INGRESS_URL_FILE" 2>"$log_file" &
    echo $! >"$pid_file"
  )

  for _ in {1..60}; do
    url="$(extract_ingress_proxy_url || true)"
    if [[ -n "$url" ]]; then
      printf '%s\n' "$url"
      return 0
    fi
    sleep 1
  done

  error "Failed to obtain a host-accessible ingress controller URL. See ${log_file}"
  return 1
}

write_runtime_env() {
  local ingress_base_url="$1"
  local gateway_base_url
  gateway_base_url="$(platform_gateway_base_url)"
  cat >"$RUNTIME_ENV_FILE" <<EOF
PLATFORM_INGRESS_BASE_URL=${ingress_base_url}
PLATFORM_GATEWAY_BASE_URL=${gateway_base_url}
NEXT_PUBLIC_API_GATEWAY_BASE_URL=${gateway_base_url}
TIANSHU_FRONTEND_DIR=${FRONTEND_DIR}
EOF
}

build_backend() {
  local -a maven_args=()
  if [[ "${MAVEN_OFFLINE:-false}" == "true" ]]; then
    maven_args+=("-o")
  fi

  if [[ "${#maven_args[@]}" -gt 0 ]]; then
    ./mvnw "${maven_args[@]}" \
      -Dmaven.repo.local="${MAVEN_REPO_LOCAL:-${HOME}/.m2/repository}" \
      -DskipTests \
      package
  else
    ./mvnw \
      -Dmaven.repo.local="${MAVEN_REPO_LOCAL:-${HOME}/.m2/repository}" \
      -DskipTests \
      package
  fi
}

build_frontend() {
  ensure_frontend_dir
  local gateway_base_url
  gateway_base_url="$(platform_gateway_base_url)"

  if [[ ! -d "${FRONTEND_DIR}/node_modules" ]]; then
    (cd "$FRONTEND_DIR" && npm install)
  fi

  (
    cd "$FRONTEND_DIR"
    NEXT_PUBLIC_API_GATEWAY_BASE_URL="$gateway_base_url" npm run build
  )
}

start_frontend_server() {
  ensure_frontend_dir
  local gateway_base_url
  gateway_base_url="$(platform_gateway_base_url)"
  start_managed_process \
    "frontend" \
    "cd \"${FRONTEND_DIR}\" && export NEXT_PUBLIC_API_GATEWAY_BASE_URL=\"${gateway_base_url}\" && exec npm start -- --hostname 127.0.0.1 --port 3000" \
    "http://127.0.0.1:3000/login" \
    "frontend"
}

gateway_login_json() {
  local username="${1:-admin}"
  local password="${2:-Admin123456}"
  local payload
  payload="$(jq -n --arg username "$username" --arg password "$password" '{username: $username, password: $password}')"
  curl --connect-timeout 2 --max-time 8 --fail-with-body -sS \
    -X POST \
    -H "Content-Type: application/json" \
    "$(platform_api_base_url)/auth/login" \
    --data "$payload"
}

wait_for_platform_readiness() {
  local login_response=""
  local access_token=""
  local session_file="${ROOT_DIR}/scripts/api/.session.json"

  for ((i = 1; i <= 60; i++)); do
    login_response="$(gateway_login_json admin Admin123456 2>/dev/null || true)"
    access_token="$(jq -r '.accessToken // empty' <<<"$login_response" 2>/dev/null || true)"
    if [[ -n "$access_token" ]]; then
      jq '.' <<<"$login_response" >"$session_file"
      info "Gateway login smoke test passed"
      break
    fi
    sleep 2
  done

  if [[ -z "$access_token" ]]; then
    error "Gateway login smoke test did not succeed"
    return 1
  fi

  wait_for_http_status \
    "$(platform_api_base_url)/auth/me" \
    "Authenticated profile endpoint" \
    "200" \
    "30" \
    -H "Authorization: Bearer ${access_token}"

  wait_for_http_status \
    "$(platform_api_base_url)/definitions" \
    "Definition catalog endpoint" \
    "200" \
    "30" \
    -H "Authorization: Bearer ${access_token}"

  wait_for_http_status \
    "$(platform_api_base_url)/roles" \
    "Role catalog endpoint" \
    "200" \
    "30" \
    -H "Authorization: Bearer ${access_token}"

  wait_for_http_status \
    "$(platform_api_base_url)/users" \
    "User directory endpoint" \
    "200" \
    "30" \
    -H "Authorization: Bearer ${access_token}"

  wait_for_http_status \
    "http://127.0.0.1:3000/api/platform-control/status" \
    "Frontend platform-control endpoint" \
    "200" \
    "30" \
    -H "Authorization: Bearer ${access_token}"
}

seed_vulnerability_images() {
  local config_file="${ROOT_DIR}/ops/nacos/configs/vulnerability-definition-service.yaml"
  [[ -f "$config_file" ]] || return 0
  sed -n 's/^[[:space:]]*dockerImageName:[[:space:]]*"\{0,1\}\([^"]*\)"\{0,1\}[[:space:]]*$/\1/p' "$config_file"
}

audit_seed_vulnerability_images() {
  local minikube_images
  minikube_images="$(minikube image ls 2>/dev/null || true)"

  local total=0
  local ready=0
  local -a docker_only=()
  local -a missing=()
  local image

  while IFS= read -r image; do
    [[ -n "$image" ]] || continue
    total=$((total + 1))
    if grep -Fq "$image" <<<"$minikube_images"; then
      ready=$((ready + 1))
      continue
    fi

    if docker image inspect "$image" >/dev/null 2>&1; then
      docker_only+=("$image")
    else
      missing+=("$image")
    fi
  done < <(seed_vulnerability_images)

  if [[ "$total" -eq 0 ]]; then
    warn "No seed vulnerability images were found in the local definition config"
    return 0
  fi

  info "Seed lab images ready in Minikube: ${ready}/${total}"

  if [[ "${#docker_only[@]}" -gt 0 ]]; then
    warn "These seed lab images exist in local Docker but are not loaded into Minikube:"
    printf '  %s\n' "${docker_only[@]}"
    info "Run: minikube image load <image>"
  fi

  if [[ "${#missing[@]}" -gt 0 ]]; then
    warn "These seed lab images are missing locally. Launching their labs will fail:"
    printf '  %s\n' "${missing[@]}"
  fi
}

service_health_url() {
  case "$1" in
    auth-service) printf '%s\n' "http://127.0.0.1:8083/actuator/health" ;;
    vulnerability-definition-service) printf '%s\n' "http://127.0.0.1:8081/actuator/health" ;;
    lab-orchestration-service) printf '%s\n' "http://127.0.0.1:8082/actuator/health" ;;
    gateway-service) printf '%s/actuator/health\n' "$(platform_gateway_base_url)" ;;
    frontend) printf '%s\n' "http://127.0.0.1:3000/login" ;;
    *) return 1 ;;
  esac
}
