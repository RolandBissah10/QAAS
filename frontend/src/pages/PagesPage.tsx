import { useEffect } from "react";
import { useSearchParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { EmptyState, ErrorState, LoadingState } from "../components/DataState";
import { PageHeader } from "../components/PageHeader";
import { Pagination } from "../components/Pagination";
import { StatusPill } from "../components/StatusPill";
import { analysisApi, pageApi, projectApi } from "../lib/api";
import { errorMessage } from "../lib/errors";
import type { Analysis, DiscoveredPage, PagedResponse } from "../lib/types";

const selectCls =
  "rounded-md border border-line bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand";

export function PagesPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const selectedProject  = searchParams.get("project")  ?? "";
  const selectedAnalysis = searchParams.get("analysis") ?? "";
  const page = parseInt(searchParams.get("page") ?? "0", 10);

  function setPage(p: number) {
    setSearchParams({ project: selectedProject, analysis: selectedAnalysis, page: String(p) }, { replace: true });
  }

  const projects = useQuery({ queryKey: ["projects"], queryFn: projectApi.list });
  const analyses = useQuery<PagedResponse<Analysis>>({
    queryKey: ["analyses-dropdown", selectedProject],
    queryFn: () => analysisApi.byProject(selectedProject, 0, 100),
    enabled: !!selectedProject,
  });
  const pages = useQuery<PagedResponse<DiscoveredPage>>({
    queryKey: ["pages", selectedAnalysis, page],
    queryFn: () => pageApi.byAnalysis(selectedAnalysis, page),
    enabled: !!selectedAnalysis,
  });

  useEffect(() => {
    const list = analyses.data?.content;
    if (list?.length && !selectedAnalysis) {
      setSearchParams({ project: selectedProject, analysis: list[0].id }, { replace: true });
    }
  }, [analyses.data, selectedAnalysis]);

  const pagesContent = pages.data?.content ?? [];

  return (
    <>
      <PageHeader
        title="Discovered Pages"
        description="Pages crawled from the submitted application URL."
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
        </div>

        <div className="rounded-md border border-line bg-white">
          <div className="border-b border-line px-4 py-3 text-sm font-semibold">
            Page Inventory {pages.data ? `(${pages.data.totalElements})` : ""}
          </div>
          {!selectedAnalysis ? (
            <EmptyState title="Select a project to view discovered pages." />
          ) : pages.isLoading ? (
            <LoadingState />
          ) : pages.isError ? (
            <ErrorState message={errorMessage(pages.error)} />
          ) : pages.data?.totalElements === 0 ? (
            <EmptyState title="No pages discovered for this analysis." />
          ) : (
            <>
              <div className="divide-y divide-line">
                {pagesContent.map((p) => (
                  <div key={p.id} className="flex items-center justify-between gap-4 px-4 py-3">
                    <div className="min-w-0">
                      <div className="truncate text-sm font-medium text-ink">{p.url}</div>
                      {p.title && (
                        <div className="mt-0.5 truncate text-xs text-slate-500">{p.title}</div>
                      )}
                    </div>
                    {p.pageType && <StatusPill status={p.pageType} />}
                  </div>
                ))}
              </div>
              <Pagination
                page={page}
                totalPages={pages.data?.totalPages ?? 1}
                totalElements={pages.data?.totalElements ?? 0}
                onPageChange={setPage}
              />
            </>
          )}
        </div>
      </div>
    </>
  );
}