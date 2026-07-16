import { FormEvent, useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ChevronUp, Clock, Plus, Settings, Trash2, UserMinus, UserPlus } from "lucide-react";
import { Button } from "../components/Button";
import { ConfirmDialog } from "../components/ConfirmDialog";
import { EmptyState, ErrorState, LoadingState } from "../components/DataState";
import { Field, TextArea, TextInput } from "../components/Field";
import { PageHeader } from "../components/PageHeader";
import { useToast } from "../components/Toast";
import { projectApi, projectMemberApi, projectSettingsApi } from "../lib/api";
import { errorMessage } from "../lib/errors";
import type { Project, ProjectMember } from "../lib/types";

// ── Schedule presets ──────────────────────────────────────────────────────────

const SCHEDULE_PRESETS = [
  { label: "None (manual only)", value: "" },
  { label: "Every hour", value: "0 * * * *" },
  { label: "Daily at midnight", value: "0 0 * * *" },
  { label: "Daily at 9 AM", value: "0 9 * * *" },
  { label: "Weekly on Monday", value: "0 9 * * 1" },
  { label: "Custom cron…", value: "__custom__" },
];

function SchedulePanel({ project }: { project: Project }) {
  const queryClient = useQueryClient();
  const toast = useToast();

  const [preset, setPreset] = useState(() => {
    const expr = project.scheduleExpression ?? "";
    const found = SCHEDULE_PRESETS.find((p) => p.value === expr);
    return found ? expr : "__custom__";
  });
  const [custom, setCustom] = useState(
    preset === "__custom__" ? (project.scheduleExpression ?? "") : ""
  );
  const [enabled, setEnabled] = useState(project.scheduleEnabled);

  const effective = preset === "__custom__" ? custom : preset;

  const save = useMutation({
    mutationFn: () =>
      projectApi.update(project.id, {
        name: project.name,
        description: project.description,
        baseUrl: project.baseUrl,
        scheduleExpression: effective || null,
        scheduleEnabled: enabled,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["projects"] });
      toast.success("Schedule saved.");
    },
    onError: (err) => toast.error(errorMessage(err)),
  });

  return (
    <form
      className="grid gap-4 border-t border-line bg-slate-50 p-4"
      onSubmit={(e) => { e.preventDefault(); save.mutate(); }}
    >
      <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">
        Automatic Schedule
      </div>
      <p className="text-xs text-slate-500 -mt-2">
        QAAS will automatically start a new analysis on this schedule using the project's
        Application URL. Requires a URL to be set on the project.
      </p>

      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Recurrence">
          <select
            className="w-full rounded-md border border-line bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand"
            value={preset}
            onChange={(e) => setPreset(e.target.value)}
          >
            {SCHEDULE_PRESETS.map((p) => (
              <option key={p.value} value={p.value}>{p.label}</option>
            ))}
          </select>
        </Field>
        {preset === "__custom__" && (
          <Field label="Cron expression (5-field, e.g. 0 9 * * 1)">
            <TextInput
              placeholder="0 9 * * 1"
              value={custom}
              onChange={(e) => setCustom(e.target.value)}
            />
          </Field>
        )}
      </div>

      {effective && (
        <label className="flex items-center gap-2 text-sm text-slate-700 cursor-pointer">
          <input
            type="checkbox"
            checked={enabled}
            onChange={(e) => setEnabled(e.target.checked)}
            className="accent-brand"
          />
          Enable automatic runs
        </label>
      )}

      <div>
        <Button type="submit" loading={save.isPending}>
          Save schedule
        </Button>
      </div>
    </form>
  );
}

// ── Member management ─────────────────────────────────────────────────────────

function MembersPanel({ projectId }: { projectId: string }) {
  const queryClient = useQueryClient();
  const toast = useToast();

  const membersQuery = useQuery({
    queryKey: ["project-members", projectId],
    queryFn: () => projectMemberApi.list(projectId),
  });

  const [email, setEmail] = useState("");
  const [role, setRole] = useState("VIEWER");

  const add = useMutation({
    mutationFn: () => projectMemberApi.add(projectId, { email, role }),
    onSuccess: () => {
      setEmail("");
      queryClient.invalidateQueries({ queryKey: ["project-members", projectId] });
      toast.success("Member added.");
    },
    onError: (err) => toast.error(errorMessage(err)),
  });

  const remove = useMutation({
    mutationFn: (userId: string) => projectMemberApi.remove(projectId, userId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["project-members", projectId] });
      toast.success("Member removed.");
    },
    onError: (err) => toast.error(errorMessage(err)),
  });

  return (
    <div className="grid gap-4 border-t border-line bg-slate-50 p-4">
      <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">
        Team Members
      </div>
      <p className="text-xs text-slate-500 -mt-2">
        <strong>TESTER</strong> members can view data and start analyses.{" "}
        <strong>VIEWER</strong> members can only view data.
      </p>

      <form
        className="flex gap-2"
        onSubmit={(e) => { e.preventDefault(); add.mutate(); }}
      >
        <TextInput
          className="flex-1"
          type="email"
          placeholder="user@example.com"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
        />
        <select
          className="rounded-md border border-line bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand"
          value={role}
          onChange={(e) => setRole(e.target.value)}
        >
          <option value="VIEWER">Viewer</option>
          <option value="TESTER">Tester</option>
        </select>
        <Button type="submit" loading={add.isPending}>
          <UserPlus className="h-4 w-4" />
          Invite
        </Button>
      </form>

      {membersQuery.isLoading && <div className="text-xs text-slate-400">Loading members…</div>}
      {membersQuery.isError && <div className="text-xs text-red-500">{errorMessage(membersQuery.error)}</div>}
      {membersQuery.data?.length === 0 && (
        <div className="text-xs text-slate-400">No members yet. Invite a teammate above.</div>
      )}
      <div className="divide-y divide-line rounded-md border border-line bg-white">
        {membersQuery.data?.map((m: ProjectMember) => (
          <div key={m.userId} className="flex items-center gap-3 px-3 py-2">
            <div className="min-w-0 flex-1">
              <div className="text-sm font-medium text-ink">{m.displayName || m.email}</div>
              {m.displayName && <div className="text-xs text-slate-500">{m.email}</div>}
            </div>
            <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-slate-600">
              {m.role}
            </span>
            <button
              type="button"
              onClick={() => remove.mutate(m.userId)}
              className="text-slate-400 hover:text-red-600 transition-colors"
              title="Remove member"
            >
              <UserMinus className="h-4 w-4" />
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}

// ── Crawl settings ────────────────────────────────────────────────────────────

function CrawlSettingsPanel({ projectId }: { projectId: string }) {
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

// ── Settings panel wrapper ────────────────────────────────────────────────────

type SettingsTab = "crawl" | "schedule" | "members";

function ProjectSettingsPanel({ project }: { project: Project }) {
  const [tab, setTab] = useState<SettingsTab>("crawl");

  const tabBtn = (t: SettingsTab, label: string) => (
    <button
      type="button"
      onClick={() => setTab(t)}
      className={`px-3 py-1.5 text-xs font-medium rounded-md transition-colors ${
        tab === t
          ? "bg-teal-50 text-brand"
          : "text-slate-500 hover:bg-slate-100 hover:text-slate-700"
      }`}
    >
      {label}
    </button>
  );

  return (
    <div>
      <div className="flex gap-1 border-t border-line bg-white px-4 py-2">
        {tabBtn("crawl", "Crawl")}
        {tabBtn("schedule", "Schedule")}
        {project.owner && tabBtn("members", "Members")}
      </div>
      {tab === "crawl"    && <CrawlSettingsPanel projectId={project.id} />}
      {tab === "schedule" && <SchedulePanel project={project} />}
      {tab === "members"  && project.owner && <MembersPanel projectId={project.id} />}
    </div>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

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
    save.mutate({ name, description, baseUrl });
  }

  const ownedProjects = projects.data?.filter((p) => p.owner) ?? [];
  const sharedProjects = projects.data?.filter((p) => !p.owner) ?? [];

  function ProjectRow({ project }: { project: Project }) {
    const isOpen = openSettings === project.id;
    return (
      <div>
        <div className="flex items-center gap-3 p-3">
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-2">
              <span className="font-medium text-ink">{project.name}</span>
              {!project.owner && (
                <span className="rounded-full bg-blue-50 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-blue-600">
                  {project.memberRole}
                </span>
              )}
              {project.scheduleEnabled && project.scheduleExpression && (
                <span className="flex items-center gap-1 rounded-full bg-teal-50 px-2 py-0.5 text-[10px] font-medium text-teal-700">
                  <Clock className="h-3 w-3" /> scheduled
                </span>
              )}
            </div>
            <div className="mt-0.5 text-sm text-slate-500">{project.description || "No description"}</div>
          </div>
          <button
            type="button"
            onClick={() => setOpenSettings(isOpen ? null : project.id)}
            title="Project settings"
            className={`flex h-8 w-8 items-center justify-center rounded-md transition-colors ${
              isOpen
                ? "bg-teal-50 text-brand"
                : "text-slate-400 hover:bg-slate-100 hover:text-slate-700"
            }`}
          >
            {isOpen ? <ChevronUp className="h-4 w-4" /> : <Settings className="h-4 w-4" />}
          </button>
          {project.owner && (
            <Button
              variant="danger"
              onClick={() => setPendingDelete({ id: project.id, name: project.name })}
              title="Delete project"
            >
              <Trash2 className="h-4 w-4" />
            </Button>
          )}
        </div>
        {isOpen && <ProjectSettingsPanel project={project} />}
      </div>
    );
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
          <Field label="Application URL">
            <TextInput
              type="url"
              placeholder="https://myapp.com"
              value={baseUrl}
              onChange={(e) => setBaseUrl(e.target.value)}
              required
            />
          </Field>
          {save.isError ? <div className="text-sm text-red-700">{errorMessage(save.error)}</div> : null}
          <Button className="w-full sm:w-fit" loading={save.isPending} type="submit">
            <Plus className="h-4 w-4" />
            Add project
          </Button>
        </form>

        <div className="grid gap-4">
          {/* Owned projects */}
          <div className="rounded-md border border-line bg-white">
            <div className="border-b border-line px-4 py-3 text-sm font-semibold">My Projects</div>
            {projects.isLoading ? <LoadingState /> : null}
            {projects.isError ? <ErrorState message={errorMessage(projects.error)} /> : null}
            {!projects.isLoading && ownedProjects.length === 0 ? <EmptyState title="No projects created." /> : null}
            <div className="divide-y divide-line">
              {ownedProjects.map((project) => (
                <ProjectRow key={project.id} project={project} />
              ))}
            </div>
          </div>

          {/* Shared projects */}
          {sharedProjects.length > 0 && (
            <div className="rounded-md border border-line bg-white">
              <div className="border-b border-line px-4 py-3 text-sm font-semibold">Shared with Me</div>
              <div className="divide-y divide-line">
                {sharedProjects.map((project) => (
                  <ProjectRow key={project.id} project={project} />
                ))}
              </div>
            </div>
          )}
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