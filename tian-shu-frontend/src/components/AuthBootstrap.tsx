"use client";

import { useEffect, startTransition } from "react";
import { useAuthStore } from "@/store/authStore";

export default function AuthBootstrap() {
  const hydrate = useAuthStore((state) => state.hydrate);
  const refreshProfile = useAuthStore((state) => state.refreshProfile);
  const hydrated = useAuthStore((state) => state.hydrated);
  const status = useAuthStore((state) => state.status);

  useEffect(() => {
    hydrate();
  }, [hydrate]);

  useEffect(() => {
    if (!hydrated || status !== "authenticated") {
      return;
    }

    startTransition(() => {
      void refreshProfile();
    });
  }, [hydrated, refreshProfile, status]);

  return null;
}
