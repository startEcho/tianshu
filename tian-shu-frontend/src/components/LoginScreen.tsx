"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useMemo, useState } from "react";
import { useAuthStore } from "@/store/authStore";

const presets = [
  { label: "ADMIN", username: "admin", password: "Admin123456", accent: "var(--accent)" },
  { label: "TRAINER", username: "trainer", password: "Trainer123456", accent: "var(--signal)" },
  { label: "STUDENT", username: "student", password: "Student123456", accent: "#8cb6ff" },
];

interface LoginScreenProps {
  nextPath: string;
}

export default function LoginScreen({ nextPath }: LoginScreenProps) {
  const router = useRouter();
  const login = useAuthStore((state) => state.login);
  const status = useAuthStore((state) => state.status);
  const error = useAuthStore((state) => state.error);
  const [username, setUsername] = useState("admin");
  const [password, setPassword] = useState("Admin123456");

  const busy = useMemo(() => status === "authenticating", [status]);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const success = await login(username, password);
    if (success) {
      router.replace(nextPath);
    }
  };

  return (
    <div className="grid gap-8 lg:grid-cols-[1.15fr_0.85fr]">
      <section className="surface-elevated rounded-[2rem] px-6 py-8 sm:px-8 lg:px-10">
        <p className="eyebrow">Access sequence</p>
        <h1 className="hero-title mt-5 max-w-3xl text-5xl leading-[0.95] text-white sm:text-6xl">
          Authenticate into the gateway-backed control plane.
        </h1>
        <p className="mt-6 max-w-2xl text-base leading-8 text-[var(--muted)]">
          This frontend now signs into the real auth service, stores JWT and refresh tokens, and routes every protected
          view through the same API gateway the platform uses in deployment.
        </p>

        <div className="mt-8 grid gap-4 md:grid-cols-3">
          {presets.map((preset) => (
            <button
              key={preset.label}
              type="button"
              className="rounded-[1.4rem] border border-white/10 bg-black/10 px-4 py-4 text-left transition hover:-translate-y-1 hover:border-white/20"
              onClick={() => {
                setUsername(preset.username);
                setPassword(preset.password);
              }}
            >
              <span className="text-xs uppercase tracking-[0.28em]" style={{ color: preset.accent }}>
                {preset.label}
              </span>
              <p className="mt-3 text-sm font-semibold text-white">{preset.username}</p>
              <p className="mt-1 text-xs text-[var(--muted)]">{preset.password}</p>
            </button>
          ))}
        </div>

        <div className="mt-8 rounded-[1.5rem] border border-white/10 bg-black/10 p-5 text-sm leading-7 text-[var(--muted)]">
          <p className="font-semibold text-white">Post-login surface</p>
          <p className="mt-2">
            Students get launch and self-service lab management. Trainers add cross-user lab visibility. Admins gain
            identity management, roles, and the complete operator loop.
          </p>
        </div>
      </section>

      <section className="surface rounded-[2rem] px-6 py-8 sm:px-8">
        <p className="eyebrow">Operator sign-in</p>
        <form className="mt-6 space-y-5" onSubmit={handleSubmit}>
          <label className="block">
            <span className="mb-2 block text-sm font-medium text-[var(--muted)]">Username</span>
            <input
              className="input-field"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              placeholder="admin"
              autoComplete="username"
            />
          </label>

          <label className="block">
            <span className="mb-2 block text-sm font-medium text-[var(--muted)]">Password</span>
            <input
              type="password"
              className="input-field"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder="Admin123456"
              autoComplete="current-password"
            />
          </label>

          {error ? (
            <div className="rounded-[1.25rem] border border-[var(--danger)]/40 bg-[var(--danger)]/10 px-4 py-3 text-sm text-white">
              {error}
            </div>
          ) : null}

          <button type="submit" className="action-button w-full" disabled={busy}>
            {busy ? "Negotiating session..." : "Authenticate"}
          </button>
        </form>

        <div className="mt-6 text-sm text-[var(--muted)]">
          <p>
            Need an architectural walkthrough first? Return to the{" "}
            <Link href="/" className="text-[var(--accent)] hover:text-white">
              system overview
            </Link>
            .
          </p>
        </div>
      </section>
    </div>
  );
}
