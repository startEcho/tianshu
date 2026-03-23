"use client";

import { FormEvent, useMemo, useState } from "react";
import { useAuthStore } from "@/store/authStore";
import { usePlatformStore } from "@/store/platformStore";
import type { VulnerabilityDefinition, VulnerabilityDefinitionDraft } from "@/types/platform";

const emptyDefinition: VulnerabilityDefinitionDraft = {
  id: "",
  name: "",
  description: "",
  category: "",
  difficulty: "Medium",
  dockerImageName: "",
  containerPort: 8080,
  exploitationGuide: "",
  tags: [],
  flagFormat: "",
};

function toDraft(definition: VulnerabilityDefinition): VulnerabilityDefinitionDraft {
  return {
    id: definition.id,
    name: definition.name,
    description: definition.description,
    category: definition.category,
    difficulty: definition.difficulty,
    dockerImageName: definition.dockerImageName,
    containerPort: definition.containerPort,
    exploitationGuide: definition.exploitationGuide || "",
    tags: definition.tags || [],
    flagFormat: definition.flagFormat || "",
  };
}

export default function DefinitionWorkbench() {
  const session = useAuthStore((state) => state.session);
  const definitions = usePlatformStore((state) => state.definitions);
  const savingDefinitionId = usePlatformStore((state) => state.savingDefinitionId);
  const deletingDefinitionIds = usePlatformStore((state) => state.deletingDefinitionIds);
  const createDefinition = usePlatformStore((state) => state.createDefinition);
  const updateDefinition = usePlatformStore((state) => state.updateDefinition);
  const deleteDefinition = usePlatformStore((state) => state.deleteDefinition);
  const [form, setForm] = useState<VulnerabilityDefinitionDraft>(emptyDefinition);
  const [tagInput, setTagInput] = useState("");
  const [editingId, setEditingId] = useState<string | null>(null);

  const canWrite = useMemo(
    () => session?.user.authorities.includes("definition:write") ?? false,
    [session?.user.authorities]
  );
  const canDelete = useMemo(
    () => session?.user.authorities.includes("definition:delete") ?? false,
    [session?.user.authorities]
  );

  const resetForm = () => {
    setEditingId(null);
    setForm(emptyDefinition);
    setTagInput("");
  };

  const handleEdit = (definition: VulnerabilityDefinition) => {
    setEditingId(definition.id);
    setForm(toDraft(definition));
    setTagInput(definition.tags.join(", "));
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const payload: VulnerabilityDefinitionDraft = {
      ...form,
      tags: tagInput
        .split(",")
        .map((item) => item.trim())
        .filter(Boolean),
      containerPort: Number(form.containerPort) || 0,
      exploitationGuide: form.exploitationGuide?.trim() || "",
      flagFormat: form.flagFormat?.trim() || "",
    };

    const result = editingId
      ? await updateDefinition(editingId, payload)
      : await createDefinition(payload);

    if (result) {
      resetForm();
    }
  };

  return (
    <section className="surface rounded-[1.75rem] p-6">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div className="max-w-3xl">
          <p className="eyebrow">Vulnerability supply</p>
          <h2 className="mt-3 text-3xl font-semibold text-white">Curate exploitable lab definitions</h2>
          <p className="mt-4 text-sm leading-8 text-[var(--muted)]">
            Adding a definition makes it visible in the catalog immediately. Launch will only succeed once the Docker
            image exists and exposes the declared `containerPort`.
          </p>
        </div>
        {editingId ? (
          <button type="button" className="action-button secondary" onClick={resetForm}>
            Cancel edit
          </button>
        ) : null}
      </div>

      <div className="mt-8 data-grid two">
        <form className="surface rounded-[1.5rem] p-5" onSubmit={handleSubmit}>
          <p className="eyebrow">{editingId ? "Update definition" : "Create definition"}</p>
          <div className="mt-5 grid gap-4">
            <div className="grid gap-4 md:grid-cols-2">
              <input
                className="input-field"
                placeholder="definition id"
                value={form.id}
                disabled={Boolean(editingId)}
                onChange={(event) => setForm((state) => ({ ...state, id: event.target.value }))}
              />
              <input
                className="input-field"
                placeholder="name"
                value={form.name}
                onChange={(event) => setForm((state) => ({ ...state, name: event.target.value }))}
              />
            </div>

            <div className="grid gap-4 md:grid-cols-2">
              <input
                className="input-field"
                placeholder="category"
                value={form.category}
                onChange={(event) => setForm((state) => ({ ...state, category: event.target.value }))}
              />
              <select
                className="select-field"
                value={form.difficulty}
                onChange={(event) => setForm((state) => ({ ...state, difficulty: event.target.value }))}
              >
                {["Easy", "Medium", "Hard", "Expert"].map((difficulty) => (
                  <option key={difficulty} value={difficulty}>
                    {difficulty}
                  </option>
                ))}
              </select>
            </div>

            <textarea
              className="textarea-field min-h-32"
              placeholder="operator-facing description"
              value={form.description}
              onChange={(event) => setForm((state) => ({ ...state, description: event.target.value }))}
            />

            <div className="grid gap-4 md:grid-cols-[1.6fr_0.6fr]">
              <input
                className="input-field"
                placeholder="docker image name"
                value={form.dockerImageName}
                onChange={(event) => setForm((state) => ({ ...state, dockerImageName: event.target.value }))}
              />
              <input
                type="number"
                min={1}
                className="input-field"
                placeholder="container port"
                value={form.containerPort}
                onChange={(event) =>
                  setForm((state) => ({ ...state, containerPort: Number(event.target.value) || 0 }))
                }
              />
            </div>

            <input
              className="input-field"
              placeholder="comma separated tags"
              value={tagInput}
              onChange={(event) => setTagInput(event.target.value)}
            />

            <input
              className="input-field"
              placeholder="flag format"
              value={form.flagFormat || ""}
              onChange={(event) => setForm((state) => ({ ...state, flagFormat: event.target.value }))}
            />

            <textarea
              className="textarea-field min-h-40"
              placeholder="exploitation guide"
              value={form.exploitationGuide || ""}
              onChange={(event) => setForm((state) => ({ ...state, exploitationGuide: event.target.value }))}
            />

            <button type="submit" className="action-button" disabled={!canWrite || savingDefinitionId !== null}>
              {savingDefinitionId
                ? editingId
                  ? "Updating..."
                  : "Creating..."
                : editingId
                  ? "Update definition"
                  : "Create definition"}
            </button>
          </div>
        </form>

        <div className="surface rounded-[1.5rem] p-5">
          <p className="eyebrow">Live catalog</p>
          <div className="mt-5 grid gap-4">
            {definitions.map((definition) => (
              <article key={definition.id} className="rounded-[1.3rem] border border-white/8 bg-black/10 p-4">
                <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                  <div className="min-w-0">
                    <p className="text-sm font-semibold text-white">{definition.name}</p>
                    <p className="mt-1 text-xs uppercase tracking-[0.22em] text-[var(--muted)]">{definition.id}</p>
                    <p className="mt-3 text-sm leading-7 text-[var(--muted)]">{definition.description}</p>
                    <div className="mt-4 flex flex-wrap gap-2">
                      {[definition.category, definition.difficulty, ...definition.tags].map((label) => (
                        <span
                          key={`${definition.id}-${label}`}
                          className="rounded-full border border-white/10 bg-white/4 px-3 py-1 text-xs text-[var(--muted)]"
                        >
                          {label}
                        </span>
                      ))}
                    </div>
                    <p className="mt-4 text-xs leading-6 text-[var(--muted)]">
                      {definition.dockerImageName} · port {definition.containerPort}
                    </p>
                  </div>
                  <div className="flex shrink-0 flex-wrap gap-2">
                    <button
                      type="button"
                      className="action-button secondary"
                      onClick={() => handleEdit(definition)}
                      disabled={!canWrite || savingDefinitionId !== null}
                    >
                      Edit
                    </button>
                    <button
                      type="button"
                      className="action-button secondary"
                      onClick={() => {
                        if (window.confirm(`Delete definition ${definition.id}?`)) {
                          void deleteDefinition(definition.id);
                        }
                      }}
                      disabled={!canDelete || Boolean(deletingDefinitionIds[definition.id])}
                    >
                      {deletingDefinitionIds[definition.id] ? "Deleting..." : "Delete"}
                    </button>
                  </div>
                </div>
              </article>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
