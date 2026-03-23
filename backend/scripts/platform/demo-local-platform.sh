#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

source "${SCRIPT_DIR}/common.sh"
source "${ROOT_DIR}/scripts/api/common.sh"

USERNAME="${DEMO_USERNAME:-admin}"
PASSWORD="${DEMO_PASSWORD:-Admin123456}"
VULNERABILITY_ID="${DEMO_VULNERABILITY_ID:-stored-xss-java-001}"
IMAGE_NAME="${DEMO_IMAGE_NAME:-tianshuvuln/vuln-stored-xss-guestbook-java}"
IMAGE_TAG="${DEMO_IMAGE_TAG:-0.1.0}"
IDEMPOTENCY_KEY="${DEMO_IDEMPOTENCY_KEY:-demo-$(date +%Y%m%d%H%M%S)}"
RESULT_FILE="${DEMO_RESULT_FILE:-${ROOT_DIR}/.platform-runtime/demo-lab.json}"
POLL_ATTEMPTS="${DEMO_POLL_ATTEMPTS:-40}"
POLL_INTERVAL_SECONDS="${DEMO_POLL_INTERVAL_SECONDS:-2}"

START_PLATFORM=true
ENSURE_IMAGE=true
ENSURE_DEFINITION=true
LAUNCH_LAB=true
FORCE_IMAGE_BUILD=false

usage() {
  cat <<EOF
Usage: scripts/platform/demo-local-platform.sh [options]

Options:
  --username <value>            Login username (default: ${USERNAME})
  --password <value>            Login password (default: ${PASSWORD})
  --vulnerability-id <value>    Demo vulnerability id (default: ${VULNERABILITY_ID})
  --idempotency-key <value>     Idempotency key used when launching the demo lab
  --result-file <path>          JSON output file (default: ${RESULT_FILE})
  --poll-attempts <count>       Poll attempts for lab startup (default: ${POLL_ATTEMPTS})
  --poll-interval <seconds>     Poll interval seconds (default: ${POLL_INTERVAL_SECONDS})
  --skip-start                  Skip ./start.sh local
  --skip-image                  Skip image build/load
  --skip-definition             Skip definition create/update
  --skip-launch                 Skip lab launch verification
  --rebuild-image               Force rebuild the stored XSS image
  --help                        Show this help message
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --username)
      USERNAME="${2:?Missing value for --username}"
      shift 2
      ;;
    --password)
      PASSWORD="${2:?Missing value for --password}"
      shift 2
      ;;
    --vulnerability-id)
      VULNERABILITY_ID="${2:?Missing value for --vulnerability-id}"
      shift 2
      ;;
    --idempotency-key)
      IDEMPOTENCY_KEY="${2:?Missing value for --idempotency-key}"
      shift 2
      ;;
    --result-file)
      RESULT_FILE="${2:?Missing value for --result-file}"
      shift 2
      ;;
    --poll-attempts)
      POLL_ATTEMPTS="${2:?Missing value for --poll-attempts}"
      shift 2
      ;;
    --poll-interval)
      POLL_INTERVAL_SECONDS="${2:?Missing value for --poll-interval}"
      shift 2
      ;;
    --skip-start)
      START_PLATFORM=false
      shift
      ;;
    --skip-image)
      ENSURE_IMAGE=false
      shift
      ;;
    --skip-definition)
      ENSURE_DEFINITION=false
      shift
      ;;
    --skip-launch)
      LAUNCH_LAB=false
      shift
      ;;
    --rebuild-image)
      FORCE_IMAGE_BUILD=true
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      error "Unknown argument: $1"
      usage
      exit 1
      ;;
  esac
done

require_command jq
require_command curl
require_command docker
require_command minikube
ensure_runtime_dirs
load_env_file

DEFINITION_PAYLOAD="$(jq -nc \
  --arg id "${VULNERABILITY_ID}" \
  --arg name "Stored XSS Guestbook (Java)" \
  --arg description "A Spring Boot guestbook that persists attacker-controlled HTML and renders it back through Thymeleaf with unescaped output." \
  --arg category "Cross-Site Scripting" \
  --arg difficulty "Easy" \
  --arg dockerImageName "${IMAGE_NAME}:${IMAGE_TAG}" \
  --arg exploitationGuide "Submit a payload such as <img src=x onerror=alert(document.domain)> in the message body, then revisit the feed to observe stored execution." \
  --arg flagFormat "flag{xss_guestbook_flag}" \
  '{
    id: $id,
    name: $name,
    description: $description,
    category: $category,
    difficulty: $difficulty,
    dockerImageName: $dockerImageName,
    containerPort: 8081,
    exploitationGuide: $exploitationGuide,
    tags: ["java","xss","stored-xss","thymeleaf","guestbook"],
    flagFormat: $flagFormat
  }'
)"

login_to_platform() {
  print_header "Login"
  local response
  response="$(api_call "POST" "/auth/login" "$(jq -nc --arg username "${USERNAME}" --arg password "${PASSWORD}" '{username: $username, password: $password}')")"
  save_session "$response"
  info "Logged in as ${USERNAME}"
}

ensure_demo_image() {
  print_header "Demo Image"
  if [[ "${FORCE_IMAGE_BUILD}" == "true" ]] || ! docker image inspect "${IMAGE_NAME}:${IMAGE_TAG}" >/dev/null 2>&1; then
    info "Building ${IMAGE_NAME}:${IMAGE_TAG}"
    IMAGE_NAME="${IMAGE_NAME}" IMAGE_TAG="${IMAGE_TAG}" LOAD_TO_MINIKUBE="true" \
      "${ROOT_DIR}/scripts/vulnerabilities/build-vuln-stored-xss-guestbook.sh"
    return 0
  fi

  info "Using existing image ${IMAGE_NAME}:${IMAGE_TAG}"
  info "Loading image into Minikube cache"
  minikube image load "${IMAGE_NAME}:${IMAGE_TAG}"
}

normalize_definition_json() {
  jq -c '{
    id,
    name,
    description,
    category,
    difficulty,
    dockerImageName,
    containerPort,
    exploitationGuide,
    tags,
    flagFormat
  }' "$1"
}

ensure_demo_definition() {
  print_header "Demo Definition"
  local access_token definition_url tmp_file status current_json expected_json

  access_token="$(require_access_token)"
  definition_url="${API_BASE_URL}/definitions/${VULNERABILITY_ID}"
  tmp_file="$(mktemp)"

  status="$(curl -sS -o "${tmp_file}" -w '%{http_code}' \
    -H "Authorization: Bearer ${access_token}" \
    "${definition_url}")"

  expected_json="$(jq -c '.' <<<"${DEFINITION_PAYLOAD}")"

  case "${status}" in
    200)
      current_json="$(normalize_definition_json "${tmp_file}")"
      if [[ "${current_json}" == "${expected_json}" ]]; then
        info "Definition ${VULNERABILITY_ID} already matches demo config"
      else
        info "Updating definition ${VULNERABILITY_ID} to demo config"
        authorized_api_call "PUT" "/definitions/${VULNERABILITY_ID}" "${DEFINITION_PAYLOAD}" >/dev/null
      fi
      ;;
    404)
      info "Creating definition ${VULNERABILITY_ID}"
      authorized_api_call "POST" "/definitions" "${DEFINITION_PAYLOAD}" >/dev/null
      ;;
    *)
      error "Definition lookup failed with HTTP ${status}"
      cat "${tmp_file}" >&2
      rm -f "${tmp_file}"
      exit 1
      ;;
  esac

  rm -f "${tmp_file}"
}

write_result_json() {
  local first_http_code="$1"
  local first_body="$2"
  local second_http_code="$3"
  local second_body="$4"
  local final_json="$5"
  local access_url="$6"
  local lab_http_code="$7"
  local lab_title="$8"
  local db_instance_row="$9"
  local outbox_state="${10}"

  mkdir -p "$(dirname "${RESULT_FILE}")"

  jq -n \
    --arg username "${USERNAME}" \
    --arg vulnerabilityId "${VULNERABILITY_ID}" \
    --arg idempotencyKey "${IDEMPOTENCY_KEY}" \
    --arg firstHttpCode "${first_http_code}" \
    --arg secondHttpCode "${second_http_code}" \
    --arg accessUrl "${access_url}" \
    --arg labHttpCode "${lab_http_code}" \
    --arg labTitle "${lab_title}" \
    --arg dbInstanceRow "${db_instance_row}" \
    --arg outboxState "${outbox_state}" \
    --argjson firstBody "${first_body}" \
    --argjson secondBody "${second_body}" \
    --argjson finalBody "${final_json}" \
    '{
      username: $username,
      vulnerabilityId: $vulnerabilityId,
      idempotencyKey: $idempotencyKey,
      firstAttempt: ($firstBody + {httpCode: $firstHttpCode}),
      secondAttempt: ($secondBody + {httpCode: $secondHttpCode}),
      finalState: $finalBody,
      accessUrl: $accessUrl,
      labHttpCode: $labHttpCode,
      labTitle: $labTitle,
      dbInstanceRow: $dbInstanceRow,
      outboxState: $outboxState
    }' >"${RESULT_FILE}"
}

launch_demo_lab() {
  print_header "Demo Lab"
  local request_body first_raw first_http_code first_body second_raw second_http_code second_body instance_id
  local final_json final_status access_url db_instance_row outbox_state lab_http_code lab_title page_file
  local refreshed_ingress_base_url

  request_body="$(jq -nc --arg vulnerabilityId "${VULNERABILITY_ID}" '{vulnerabilityId: $vulnerabilityId}')"

  first_raw="$(curl --fail-with-body -sS -w '\n%{http_code}' \
    -X POST \
    -H "Authorization: Bearer $(require_access_token)" \
    -H 'Content-Type: application/json' \
    -H "Idempotency-Key: ${IDEMPOTENCY_KEY}" \
    "${API_BASE_URL}/labs" \
    --data "${request_body}")"
  first_http_code="$(tail -n 1 <<<"${first_raw}")"
  first_body="$(sed '$d' <<<"${first_raw}")"

  second_raw="$(curl --fail-with-body -sS -w '\n%{http_code}' \
    -X POST \
    -H "Authorization: Bearer $(require_access_token)" \
    -H 'Content-Type: application/json' \
    -H "Idempotency-Key: ${IDEMPOTENCY_KEY}" \
    "${API_BASE_URL}/labs" \
    --data "${request_body}")"
  second_http_code="$(tail -n 1 <<<"${second_raw}")"
  second_body="$(sed '$d' <<<"${second_raw}")"

  instance_id="$(jq -r '.instanceId' <<<"${first_body}")"
  if [[ -z "${instance_id}" || "${instance_id}" == "null" ]]; then
    error "Launch response did not contain instanceId"
    printf '%s\n' "${first_body}" >&2
    exit 1
  fi

  if [[ "${instance_id}" != "$(jq -r '.instanceId' <<<"${second_body}")" ]]; then
    error "Idempotency check failed: repeated POST /labs returned a different instanceId"
    printf '%s\n' "${first_body}" >&2
    printf '%s\n' "${second_body}" >&2
    exit 1
  fi

  final_json='{}'
  final_status=''
  access_url=''
  for _ in $(seq 1 "${POLL_ATTEMPTS}"); do
    final_json="$(authorized_api_call "GET" "/labs/${instance_id}")"
    final_status="$(jq -r '.status' <<<"${final_json}")"
    access_url="$(jq -r '.accessUrl // empty' <<<"${final_json}")"
    if [[ "${final_status}" == "RUNNING" || "${final_status}" == "LAUNCH_FAILED" ]]; then
      break
    fi
    sleep "${POLL_INTERVAL_SECONDS}"
  done

  db_instance_row="$(docker exec tianshu-postgres psql -U tianshu -d tianshu_lab -Atc \
    "select instance_id || '|' || status || '|' || coalesce(access_url,'') || '|' || coalesce(launch_request_id,'') from lab_instances where instance_id = '${instance_id}';")"
  outbox_state="$(docker exec tianshu-postgres psql -U tianshu -d tianshu_lab -Atc \
    "select case when published_at is null then 'UNPUBLISHED' else 'PUBLISHED' end from lab_launch_outbox where instance_id = '${instance_id}';")"

  lab_http_code=''
  lab_title=''
  page_file="$(mktemp)"
  if [[ "${final_status}" == "RUNNING" && -n "${access_url}" ]]; then
    for _ in $(seq 1 20); do
      lab_http_code="$(curl -sS -o "${page_file}" -w '%{http_code}' "${access_url}" || true)"
      if [[ "${lab_http_code}" == "200" ]]; then
        lab_title="$(grep -o '<title>[^<]*</title>' "${page_file}" | head -n 1 || true)"
        break
      fi
      if [[ "${lab_http_code}" == "000" || "${lab_http_code}" == "404" || -z "${lab_http_code}" ]]; then
        refreshed_ingress_base_url="$(start_ingress_proxy || true)"
        if [[ -n "${refreshed_ingress_base_url}" ]]; then
          access_url="${refreshed_ingress_base_url%/}/labs/${instance_id}/"
          warn "Reported lab URL was not ready; retried with refreshed ingress URL ${access_url}"
        fi
      fi
      sleep 2
    done
  fi
  rm -f "${page_file}"

  write_result_json \
    "${first_http_code}" \
    "${first_body}" \
    "${second_http_code}" \
    "${second_body}" \
    "${final_json}" \
    "${access_url}" \
    "${lab_http_code}" \
    "${lab_title}" \
    "${db_instance_row}" \
    "${outbox_state}"

  if [[ "${final_status}" != "RUNNING" ]]; then
    error "Demo lab did not reach RUNNING"
    print_json "${final_json}" >&2
    exit 1
  fi

  if [[ "${lab_http_code}" != "200" ]]; then
    error "Demo lab URL is not reachable yet: ${access_url}"
    exit 1
  fi

  info "Demo lab is ready: ${access_url}"
  info "Result written to ${RESULT_FILE}"
}

print_summary() {
  local gateway_base_url frontend_url grafana_url zipkin_url

  gateway_base_url="$(platform_gateway_base_url)"
  frontend_url="http://127.0.0.1:3000"
  grafana_url="http://127.0.0.1:3001"
  zipkin_url="http://127.0.0.1:9411"

  print_header "Demo Ready"
  printf 'Frontend: %s\n' "${frontend_url}"
  printf 'Gateway:  %s\n' "${gateway_base_url}"
  printf 'Grafana:  %s\n' "${grafana_url}"
  printf 'Zipkin:   %s\n' "${zipkin_url}"
  printf 'Account:  %s / %s\n' "${USERNAME}" "${PASSWORD}"
  printf 'Definition: %s\n' "${VULNERABILITY_ID}"
  if [[ -f "${RESULT_FILE}" ]]; then
    printf 'Result: %s\n' "${RESULT_FILE}"
    printf 'Lab URL: %s\n' "$(jq -r '.accessUrl // empty' "${RESULT_FILE}")"
  fi
  printf 'Stop: ./stop.sh local\n'
}

main() {
  if [[ "${START_PLATFORM}" == "true" ]]; then
    print_header "Platform Startup"
    "${ROOT_DIR}/start.sh" local
  else
    warn "Skipping platform startup"
  fi

  if [[ "${ENSURE_IMAGE}" == "true" ]]; then
    ensure_demo_image
  else
    warn "Skipping demo image step"
  fi

  login_to_platform

  if [[ "${ENSURE_DEFINITION}" == "true" ]]; then
    ensure_demo_definition
  else
    warn "Skipping demo definition step"
  fi

  if [[ "${LAUNCH_LAB}" == "true" ]]; then
    launch_demo_lab
  else
    warn "Skipping demo lab launch"
  fi

  print_summary
}

main "$@"
