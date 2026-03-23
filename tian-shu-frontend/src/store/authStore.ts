"use client";

import { create } from "zustand";
import { toast } from "react-toastify";
import { api } from "@/services/api";
import { clearSession, readSession, writeSession } from "@/services/session";
import type { AuthUser, ClientSession } from "@/types/platform";

type AuthStatus = "booting" | "anonymous" | "authenticating" | "authenticated";

interface AuthStore {
  status: AuthStatus;
  session: ClientSession | null;
  hydrated: boolean;
  error: string | null;
  hydrate: () => void;
  login: (username: string, password: string) => Promise<boolean>;
  logout: () => Promise<void>;
  refreshProfile: () => Promise<void>;
  updateUser: (user: AuthUser) => void;
}

export const useAuthStore = create<AuthStore>((set, get) => ({
  status: "booting",
  session: null,
  hydrated: false,
  error: null,
  hydrate: () => {
    const session = readSession();
    set({
      session,
      hydrated: true,
      status: session ? "authenticated" : "anonymous",
      error: null,
    });
  },
  login: async (username, password) => {
    set({ status: "authenticating", error: null });
    try {
      const payload = await api.login(username, password);
      const session: ClientSession = {
        accessToken: payload.accessToken,
        refreshToken: payload.refreshToken,
        expiresAt: Date.now() + payload.expiresInSeconds * 1000,
        user: payload.user,
      };
      writeSession(session);
      set({
        session,
        status: "authenticated",
        hydrated: true,
        error: null,
      });
      toast.success(`Welcome back, ${payload.user.displayName}.`);
      return true;
    } catch (error) {
      const message =
        error instanceof Error ? error.message : "Unable to sign in with the provided credentials.";
      set({
        error: message,
        status: "anonymous",
        hydrated: true,
      });
      toast.error("Authentication failed. Check the demo credentials or backend availability.");
      return false;
    }
  },
  logout: async () => {
    const session = get().session ?? readSession();
    try {
      if (session?.refreshToken) {
        await api.logout(session.refreshToken);
      }
    } catch {
      // The client should still clear local state if server-side logout fails.
    } finally {
      clearSession();
      set({
        session: null,
        status: "anonymous",
        hydrated: true,
        error: null,
      });
      toast.info("Session cleared.");
    }
  },
  refreshProfile: async () => {
    const session = get().session ?? readSession();
    if (!session) {
      set({ status: "anonymous", hydrated: true, session: null });
      return;
    }

    try {
      const profile = await api.me();
      const nextSession = {
        ...session,
        user: profile,
      };
      writeSession(nextSession);
      set({
        session: nextSession,
        status: "authenticated",
        hydrated: true,
        error: null,
      });
    } catch {
      clearSession();
      set({
        session: null,
        status: "anonymous",
        hydrated: true,
        error: "Session expired. Please sign in again.",
      });
    }
  },
  updateUser: (user) => {
    const session = get().session;
    if (!session) {
      return;
    }
    const nextSession = { ...session, user };
    writeSession(nextSession);
    set({ session: nextSession });
  },
}));
