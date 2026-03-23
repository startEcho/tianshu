import axios, { AxiosError, AxiosHeaders, InternalAxiosRequestConfig } from "axios";
import { clearSession, readSession, writeSession } from "@/services/session";
import type {
  ClientSession,
  CreateUserRequest,
  LabInstanceInfo,
  PlatformControlResponse,
  PlatformRuntimeStatus,
  RoleResponse,
  TokenResponse,
  UserSummaryResponse,
  VulnerabilityDefinition,
  VulnerabilityDefinitionDraft,
} from "@/types/platform";

const API_GATEWAY_BASE_URL =
  (process.env.NEXT_PUBLIC_API_GATEWAY_BASE_URL || "http://127.0.0.1:8080").replace(/\/$/, "");
const API_ROOT = `${API_GATEWAY_BASE_URL}/api/v1`;

const authClient = axios.create({
  baseURL: API_ROOT,
  timeout: 15000,
  headers: {
    "Content-Type": "application/json",
  },
});

const apiClient = axios.create({
  baseURL: API_ROOT,
  timeout: 20000,
  headers: {
    "Content-Type": "application/json",
  },
});

function controlHeaders() {
  const session = readSession();
  return {
    "Content-Type": "application/json",
    ...(session?.accessToken ? { Authorization: `Bearer ${session.accessToken}` } : {}),
  };
}

async function controlRequest<T>(path: string, init?: RequestInit) {
  const response = await fetch(path, {
    ...init,
    cache: "no-store",
    headers: {
      ...controlHeaders(),
      ...(init?.headers || {}),
    },
  });

  if (!response.ok) {
    let message = "Platform control request failed.";
    try {
      const payload = (await response.json()) as { error?: string };
      if (payload.error) {
        message = payload.error;
      }
    } catch {
      // Ignore non-JSON error bodies.
    }
    throw new Error(message);
  }

  return (await response.json()) as T;
}

let refreshPromise: Promise<ClientSession | null> | null = null;

function buildSession(payload: TokenResponse): ClientSession {
  return {
    accessToken: payload.accessToken,
    refreshToken: payload.refreshToken,
    expiresAt: Date.now() + payload.expiresInSeconds * 1000,
    user: payload.user,
  };
}

function applyAuthorizationHeader(config: InternalAxiosRequestConfig, token: string) {
  const headers = AxiosHeaders.from(config.headers);
  headers.set("Authorization", `Bearer ${token}`);
  config.headers = headers;
}

apiClient.interceptors.request.use((config) => {
  const session = readSession();
  if (session?.accessToken) {
    applyAuthorizationHeader(config, session.accessToken);
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const status = error.response?.status;
    const originalRequest = error.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined;
    const currentSession = readSession();

    if (status !== 401 || !originalRequest || originalRequest._retry || !currentSession?.refreshToken) {
      return Promise.reject(error);
    }

    originalRequest._retry = true;

    if (!refreshPromise) {
      refreshPromise = authClient
        .post<TokenResponse>("/auth/refresh", { refreshToken: currentSession.refreshToken })
        .then((response) => {
          const nextSession = buildSession(response.data);
          writeSession(nextSession);
          return nextSession;
        })
        .catch(() => {
          clearSession();
          return null;
        })
        .finally(() => {
          refreshPromise = null;
        });
    }

    const refreshedSession = await refreshPromise;
    if (!refreshedSession) {
      return Promise.reject(error);
    }

    applyAuthorizationHeader(originalRequest, refreshedSession.accessToken);
    return apiClient(originalRequest);
  }
);

export const api = {
  gatewayBaseUrl: API_GATEWAY_BASE_URL,
  login: async (username: string, password: string) => {
    const response = await authClient.post<TokenResponse>("/auth/login", { username, password });
    return response.data;
  },
  logout: async (refreshToken: string) => {
    await apiClient.post("/auth/logout", { refreshToken });
  },
  me: async () => {
    const response = await apiClient.get("/auth/me");
    return response.data;
  },
  getDefinitions: async () => {
    const response = await apiClient.get<VulnerabilityDefinition[]>("/definitions");
    return response.data;
  },
  createDefinition: async (payload: VulnerabilityDefinitionDraft) => {
    const response = await apiClient.post<VulnerabilityDefinition>("/definitions", payload);
    return response.data;
  },
  updateDefinition: async (id: string, payload: VulnerabilityDefinitionDraft) => {
    const response = await apiClient.put<VulnerabilityDefinition>(`/definitions/${id}`, payload);
    return response.data;
  },
  deleteDefinition: async (id: string) => {
    await apiClient.delete(`/definitions/${id}`);
  },
  launchLab: async (vulnerabilityId: string) => {
    const response = await apiClient.post<LabInstanceInfo>("/labs", { vulnerabilityId });
    return response.data;
  },
  getLabs: async () => {
    const response = await apiClient.get<LabInstanceInfo[]>("/labs");
    return response.data;
  },
  terminateLab: async (instanceId: string) => {
    await apiClient.delete(`/labs/${instanceId}`);
  },
  getUsers: async () => {
    const response = await apiClient.get<UserSummaryResponse[]>("/users");
    return response.data;
  },
  getRoles: async () => {
    const response = await apiClient.get<RoleResponse[]>("/roles");
    return response.data;
  },
  createUser: async (payload: CreateUserRequest) => {
    const response = await apiClient.post<UserSummaryResponse>("/users", payload);
    return response.data;
  },
  assignRoles: async (userId: string, roles: string[]) => {
    const response = await apiClient.put<UserSummaryResponse>(`/users/${userId}/roles`, { roles });
    return response.data;
  },
  getPlatformRuntimeStatus: async () => {
    return controlRequest<PlatformRuntimeStatus>("/api/platform-control/status");
  },
  startPlatform: async (mode: "quick" | "rebuild") => {
    return controlRequest<PlatformControlResponse>("/api/platform-control/start", {
      method: "POST",
      body: JSON.stringify({ mode }),
    });
  },
  restartPlatform: async (mode: "quick" | "rebuild") => {
    return controlRequest<PlatformControlResponse>("/api/platform-control/restart", {
      method: "POST",
      body: JSON.stringify({ mode }),
    });
  },
  stopPlatform: async (options: { keepFrontend: boolean; stopMinikube: boolean; stopInfrastructure: boolean }) => {
    return controlRequest<PlatformControlResponse>("/api/platform-control/stop", {
      method: "POST",
      body: JSON.stringify(options),
    });
  },
  observabilityLinks: {
    grafana: "http://127.0.0.1:3001",
    prometheus: "http://127.0.0.1:9090",
    zipkin: "http://127.0.0.1:9411",
    nacos: "http://127.0.0.1:8848/nacos",
  },
};
