import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Download, FileText } from "lucide-react";
import { Button } from "../components/Button";
import { EmptyState, ErrorState, LoadingState } from "../components/DataState";
import { PageHeader } from "../components/PageHeader";
import { StatusPill } from "../components/StatusPill";
import { analysisApi, projectApi, reportApi } from "../lib/api";
import type { ReportFormat } from "../lib/types";
import { errorMessage } from "../lib/errors";

const FORMATS: { value: ReportFormat; label: string; available: boolean }[] = [
  { value: "JSON", label: "JSON", available: true },
  { value: "HTML", label: "HTML", available: true },
  { value: "PDF",  label: "PDF",  available: true },
];

export function ReportsPage() {
  const queryClient = useQueryClient();
  const [selectedProject, setSelectedProject] = useState("");
  const [selectedAnalysis, setSelectedAnalysis] = useState("");
  const [format, setFormat] = useState<ReportFormat>("JSON");

  const projects = useQuery({ queryKey: ["projects"], queryFn: projectApi.list });
  const analyses = useQuery({
    queryKey: ["analyses", selectedProject],
    queryFn: () => analysisApi.byProject(selectedProject),
    enabled: !!selectedProject,
  });
  const reports = useQuery({
    queryKey: ["reports", selectedAnalysis],
    queryFn: () => reportApi.byAnalysis(selectedAnalysis),
    enabled: !!selectedAnalysis,
  });

  const generate = useMutation({
    mutationFn: () => reportApi.generate(selectedAnalysis, format),
    onSuccess: async () =>
      queryClient.invalidateQueries({ queryKey: ["reports", selectedAnalysis] }),
  });

  return (
    <>
      <PageHeader
        title="Reports"
        description="Generate and download quality reports for completed analyses."
      />
      <div className="grid gap-6 p-4 sm:p-6 xl:grid-cols-[340px_1fr]">
        <div className="grid gap-4 rounded-md border border-line bg-white p-4 sm:p-5 self-start">
          <div className="text-sm font-semibold text-ink">Generate Report</div>
          <div className="grid gap-3">
            <select
              className="w-full rounded-md border border-line bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand"
              value={selectedProject}
              onChange={(e) => { setSelectedProject(e.target.value); setSelectedAnalysis(""); }}
            >
              <option value="">Select project…</option>
              {projects.data?.map((p) => (
                <option key={p.id} value={p.id}>{p.name}</option>
              ))}
            </select>
            <select
              className="w-full rounded-md border border-line bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand"
              value={selectedAnalysis}
              onChange={(e) => setSelectedAnalysis(e.target.value)}
              disabled={!selectedProject}
            >
              <option value="">Select analysis…</option>
              {analyses.data?.filter((a) => a.status === "COMPLETED").map((a) => (
                <option key={a.id} value={a.id}>{a.url}</option>
              ))}
            </select>
            <div className="flex gap-2">
              {FORMATS.map((f) => (
                <button
                  key={f.value}
                  type="button"
                  disabled={!f.available}
                  onClick={() => f.available && setFormat(f.value)}
                  title={f.available ? undefined : "Coming soon"}
                  className={`flex-1 rounded-md border px-3 py-2 text-sm font-medium transition-colors ${
                    format === f.value && f.available
                      ? "border-brand bg-teal-50 text-brand"
                      : f.available
                      ? "border-line text-slate-600 hover:bg-slate-50"
                      : "cursor-not-allowed border-line text-slate-300"
                  }`}
                >
                  {f.label}
                </button>
              ))}
            </div>
          </div>
          {generate.isError && (
            <div className="text-sm text-red-700">{errorMessage(generate.error)}</div>
          )}
          <Button
            className="w-full"
            loading={generate.isPending}
            disabled={!selectedAnalysis}
            onClick={() => generate.mutate()}
          >
            <FileText className="h-4 w-4" />
            Generate Report
          </Button>
        </div>

        <div className="rounded-md border border-line bg-white">
          <div className="border-b border-line px-4 py-3 text-sm font-semibold">
            Generated Reports
          </div>
          {!selectedAnalysis ? (
            <EmptyState title="Select an analysis to view its reports." />
          ) : reports.isLoading ? (
            <LoadingState />
          ) : reports.isError ? (
            <ErrorState message={errorMessage(reports.error)} />
          ) : reports.data?.length === 0 ? (
            <EmptyState title="No reports generated yet for this analysis." />
          ) : (
            <div className="divide-y divide-line">
              {reports.data?.map((r) => (
                <div key={r.id} className="grid gap-3 px-4 py-4 sm:grid-cols-[1fr_auto] sm:items-center">
                  <div className="grid gap-2">
                    <div className="flex items-center gap-2">
                      <StatusPill status={r.format} />
                      <span className="text-sm text-slate-500">
                        Generated {new Date(r.generatedAt).toLocaleString()}
                      </span>
                    </div>
                    <div className="grid grid-cols-2 gap-2 text-sm sm:grid-cols-4">
                      <div className="rounded-md bg-slate-50 p-2 text-center">
                        <div className="text-lg font-bold text-ink">{r.qualityScore ?? "—"}%</div>
                        <div className="text-xs text-slate-500">Quality Score</div>
                      </div>
                      <div className="rounded-md bg-slate-50 p-2 text-center">
                        <div className="text-lg font-bold text-ink">{r.pagesDiscovered ?? 0}</div>
                        <div className="text-xs text-slate-500">Pages</div>
                      </div>
                      <div className="rounded-md bg-emerald-50 p-2 text-center">
                        <div className="text-lg font-bold text-emerald-700">{r.passedTests ?? 0}</div>
                        <div className="text-xs text-emerald-600">Passed</div>
                      </div>
                      <div className="rounded-md bg-red-50 p-2 text-center">
                        <div className="text-lg font-bold text-red-700">{r.bugCount ?? 0}</div>
                        <div className="text-xs text-red-600">Bugs</div>
                      </div>
                    </div>
                  </div>
                  {r.filePath && (
                    <a
                      href={reportApi.downloadUrl(r.id)}
                      download
                      title="Download report"
                      className="inline-flex items-center justify-center rounded-md p-2 text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-700"
                    >
                      <Download className="h-4 w-4" />
                    </a>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </>
  );
}
