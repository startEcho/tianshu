"use client";

import { startTransition, useDeferredValue, useEffect, useMemo, useState } from "react";
import AuthGate from "@/components/AuthGate";
import { usePlatformStore } from "@/store/platformStore";

export default function VulnerabilitiesPage() {
  const definitions = usePlatformStore((state) => state.definitions);
  const labs = usePlatformStore((state) => state.labs);
  const loadingDefinitions = usePlatformStore((state) => state.loadingDefinitions);
  const launchingDefinitionId = usePlatformStore((state) => state.launchingDefinitionId);
  const fetchDefinitions = usePlatformStore((state) => state.fetchDefinitions);
  const fetchLabs = usePlatformStore((state) => state.fetchLabs);
  const launchLab = usePlatformStore((state) => state.launchLab);

  const [query, setQuery] = useState("");
  const [category, setCategory] = useState("ALL");
  const [difficulty, setDifficulty] = useState("ALL");
  const deferredQuery = useDeferredValue(query);

  useEffect(() => {
    startTransition(() => {
      void fetchDefinitions();
      void fetchLabs();
    });
  }, [fetchDefinitions, fetchLabs]);

  const categories = useMemo(
    () => ["ALL", ...Array.from(new Set(definitions.map((item) => item.category))).sort()],
    [definitions]
  );
  const difficulties = useMemo(
    () => ["ALL", ...Array.from(new Set(definitions.map((item) => item.difficulty))).sort()],
    [definitions]
  );

  const visibleDefinitions = useMemo(() => {
    const keyword = deferredQuery.trim().toLowerCase();
    return definitions.filter((definition) => {
      const matchesQuery =
        keyword.length === 0 ||
        [definition.name, definition.id, definition.category, definition.description, ...(definition.tags || [])]
          .join(" ")
          .toLowerCase()
          .includes(keyword);
      const matchesCategory = category === "ALL" || definition.category === category;
      const matchesDifficulty = difficulty === "ALL" || definition.difficulty === difficulty;
      return matchesQuery && matchesCategory && matchesDifficulty;
    });
  }, [category, definitions, deferredQuery, difficulty]);

  return (
    <AuthGate>
      <div className="space-y-8">
        <section className="surface-elevated rounded-[2rem] px-6 py-8 sm:px-8 lg:px-10">
          <p className="eyebrow">Vulnerability atlas</p>
          <div className="mt-4 flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
            <div>
              <h1 className="hero-title text-5xl leading-none text-white sm:text-6xl">Curated exploit scenarios</h1>
              <p className="mt-4 max-w-3xl text-base leading-8 text-[var(--muted)]">
                Filter the catalog, review the exploitation brief, and launch environments into the orchestration
                service without leaving the authenticated shell.
              </p>
            </div>
            <div className="rounded-[1.4rem] border border-white/10 bg-black/10 px-4 py-3 text-sm text-[var(--muted)]">
              {loadingDefinitions ? "Refreshing catalog..." : `${visibleDefinitions.length} definitions visible`}
            </div>
          </div>
        </section>

        <section className="surface rounded-[1.75rem] p-5 sm:p-6">
          <div className="grid gap-4 md:grid-cols-[1.3fr_0.9fr_0.9fr]">
            <input
              className="input-field"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Search by name, category, ID, or tags"
            />
            <select className="select-field" value={category} onChange={(event) => setCategory(event.target.value)}>
              {categories.map((item) => (
                <option key={item} value={item}>
                  {item}
                </option>
              ))}
            </select>
            <select
              className="select-field"
              value={difficulty}
              onChange={(event) => setDifficulty(event.target.value)}
            >
              {difficulties.map((item) => (
                <option key={item} value={item}>
                  {item}
                </option>
              ))}
            </select>
          </div>
        </section>

        <section className="grid gap-5 lg:grid-cols-2">
          {visibleDefinitions.map((definition) => {
            const runningLab = labs.find(
              (lab) =>
                lab.vulnerabilityId === definition.id &&
                (lab.status === "PENDING" || lab.status === "PROVISIONING" || lab.status.startsWith("RUN"))
            );

            return (
              <article key={definition.id} className="surface rounded-[1.75rem] p-6">
                <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                  <div>
                    <p className="eyebrow">{definition.category}</p>
                    <h2 className="mt-3 text-3xl font-semibold text-white">{definition.name}</h2>
                    <p className="mt-2 text-sm text-[var(--muted)]">{definition.id}</p>
                  </div>
                  <div className="rounded-full border border-white/10 bg-black/10 px-4 py-2 text-sm text-white">
                    {definition.difficulty}
                  </div>
                </div>

                <p className="mt-6 text-sm leading-7 text-[var(--muted)]">{definition.description}</p>

                <div className="mt-6 rounded-[1.3rem] border border-white/8 bg-black/10 p-4">
                  <p className="text-xs uppercase tracking-[0.24em] text-[var(--muted)]">Exploitation guide</p>
                  <p className="mt-3 whitespace-pre-wrap text-sm leading-7 text-white/88">
                    {definition.exploitationGuide || "No guide available."}
                  </p>
                </div>

                <div className="mt-5 flex flex-wrap gap-2">
                  {definition.tags.map((tag) => (
                    <span
                      key={tag}
                      className="rounded-full border border-white/8 bg-white/6 px-3 py-1 text-xs uppercase tracking-[0.18em] text-[var(--muted)]"
                    >
                      {tag}
                    </span>
                  ))}
                </div>

                <div className="mt-6 flex flex-wrap items-center gap-3">
                  <button
                    type="button"
                    className="action-button"
                    onClick={() => void launchLab(definition.id)}
                    disabled={Boolean(runningLab) || launchingDefinitionId === definition.id}
                  >
                    {launchingDefinitionId === definition.id
                      ? "Provisioning..."
                      : runningLab
                        ? "Already active"
                        : "Launch lab"}
                  </button>
                  {runningLab ? (
                    <span className="rounded-full border border-[var(--accent)]/30 bg-[var(--accent)]/10 px-4 py-2 text-sm text-[var(--accent)]">
                      {runningLab.status} · {runningLab.instanceId}
                    </span>
                  ) : null}
                </div>
              </article>
            );
          })}
        </section>
      </div>
    </AuthGate>
  );
}
