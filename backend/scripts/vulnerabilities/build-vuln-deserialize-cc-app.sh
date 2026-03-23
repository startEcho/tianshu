#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
IMAGE_NAME="${IMAGE_NAME:-tianshuvuln/vuln-deserialize-cc-app}"
IMAGE_TAG="${IMAGE_TAG:-2.1.0-java8}"
LOAD_TO_MINIKUBE="${LOAD_TO_MINIKUBE:-true}"

cd "$ROOT_DIR"

echo "==> Building image ${IMAGE_NAME}:${IMAGE_TAG}"
docker build \
  -f dockerfiles/vuln-deserialize-cc-app.Dockerfile \
  -t "${IMAGE_NAME}:${IMAGE_TAG}" \
  .

if [[ "$LOAD_TO_MINIKUBE" == "true" ]]; then
  echo "==> Loading image into Minikube cache"
  minikube image load "${IMAGE_NAME}:${IMAGE_TAG}"
fi

echo "==> Done"
echo "Use dockerImageName=${IMAGE_NAME}:${IMAGE_TAG} in /admin when creating the definition."
