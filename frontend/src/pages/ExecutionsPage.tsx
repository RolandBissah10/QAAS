import { useSearchParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { EmptyState, ErrorState, LoadingState } from "../components/DataState";
import { PageHeader } from "../components/PageHeader";
import { Pagination } from "../components/Pagination";
import { StatusPill } from "../components/StatusPill";
import { analysisApi, api, executionApi, projectApi, screenshotApi } from "../lib/api";
import { errorMessage } from "../lib/errors";
import type { PagedResponse, Screenshot, TestExecution } from "../lib/types";

const selectCls =
  "rounded-md border border-line bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand";

function ScreenshotImage({ screenshot }: { screenshot: Screenshot }) {
  const blob = useQuery({
    queryKey: ["screenshot-img", screenshot.id],
    queryFn: async () => {
      const resp = await api.get(screenshotApi.imageUrl(screenshot.id), { responseType: "blob" });
      return URL.createObjectURL(resp.data as Blob);
    },
    staleTime: Infinity,
  });

  if (blob.isLoading) return <div className="h-32 w-full animate-pulse rounded border border-line bg-slate-100" />;
  if (blob.isError || !blob.data) return (
    <div className="flex h-32 w-full items-center justify-center rounded border border-line bg-slate-50 text-xs text-slate-400">
      Image unavailable
    </div>
  );
  return (
    <a href={blob.data} target="_blank" rel="noopener noreferrer">
      <img src={blob.data} alt="" className="h-32 w-full rounded border border-line object-cover transition-opacity hover:opacity-90" />
    </a>
  );
}

export function ExecutionsPage() {
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
  const executions = useQuery<PagedResponse<TestExecution>>({
    queryKey: ["executions", selectedAnalysis, page],
    queryFn: () => executionApi.byAnalysis(selectedAnalysis, page),
    enabled: !!selectedAnalysis,
  });
  const screenshots = useQuery({
    queryKey: ["screenshots", selectedAnalysis],
    queryFn: () => screenshotApi.byAnalysis(selectedAnalysis),
    enabled: !!selectedAnalysis,
  });

  const execContent = executions.data?.content ?? [];
  const passed = execContent.filter((e) => e.status === "PASSED").length;
  const failed = execContent.filter((e) => e.status === "FAILED" || e.status === "ERROR").length;

  return (
    <>
      <PageHeader
        title="Executions"
        description="Playwright test execution results for each generated test case."
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

        {(executions.data?.totalElements ?? 0) > 0 && (
          <div className="flex gap-4">
            <div className="rounded-md border border-emerald-200 bg-emerald-50 px-4 py-2 text-sm text-emerald-700">
              Passed: <span className="font-bold">{passed}</span>
            </div>
            <div className="rounded-md border border-red-200 bg-red-50 px-4 py-2 text-sm text-red-700">
              Failed: <span className="font-bold">{failed}</span>
            </div>
            <div className="rounded-md border border-line bg-white px-4 py-2 text-sm text-slate-600">
              Total: <span className="font-bold">{executions.data!.totalElements}</span>
            </div>
          </div>
        )}

        <div className="rounded-md border border-line bg-white">
          <div className="border-b border-line px-4 py-3 text-sm font-semibold">
            Execution Results {executions.data ? `(${executions.data.totalElements})` : ""}
          </div>
          {!selectedAnalysis ? (
            <EmptyState title="Select a project and analysis to view execution results." />
          ) : executions.isLoading ? (
            <LoadingState />
          ) : executions.isError ? (
            <ErrorState message={errorMessage(executions.error)} />
          ) : executions.data?.totalElements === 0 ? (
            <EmptyState title="No executions recorded for this analysis." />
          ) : (
            <>
              <div className="divide-y divide-line">
                {execContent.map((exec) => (
                  <div key={exec.id} className="grid gap-2 px-4 py-3 sm:grid-cols-[1fr_auto] sm:items-start">
                    <div className="min-w-0">
                      <div className="text-sm font-medium text-ink">{exec.testName}</div>
                      {exec.errorMessage && (
                        <div className="mt-1 text-xs text-red-600 line-clamp-2">{exec.errorMessage}</div>
                      )}
                      <div className="mt-1 text-xs text-slate-400">
                        {new Date(exec.startedAt).toLocaleString()}
                        {exec.completedAt
                          ? ` · ${Math.round(new Date(exec.completedAt).getTime() - new Date(exec.startedAt).getTime())}ms`
                          : null}
                      </div>
                    </div>
                    <StatusPill status={exec.status} />
                  </div>
                ))}
              </div>
              <Pagination
                page={page}
                totalPages={executions.data?.totalPages ?? 1}
                totalElements={executions.data?.totalElements ?? 0}
                onPageChange={setPage}
              />
            </>
          )}
        </div>

        {selectedAnalysis && (screenshots.data?.length ?? 0) > 0 && (
          <div className="rounded-md border border-line bg-white">
            <div className="border-b border-line px-4 py-3 text-sm font-semibold">
              Screenshots ({screenshots.data!.length})
            </div>
            <div className="grid grid-cols-2 gap-3 p-4 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5">
              {screenshots.data!.map((s) => (
                <div key={s.id} className="grid gap-1">
                  <ScreenshotImage screenshot={s} />
                  <div className="truncate text-center text-xs text-slate-400">
                    {new Date(s.capturedAt).toLocaleTimeString()}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </>
  );
}