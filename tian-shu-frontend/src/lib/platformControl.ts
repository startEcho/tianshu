import { createHmac, timingSafeEqual } from "crypto";
import { promises as fs } from "fs";
import path from "path";
import { execFile, spawn } from "child_process";
import { promisify } from "util";
import type { PlatformRuntimeStatus, PlatformServiceStatus } from "@/types/platform";

const execFileAsync = promisify(execFile);

const FRONTEND_ROOT = process.cwd();
const BACKEND_ROOT = process.env.TIANSHU_BACKEND_ROOT || path.resolve(FRONTEND_ROOT, "../backend");
const ENV_FILE = process.env.TIANSHU_ENV_FILE || path.join(BACKEND_ROOT, ".env.infrastructure");
const RUNTIME_DIR = path.join(BACKEND_ROOT, ".platform-runtime");
const PID_DIR = path.join(RUNTIME_DIR, "pids");
const RUNTIME_ENV_FILE = path.join(RUNTIME_DIR, "platform.env");
const LOG_DIR = path.join(BACKEND_ROOT, "logs");

const SERVICE_DEFINITIONS = [
  { name: "frontend", label: "Frontend", url: "http://127.0.0.1:3000/login", mode: "health" as const },
  { name: "gateway-service", label: "Gateway", url: "__gateway__", mode: "health" as const },
  { name: "auth-service", label: "Auth Service", url: "http://127.0.0.1:8083/actuator/health", mode: "health" as const },
  {
    name: "vulnerability-definition-service",
    label: "Definition Service",
    url: "http://127.0.0.1:8081/actuator/health",
    mode: "health" as const,
  },
  {
    name: "lab-orchestration-service",
    label: "Lab Orchestration",
    url: "http://127.0.0.1:8082/actuator/health",
    mode: "health" as const,
  },
  { name: "minikube-ingress-proxy", label: "Ingress Proxy", url: "__ingress__", mode: "reachability" as const },
];

const INFRA_CONTAINERS = [
  { name: "PostgreSQL", containerName: "tianshu-postgres" },
  { name: "Redis", containerName: "tianshu-redis" },
  { name: "Nacos", containerName: "tianshu-nacos" },
  { name: "Prometheus", containerName: "tianshu-prometheus" },
  { name: "Grafana", containerName: "tianshu-grafana" },
  { name: "Loki", containerName: "tianshu-loki" },
  { name: "Promtail", containerName: "tianshu-promtail" },
  { name: "Zipkin", containerName: "tianshu-zipkin" },
];

type PlatformControlAction = "start" | "restart" | "stop";

export class PlatformControlError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

function normalizeBaseUrl(value: string | undefined, fallback: string) {
  return (value || fallback).replace(/\/$/, "");
}

async function readTextFile(filePath: string) {
  try {
    return await fs.readFile(filePath, "utf8");
  } catch {
    return "";
  }
}

function parseEnv(content: string) {
  const result: Record<string, string> = {};
  for (const line of content.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) {
      continue;
    }

    const separatorIndex = trimmed.indexOf("=");
    if (separatorIndex <= 0) {
      continue;
    }

    const key = trimmed.slice(0, separatorIndex).trim();
    const value = trimmed.slice(separatorIndex + 1).trim();
    result[key] = value;
  }
  return result;
}

async function loadPlatformEnv() {
  const [baseEnvText, runtimeEnvText] = await Promise.all([
    readTextFile(ENV_FILE),
    readTextFile(RUNTIME_ENV_FILE),
  ]);

  return {
    ...parseEnv(baseEnvText),
    ...parseEnv(runtimeEnvText),
  };
}

function decodeBase64Url(input: string) {
  const normalized = input.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=");
  return Buffer.from(padded, "base64");
}

function claimArray(payload: Record<string, unknown>, key: string) {
  const value = payload[key];
  return Array.isArray(value) ? value.map((entry) => String(entry)) : [];
}

function verifySignedToken(token: string, secret: string, issuer: string) {
  const segments = token.split(".");
  if (segments.length !== 3) {
    throw new PlatformControlError(401, "Malformed operator token.");
  }

  const [headerEncoded, payloadEncoded, signatureEncoded] = segments;
  const signingInput = `${headerEncoded}.${payloadEncoded}`;
  const expectedSignature = createHmac("sha256", secret).update(signingInput).digest();
  const providedSignature = decodeBase64Url(signatureEncoded);

  if (
    expectedSignature.length !== providedSignature.length ||
    !timingSafeEqual(expectedSignature, providedSignature)
  ) {
    throw new PlatformControlError(401, "Operator token signature is invalid.");
  }

  const payload = JSON.parse(decodeBase64Url(payloadEncoded).toString("utf8")) as Record<string, unknown>;
  if (payload.iss !== issuer) {
    throw new PlatformControlError(401, "Operator token issuer is invalid.");
  }

  if ((payload.token_type || payload.tokenType) !== "access") {
    throw new PlatformControlError(401, "Operator token must be an access token.");
  }

  // Deliberately do not reject on exp here: the control console must be able to
  // bring the platform back after auth-service has been stopped.

  return payload;
}

export async function requireAdminOperatorAccess(authorizationHeader: string | null) {
  if (!authorizationHeader?.startsWith("Bearer ")) {
    throw new PlatformControlError(401, "Admin access token is required.");
  }

  const platformEnv = await loadPlatformEnv();
  const jwtSecret = platformEnv.JWT_SECRET;
  if (!jwtSecret) {
    throw new PlatformControlError(500, "JWT secret could not be loaded for operator verification.");
  }

  const payload = verifySignedToken(
    authorizationHeader.slice("Bearer ".length).trim(),
    jwtSecret,
    platformEnv.JWT_ISSUER || "tianshu-platform"
  );

  const roles = claimArray(payload, "roles");
  const authorities = claimArray(payload, "authorities");
  if (!roles.includes("ADMIN") && !authorities.includes("user:read")) {
    throw new PlatformControlError(403, "Only platform administrators may control runtime operations.");
  }

  return {
    subject: String(payload.sub || ""),
    roles,
    authorities,
  };
}

async function isProcessRunning(pid: number) {
  try {
    process.kill(pid, 0);
    return true;
  } catch {
    return false;
  }
}

async function pidFor(name: string) {
  try {
    const raw = await fs.readFile(path.join(PID_DIR, `${name}.pid`), "utf8");
    const parsed = Number.parseInt(raw.trim(), 10);
    return Number.isFinite(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

async function probeUrl(url: string, mode: "health" | "reachability") {
  try {
    const response = await fetch(url, {
      method: "GET",
      cache: "no-store",
      signal: AbortSignal.timeout(2500),
    });
    return mode === "health" ? response.ok : true;
  } catch {
    return false;
  }
}

async function execCommand(command: string, args: string[], timeout = 5000) {
  try {
    const result = await execFileAsync(command, args, {
      cwd: BACKEND_ROOT,
      timeout,
      maxBuffer: 1024 * 1024,
    });
    return {
      ok: true,
      stdout: result.stdout.trim(),
      stderr: result.stderr.trim(),
    };
  } catch (error) {
    const failure = error as Error & { stdout?: string; stderr?: string };
    return {
      ok: false,
      stdout: failure.stdout?.trim() || "",
      stderr: failure.stderr?.trim() || failure.message,
    };
  }
}

async function collectServiceStatus(
  gatewayBaseUrl: string,
  ingressBaseUrl: string | null
): Promise<PlatformServiceStatus[]> {
  return Promise.all(
    SERVICE_DEFINITIONS.map(async (service) => {
      const url =
        service.url === "__gateway__"
          ? `${gatewayBaseUrl}/actuator/health`
          : service.url === "__ingress__"
            ? ingressBaseUrl || undefined
            : service.url;

      const pid = await pidFor(service.name);
      const pidRunning = pid ? await isProcessRunning(pid) : false;
      const healthUp = url ? await probeUrl(url, service.mode) : false;

      return {
        name: service.name,
        label: service.label,
        url,
        pid: pid || undefined,
        processState: pidRunning ? "running" : healthUp ? "external" : "stopped",
        health: healthUp ? "UP" : "DOWN",
      };
    })
  );
}

async function collectInfrastructureStatus() {
  const dockerVersion = await execCommand("docker", ["version", "--format", "{{.Client.Version}}"]);
  const dockerAvailable = dockerVersion.ok;

  const containers = await Promise.all(
    INFRA_CONTAINERS.map(async (container) => {
      const result = await execCommand("docker", ["inspect", "--format", "{{.State.Status}}", container.containerName], 4000);
      return {
        ...container,
        status: result.ok ? result.stdout || "running" : "absent",
      };
    })
  );

  const minikubeResult = await execCommand("minikube", ["status"], 8000);
  const minikubeSummary = [minikubeResult.stdout, minikubeResult.stderr].filter(Boolean).join("\n").trim() || "Stopped";
  const minikubeRunning = /host:\s+Running/i.test(minikubeSummary) || /apiserver:\s+Running/i.test(minikubeSummary);

  return {
    dockerAvailable,
    dockerRunning: dockerAvailable && containers.some((container) => container.status === "running"),
    minikubeRunning,
    minikubeSummary,
    containers,
  };
}

export async function getPlatformRuntimeStatus(): Promise<PlatformRuntimeStatus> {
  const platformEnv = await loadPlatformEnv();
  const gatewayBaseUrl = normalizeBaseUrl(
    platformEnv.PLATFORM_GATEWAY_BASE_URL || platformEnv.NEXT_PUBLIC_API_GATEWAY_BASE_URL,
    "http://127.0.0.1:8080"
  );
  const ingressBaseUrl = platformEnv.PLATFORM_INGRESS_BASE_URL || null;

  const [services, infrastructure] = await Promise.all([
    collectServiceStatus(gatewayBaseUrl, ingressBaseUrl),
    collectInfrastructureStatus(),
  ]);

  const coreServicesUp = services
    .filter((service) => service.name !== "minikube-ingress-proxy")
    .every((service) => service.health === "UP");
  const anythingUp = services.some((service) => service.health === "UP" || service.processState !== "stopped");

  return {
    generatedAt: new Date().toISOString(),
    overallState:
      coreServicesUp && infrastructure.dockerRunning && infrastructure.minikubeRunning
        ? "running"
        : anythingUp || infrastructure.dockerRunning || infrastructure.minikubeRunning
          ? "partial"
          : "stopped",
    backendRoot: BACKEND_ROOT,
    envFile: ENV_FILE,
    runtimeDir: RUNTIME_DIR,
    frontendDir: FRONTEND_ROOT,
    gatewayBaseUrl,
    apiBaseUrl: `${gatewayBaseUrl}/api/v1`,
    ingressBaseUrl,
    runtimeEnv: platformEnv,
    services,
    infrastructure,
  };
}

async function spawnScript(
  scriptName: string,
  logFileName: string,
  env: Record<string, string>
) {
  await fs.mkdir(LOG_DIR, { recursive: true });
  const logHandle = await fs.open(path.join(LOG_DIR, logFileName), "a");

  const child = spawn(path.join(BACKEND_ROOT, scriptName), [], {
    cwd: BACKEND_ROOT,
    env: {
      ...process.env,
      TIANSHU_FRONTEND_DIR: FRONTEND_ROOT,
      ...env,
    },
    detached: true,
    stdio: ["ignore", logHandle.fd, logHandle.fd],
  });

  child.unref();
  await logHandle.close();
}

export async function queuePlatformAction(
  action: PlatformControlAction,
  options: {
    mode?: "quick" | "rebuild";
    keepFrontend?: boolean;
    stopInfrastructure?: boolean;
    stopMinikube?: boolean;
  }
) {
  if (action === "start") {
    await spawnScript("start.sh", "platform-control-start.log", {
      SKIP_BACKEND_BUILD: options.mode === "quick" ? "true" : "false",
      SKIP_FRONTEND_BUILD: "true",
    });
    return {
      accepted: true,
      action,
      mode: options.mode,
      message:
        options.mode === "quick"
          ? "Quick start queued. Backend build is skipped and the control console stays online."
          : "Rebuild start queued. Backend artifacts will be rebuilt before services come up.",
    };
  }

  if (action === "restart") {
    await spawnScript("restart.sh", "platform-control-restart.log", {
      SKIP_BACKEND_BUILD: options.mode === "quick" ? "true" : "false",
      SKIP_FRONTEND_BUILD: "true",
      STOP_FRONTEND: "false",
    });
    return {
      accepted: true,
      action,
      mode: options.mode,
      message:
        options.mode === "quick"
          ? "Runtime restart queued. The control console will remain online."
          : "Rebuild restart queued. Backend artifacts will rebuild before services restart.",
    };
  }

  await spawnScript("stop.sh", "platform-control-stop.log", {
    STOP_FRONTEND: options.keepFrontend === false ? "true" : "false",
    STOP_INFRA: options.stopInfrastructure === false ? "false" : "true",
    STOP_MINIKUBE: options.stopMinikube === false ? "false" : "true",
  });

  return {
    accepted: true,
    action,
    message:
      options.keepFrontend === false
        ? "Full shutdown queued. This control console will go offline shortly."
        : "Runtime stop queued. The control console will remain online so you can start the platform again.",
  };
}
