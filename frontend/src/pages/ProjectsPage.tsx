import { FormEvent, useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ChevronUp, Plus, Settings, Trash2 } from "lucide-react";
import { Button } from "../components/Button";
import { ConfirmDialog } from "../components/ConfirmDialog";
import { EmptyState, ErrorState, LoadingState } from "../components/DataState";
import { Field, TextArea, TextInput } from "../components/Field";
import { PageHeader } from "../components/PageHeader";
import { useToast } from "../components/Toast";
import { projectApi, projectSettingsApi } from "../lib/api";
import { errorMessage } from "../lib/errors";

function ProjectSettingsPanel({ projectId }: { projectId: string }) {
  const queryClient = useQueryClient();
  const toast = useToast();

  const settings = useQuery({
    queryKey: ["project-settings", projectId],
    queryFn: () => projectSettingsApi.get(projectId),
  });

  const [maxPages, setMaxPages] = useState<number | "">("");
  const [authUrl, setAuthUrl] = useState("");
  const [authUsername, setAuthUsername] = useState("");
  const [authPassword, setAuthPassword] = useState("");
  const [excludedPatterns, setExcludedPatterns] = useState("");
  const [initialised, setInitialised] = useState(false);

  useEffect(() => {
    if (settings.data && !initialised) {
      setMaxPages(settings.data.maxPages ?? 20);
      setAuthUrl(settings.data.authUrl ?? "");
      setAuthUsername(settings.data.authUsername ?? "");
      setExcludedPatterns(settings.data.excludedPatterns ?? "");
      setInitialised(true);
    }
  }, [settings.data, initialised]);

  const save = useMutation({
    mutationFn: () =>
      projectSettingsApi.save(projectId, {
        maxPages: Number(maxPages) || 20,
        authUrl: authUrl || undefined,
        authUsername: authUsername || undefined,
        authPassword: authPassword || undefined,
        excludedPatterns: excludedPatterns || undefined,
      }),
    onSuccess: () => {
      setAuthPassword("");
      queryClient.invalidateQueries({ queryKey: ["project-settings", projectId] });
      toast.success("Settings saved.");
    },
    onError: (err) => toast.error(errorMessage(err)),
  });

  if (settings.isLoading) return <div className="px-3 py-2 text-xs text-slate-400">Loading settings…</div>;
  if (settings.isError) return <div className="px-3 py-2 text-xs text-red-500">{errorMessage(settings.error)}</div>;

  return (
    <form
      className="grid gap-4 border-t border-line bg-slate-50 p-4"
      onSubmit={(e) => { e.preventDefault(); save.mutate(); }}
    >
      <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">Crawl Settings</div>

      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Max Pages">
          <TextInput
            type="number"
            min={1}
            max={200}
            value={maxPages}
            onChange={(e) => setMaxPages(e.target.value === "" ? "" : Number(e.target.value))}
          />
        </Field>
        <Field label="Excluded URL patterns (comma-separated)">
          <TextArea
            placeholder="/logout, /admin, /api/internal"
            value={excludedPatterns}
            onChange={(e) => setExcludedPatterns(e.target.value)}
          />
        </Field>
      </div>

      <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">
        Authentication{" "}
        {settings.data?.authConfigured && (
          <span className="ml-1 rounded-full bg-emerald-100 px-2 py-0.5 text-[10px] font-medium text-emerald-700 normal-case">
            configured
          </span>
        )}
      </div>
      <p className="text-xs text-slate-500 -mt-2">
        If the app requires login, QAAS will authenticate before crawling. Leave blank to crawl as a guest.
      </p>

      <div className="grid gap-4 sm:grid-cols-3">
        <Field label="Login page URL">
          <TextInput
            type="url"
            placeholder="https://myapp.com/login"
            value={authUrl}
            onChange={(e) => setAuthUrl(e.target.value)}
          />
        </Field>
        <Field label="Username / Email">
          <TextInput
            placeholder="user@example.com"
            value={authUsername}
            onChange={(e) => setAuthUsername(e.target.value)}
          />
        </Field>
        <Field label={settings.data?.authConfigured ? "New password (leave blank to keep)" : "Password"}>
          <TextInput
            type="password"
            placeholder={settings.data?.authConfigured ? "••••••••" : ""}
            value={authPassword}
            onChange={(e) => setAuthPassword(e.target.value)}
          />
        </Field>
      </div>

      <div>
        <Button type="submit" loading={save.isPending}>
          Save settings
        </Button>
      </div>
    </form>
  );
}

export function ProjectsPage() {
  const queryClient = useQueryClient();
  const toast = useToast();
  const projects = useQuery({ queryKey: ["projects"], queryFn: projectApi.list });
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [baseUrl, setBaseUrl] = useState("");
  const [openSettings, setOpenSettings] = useState<string | null>(null);
  const [pendingDelete, setPendingDelete] = useState<{ id: string; name: string } | null>(null);

  const save = useMutation({
    mutationFn: projectApi.create,
    onSuccess: async (project) => {
      setName("");
      setDescription("");
      setBaseUrl("");
      await queryClient.invalidateQueries({ queryKey: ["projects"] });
      toast.success(`Project "${project.name}" created.`);
    },
    onError: (err) => toast.error(errorMessage(err)),
  });

  const remove = useMutation({
    mutationFn: projectApi.remove,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["projects"] });
      toast.success(`Project "${pendingDelete?.name}" deleted.`);
      setPendingDelete(null);
    },
    onError: (err) => {
      toast.error(errorMessage(err));
      setPendingDelete(null);
    },
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    save.mutate({ name, description, baseUrl: baseUrl || undefined });
  }

  return (
    <>
      <PageHeader title="Projects" description="Group analyses by product, service, or business domain." />
      <div className="grid gap-6 p-4 sm:p-6 xl:grid-cols-[380px_1fr]">
        <form className="grid gap-4 rounded-md border border-line bg-white p-4 sm:p-5 self-start" onSubmit={submit}>
          <Field label="Name">
            <TextInput value={name} onChange={(e) => setName(e.target.value)} required />
          </Field>
          <Field label="Description">
            <TextArea value={description} onChange={(e) => setDescription(e.target.value)} />
          </Field>
          <Field label="Application URL (optional)">
            <TextInput
              type="url"
              placeholder="https://myapp.com"
              value={baseUrl}
              onChange={(e) => setBaseUrl(e.target.value)}
            />
          </Field>
          {save.isError ? <div className="text-sm text-red-700">{errorMessage(save.error)}</div> : null}
          <Button className="w-full sm:w-fit" loading={save.isPending} type="submit">
            <Plus className="h-4 w-4" />
            Add project
          </Button>
        </form>

        <div className="rounded-md border border-line bg-white">
          <div className="border-b border-line px-4 py-3 text-sm font-semibold">Project Registry</div>
          {projects.isLoading ? <LoadingState /> : null}
          {projects.isError ? <ErrorState message={errorMessage(projects.error)} /> : null}
          {projects.data?.length === 0 ? <EmptyState title="No projects created." /> : null}
          <div className="divide-y divide-line">
            {projects.data?.map((project) => (
              <div key={project.id}>
                <div className="flex items-center gap-3 p-3">
                  <div className="min-w-0 flex-1">
                    <div className="font-medium text-ink">{project.name}</div>
                    <div className="mt-0.5 text-sm text-slate-500">{project.description || "No description"}</div>
                  </div>
                  <button
                    type="button"
                    onClick={() => setOpenSettings(openSettings === project.id ? null : project.id)}
                    title="Project settings"
                    className={`flex h-8 w-8 items-center justify-center rounded-md transition-colors ${
                      openSettings === project.id
                        ? "bg-teal-50 text-brand"
                        : "text-slate-400 hover:bg-slate-100 hover:text-slate-700"
                    }`}
                  >
                    {openSettings === project.id ? (
                      <ChevronUp className="h-4 w-4" />
                    ) : (
                      <Settings className="h-4 w-4" />
                    )}
                  </button>
                  <Button
                    variant="danger"
                    onClick={() => setPendingDelete({ id: project.id, name: project.name })}
                    title="Delete project"
                  >
                    <Trash2 className="h-4 w-4" />
                  </Button>
                </div>
                {openSettings === project.id && (
                  <ProjectSettingsPanel projectId={project.id} />
                )}
              </div>
            ))}
          </div>
        </div>
      </div>

      <ConfirmDialog
        open={pendingDelete !== null}
        title="Delete project"
        description={`Are you sure you want to delete "${pendingDelete?.name}"? This will permanently remove all associated analyses, tests, bugs, and reports.`}
        confirmLabel="Delete"
        loading={remove.isPending}
        onConfirm={() => pendingDelete && remove.mutate(pendingDelete.id)}
        onCancel={() => setPendingDelete(null)}
      />
    </>
  );
}