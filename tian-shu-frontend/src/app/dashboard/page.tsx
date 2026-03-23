"use client";

import Link from "next/link";
import { startTransition, useEffect, useMemo } from "react";
import AuthGate from "@/components/AuthGate";
import { api } from "@/services/api";
import { useAuthStore } from "@/store/authStore";
import { usePlatformStore } from "@/store/platformStore";

function statusTone(status: string) {
  if (status.startsWith("RUN")) {
    return "text-[var(--accent)]";
  }
  if (status.startsWith("TERM")) {
    return "text-[var(--signal)]";
  }
  if (status.startsWith("ERR")) {
    return "text-[var(--danger)]";
  }
  return "text-white";
}

export default function DashboardPage() {
  const session = useAuthStore((state) => state.session);
  const labs = usePlatformStore((state) => state.labs);
  const definitions = usePlatformStore((state) => state.definitions);
  const loadingLabs = usePlatformStore((state) => state.loadingLabs);
  const loadingDefinitions = usePlatformStore((state) => state.loadingDefinitions);
  const terminatingLabIds = usePlatformStore((state) => state.terminatingLabIds);
  const fetchLabs = usePlatformStore((state) => state.fetchLabs);
  const fetchDefinitions = usePlatformStore((state) => state.fetchDefinitions);
  const terminateLab = usePlatformStore((state) => state.terminateLab);

  useEffect(() => {
    startTransition(() => {
      void fetchLabs();
      void fetchDefinitions();
    });
  }, [fetchDefinitions, fetchLabs]);

  const visibleLabs = useMemo(
    () => labs.filter((lab) => !lab.status.startsWith("TERM")),
    [labs]
  );

  const metrics = useMemo(
    () => [
      { label: "Role set", value: session?.user.roles.join(" · ") || "Unknown" },
      { label: "Lab definitions", value: definitions.length.toString() },
      { label: "Active instances", value: visibleLabs.filter((lab) => lab.status.startsWith("RUN")).length.toString() },
      { label: "Authority count", value: session?.user.authorities.length.toString() || "0" },
    ],
    [definitions.length, session?.user.authorities.length, session?.user.roles, visibleLabs]
  );

  return (
    <AuthGate>
      <div className="space-y-8">
        <section className="surface-elevated rounded-[2rem] px-6 py-8 sm:px-8 lg:px-10">
          <div className="flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
            <div>
              <p className="eyebrow">Mission control</p>
              <h1 className="hero-title mt-4 text-5xl leading-none text-white sm:text-6xl">Operator cockpit</h1>
              <p className="mt-4 max-w-3xl text-base leading-8 text-[var(--muted)]">
                Track active labs, verify your session footprint, and pivot into observability without leaving the
                control surface.
              </p>
            </div>
            <div className="grid min-w-[280px] gap-3 text-sm">
              {metrics.map((metric) => (
                <div key={metric.label} className="rounded-[1.25rem] border border-white/10 bg-black/10 px-4 py-3">
                  <p className="text-[var(--muted)]">{metric.label}</p>
                  <p className="mt-1 text-xl font-semibold text-white">{metric.value}</p>
                </div>
              ))}
            </div>
          </div>
        </section>

        <section className="data-grid two">
          <div className="surface rounded-[1.75rem] p-6">
            <div className="flex items-center justify-between">
              <div>
                <p className="eyebrow">Observability</p>
                <h2 className="mt-3 text-2xl font-semibold text-white">Live operator links</h2>
              </div>
              <Link href="/vulnerabilities" className="action-button secondary">
                Launch new lab
              </Link>
            </div>
            <div className="mt-6 grid gap-3 text-sm">
              {Object.entries(api.observabilityLinks).map(([label, href]) => (
                <a
                  key={label}
                  href={href}
                  target="_blank"
                  rel="noreferrer"
                  className="rounded-[1.2rem] border border-white/10 bg-black/10 px-4 py-3 text-white transition hover:border-[var(--accent)]/40 hover:text-[var(--accent)]"
                >
                  <span className="capitalize">{label}</span>
                  <span className="mt-1 block text-xs text-[var(--muted)]">{href}</span>
                </a>
              ))}
            </div>
          </div>

          <div className="surface rounded-[1.75rem] p-6">
            <p className="eyebrow">Access fingerprint</p>
            <h2 className="mt-3 text-2xl font-semibold text-white">{session?.user.displayName}</h2>
            <div className="mt-6 space-y-3 text-sm text-[var(--muted)]">
              <div className="rounded-[1.2rem] border border-white/8 bg-black/10 px-4 py-3">
                <span className="block text-xs uppercase tracking-[0.24em]">Username</span>
                <p className="mt-1 text-base text-white">{session?.user.username}</p>
              </div>
              <div className="rounded-[1.2rem] border border-white/8 bg-black/10 px-4 py-3">
                <span className="block text-xs uppercase tracking-[0.24em]">Roles</span>
                <p className="mt-1 text-base text-white">{session?.user.roles.join(", ")}</p>
              </div>
              <div className="rounded-[1.2rem] border border-white/8 bg-black/10 px-4 py-3">
                <span className="block text-xs uppercase tracking-[0.24em]">Key authorities</span>
                <p className="mt-1 text-base text-white">
                  {session?.user.authorities.slice(0, 4).join(", ") || "No authorities"}
                </p>
              </div>
            </div>
          </div>
        </section>

        <section className="surface rounded-[2rem] p-6 sm:p-8">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <p className="eyebrow">Lab fleet</p>
              <h2 className="mt-3 text-3xl font-semibold text-white">Active exploit environments</h2>
            </div>
            <div className="text-sm text-[var(--muted)]">
              {loadingLabs || loadingDefinitions ? "Refreshing operator view..." : `${visibleLabs.length} lab records loaded`}
            </div>
          </div>

          {visibleLabs.length === 0 ? (
            <div className="mt-8 rounded-[1.5rem] border border-dashed border-white/12 bg-black/10 px-6 py-10 text-center">
              <p className="text-lg text-white">No lab instances yet.</p>
              <p className="mt-2 text-sm text-[var(--muted)]">
                Use the vulnerability atlas to provision your first environment through the new gateway-authenticated
                flow.
              </p>
              <Link href="/vulnerabilities" className="action-button mt-6 inline-flex">
                Open vulnerability atlas
              </Link>
            </div>
          ) : (
            <div className="mt-8 grid gap-4 lg:grid-cols-2">
              {visibleLabs.map((lab) => (
                <article key={lab.instanceId} className="rounded-[1.5rem] border border-white/10 bg-black/10 p-5">
                  <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                    <div>
                      <p className="text-xs uppercase tracking-[0.24em] text-[var(--muted)]">{lab.vulnerabilityId}</p>
                      <h3 className="mt-2 text-2xl font-semibold text-white">{lab.instanceId}</h3>
                    </div>
                    <span className={`text-sm font-semibold uppercase tracking-[0.24em] ${statusTone(lab.status)}`}>
                      {lab.status}
                    </span>
                  </div>
                  <div className="mt-5 space-y-2 text-sm text-[var(--muted)]">
                    <p>Owner: {lab.ownerUsername || session?.user.username}</p>
                    <p>Created: {lab.createdAt ? new Date(lab.createdAt).toLocaleString() : "Pending"}</p>
                    <p className="break-all">Access URL: {lab.accessUrl || "Provisioning..."}</p>
                  </div>
                  <div className="mt-6 flex flex-wrap gap-3">
                    {lab.accessUrl ? (
                      <a href={lab.accessUrl} target="_blank" rel="noreferrer" className="action-button">
                        Open lab
                      </a>
                    ) : null}
                    <button
                      type="button"
                      className="action-button secondary"
                      onClick={() => void terminateLab(lab.instanceId)}
                      disabled={Boolean(terminatingLabIds[lab.instanceId])}
                    >
                      {terminatingLabIds[lab.instanceId] ? "Terminating..." : "Terminate"}
                    </button>
                  </div>
                </article>
              ))}
            </div>
          )}
        </section>
      </div>
    </AuthGate>
  );
}
