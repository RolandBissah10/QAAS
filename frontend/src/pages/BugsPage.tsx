import { useSearchParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { EmptyState, ErrorState, LoadingState } from "../components/DataState";
import { PageHeader } from "../components/PageHeader";
import { Pagination } from "../components/Pagination";
import { StatusPill } from "../components/StatusPill";
import { analysisApi, bugApi, projectApi } from "../lib/api";
import { errorMessage } from "../lib/errors";
import type { Bug, BugStatus, PagedResponse } from "../lib/types";

const BUG_STATUSES: BugStatus[] = ["OPEN", "CONFIRMED", "FIXED", "WONT_FIX"];

const selectCls =
  "rounded-md border border-line bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand";

function StatusSelect({ bugId, currentStatus }: { bugId: string; currentStatus: BugStatus }) {
  const queryClient = useQueryClient();
  const update = useMutation({
    mutationFn: (status: BugStatus) => bugApi.updateStatus(bugId, status),
    onSuccess: (updated) => {
      queryClient.setQueriesData<PagedResponse<Bug>>(
        { predicate: (q) => q.queryKey[0] === "bugs" },
        (old) =>
          old
            ? { ...old, content: old.content.map((b) => (b.id === updated.id ? { ...b, status: updated.status } : b)) }
            : old,
      );
    },
  });

  return (
    <select
      className={`${selectCls} h-8 py-0 text-xs disabled:opacity-60`}
      value={currentStatus}
      disabled={update.isPending}
      onChange={(e) => update.mutate(e.target.value as BugStatus)}
    >
      {BUG_STATUSES.map((s) => (
        <option key={s} value={s}>{s.replace("_", " ")}</option>
      ))}
    </select>
  );
}

export function BugsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const selectedProject  = searchParams.get("project")  ?? "";
  const selectedAnalysis = searchParams.get("analysis") ?? "";
  const page = parseInt(searchParams.get("page") ?? "0", 10);

  function setPage(p: number) {
    setSearchParams({ project: selectedProject, analysis: selectedAnalysis, page: String(p) }, { replace: true });
  }

  const projects = useQuery({ queryKey: ["projects"], queryFn: projectApi.list });
  const analyses = useQuery({
    queryKey: ["analyses-dropdown", selectedProject],
    queryFn: () => analysisApi.byProject(selectedProject, 0, 100),
    enabled: !!selectedProject,
  });
  const bugs = useQuery<PagedResponse<Bug>>({
    queryKey: ["bugs", selectedAnalysis, page],
    queryFn: () => bugApi.byAnalysis(selectedAnalysis, page),
    enabled: !!selectedAnalysis,
  });

  const bugsContent = bugs.data?.content ?? [];
  const criticalCount = bugsContent.filter((b) => b.severity === "CRITICAL").length;
  const highCount = bugsContent.filter((b) => b.severity === "HIGH").length;

  return (
    <>
      <PageHeader
        title="Bug Reports"
        description="Bugs detected automatically from failed test executions."
      />
      <div className="grid gap-4 p-4 sm:p-6">
        <div className="flex flex-wrap gap-3">
          <select
            className={selectCls}
            value={selectedProject}
            onChange={(e) => setSearchParams(e.target.value ? { project: e.target.value } : {}, { replace: true })}
          >
            <option value="">Select project…</option>
            {projects.data?.map((p) => (
              <option key={p.id} value={p.id}>{p.name}</option>
            ))}
          </select>
          <select
            className={selectCls}
            value={selectedAnalysis}
            onChange={(e) => setSearchParams({ project: selectedProject, analysis: e.target.value }, { replace: true })}
            disabled={!selectedProject}
          >
            <option value="">Select analysis…</option>
            {analyses.data?.content.map((a) => (
              <option key={a.id} value={a.id}>{a.url} — {a.status}</option>
            ))}
          </select>
        </div>

        {(bugs.data?.totalElements ?? 0) > 0 && (
          <div className="flex gap-4">
            <div className="rounded-md border border-red-200 bg-red-50 px-4 py-2 text-sm text-red-700">
              Critical: <span className="font-bold">{criticalCount}</span>
            </div>
            <div className="rounded-md border border-orange-200 bg-orange-50 px-4 py-2 text-sm text-orange-700">
              High: <span className="font-bold">{highCount}</span>
            </div>
            <div className="rounded-md border border-line bg-white px-4 py-2 text-sm text-slate-600">
              Total: <span className="font-bold">{bugs.data!.totalElements}</span>
            </div>
          </div>
        )}

        <div className="rounded-md border border-line bg-white">
          <div className="border-b border-line px-4 py-3 text-sm font-semibold">
            Detected Bugs {bugs.data ? `(${bugs.data.totalElements})` : ""}
          </div>
          {!selectedAnalysis ? (
            <EmptyState title="Select a project and analysis to view bugs." />
          ) : bugs.isLoading ? (
            <LoadingState />
          ) : bugs.isError ? (
            <ErrorState message={errorMessage(bugs.error)} />
          ) : bugs.data?.totalElements === 0 ? (
            <EmptyState title="No bugs detected. All tests passed." />
          ) : (
            <>
              <div className="divide-y divide-line">
                {bugsContent.map((bug) => (
                  <div key={bug.id} className="grid gap-2 px-4 py-3 sm:grid-cols-[1fr_auto_auto] sm:items-center">
                    <div className="min-w-0">
                      <div className="font-medium text-ink">{bug.title}</div>
                      {bug.description && (
                        <div className="mt-1 text-sm text-slate-500 line-clamp-2">{bug.description}</div>
                      )}
                      <div className="mt-1 text-xs text-slate-400">
                        {new Date(bug.detectedAt).toLocaleString()}
                      </div>
                    </div>
                    <StatusPill status={bug.severity} />
                    <StatusSelect bugId={bug.id} currentStatus={bug.status} />
                  </div>
                ))}
              </div>
              <Pagination
                page={page}
                totalPages={bugs.data?.totalPages ?? 1}
                totalElements={bugs.data?.totalElements ?? 0}
                onPageChange={setPage}
              />
            </>
          )}
        </div>
      </div>
    </>
  );
}