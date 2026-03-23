# TianShu Platform

TianShu is a full-stack exploit lab control plane built for interview-grade microservice demonstrations.
It combines gateway-based authentication, RBAC, PostgreSQL persistence, Redis-backed refresh tokens,
Nacos configuration, Prometheus/Grafana/Loki/Zipkin observability, and Kubernetes-backed lab environments.

## Platform Shape

Core services:

- `gateway-service`: unified API entrypoint, JWT validation, CORS, route dispatch
- `auth-service`: login, refresh, logout, user/role/permission management
- `vulnerability-definition-service`: curated vulnerability catalog
- `lab-orchestration-service`: lab lifecycle, ownership checks, Kubernetes resource creation

Infrastructure:

- `PostgreSQL`
- `Redis`
- `Nacos`
- `Prometheus`
- `Grafana`
- `Loki`
- `Promtail`
- `Zipkin`
- `Minikube + ingress-nginx`

Frontend:

- sibling project at `../tian-shu-frontend`

## Quick Start

Prerequisites:

- JDK 17
- Maven Wrapper compatible runtime
- Node.js and npm
- Docker Desktop
- Minikube
- kubectl

You do not need to install `Nacos`, `PostgreSQL`, `Redis`, `Prometheus`, `Grafana`, `Loki`, or `Zipkin` manually.
`./start.sh` starts them from Docker images and pulls them on first run.

Start the complete local platform:

```bash
./start.sh
```

The startup script now waits for:

- infrastructure health checks
- backend service health endpoints
- frontend startup
- authenticated gateway routes used by the operator UI

It also prints a seed lab image audit at the end. A `Platform Ready` banner means the core platform is ready, but any lab image reported as missing still needs to be built, pulled, or loaded into Minikube before that lab can launch.

Check runtime status:

```bash
./status.sh
```

Stop the complete local platform:

```bash
./stop.sh
```

Restart the complete local platform:

```bash
./restart.sh
```

## Default URLs

- Frontend: `http://127.0.0.1:3000`
- Gateway: `http://127.0.0.1:8080`
- Auth: `http://127.0.0.1:8083`
- Vulnerability Definition Service: `http://127.0.0.1:8081`
- Lab Orchestration Service: `http://127.0.0.1:8082`
- Grafana: `http://127.0.0.1:3001`
- Prometheus: `http://127.0.0.1:9090`
- Zipkin: `http://127.0.0.1:9411`
- RabbitMQ: `http://127.0.0.1:15672`
- Nacos: `http://127.0.0.1:8848/nacos`

The lab access base URL is discovered dynamically from Minikube and written to:

- `.platform-runtime/platform.env`

The public gateway base URL is configured through:

- `.env.infrastructure`
- `.platform-runtime/platform.env`

## Default Accounts

- `admin / Admin123456`
- `trainer / Trainer123456`
- `student / Student123456`

## Main Scripts

- `./start.sh`: local full-platform startup entrypoint
- `./stop.sh`: local full-platform shutdown entrypoint
- `./status.sh`: local runtime status summary
- `./restart.sh`: local full-platform restart entrypoint
- `./scripts/platform/status-local-platform.sh`: underlying runtime status and health summary
- `./scripts/api/platform-flow.sh`: CLI smoke flow through the gateway
- `./scripts/nacos/publish-local-configs.sh`: publish local Nacos config set
- `./start.sh --mode k8s`: legacy Minikube manifest deployment entrypoint
- `./stop.sh --mode k8s`: legacy Minikube manifest cleanup entrypoint
- `./status.sh --mode k8s`: legacy Minikube manifest status summary

## Important Notes

- The frontend project is not inside this repository root. By default the startup script expects `../tian-shu-frontend`.
- Lab URLs are not hardcoded. The local startup script discovers a host-accessible ingress controller URL from Minikube and injects it into `lab-orchestration-service`.
- Gateway URLs are also configurable. Update `PLATFORM_GATEWAY_BASE_URL` in `.env.infrastructure` if your local gateway host or port differs from the default.
- If you want to stop backend, infra, and Kubernetes lab resources but keep the frontend operator console online, run `STOP_FRONTEND=false ./stop.sh`.
- The admin console at `/admin` can inspect runtime state, trigger quick start or rebuild start, restart the runtime, stop the runtime while keeping the frontend alive, and perform a full shutdown.
- First startup depends on Docker being able to pull infrastructure images. If your local Docker cannot reach the registry, configure a mirror or pre-pull the required images once.
- If Minikube or Docker images are cold, first startup can take noticeably longer because Kubernetes base images and vulnerability images must be pulled.
- Seed vulnerability definitions can exist even when their Docker images are not present locally. In that case the catalog is visible, but launching those labs will fail until the matching images are built or loaded into Minikube.
- Stopping the platform removes active lab Kubernetes resources, but PostgreSQL data remains unless you explicitly remove Docker volumes.

## Documentation

- [Project Architecture](docs/project-architecture.md)
- [Local Platform Runbook](docs/local-platform-runbook.md)
- [Security And Observability Notes](docs/platform-security-observability.md)
- [Vulnerability Authoring](docs/vulnerability-authoring.md)
- [Core Lab Orchestration Algorithm (ZH)](docs/core-lab-orchestration-algorithm-zh.md)
