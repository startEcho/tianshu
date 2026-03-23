#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CONFIG_DIR="${ROOT_DIR}/ops/nacos/configs"

NACOS_SERVER_ADDR="${NACOS_SERVER_ADDR:-127.0.0.1:8848}"
NACOS_NAMESPACE="${NACOS_NAMESPACE:-public}"
NACOS_GROUP="${NACOS_GROUP:-DEFAULT_GROUP}"
NACOS_USERNAME="${NACOS_USERNAME:-nacos}"
NACOS_PASSWORD="${NACOS_PASSWORD:-nacos}"

publish_config() {
  local data_id="$1"
  local file_path="$2"

  echo "Publishing ${data_id} ..."
  curl -fsS -X POST "http://${NACOS_SERVER_ADDR}/nacos/v1/cs/configs" \
    --data-urlencode "dataId=${data_id}" \
    --data-urlencode "group=${NACOS_GROUP}" \
    --data-urlencode "tenant=${NACOS_NAMESPACE}" \
    --data-urlencode "type=yaml" \
    --data-urlencode "username=${NACOS_USERNAME}" \
    --data-urlencode "password=${NACOS_PASSWORD}" \
    --data-urlencode "content@${file_path}" >/dev/null
}

publish_config "vulnerability-definition-service.yaml" "${CONFIG_DIR}/vulnerability-definition-service.yaml"
publish_config "lab-orchestration-service.yaml" "${CONFIG_DIR}/lab-orchestration-service.yaml"
publish_config "auth-service.yaml" "${CONFIG_DIR}/auth-service.yaml"
publish_config "gateway-service.yaml" "${CONFIG_DIR}/gateway-service.yaml"

echo "Nacos config sync finished."
