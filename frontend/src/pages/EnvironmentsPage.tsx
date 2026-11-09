import { FormEvent, useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Pencil, Plus, Trash2 } from "lucide-react";
import { Button } from "../components/Button";
import { ConfirmDialog } from "../components/ConfirmDialog";
import { EmptyState, ErrorState, LoadingState } from "../components/DataState";
import { Field, SelectInput, TextInput } from "../components/Field";
import { PageHeader } from "../components/PageHeader";
import { useToast } from "../components/Toast";
import { environmentApi, projectApi } from "../lib/api";
import { errorMessage } from "../lib/errors";
import type { TestEnvironment } from "../lib/types";
import { useSearchParams } from "react-router-dom";

type EnvironmentForm = {
  name: string;
  baseUrl: string;
  authUsername: string;
  authPassword: string;
  clearCredentials: boolean;
};

const emptyForm: EnvironmentForm = {
  name: "",
  baseUrl: "",
  authUsername: "",
  authPassword: "",
  clearCredentials: false,
};

export function EnvironmentsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const projectId = searchParams.get("project") ?? "";
  const selectedEnvironmentId = searchParams.get("environment") ?? "";
  const [form, setForm] = useState<EnvironmentForm>(emptyForm);
  const [editing, setEditing] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<TestEnvironment | null>(null);
  const queryClient = useQueryClient();
  const toast = useToast();

  const projects = useQuery({ queryKey: ["projects"], queryFn: projectApi.list });
  const project = projects.data?.find((item) => item.id === projectId);
  const environments = useQuery({
    queryKey: ["environments", projectId],
    queryFn: () => environmentApi.list(projectId),
    enabled: !!projectId,
  });
  const selected = environments.data?.find((item) => item.id === selectedEnvironmentId);

  useEffect(() => {
    if (selected) {
      setForm({
        name: selected.name,
        baseUrl: selected.baseUrl,
        authUsername: selected.authUsername ?? "",
        authPassword: "",
        clearCredentials: false,
      });
      setEditing(true);
    } else {
      setForm(emptyForm);
      setEditing(false);
    }
  }, [selectedEnvironmentId, selected]);

  const save = useMutation({
    mutationFn: () => selected
      ? environmentApi.update(projectId, selected.id, form)
      : environmentApi.create(projectId, form),
    onSuccess: async (saved) => {
      await queryClient.invalidateQueries({ queryKey: ["environments", projectId] });
      updateSelection(saved.id);
      setForm({ ...form, authPassword: "", clearCredentials: false });
      toast.success(selected ? "Environment updated." : "Environment created.");
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const remove = useMutation({
    mutationFn: (environmentId: string) => environmentApi.remove(projectId, environmentId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["environments", projectId] });
      updateSelection(null);
      setPendingDelete(null);
      toast.success("Environment deleted.");
    },
    onError: (error) => {
      toast.error(errorMessage(error));
      setPendingDelete(null);
    },
  });

  function updateSelection(environmentId: string | null) {
    const next = new URLSearchParams(searchParams);
    if (environmentId) next.set("environment", environmentId);
    else next.delete("environment");
    setSearchParams(next, { replace: true });
  }

  function setField<K extends keyof EnvironmentForm>(field: K, value: EnvironmentForm[K]) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    save.mutate();
  }

  return (
    <>
      <PageHeader
        title="Environments"
        description="Configure safe target URLs and optional credentials for QA, staging, and other project environments."
      />
      <div className="grid gap-4 p-4 sm:p-6 lg:grid-cols-[minmax(240px,1fr)_2fr]">
        <section className="grid content-start gap-3 rounded-md border border-line bg-white p-4">
          <Field label="Project">
            <SelectInput
              value={projectId}
              onChange={(event) => {
                const next = new URLSearchParams();
                if (event.target.value) next.set("project", event.target.value);
                setSearchParams(next, { replace: true });
              }}
            >
              <option value="">Select project…</option>
              {projects.data?.map((item) => (
                <option key={item.id} value={item.id}>{item.name}</option>
              ))}
            </SelectInput>
          </Field>
          {projectId && environments.isLoading && <LoadingState />}
          {environments.isError && <ErrorState message={errorMessage(environments.error)} />}
          {projectId && !environments.isLoading && !environments.data?.length && (
            <EmptyState title="No environments configured yet." />
          )}
          {environments.data?.map((environment: TestEnvironment) => (
            <div
              key={environment.id}
              className={`flex items-center gap-2 rounded-md border p-3 ${
                selectedEnvironmentId === environment.id ? "border-brand bg-teal-50" : "border-line"
              }`}
            >
              <button
                type="button"
                className="min-w-0 flex-1 text-left"
                onClick={() => updateSelection(environment.id)}
              >
                <span className="block truncate text-sm font-medium text-ink">{environment.name}</span>
                <span className="block truncate text-xs text-slate-500">{environment.baseUrl}</span>
              </button>
              {project?.owner && (
                <>
                  <button
                    type="button"
                    title="Edit environment"
                    className="rounded p-1.5 text-slate-500 hover:bg-slate-100"
                    onClick={() => updateSelection(environment.id)}
                  >
                    <Pencil className="h-4 w-4" />
                  </button>
                  <button
                    type="button"
                    title="Delete environment"
                    className="rounded p-1.5 text-slate-500 hover:bg-red-50 hover:text-red-600"
                    onClick={() => setPendingDelete(environment)}
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                </>
              )}
            </div>
          ))}
          {project?.owner && projectId && (
            <Button
              variant="secondary"
              onClick={() => updateSelection(null)}
            >
              <Plus className="h-4 w-4" />
              New environment
            </Button>
          )}
        </section>

        <section className="rounded-md border border-line bg-white p-4 sm:p-5">
          {!projectId ? (
            <EmptyState title="Select a project to manage its environments." />
          ) : !project?.owner ? (
            <div className="grid gap-3">
              <h2 className="text-sm font-semibold text-ink">{selected?.name ?? "Environment details"}</h2>
              {selected ? (
                <>
                  <div className="text-sm text-slate-600">Base URL: {selected.baseUrl}</div>
                  <div className="text-sm text-slate-600">
                    Credentials: {selected.credentialsConfigured ? "Configured" : "Not configured"}
                  </div>
                </>
              ) : (
                <EmptyState title="Choose an environment to view its details." />
              )}
              <p className="text-xs text-slate-500">Only the project owner can change environment settings.</p>
            </div>
          ) : (
            <form className="grid gap-4" onSubmit={submit}>
              <h2 className="text-sm font-semibold text-ink">
                {editing ? `Edit ${selected?.name ?? "environment"}` : "Create environment"}
              </h2>
              <Field label="Environment name">
                <TextInput
                  value={form.name}
                  maxLength={100}
                  onChange={(event) => setField("name", event.target.value)}
                  required
                />
              </Field>
              <Field label="Base URL (origin only, e.g. https://qa.example.com)">
                <TextInput
                  type="url"
                  value={form.baseUrl}
                  maxLength={2048}
                  onChange={(event) => setField("baseUrl", event.target.value)}
                  placeholder="https://qa.example.com"
                  required
                />
              </Field>
              <Field label="HTTP authentication username (optional)">
                <TextInput
                  value={form.authUsername}
                  maxLength={255}
                  onChange={(event) => setField("authUsername", event.target.value)}
                  autoComplete="off"
                />
              </Field>
              <Field label={selected?.credentialsConfigured ? "New password (leave blank to keep)" : "HTTP authentication password (optional)"}>
                <TextInput
                  type="password"
                  value={form.authPassword}
                  maxLength={1024}
                  onChange={(event) => setField("authPassword", event.target.value)}
                  autoComplete="new-password"
                />
              </Field>
              {selected?.credentialsConfigured && (
                <label className="flex items-center gap-2 text-sm text-slate-600">
                  <input
                    type="checkbox"
                    checked={form.clearCredentials}
                    onChange={(event) => setField("clearCredentials", event.target.checked)}
                    className="accent-brand"
                  />
                  Remove saved credentials
                </label>
              )}
              <p className="text-xs text-slate-500">
                URLs must resolve to public IP addresses. Credentials are encrypted at rest and never returned by the API.
              </p>
              <div className="flex gap-2">
                <Button type="submit" loading={save.isPending}>
                  {editing ? "Save changes" : "Create environment"}
                </Button>
                {editing && (
                  <Button type="button" variant="secondary" onClick={() => updateSelection(null)}>
                    Cancel
                  </Button>
                )}
              </div>
            </form>
          )}
        </section>
      </div>
      <ConfirmDialog
        open={pendingDelete !== null}
        title="Delete environment?"
        description={`Delete "${pendingDelete?.name ?? ""}"? Existing suite-run history will be retained.`}
        loading={remove.isPending}
        onConfirm={() => pendingDelete && remove.mutate(pendingDelete.id)}
        onCancel={() => setPendingDelete(null)}
      />
    </>
  );
}
