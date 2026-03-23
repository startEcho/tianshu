export interface AuthUser {
  userId: string;
  username: string;
  displayName: string;
  roles: string[];
  authorities: string[];
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
  user: AuthUser;
}

export interface ClientSession {
  accessToken: string;
  refreshToken: string;
  expiresAt: number;
  user: AuthUser;
}

export interface VulnerabilityDefinition {
  id: string;
  name: string;
  description: string;
  category: string;
  difficulty: string;
  dockerImageName: string;
  containerPort: number;
  exploitationGuide?: string;
  tags: string[];
  flagFormat?: string;
}

export interface VulnerabilityDefinitionDraft {
  id: string;
  name: string;
  description: string;
  category: string;
  difficulty: string;
  dockerImageName: string;
  containerPort: number;
  exploitationGuide?: string;
  tags: string[];
  flagFormat?: string;
}

export interface LabInstanceInfo {
  instanceId: string;
  vulnerabilityId: string;
  accessUrl: string;
  status: string;
  ownerUserId?: string;
  ownerUsername?: string;
  createdAt?: string;
  terminatedAt?: string | null;
}

export interface CreateUserRequest {
  username: string;
  password: string;
  displayName: string;
  roles: string[];
  enabled: boolean;
}

export interface UserSummaryResponse {
  userId: string;
  username: string;
  displayName: string;
  enabled: boolean;
  roles: string[];
  authorities: string[];
  createdAt: string;
  updatedAt: string;
}

export interface RoleResponse {
  code: string;
  description: string;
  permissions: string[];
}

export type PlatformProcessState = "running" | "external" | "stopped";
export type PlatformHealthState = "UP" | "DOWN";
export type PlatformOverallState = "running" | "partial" | "stopped";

export interface PlatformServiceStatus {
  name: string;
  label: string;
  url?: string;
  processState: PlatformProcessState;
  health: PlatformHealthState;
  pid?: number;
}

export interface PlatformContainerStatus {
  name: string;
  containerName: string;
  status: string;
}

export interface PlatformInfrastructureStatus {
  dockerAvailable: boolean;
  dockerRunning: boolean;
  minikubeRunning: boolean;
  minikubeSummary: string;
  containers: PlatformContainerStatus[];
}

export interface PlatformRuntimeStatus {
  generatedAt: string;
  overallState: PlatformOverallState;
  backendRoot: string;
  envFile: string;
  runtimeDir: string;
  frontendDir: string;
  gatewayBaseUrl: string;
  apiBaseUrl: string;
  ingressBaseUrl?: string | null;
  runtimeEnv: Record<string, string>;
  services: PlatformServiceStatus[];
  infrastructure: PlatformInfrastructureStatus;
}

export interface PlatformControlResponse {
  accepted: boolean;
  action: "start" | "restart" | "stop";
  mode?: "quick" | "rebuild";
  message: string;
}
