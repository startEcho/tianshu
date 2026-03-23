"use client";

import { FormEvent, startTransition, useEffect, useMemo, useState } from "react";
import AuthGate from "@/components/AuthGate";
import DefinitionWorkbench from "@/components/admin/DefinitionWorkbench";
import PlatformControlPanel from "@/components/admin/PlatformControlPanel";
import { usePlatformStore } from "@/store/platformStore";

const emptyForm = {
  username: "",
  password: "",
  displayName: "",
  roles: ["STUDENT"],
  enabled: true,
};

export default function AdminPage() {
  const users = usePlatformStore((state) => state.users);
  const roles = usePlatformStore((state) => state.roles);
  const loadingAdmin = usePlatformStore((state) => state.loadingAdmin);
  const creatingUser = usePlatformStore((state) => state.creatingUser);
  const assigningRoles = usePlatformStore((state) => state.assigningRoles);
  const fetchAdminData = usePlatformStore((state) => state.fetchAdminData);
  const fetchDefinitions = usePlatformStore((state) => state.fetchDefinitions);
  const createUser = usePlatformStore((state) => state.createUser);
  const assignRoles = usePlatformStore((state) => state.assignRoles);
  const [form, setForm] = useState(emptyForm);
  const [draftRoles, setDraftRoles] = useState<Record<string, string[]>>({});

  useEffect(() => {
    startTransition(() => {
      void fetchAdminData();
      void fetchDefinitions();
    });
  }, [fetchAdminData, fetchDefinitions]);

  useEffect(() => {
    const nextDrafts = Object.fromEntries(users.map((user) => [user.userId, user.roles]));
    setDraftRoles(nextDrafts);
  }, [users]);

  const roleCodes = useMemo(() => roles.map((role) => role.code), [roles]);

  const handleCreate = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const success = await createUser(form);
    if (success) {
      setForm(emptyForm);
    }
  };

  return (
    <AuthGate requiredAuthorities={["user:read"]}>
      <div className="space-y-8">
        <section className="surface-elevated rounded-[2rem] px-6 py-8 sm:px-8 lg:px-10">
          <p className="eyebrow">Admin grid</p>
          <h1 className="hero-title mt-4 text-5xl leading-none text-white sm:text-6xl">Operate runtime, catalog, and identity</h1>
          <p className="mt-4 max-w-3xl text-base leading-8 text-[var(--muted)]">
            This page now acts as the platform nerve center: start and stop the local runtime, manage the exploit
            catalog, inspect role bundles, and assign operator access without leaving the control fabric.
          </p>
        </section>

        <PlatformControlPanel />

        <DefinitionWorkbench />

        <section className="data-grid two">
          <form className="surface rounded-[1.75rem] p-6" onSubmit={handleCreate}>
            <p className="eyebrow">Create operator</p>
            <h2 className="mt-3 text-2xl font-semibold text-white">Seed a new account</h2>
            <div className="mt-6 grid gap-4">
              <input
                className="input-field"
                placeholder="username"
                value={form.username}
                onChange={(event) => setForm((state) => ({ ...state, username: event.target.value }))}
              />
              <input
                className="input-field"
                placeholder="display name"
                value={form.displayName}
                onChange={(event) => setForm((state) => ({ ...state, displayName: event.target.value }))}
              />
              <input
                type="password"
                className="input-field"
                placeholder="initial password"
                value={form.password}
                onChange={(event) => setForm((state) => ({ ...state, password: event.target.value }))}
              />
              <div className="rounded-[1.3rem] border border-white/8 bg-black/10 p-4">
                <p className="text-sm font-semibold text-white">Role set</p>
                <div className="mt-3 flex flex-wrap gap-3">
                  {roleCodes.map((role) => {
                    const active = form.roles.includes(role);
                    return (
                      <button
                        key={role}
                        type="button"
                        className={`rounded-full border px-4 py-2 text-sm ${
                          active
                            ? "border-[var(--accent)] bg-[var(--accent)]/16 text-[var(--accent)]"
                            : "border-white/10 bg-white/4 text-[var(--muted)]"
                        }`}
                        onClick={() =>
                          setForm((state) => ({
                            ...state,
                            roles: active
                              ? state.roles.filter((item) => item !== role)
                              : [...state.roles, role],
                          }))
                        }
                      >
                        {role}
                      </button>
                    );
                  })}
                </div>
              </div>
              <button type="submit" className="action-button" disabled={creatingUser}>
                {creatingUser ? "Creating..." : "Create user"}
              </button>
            </div>
          </form>

          <div className="surface rounded-[1.75rem] p-6">
            <p className="eyebrow">Role inventory</p>
            <h2 className="mt-3 text-2xl font-semibold text-white">Permission bundles</h2>
            <div className="mt-6 grid gap-4">
              {roles.map((role) => (
                <article key={role.code} className="rounded-[1.3rem] border border-white/8 bg-black/10 p-4">
                  <div className="flex items-center justify-between gap-4">
                    <div>
                      <p className="text-sm font-semibold text-white">{role.code}</p>
                      <p className="mt-1 text-sm text-[var(--muted)]">{role.description}</p>
                    </div>
                    <span className="rounded-full border border-white/10 px-3 py-1 text-xs text-[var(--muted)]">
                      {role.permissions.length} permissions
                    </span>
                  </div>
                  <div className="mt-4 flex flex-wrap gap-2">
                    {role.permissions.map((permission) => (
                      <span
                        key={permission}
                        className="rounded-full border border-white/8 bg-white/4 px-3 py-1 text-xs text-[var(--muted)]"
                      >
                        {permission}
                      </span>
                    ))}
                  </div>
                </article>
              ))}
            </div>
          </div>
        </section>

        <section className="surface rounded-[1.75rem] p-6">
          <div className="flex items-center justify-between">
            <div>
              <p className="eyebrow">Operator registry</p>
              <h2 className="mt-3 text-3xl font-semibold text-white">Current user estate</h2>
            </div>
            <p className="text-sm text-[var(--muted)]">
              {loadingAdmin ? "Syncing identity store..." : `${users.length} users loaded`}
            </p>
          </div>

          <div className="mt-6 overflow-x-auto">
            <table className="min-w-full border-separate border-spacing-y-3 text-left text-sm">
              <thead>
                <tr className="text-[var(--muted)]">
                  <th className="px-4">User</th>
                  <th className="px-4">Roles</th>
                  <th className="px-4">Authorities</th>
                  <th className="px-4">Actions</th>
                </tr>
              </thead>
              <tbody>
                {users.map((user) => (
                  <tr key={user.userId} className="surface">
                    <td className="rounded-l-[1rem] px-4 py-4">
                      <p className="font-semibold text-white">{user.displayName}</p>
                      <p className="mt-1 text-xs text-[var(--muted)]">{user.username}</p>
                    </td>
                    <td className="px-4 py-4">
                      <div className="flex flex-wrap gap-2">
                        {roleCodes.map((role) => {
                          const active = (draftRoles[user.userId] || []).includes(role);
                          return (
                            <button
                              key={`${user.userId}-${role}`}
                              type="button"
                              className={`rounded-full border px-3 py-1 text-xs ${
                                active
                                  ? "border-[var(--accent)] bg-[var(--accent)]/16 text-[var(--accent)]"
                                  : "border-white/10 bg-white/4 text-[var(--muted)]"
                              }`}
                              onClick={() =>
                                setDraftRoles((state) => {
                                  const current = state[user.userId] || [];
                                  return {
                                    ...state,
                                    [user.userId]: current.includes(role)
                                      ? current.filter((item) => item !== role)
                                      : [...current, role],
                                  };
                                })
                              }
                            >
                              {role}
                            </button>
                          );
                        })}
                      </div>
                    </td>
                    <td className="px-4 py-4 text-[var(--muted)]">{user.authorities.join(", ")}</td>
                    <td className="rounded-r-[1rem] px-4 py-4">
                      <button
                        type="button"
                        className="action-button secondary"
                        disabled={Boolean(assigningRoles[user.userId])}
                        onClick={() => void assignRoles(user.userId, draftRoles[user.userId] || [])}
                      >
                        {assigningRoles[user.userId] ? "Saving..." : "Save roles"}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      </div>
    </AuthGate>
  );
}
