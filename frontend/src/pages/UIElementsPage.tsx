import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { EmptyState, ErrorState, LoadingState } from "../components/DataState";
import { PageHeader } from "../components/PageHeader";
import { analysisApi, pageApi, projectApi, uiElementApi } from "../lib/api";
import { errorMessage } from "../lib/errors";

export function UIElementsPage() {
  const [selectedProject, setSelectedProject] = useState("");
  const [selectedAnalysis, setSelectedAnalysis] = useState("");
  const [selectedPage, setSelectedPage] = useState("");

  const projects = useQuery({ queryKey: ["projects"], queryFn: projectApi.list });
  const analyses = useQuery({
    queryKey: ["analyses", selectedProject],
    queryFn: () => analysisApi.byProject(selectedProject),
    enabled: !!selectedProject,
  });
  const pages = useQuery({
    queryKey: ["pages", selectedAnalysis],
    queryFn: () => pageApi.byAnalysis(selectedAnalysis),
    enabled: !!selectedAnalysis,
  });
  const elements = useQuery({
    queryKey: ["elements", selectedPage],
    queryFn: () => uiElementApi.byPage(selectedPage),
    enabled: !!selectedPage,
  });

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
            onChange={(e) => {
              setSelectedProject(e.target.value);
              setSelectedAnalysis("");
              setSelectedPage("");
            }}
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
            value={selectedAnalysis}
            onChange={(e) => {
              setSelectedAnalysis(e.target.value);
              setSelectedPage("");
            }}
            disabled={!selectedProject}
          >
            <option value="">Select analysis…</option>
            {analyses.data?.map((a) => (
              <option key={a.id} value={a.id}>
                {a.url}
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
            {pages.data?.map((p) => (
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
            <EmptyState title="Select a project, analysis, and page to view UI elements." />
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