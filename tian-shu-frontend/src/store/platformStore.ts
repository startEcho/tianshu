"use client";

import { create } from "zustand";
import { toast } from "react-toastify";
import { api } from "@/services/api";
import type {
  CreateUserRequest,
  LabInstanceInfo,
  RoleResponse,
  UserSummaryResponse,
  VulnerabilityDefinition,
  VulnerabilityDefinitionDraft,
} from "@/types/platform";

interface PlatformStore {
  definitions: VulnerabilityDefinition[];
  labs: LabInstanceInfo[];
  users: UserSummaryResponse[];
  roles: RoleResponse[];
  loadingDefinitions: boolean;
  loadingLabs: boolean;
  loadingAdmin: boolean;
  launchingDefinitionId: string | null;
  terminatingLabIds: Record<string, boolean>;
  creatingUser: boolean;
  savingDefinitionId: string | null;
  deletingDefinitionIds: Record<string, boolean>;
  assigningRoles: Record<string, boolean>;
  fetchDefinitions: () => Promise<void>;
  createDefinition: (payload: VulnerabilityDefinitionDraft) => Promise<VulnerabilityDefinition | null>;
  updateDefinition: (id: string, payload: VulnerabilityDefinitionDraft) => Promise<VulnerabilityDefinition | null>;
  deleteDefinition: (id: string) => Promise<boolean>;
  fetchLabs: () => Promise<void>;
  launchLab: (vulnerabilityId: string) => Promise<LabInstanceInfo | null>;
  terminateLab: (instanceId: string) => Promise<boolean>;
  fetchAdminData: () => Promise<void>;
  createUser: (payload: CreateUserRequest) => Promise<boolean>;
  assignRoles: (userId: string, roles: string[]) => Promise<boolean>;
}

function sortDefinitions(definitions: VulnerabilityDefinition[]) {
  return [...definitions].sort((left, right) => left.id.localeCompare(right.id));
}

export const usePlatformStore = create<PlatformStore>((set, get) => ({
  definitions: [],
  labs: [],
  users: [],
  roles: [],
  loadingDefinitions: false,
  loadingLabs: false,
  loadingAdmin: false,
  launchingDefinitionId: null,
  terminatingLabIds: {},
  creatingUser: false,
  savingDefinitionId: null,
  deletingDefinitionIds: {},
  assigningRoles: {},
  fetchDefinitions: async () => {
    if (get().loadingDefinitions) {
      return;
    }

    set({ loadingDefinitions: true });
    try {
      const definitions = await api.getDefinitions();
      set({ definitions: sortDefinitions(definitions) });
    } catch (error) {
      console.error(error);
      toast.error("Unable to load vulnerability definitions.");
    } finally {
      set({ loadingDefinitions: false });
    }
  },
  createDefinition: async (payload) => {
    set({ savingDefinitionId: payload.id || "__new__" });
    try {
      const definition = await api.createDefinition(payload);
      set((state) => ({
        definitions: sortDefinitions([
          definition,
          ...state.definitions.filter((item) => item.id !== definition.id),
        ]),
      }));
      toast.success(`Definition ${definition.id} created.`);
      return definition;
    } catch (error) {
      console.error(error);
      toast.error("Definition creation failed.");
      return null;
    } finally {
      set({ savingDefinitionId: null });
    }
  },
  updateDefinition: async (id, payload) => {
    set({ savingDefinitionId: id });
    try {
      const definition = await api.updateDefinition(id, payload);
      set((state) => ({
        definitions: sortDefinitions(
          state.definitions.map((item) => (item.id === id ? definition : item))
        ),
      }));
      toast.success(`Definition ${definition.id} updated.`);
      return definition;
    } catch (error) {
      console.error(error);
      toast.error("Definition update failed.");
      return null;
    } finally {
      set({ savingDefinitionId: null });
    }
  },
  deleteDefinition: async (id) => {
    set((state) => ({
      deletingDefinitionIds: { ...state.deletingDefinitionIds, [id]: true },
    }));
    try {
      await api.deleteDefinition(id);
      set((state) => ({
        definitions: state.definitions.filter((item) => item.id !== id),
      }));
      toast.success(`Definition ${id} deleted.`);
      return true;
    } catch (error) {
      console.error(error);
      toast.error("Definition deletion failed.");
      return false;
    } finally {
      set((state) => ({
        deletingDefinitionIds: { ...state.deletingDefinitionIds, [id]: false },
      }));
    }
  },
  fetchLabs: async () => {
    if (get().loadingLabs) {
      return;
    }

    set({ loadingLabs: true });
    try {
      const labs = await api.getLabs();
      set({ labs });
    } catch (error) {
      console.error(error);
      toast.error("Unable to load active labs.");
    } finally {
      set({ loadingLabs: false });
    }
  },
  launchLab: async (vulnerabilityId) => {
    set({ launchingDefinitionId: vulnerabilityId });
    try {
      const launchedLab = await api.launchLab(vulnerabilityId);
      set((state) => ({
        labs: [launchedLab, ...state.labs.filter((item) => item.instanceId !== launchedLab.instanceId)],
      }));
      toast.success(`Lab ${launchedLab.instanceId} is provisioning.`);
      return launchedLab;
    } catch (error) {
      console.error(error);
      toast.error("Lab launch failed.");
      return null;
    } finally {
      set({ launchingDefinitionId: null });
    }
  },
  terminateLab: async (instanceId) => {
    set((state) => ({
      terminatingLabIds: { ...state.terminatingLabIds, [instanceId]: true },
    }));
    try {
      await api.terminateLab(instanceId);
      set((state) => ({
        labs: state.labs.filter((lab) => lab.instanceId !== instanceId),
      }));
      toast.success(`Lab ${instanceId} terminated.`);
      return true;
    } catch (error) {
      console.error(error);
      toast.error("Unable to terminate lab.");
      return false;
    } finally {
      set((state) => ({
        terminatingLabIds: { ...state.terminatingLabIds, [instanceId]: false },
      }));
    }
  },
  fetchAdminData: async () => {
    if (get().loadingAdmin) {
      return;
    }

    set({ loadingAdmin: true });
    try {
      const [users, roles] = await Promise.all([api.getUsers(), api.getRoles()]);
      set({ users, roles });
    } catch (error) {
      console.error(error);
      toast.error("Admin data could not be loaded.");
    } finally {
      set({ loadingAdmin: false });
    }
  },
  createUser: async (payload) => {
    set({ creatingUser: true });
    try {
      const user = await api.createUser(payload);
      set((state) => ({ users: [user, ...state.users] }));
      toast.success(`User ${user.username} created.`);
      return true;
    } catch (error) {
      console.error(error);
      toast.error("User creation failed.");
      return false;
    } finally {
      set({ creatingUser: false });
    }
  },
  assignRoles: async (userId, roles) => {
    set((state) => ({
      assigningRoles: { ...state.assigningRoles, [userId]: true },
    }));
    try {
      const updatedUser = await api.assignRoles(userId, roles);
      set((state) => ({
        users: state.users.map((user) => (user.userId === userId ? updatedUser : user)),
      }));
      toast.success(`Roles updated for ${updatedUser.username}.`);
      return true;
    } catch (error) {
      console.error(error);
      toast.error("Role assignment failed.");
      return false;
    } finally {
      set((state) => ({
        assigningRoles: { ...state.assigningRoles, [userId]: false },
      }));
    }
  },
}));
