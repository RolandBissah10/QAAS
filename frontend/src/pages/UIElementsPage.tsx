import { useEffect } from "react";
import { useSearchParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { EmptyState, ErrorState, LoadingState } from "../components/DataState";
import { PageHeader } from "../components/PageHeader";
import { analysisApi, pageApi, projectApi, uiElementApi } from "../lib/api";
import { errorMessage } from "../lib/errors";

export function UIElementsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const selectedProject  = searchParams.get("project")  ?? "";
  const selectedAnalysis = searchParams.get("analysis") ?? "";
  const selectedPage     = searchParams.get("page")     ?? "";

  function setSelectedPage(id: string) {
    setSearchParams({ project: selectedProject, analysis: selectedAnalysis, page: id }, { replace: true });
  }

  const projects = useQuery({ queryKey: ["projects"], queryFn: projectApi.list });
  const analyses = useQuery({
    queryKey: ["analyses-dropdown", selectedProject],
    queryFn: () => analysisApi.byProject(selectedProject, 0, 100),
    enabled: !!selectedProject,
  });
  const pages = useQuery({
    queryKey: ["pages-dropdown", selectedAnalysis],
    queryFn: () => pageApi.byAnalysis(selectedAnalysis, 0, 100),
    enabled: !!selectedAnalysis,
  });
  const elements = useQuery({
    queryKey: ["elements", selectedPage],
    queryFn: () => uiElementApi.byPage(selectedPage),
    enabled: !!selectedPage,
  });

  useEffect(() => {
    const list = analyses.data?.content;
    if (list?.length && !selectedAnalysis) {
      setSearchParams({ project: selectedProject, analysis: list[0].id }, { replace: true });
    }
  }, [analyses.data, selectedAnalysis]);

  useEffect(() => {
    const list = pages.data?.content;
    if (list?.length && !selectedPage) {
      setSearchParams({ project: selectedProject, analysis: selectedAnalysis, page: list[0].id }, { replace: true });
    }
  }, [pages.data, selectedPage]);

  return (
    <>
      <PageHeader
        title="UI Elements"
        description="Discovered interactive elements (forms, inputs, buttons, links) from crawled pages."
      />
      <div className="grid gap-4 p-4 sm:p-6">
        <div className="flex flex-wrap gap-3">
          <select
            className="rounded-md border border-line bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand"
            value={selectedProject}
            onChange={(e) => setSearchParams(e.target.value ? { project: e.target.value } : {}, { replace: true })}
          >
            <option value="">Select project…</option>
            {projects.data?.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
          <select
            className="rounded-md border border-line bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand"
            value={selectedPage}
            onChange={(e) => setSelectedPage(e.target.value)}
            disabled={!selectedAnalysis}
          >
            <option value="">Select page…</option>
            {pages.data?.content.map((p) => (
              <option key={p.id} value={p.id}>
                {p.url}
              </option>
            ))}
          </select>
        </div>

        <div className="overflow-hidden rounded-md border border-line bg-white">
          <div className="border-b border-line px-4 py-3 text-sm font-semibold">
            Discovered Elements {elements.data ? `(${elements.data.length})` : ""}
          </div>
          {!selectedPage ? (
            <EmptyState title="Select a project and page to view UI elements." />
          ) : elements.isLoading ? (
            <LoadingState />
          ) : elements.isError ? (
            <ErrorState message={errorMessage(elements.error)} />
          ) : elements.data?.length === 0 ? (
            <EmptyState title="No UI elements discovered for this page." />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-line bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
                    <th className="px-4 py-2 font-semibold">Type</th>
                    <th className="px-4 py-2 font-semibold">Label</th>
                    <th className="px-4 py-2 font-semibold">Selector</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {elements.data?.map((el) => (
                    <tr key={el.id} className="hover:bg-slate-50">
                      <td className="px-4 py-2.5">
                        <span className="rounded bg-teal-50 px-2 py-0.5 text-xs font-medium text-brand">
                          {el.elementType}
                        </span>
                      </td>
                      <td className="px-4 py-2.5 text-ink">{el.label || "—"}</td>
                      <td className="max-w-xs truncate px-4 py-2.5 font-mono text-xs text-slate-500">
                        {el.selector}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </>
  );
}