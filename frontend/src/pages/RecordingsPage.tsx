import { useState, useRef } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useSearchParams } from "react-router-dom";
import {
  ArrowLeft, Upload, Trash2, AlertCircle, Loader2, ChevronRight, X, Radio,
} from "lucide-react";
import type { Recording, RecordingEntry } from "../lib/types";
import { recordingApi, projectApi } from "../lib/api";
import { PageHeader } from "../components/PageHeader";
import { LoadingState, ErrorState, EmptyState } from "../components/DataState";
import { errorMessage } from "../lib/errors";

// ── Helpers ───────────────────────────────────────────────────────────────────

function methodBadge(method: string) {
  const map: Record<string, string> = {
    GET:     "bg-teal-50 text-teal-700",
    POST:    "bg-green-50 text-green-700",
    PUT:     "bg-blue-50 text-blue-700",
    PATCH:   "bg-purple-50 text-purple-700",
    DELETE:  "bg-red-50 text-red-700",
    HEAD:    "bg-slate-100 text-slate-500",
    OPTIONS: "bg-slate-100 text-slate-500",
  };
  return `inline-flex items-center rounded px-1.5 py-0.5 text-[10px] font-bold uppercase ${map[method] ?? "bg-slate-100 text-slate-500"}`;
}

function statusColor(code: number) {
  if (code === 0) return "text-slate-400";
  if (code < 300) return "text-emerald-600";
  if (code < 400) return "text-blue-600";
  if (code < 500) return "text-orange-500";
  return "text-red-600";
}

function fmtTime(ms: number) {
  if (ms < 1000) return `${Math.round(ms)}ms`;
  return `${(ms / 1000).toFixed(1)}s`;
}

function parseHeaders(json: string | undefined): { name: string; value: string }[] {
  if (!json) return [];
  try { return JSON.parse(json) as { name: string; value: string }[]; }
  catch { return []; }
}

function shortUrl(url: string) {
  try { const u = new URL(url); return u.pathname + u.search; }
  catch { return url; }
}

// ── Sub-components ────────────────────────────────────────────────────────────

function RecordingCard({
  rec,
  onOpen,
  onDelete,
  onStop,
}: {
  rec: Recording;
  onOpen: () => void;
  onDelete: () => void;
  onStop: () => void;
}) {
  return (
    <div className="rounded-md border border-line bg-white p-4 flex flex-col gap-3">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <div className="font-medium text-ink truncate">{rec.name}</div>
          {rec.targetUrl && (
            <div className="text-xs text-slate-500 truncate mt-0.5">{rec.targetUrl}</div>
          )}
        </div>
        <span
          className={`shrink-0 flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-semibold uppercase ${
            rec.status === "READY"         ? "bg-emerald-50 text-emerald-700"
            : rec.status === "ERROR"       ? "bg-red-50 text-red-700"
            : rec.status === "CAPTURING"   ? "bg-purple-50 text-purple-700"
            : "bg-blue-50 text-blue-700"
          }`}
        >
          {(rec.status === "CAPTURING" || rec.status === "PROCESSING") && (
            <Loader2 className="h-2.5 w-2.5 animate-spin" />
          )}
          {rec.status}
        </span>
      </div>

      {rec.status === "ERROR" && rec.errorMessage && (
        <div className="flex items-start gap-1.5 rounded bg-red-50 px-2 py-1.5 text-xs text-red-700">
          <AlertCircle className="h-3.5 w-3.5 mt-0.5 shrink-0" />
          <span className="break-all">{rec.errorMessage}</span>
        </div>
      )}

      <div className="flex items-center gap-4 text-xs text-slate-500">
        <span><span className="font-medium text-ink">{rec.entryCount}</span> requests</span>
        <span><span className="font-medium text-ink">{rec.apiEndpointCount}</span> API</span>
        {rec.capturedAt && (
          <span className="ml-auto">{new Date(rec.capturedAt).toLocaleDateString()}</span>
        )}
      </div>

      <div className="flex items-center gap-2 pt-1 border-t border-line">
        {rec.status === "CAPTURING" || rec.status === "PROCESSING" ? (
          <button
            onClick={onStop}
            className="flex-1 rounded-md border border-orange-300 bg-orange-50 px-3 py-1.5 text-xs font-medium text-orange-700 hover:bg-orange-100"
          >
            Stop Capture
          </button>
        ) : (
          <button
            onClick={onOpen}
            disabled={rec.status !== "READY"}
            className="flex-1 rounded-md border border-line px-3 py-1.5 text-xs font-medium text-ink hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed"
          >
            View Requests
          </button>
        )}
        <button
          onClick={onDelete}
          className="rounded-md border border-red-200 px-3 py-1.5 text-xs font-medium text-red-600 hover:bg-red-50"
        >
          <Trash2 className="h-3.5 w-3.5" />
        </button>
      </div>
    </div>
  );
}

function HeadersTable({ json }: { json?: string }) {
  const headers = parseHeaders(json);
  if (!headers.length) return <span className="text-xs text-slate-400 italic">No headers</span>;
  return (
    <table className="w-full text-xs">
      <tbody>
        {headers.map((h, i) => (
          <tr key={i} className="border-b border-slate-100 last:border-0">
            <td className="py-1 pr-3 font-medium text-slate-600 align-top w-2/5 break-all">{h.name}</td>
            <td className="py-1 text-ink break-all">{h.value}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function BodyBlock({ body }: { body?: string }) {
  if (!body) return <span className="text-xs text-slate-400 italic">No body</span>;
  return (
    <pre className="overflow-x-auto rounded bg-slate-50 p-3 text-xs text-ink whitespace-pre-wrap break-all max-h-64 overflow-y-auto">
      {body}
    </pre>
  );
}

// ── Upload Modal ──────────────────────────────────────────────────────────────

function UploadModal({
  projectId,
  onClose,
}: {
  projectId: string;
  onClose: () => void;
}) {
  const [name, setName] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [dragging, setDragging] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);
  const qc = useQueryClient();

  const upload = useMutation({
    mutationFn: () => recordingApi.upload(projectId, name || (file?.name ?? "Recording"), file!),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["recordings", projectId] });
      onClose();
    },
  });

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="w-full max-w-md rounded-lg border border-line bg-white shadow-xl">
        <div className="flex items-center justify-between border-b border-line px-5 py-4">
          <div className="font-semibold text-ink">Upload HAR Recording</div>
          <button onClick={onClose} className="text-slate-400 hover:text-ink"><X className="h-4 w-4" /></button>
        </div>

        <div className="p-5 space-y-4">
          <div>
            <label className="mb-1 block text-sm font-medium text-slate-600">Name</label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="My app session"
              className="w-full rounded-md border border-line px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-teal-500"
            />
          </div>

          <div>
            <label className="mb-1 block text-sm font-medium text-slate-600">HAR File</label>
            <div
              onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
              onDragLeave={() => setDragging(false)}
              onDrop={(e) => {
                e.preventDefault();
                setDragging(false);
                const f = e.dataTransfer.files[0];
                if (f) setFile(f);
              }}
              onClick={() => fileRef.current?.click()}
              className={`flex cursor-pointer flex-col items-center justify-center gap-2 rounded-md border-2 border-dashed p-6 text-sm transition-colors ${
                dragging ? "border-teal-400 bg-teal-50" : "border-slate-200 hover:border-teal-300 hover:bg-slate-50"
              }`}
            >
              <Upload className="h-6 w-6 text-slate-400" />
              {file ? (
                <span className="font-medium text-ink">{file.name}</span>
              ) : (
                <span className="text-slate-500">Drag &amp; drop or click to select a <strong>.har</strong> file</span>
              )}
              <input
                ref={fileRef}
                type="file"
                accept=".har"
                className="hidden"
                onChange={(e) => { const f = e.target.files?.[0]; if (f) setFile(f); }}
              />
            </div>
          </div>

          <div className="rounded-md bg-blue-50 p-3 text-xs text-blue-800 space-y-1">
            <div className="font-semibold">How to export a HAR file from Chrome:</div>
            <ol className="list-decimal pl-4 space-y-0.5">
              <li>Open DevTools (F12) and go to the <strong>Network</strong> tab</li>
              <li>Browse through your application as normal</li>
              <li>Right-click any request → <strong>Save all as HAR with content</strong></li>
            </ol>
          </div>

          {upload.isError && (
            <div className="rounded bg-red-50 px-3 py-2 text-xs text-red-700">{errorMessage(upload.error)}</div>
          )}
        </div>

        <div className="flex justify-end gap-2 border-t border-line px-5 py-4">
          <button onClick={onClose} className="rounded-md border border-line px-4 py-2 text-sm text-slate-600 hover:bg-slate-50">
            Cancel
          </button>
          <button
            onClick={() => upload.mutate()}
            disabled={!file || upload.isPending}
            className="flex items-center gap-2 rounded-md bg-teal-600 px-4 py-2 text-sm font-medium text-white hover:bg-teal-700 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {upload.isPending && <Loader2 className="h-3.5 w-3.5 animate-spin" />}
            Upload
          </button>
        </div>
      </div>
    </div>
  );
}

// ── Entry Detail Panel ────────────────────────────────────────────────────────

function EntryDetail({ entry, onClose }: { entry: RecordingEntry; onClose: () => void }) {
  const [tab, setTab] = useState<"request" | "response">("request");

  return (
    <div className="flex flex-col h-full border-l border-line bg-white">
      <div className="flex items-center justify-between gap-2 border-b border-line px-4 py-3">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <span className={methodBadge(entry.method)}>{entry.method}</span>
            <span className={`text-xs font-semibold ${statusColor(entry.statusCode)}`}>{entry.statusCode}</span>
            <span className="text-xs text-slate-400">{fmtTime(entry.timeTaken)}</span>
          </div>
          <div className="mt-1 text-xs text-slate-500 truncate" title={entry.url}>{entry.url}</div>
        </div>
        <button onClick={onClose} className="shrink-0 text-slate-400 hover:text-ink"><X className="h-4 w-4" /></button>
      </div>

      <div className="flex border-b border-line">
        {(["request", "response"] as const).map((t) => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className={`px-4 py-2 text-xs font-medium capitalize border-b-2 transition-colors ${
              tab === t ? "border-teal-500 text-teal-600" : "border-transparent text-slate-500 hover:text-ink"
            }`}
          >
            {t}
          </button>
        ))}
      </div>

      <div className="flex-1 overflow-y-auto p-4 space-y-4 text-sm">
        {tab === "request" ? (
          <>
            <div>
              <div className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Headers</div>
              <HeadersTable json={entry.requestHeaders} />
            </div>
            <div>
              <div className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Body</div>
              <BodyBlock body={entry.requestBody} />
            </div>
          </>
        ) : (
          <>
            <div>
              <div className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Headers</div>
              <HeadersTable json={entry.responseHeaders} />
            </div>
            <div>
              <div className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Body</div>
              <BodyBlock body={entry.responseBody} />
            </div>
          </>
        )}
      </div>
    </div>
  );
}

// ── Detail View ───────────────────────────────────────────────────────────────

function DetailView({
  recording,
  onBack,
  onDelete,
}: {
  recording: Recording;
  onBack: () => void;
  onDelete: () => void;
}) {
  const [methodFilter, setMethodFilter] = useState("");
  const [urlSearch, setUrlSearch] = useState("");
  const [selectedEntry, setSelectedEntry] = useState<RecordingEntry | null>(null);
  const [page, setPage] = useState(0);

  const entries = useQuery({
    queryKey: ["recording-entries", recording.id, page],
    queryFn: () => recordingApi.entries(recording.id, page, 100),
  });

  const stats = useQuery({
    queryKey: ["recording-stats", recording.id],
    queryFn: () => recordingApi.stats(recording.id),
  });

  const allEntries = entries.data?.content ?? [];
  const methods = [...new Set(allEntries.map((e) => e.method))].sort();

  const filtered = allEntries.filter((e) => {
    if (methodFilter && e.method !== methodFilter) return false;
    if (urlSearch && !e.url.toLowerCase().includes(urlSearch.toLowerCase())) return false;
    return true;
  });

  return (
    <div className="flex flex-col h-[calc(100vh-4rem)]">
      {/* Top bar */}
      <div className="border-b border-line bg-white px-4 py-3 flex items-center justify-between gap-4">
        <button onClick={onBack} className="flex items-center gap-1.5 text-sm text-slate-500 hover:text-ink">
          <ArrowLeft className="h-4 w-4" /> Back
        </button>
        <div className="min-w-0 flex-1">
          <div className="font-semibold text-ink truncate">{recording.name}</div>
          {recording.targetUrl && <div className="text-xs text-slate-500">{recording.targetUrl}</div>}
        </div>
        <button
          onClick={onDelete}
          className="flex items-center gap-1.5 rounded-md border border-red-200 px-3 py-1.5 text-xs text-red-600 hover:bg-red-50"
        >
          <Trash2 className="h-3.5 w-3.5" /> Delete
        </button>
      </div>

      {/* Stats bar */}
      {stats.data && (
        <div className="flex flex-wrap items-center gap-x-6 gap-y-1 border-b border-line bg-white px-4 py-2 text-xs text-slate-500">
          <span><span className="font-semibold text-ink">{stats.data.totalEntries}</span> requests</span>
          <span><span className="font-semibold text-ink">{recording.apiEndpointCount}</span> API calls</span>
          <span className={stats.data.errorCount > 0 ? "text-red-600 font-medium" : ""}>
            <span className="font-semibold">{stats.data.errorCount}</span> errors
          </span>
          <span><span className="font-semibold text-ink">{Math.round(stats.data.avgTimeTakenMs)}ms</span> avg</span>
        </div>
      )}

      {/* Filters */}
      <div className="flex flex-wrap items-center gap-2 border-b border-line bg-white px-4 py-2">
        <button
          onClick={() => setMethodFilter("")}
          className={`rounded-full px-3 py-1 text-xs font-medium ${!methodFilter ? "bg-teal-600 text-white" : "bg-slate-100 text-slate-600 hover:bg-slate-200"}`}
        >
          All
        </button>
        {methods.map((m) => (
          <button
            key={m}
            onClick={() => setMethodFilter(methodFilter === m ? "" : m)}
            className={`rounded-full px-3 py-1 text-xs font-medium ${methodFilter === m ? "bg-teal-600 text-white" : "bg-slate-100 text-slate-600 hover:bg-slate-200"}`}
          >
            {m}
          </button>
        ))}
        <input
          type="text"
          value={urlSearch}
          onChange={(e) => setUrlSearch(e.target.value)}
          placeholder="Filter by URL…"
          className="ml-auto w-full rounded-md border border-line px-3 py-1 text-xs focus:outline-none focus:ring-2 focus:ring-teal-500 sm:w-48"
        />
      </div>

      {/* Entry list + detail panel */}
      <div className="flex flex-1 overflow-hidden">
        {/* Entry list — hidden on mobile when a detail panel is open */}
        <div className={`flex flex-col overflow-y-auto ${selectedEntry ? "hidden md:flex md:w-1/2" : "w-full"}`}>
          {entries.isLoading && <LoadingState />}
          {entries.isError && <ErrorState message={errorMessage(entries.error)} />}
          {!entries.isLoading && filtered.length === 0 && (
            <EmptyState title="No requests match the current filter." />
          )}
          {filtered.map((entry) => (
            <button
              key={entry.id}
              onClick={() => setSelectedEntry(selectedEntry?.id === entry.id ? null : entry)}
              className={`flex items-center gap-3 border-b border-line px-4 py-2.5 text-left hover:bg-slate-50 transition-colors ${
                selectedEntry?.id === entry.id ? "bg-teal-50" : ""
              }`}
            >
              <span className={methodBadge(entry.method)}>{entry.method}</span>
              <span className={`w-10 shrink-0 text-xs font-semibold ${statusColor(entry.statusCode)}`}>
                {entry.statusCode || "—"}
              </span>
              <span className="flex-1 min-w-0 text-xs text-ink truncate" title={entry.url}>
                {shortUrl(entry.url)}
              </span>
              <span className="shrink-0 text-xs text-slate-400">{fmtTime(entry.timeTaken)}</span>
              <ChevronRight className="h-3.5 w-3.5 shrink-0 text-slate-300" />
            </button>
          ))}

          {/* Pagination */}
          {entries.data && entries.data.totalPages > 1 && (
            <div className="flex items-center justify-center gap-3 border-t border-line py-3">
              <button
                disabled={page === 0}
                onClick={() => setPage((p) => p - 1)}
                className="text-xs text-teal-600 disabled:opacity-40"
              >
                ← Prev
              </button>
              <span className="text-xs text-slate-500">
                Page {page + 1} of {entries.data.totalPages}
              </span>
              <button
                disabled={page + 1 >= entries.data.totalPages}
                onClick={() => setPage((p) => p + 1)}
                className="text-xs text-teal-600 disabled:opacity-40"
              >
                Next →
              </button>
            </div>
          )}
        </div>

        {/* Detail panel — full width on mobile, half width on md+ */}
        {selectedEntry && (
          <div className="w-full overflow-hidden md:w-1/2">
            <EntryDetail entry={selectedEntry} onClose={() => setSelectedEntry(null)} />
          </div>
        )}
      </div>
    </div>
  );
}

// ── Main Page ─────────────────────────────────────────────────────────────────

export function RecordingsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const selectedProject = searchParams.get("project") ?? "";
  const [uploadOpen, setUploadOpen] = useState(false);
  const [detailRecording, setDetailRecording] = useState<Recording | null>(null);
  const qc = useQueryClient();

  const projects = useQuery({ queryKey: ["projects"], queryFn: projectApi.list });

  const hasInProgress = (recs: Recording[] | undefined) =>
    recs?.some((r) => r.status === "CAPTURING" || r.status === "PROCESSING") ?? false;

  const recordings = useQuery({
    queryKey: ["recordings", selectedProject],
    queryFn: () => recordingApi.list(selectedProject),
    enabled: !!selectedProject,
    refetchInterval: (query) => hasInProgress(query.state.data) ? 3_000 : false,
  });

  const captureMutation = useMutation({
    mutationFn: () => recordingApi.capture(selectedProject),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["recordings", selectedProject] }),
  });

  const stopMutation = useMutation({
    mutationFn: (id: string) => recordingApi.stop(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["recordings", selectedProject] }),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => recordingApi.remove(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["recordings", selectedProject] });
      setDetailRecording(null);
    },
  });

  if (detailRecording) {
    return (
      <DetailView
        recording={detailRecording}
        onBack={() => setDetailRecording(null)}
        onDelete={() => deleteMutation.mutate(detailRecording.id)}
      />
    );
  }

  return (
    <>
      <PageHeader
        title="Recordings"
        description="Upload HAR session recordings to inspect captured requests, responses, and API calls."
      />

      <div className="p-4 sm:p-6 space-y-4">
        {/* Toolbar */}
        <div className="flex flex-wrap items-center gap-3">
          <div className="flex items-center gap-2">
            <label className="text-sm font-medium text-slate-600 shrink-0">Project</label>
            <select
              value={selectedProject}
              onChange={(e) => setSearchParams(e.target.value ? { project: e.target.value } : {}, { replace: true })}
              className="rounded-md border border-line bg-white px-3 py-1.5 text-sm text-ink shadow-sm focus:outline-none focus:ring-2 focus:ring-teal-500"
            >
              <option value="">Select project…</option>
              {projects.data?.map((p) => (
                <option key={p.id} value={p.id}>{p.name}</option>
              ))}
            </select>
          </div>

          <div className="ml-auto flex items-center gap-2">
            <button
              onClick={() => captureMutation.mutate()}
              disabled={!selectedProject || captureMutation.isPending || hasInProgress(recordings.data)}
              title={!selectedProject ? "Select a project first" : "Launch a Playwright session against the project URL and capture all traffic automatically"}
              className="flex items-center gap-2 rounded-md border border-purple-300 bg-purple-50 px-4 py-2 text-sm font-medium text-purple-700 hover:bg-purple-100 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {captureMutation.isPending || hasInProgress(recordings.data)
                ? <Loader2 className="h-4 w-4 animate-spin" />
                : <Radio className="h-4 w-4" />}
              Auto-Capture
            </button>
            <button
              onClick={() => setUploadOpen(true)}
              disabled={!selectedProject}
              title={!selectedProject ? "Select a project first" : "Upload a HAR file"}
              className="flex items-center gap-2 rounded-md bg-teal-600 px-4 py-2 text-sm font-medium text-white hover:bg-teal-700 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <Upload className="h-4 w-4" /> Upload HAR
            </button>
          </div>
        </div>

        {/* Content */}
        {!selectedProject && (
          <EmptyState title="Select a project to view and upload recordings." />
        )}

        {selectedProject && recordings.isLoading && <LoadingState />}
        {selectedProject && recordings.isError && <ErrorState message={errorMessage(recordings.error)} />}

        {selectedProject && !recordings.isLoading && recordings.data?.length === 0 && (
          <EmptyState title="No recordings yet — upload a HAR file to get started." />
        )}

        {recordings.data && recordings.data.length > 0 && (
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {recordings.data.map((rec) => (
              <RecordingCard
                key={rec.id}
                rec={rec}
                onOpen={() => setDetailRecording(rec)}
                onStop={() => stopMutation.mutate(rec.id)}
                onDelete={() => deleteMutation.mutate(rec.id)}
              />
            ))}
          </div>
        )}
      </div>

      {uploadOpen && (
        <UploadModal projectId={selectedProject} onClose={() => setUploadOpen(false)} />
      )}
    </>
  );
}