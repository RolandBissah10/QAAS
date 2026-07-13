import { useSearchParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { EmptyState, ErrorState, LoadingState } from "../components/DataState";
import { PageHeader } from "../components/PageHeader";
import { analysisApi, apiEndpointApi, projectApi } from "../lib/api";
import { errorMessage } from "../lib/errors";
import type { ApiEndpoint } from "../lib/types";

const METHOD_COLORS: Record<string, string> = {
  GET:    "bg-blue-100 text-blue-700",
  POST:   "bg-green-100 text-green-700",
  PUT:    "bg-yellow-100 text-yellow-700",
  PATCH:  "bg-orange-100 text-orange-700",
  DELETE: "bg-red-100 text-red-700",
};

function statusColor(status?: number) {
  if (!status) return "text-slate-400";
  if (status < 300) return "text-emerald-600";
  if (status < 400) return "text-yellow-600";
  return "text-red-600";
}

const selectCls =
  "rounded-md border border-line bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand";

export function ApiEndpointsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const selectedProject  = searchParams.get("project")  ?? "";
  const selectedAnalysis = searchParams.get("analysis") ?? "";

  function setSelectedProject(id: string) {
    setSearchParams({ project: id }, { replace: true });
  }
  function setSelectedAnalysis(id: string) {
    setSearchParams({ project: selectedProject, analysis: id }, { replace: true });
  }

  const projects = useQuery({ queryKey: ["projects"], queryFn: projectApi.list });

  const analyses = useQuery({
    queryKey: ["analyses-dropdown", selectedProject],
    queryFn:  () => analysisApi.byProject(selectedProject, 0, 100),
    enabled:  !!selectedProject,
  });

  const endpoints = useQuery<ApiEndpoint[]>({
    queryKey: ["api-endpoints", selectedAnalysis],
    queryFn:  () => apiEndpointApi.byAnalysis(selectedAnalysis),
    enabled:  !!selectedAnalysis,
  });

  return (
    <>
      <PageHeader
        title="API Endpoints"
        description="API calls captured during browser crawls, with smoke-test results."
      />
      <div className="grid gap-4 p-4 sm:p-6">

        {/* Filters */}
        <div className="flex flex-wrap gap-3">
          <select
            className={selectCls}
            value={selectedProject}
            onChange={(e) => { setSelectedProject(e.target.value); setSelectedAnalysis(""); }}
          >
            <option value="">Select a project…</option>
            {projects.data?.map((p) => (
              <option key={p.id} value={p.id}>{p.name}</option>
            ))}
          </select>

          <select
            className={selectCls}
            value={selectedAnalysis}
            onChange={(e) => setSelectedAnalysis(e.target.value)}
            disabled={!selectedProject}
          >
            <option value="">Select an analysis…</option>
            {analyses.data?.content.map((a) => (
              <option key={a.id} value={a.id}>
                {a.url} — {new Date(a.startedAt).toLocaleDateString()}
              </option>
            ))}
          </select>
        </div>

        {/* Content */}
        {!selectedAnalysis ? (
          <EmptyState title="Select a project and analysis to view discovered API endpoints." />
        ) : endpoints.isLoading ? (
          <LoadingState />
        ) : endpoints.isError ? (
          <ErrorState message={errorMessage(endpoints.error)} />
        ) : !endpoints.data?.length ? (
          <EmptyState title="No API endpoints discovered for this analysis. Make sure the backend has been rebuilt and rerun the analysis." />
        ) : (
          <div className="rounded-md border border-line bg-white">
            <div className="divide-y divide-line">
              {endpoints.data.map((ep) => (
                <div key={ep.id} className="flex items-center gap-4 px-4 py-3">
                  {/* Method badge */}
                  <span
                    className={`shrink-0 rounded px-2 py-0.5 text-xs font-bold uppercase ${
                      METHOD_COLORS[ep.method] ?? "bg-slate-100 text-slate-600"
                    }`}
                  >
                    {ep.method}
                  </span>

                  {/* URL */}
                  <span className="min-w-0 flex-1 truncate font-mono text-xs text-ink" title={ep.url}>
                    {ep.url}
                  </span>

                  {/* HTTP status */}
                  {ep.observedStatus != null && (
                    <span className={`shrink-0 text-xs font-semibold ${statusColor(ep.observedStatus)}`}>
                      {ep.observedStatus}
                    </span>
                  )}

                  {/* Auth required */}
                  {ep.requiresAuth && (
                    <span className="shrink-0 rounded bg-violet-100 px-2 py-0.5 text-xs font-medium text-violet-700">
                      Auth
                    </span>
                  )}
                </div>
              ))}
            </div>
            <div className="border-t border-line px-4 py-2 text-xs text-slate-500">
              {endpoints.data.length} endpoint{endpoints.data.length !== 1 ? "s" : ""} discovered
            </div>
          </div>
        )}
      </div>
    </>
  );
}