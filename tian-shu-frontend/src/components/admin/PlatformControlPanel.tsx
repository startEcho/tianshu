"use client";

import { useEffect, useMemo, useState } from "react";
import { toast } from "react-toastify";
import { api } from "@/services/api";
import { useAuthStore } from "@/store/authStore";
import type { PlatformOverallState, PlatformRuntimeStatus } from "@/types/platform";

function overallTone(state: PlatformOverallState) {
  if (state === "running") {
    return "border-emerald-400/40 bg-emerald-400/12 text-emerald-200";
  }
  if (state === "partial") {
    return "border-amber-400/40 bg-amber-400/12 text-amber-100";
  }
  return "border-white/10 bg-white/6 text-[var(--muted)]";
}

function serviceTone(value: "UP" | "DOWN" | "running" | "external" | "stopped") {
  if (value === "UP" || value === "running") {
    return "text-emerald-200";
  }
  if (value === "external") {
    return "text-sky-200";
  }
  return "text-rose-200";
}

export default function PlatformControlPanel() {
  const session = useAuthStore((state) => state.session);
  const [status, setStatus] = useState<PlatformRuntimeStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [busyAction, setBusyAction] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const isAdmin = useMemo(() => session?.user.roles.includes("ADMIN") ?? false, [session?.user.roles]);

  const refreshStatus = async (silent = false) => {
    if (!silent) {
      setLoading(true);
    }

    try {
      const nextStatus = await api.getPlatformRuntimeStatus();
      setStatus(nextStatus);
      setError(null);
    } catch (refreshError) {
      console.error(refreshError);
      const message =
        refreshError instanceof Error ? refreshError.message : "Runtime state could not be inspected.";
      setError(message);
      if (!silent) {
        toast.error(message);
      }
    } finally {
      if (!silent) {
        setLoading(false);
      }
    }
  };

  useEffect(() => {
    void refreshStatus();
    const intervalId = window.setInterval(() => {
      void refreshStatus(true);
    }, 10000);

    return () => window.clearInterval(intervalId);
  }, []);

  const scheduleFollowUpRefresh = () => {
    [1200, 5000, 10000].forEach((delay) => {
      window.setTimeout(() => {
        void refreshStatus(true);
      }, delay);
    });
  };

  const performAction = async (
    actionId: string,
    callback: () => Promise<{ message: string }>,
    confirmMessage?: string
  ) => {
    if (confirmMessage && !window.confirm(confirmMessage)) {
      return;
    }

    setBusyAction(actionId);
    try {
      const response = await callback();
      toast.success(response.message);
      scheduleFollowUpRefresh();
    } catch (actionError) {
      console.error(actionError);
      toast.error(actionError instanceof Error ? actionError.message : "Platform action failed.");
    } finally {
      setBusyAction(null);
    }
  };

  if (!isAdmin) {
    return (
      <section className="surface rounded-[1.75rem] p-6">
        <p className="eyebrow">Runtime orchestrator</p>
        <h2 className="mt-3 text-3xl font-semibold text-white">Platform control stays admin-only</h2>
        <p className="mt-4 max-w-2xl text-sm leading-7 text-[var(--muted)]">
          Runtime start, stop, and restart actions are signed locally and restricted to the `ADMIN` role because they
          affect Docker, Minikube, and the backend services on this machine.
        </p>
      </section>
    );
  }

  return (
    <section className="surface-elevated rounded-[1.9rem] p-6 sm:p-8">
      <div className="flex flex-col gap-6 lg:flex-row lg:items-start lg:justify-between">
        <div className="max-w-3xl">
          <p className="eyebrow text-[var(--accent)]">Runtime orchestrator</p>
          <h2 className="hero-title mt-4 text-4xl text-white sm:text-5xl">Operate the platform without leaving the UI</h2>
          <p className="mt-4 text-sm leading-8 text-[var(--muted)]">
            This console drives the same `start.sh`, `restart.sh`, and `stop.sh` scripts from the frontend. The default
            stop action keeps this operator panel online so you can bring the runtime back without reopening a shell.
          </p>
        </div>
        <div className="flex flex-col items-start gap-3">
          <span className={`rounded-full border px-4 py-2 text-xs font-semibold uppercase tracking-[0.24em] ${overallTone(status?.overallState || "stopped")}`}>
            {loading ? "Checking" : status?.overallState || "stopped"}
          </span>
          <button
            type="button"
            className="action-button secondary"
            disabled={busyAction !== null}
            onClick={() => void refreshStatus()}
          >
            {loading ? "Refreshing..." : "Refresh status"}
          </button>
        </div>
      </div>

      {error ? (
        <div className="mt-6 rounded-[1.4rem] border border-rose-400/20 bg-rose-500/10 px-4 py-3 text-sm text-rose-100">
          {error}
        </div>
      ) : null}

      <div className="mt-8 data-grid three">
        <article className="surface rounded-[1.6rem] p-5">
          <p className="eyebrow">Routes</p>
          <div className="mt-4 space-y-3 text-sm text-[var(--muted)]">
            <div>
              <p className="text-xs uppercase tracking-[0.2em]">Gateway</p>
              <p className="mt-1 break-all text-white">{status?.gatewayBaseUrl || api.gatewayBaseUrl}</p>
            </div>
            <div>
              <p className="text-xs uppercase tracking-[0.2em]">API root</p>
              <p className="mt-1 break-all text-white">{status?.apiBaseUrl || `${api.gatewayBaseUrl}/api/v1`}</p>
            </div>
            <div>
              <p className="text-xs uppercase tracking-[0.2em]">Lab ingress</p>
              <p className="mt-1 break-all text-white">{status?.ingressBaseUrl || "Not resolved yet"}</p>
            </div>
          </div>
        </article>

        <article className="surface rounded-[1.6rem] p-5">
          <p className="eyebrow">Infrastructure</p>
          <div className="mt-4 grid gap-3 text-sm text-[var(--muted)]">
            <div className="flex items-center justify-between gap-4">
              <span>Docker</span>
              <span className={serviceTone(status?.infrastructure.dockerRunning ? "running" : "stopped")}>
                {status?.infrastructure.dockerRunning ? "Running" : status?.infrastructure.dockerAvailable ? "Idle" : "Unavailable"}
              </span>
            </div>
            <div className="flex items-center justify-between gap-4">
              <span>Minikube</span>
              <span className={serviceTone(status?.infrastructure.minikubeRunning ? "running" : "stopped")}>
                {status?.infrastructure.minikubeRunning ? "Running" : "Stopped"}
              </span>
            </div>
            <div className="flex items-center justify-between gap-4">
              <span>Infra containers</span>
              <span className="text-white">
                {status?.infrastructure.containers.filter((item) => item.status === "running").length || 0}/
                {status?.infrastructure.containers.length || 0}
              </span>
            </div>
          </div>
        </article>

        <article className="surface rounded-[1.6rem] p-5">
          <p className="eyebrow">Execution modes</p>
          <div className="mt-4 space-y-3 text-sm leading-7 text-[var(--muted)]">
            <p>
              `Quick start` skips backend rebuilds and is the fastest path after a stop.
            </p>
            <p>
              `Rebuild start` recompiles backend artifacts before the runtime comes back.
            </p>
            <p>
              `Full shutdown` stops this frontend too. After that, startup must come from the shell.
            </p>
          </div>
        </article>
      </div>

      <div className="mt-8 flex flex-wrap gap-3">
        <button
          type="button"
          className="action-button"
          disabled={busyAction !== null}
          onClick={() =>
            void performAction("start-quick", () => api.startPlatform("quick"))
          }
        >
          {busyAction === "start-quick" ? "Queueing..." : "Quick start"}
        </button>
        <button
          type="button"
          className="action-button secondary"
          disabled={busyAction !== null}
          onClick={() =>
            void performAction("start-rebuild", () => api.startPlatform("rebuild"))
          }
        >
          {busyAction === "start-rebuild" ? "Queueing..." : "Rebuild start"}
        </button>
        <button
          type="button"
          className="action-button secondary"
          disabled={busyAction !== null}
          onClick={() =>
            void performAction("restart", () => api.restartPlatform("quick"))
          }
        >
          {busyAction === "restart" ? "Queueing..." : "Restart runtime"}
        </button>
        <button
          type="button"
          className="action-button secondary"
          disabled={busyAction !== null}
          onClick={() =>
            void performAction("stop-runtime", () =>
              api.stopPlatform({
                keepFrontend: true,
                stopInfrastructure: true,
                stopMinikube: true,
              })
            )
          }
        >
          {busyAction === "stop-runtime" ? "Queueing..." : "Stop runtime"}
        </button>
        <button
          type="button"
          className="action-button secondary"
          disabled={busyAction !== null}
          onClick={() =>
            void performAction(
              "stop-full",
              () =>
                api.stopPlatform({
                  keepFrontend: false,
                  stopInfrastructure: true,
                  stopMinikube: true,
                }),
              "This will stop the frontend control console itself. Continue?"
            )
          }
        >
          {busyAction === "stop-full" ? "Queueing..." : "Full shutdown"}
        </button>
      </div>

      <div className="mt-8 overflow-x-auto">
        <table className="min-w-full border-separate border-spacing-y-3 text-left text-sm">
          <thead>
            <tr className="text-[var(--muted)]">
              <th className="px-4">Service</th>
              <th className="px-4">Process</th>
              <th className="px-4">Health</th>
              <th className="px-4">Endpoint</th>
            </tr>
          </thead>
          <tbody>
            {(status?.services || []).map((service) => (
              <tr key={service.name} className="surface">
                <td className="rounded-l-[1rem] px-4 py-4">
                  <p className="font-semibold text-white">{service.label}</p>
                  <p className="mt-1 text-xs text-[var(--muted)]">{service.name}</p>
                </td>
                <td className={`px-4 py-4 font-medium uppercase tracking-[0.18em] ${serviceTone(service.processState)}`}>
                  {service.processState}
                </td>
                <td className={`px-4 py-4 font-medium uppercase tracking-[0.18em] ${serviceTone(service.health)}`}>
                  {service.health}
                </td>
                <td className="rounded-r-[1rem] px-4 py-4 text-[var(--muted)]">
                  {service.url ? (
                    <a href={service.url} target="_blank" rel="noreferrer" className="break-all text-white hover:text-[var(--accent)]">
                      {service.url}
                    </a>
                  ) : (
                    "No endpoint"
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
