import { useEffect } from "react";
import { useSearchParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { EmptyState, ErrorState, LoadingState } from "../components/DataState";
import { PageHeader } from "../components/PageHeader";
import { Pagination } from "../components/Pagination";
import { StatusPill } from "../components/StatusPill";
import { analysisApi, projectApi, testApi } from "../lib/api";
import { errorMessage } from "../lib/errors";
import type { GeneratedTest, PagedResponse } from "../lib/types";

const selectCls =
  "rounded-md border border-line bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand";

export function TestsPage() {
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
  const tests = useQuery<PagedResponse<GeneratedTest>>({
    queryKey: ["tests", selectedAnalysis, page],
    queryFn: () => testApi.byAnalysis(selectedAnalysis, page),
    enabled: !!selectedAnalysis,
  });

  useEffect(() => {
    const list = analyses.data?.content;
    if (list?.length && !selectedAnalysis) {
      setSearchParams({ project: selectedProject, analysis: list[0].id }, { replace: true });
    }
  }, [analyses.data, selectedAnalysis]);

  const testsContent = tests.data?.content ?? [];
  const passed = testsContent.filter((t) => t.status === "passed").length;
  const failed = testsContent.filter((t) => t.status === "failed").length;

  return (
    <>
      <PageHeader
        title="Generated Tests"
        description="UI test cases generated automatically from discovered pages."
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

        {(tests.data?.totalElements ?? 0) > 0 && (
          <div className="flex gap-4">
            <div className="rounded-md border border-emerald-200 bg-emerald-50 px-4 py-2 text-sm text-emerald-700">
              Passed: <span className="font-bold">{passed}</span>
            </div>
            <div className="rounded-md border border-red-200 bg-red-50 px-4 py-2 text-sm text-red-700">
              Failed: <span className="font-bold">{failed}</span>
            </div>
            <div className="rounded-md border border-line bg-white px-4 py-2 text-sm text-slate-600">
              Total: <span className="font-bold">{tests.data!.totalElements}</span>
            </div>
          </div>
        )}

        <div className="rounded-md border border-line bg-white">
          <div className="border-b border-line px-4 py-3 text-sm font-semibold">
            Test Cases {tests.data ? `(${tests.data.totalElements})` : ""}
          </div>
          {!selectedAnalysis ? (
            <EmptyState title="Select a project and analysis to view generated tests." />
          ) : tests.isLoading ? (
            <LoadingState />
          ) : tests.isError ? (
            <ErrorState message={errorMessage(tests.error)} />
          ) : tests.data?.totalElements === 0 ? (
            <EmptyState title="No tests generated for this analysis yet." />
          ) : (
            <>
              <div className="divide-y divide-line">
                {testsContent.map((test) => (
                  <div key={test.id} className="flex items-center justify-between gap-4 px-4 py-3">
                    <div className="min-w-0">
                      <div className="text-sm font-medium text-ink">{test.name}</div>
                      {test.targetUrl && (
                        <div className="mt-0.5 truncate text-xs text-slate-500">{test.targetUrl}</div>
                      )}
                    </div>
                    <div className="flex shrink-0 items-center gap-2">
                      <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-600">
                        {test.type}
                      </span>
                      <StatusPill status={test.status.toUpperCase()} />
                    </div>
                  </div>
                ))}
              </div>
              <Pagination
                page={page}
                totalPages={tests.data?.totalPages ?? 1}
                totalElements={tests.data?.totalElements ?? 0}
                onPageChange={setPage}
              />
            </>
          )}
        </div>
      </div>
    </>
  );
}