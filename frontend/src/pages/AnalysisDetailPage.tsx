import { useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Download, FileText, StopCircle } from "lucide-react";
import { AnalysisProgress } from "../components/AnalysisProgress";
import { Button } from "../components/Button";
import { EmptyState, ErrorState, LoadingState } from "../components/DataState";
import { Pagination } from "../components/Pagination";
import { StatusPill } from "../components/StatusPill";
import { useToast } from "../components/Toast";
import {
  analysisApi,
  api,
  apiEndpointApi,
  bugApi,
  deepFindingApi,
  downloadReport,
  executionApi,
  pageApi,
  reportApi,
  screenshotApi,
  testApi,
} from "../lib/api";
import { errorMessage } from "../lib/errors";
import { useAuth } from "../state/auth";
import type {
  ApiEndpoint,
  Bug,
  BugStatus,
  DeepFinding,
  DeepFindingCategory,
  DiscoveredPage,
  GeneratedTest,
  PagedResponse,
  Report,
  ReportFormat,
  Screenshot,
  TestExecution,
} from "../lib/types";

// ── Bug status select ────────────────────────────────────────────────────────

const BUG_STATUSES: BugStatus[] = ["OPEN", "CONFIRMED", "FIXED", "WONT_FIX"];

function BugStatusSelect({ bug }: { bug: Bug }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const update = useMutation({
    mutationFn: (status: BugStatus) => bugApi.updateStatus(bug.id, status),
    onSuccess: (updated) => {
      queryClient.setQueriesData<PagedResponse<Bug>>(
        { predicate: (q) => q.queryKey[0] === "bugs" },
        (old) =>
          old
            ? { ...old, content: old.content.map((b) => (b.id === updated.id ? { ...b, status: updated.status } : b)) }
            : old,
      );
      toast.success(`Bug status updated to ${updated.status.replace("_", " ")}.`);
    },
    onError: (err) => toast.error(errorMessage(err)),
  });

  return (
    <select
      className="h-7 rounded-md border border-line bg-white px-2 text-xs focus:outline-none focus:ring-2 focus:ring-brand disabled:opacity-60"
      value={bug.status}
      disabled={update.isPending}
      onChange={(e) => update.mutate(e.target.value as BugStatus)}
    >
      {BUG_STATUSES.map((s) => (
        <option key={s} value={s}>{s.replace("_", " ")}</option>
      ))}
    </select>
  );
}

// ── Screenshot thumbnail ─────────────────────────────────────────────────────

function ScreenshotImage({ screenshot }: { screenshot: Screenshot }) {
  const blob = useQuery({
    queryKey: ["screenshot-img", screenshot.id],
    queryFn: async () => {
      const resp = await api.get(screenshotApi.imageUrl(screenshot.id), { responseType: "blob" });
      return URL.createObjectURL(resp.data as Blob);
    },
    staleTime: Infinity,
  });

  if (blob.isLoading) return <div className="h-28 w-full animate-pulse rounded border border-line bg-slate-100" />;
  if (!blob.data) return <div className="flex h-28 w-full items-center justify-center rounded border border-line bg-slate-50 text-xs text-slate-400">Unavailable</div>;

  return (
    <a href={blob.data} target="_blank" rel="noopener noreferrer">
      <img src={blob.data} alt="" className="h-28 w-full rounded border border-line object-cover hover:opacity-90 transition-opacity" />
    </a>
  );
}

// ── Tab types ────────────────────────────────────────────────────────────────

type Tab = "pages" | "tests" | "executions" | "bugs" | "reports" | "api-endpoints" | "deep-findings";

const TABS: { key: Tab; label: string }[] = [
  { key: "pages",         label: "Pages"         },
  { key: "tests",         label: "Tests"         },
  { key: "executions",    label: "Executions"    },
  { key: "bugs",          label: "Bugs"          },
  { key: "api-endpoints", label: "API Endpoints" },
  { key: "deep-findings", label: "Deep Findings" },
  { key: "reports",       label: "Reports"       },
];

const CATEGORY_COLORS: Record<DeepFindingCategory, string> = {
  SECURITY:      "bg-red-100 text-red-700",
  ACCESSIBILITY: "bg-purple-100 text-purple-700",
  PERFORMANCE:   "bg-amber-100 text-amber-700",
  CONSOLE_ERROR: "bg-orange-100 text-orange-700",
  BROKEN_LINK:   "bg-slate-100 text-slate-700",
};

const SEVERITY_COLORS: Record<string, string> = {
  HIGH:   "bg-red-100 text-red-700",
  MEDIUM: "bg-amber-100 text-amber-700",
  LOW:    "bg-blue-100 text-blue-700",
};

const FORMATS: { value: ReportFormat; label: string }[] = [
  { value: "JSON", label: "JSON" },
  { value: "HTML", label: "HTML" },
  { value: "PDF",  label: "PDF"  },
];

// ── Main page ────────────────────────────────────────────────────────────────

export function AnalysisDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { accessToken } = useAuth();
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const [reportFormat, setReportFormat] = useState<ReportFormat>("JSON");

  // Tab and per-tab page numbers live in the URL so a browser refresh restores position.
  const tab         = (searchParams.get("tab") as Tab) ?? "pages";
  const pagesPage      = parseInt(searchParams.get("pp") ?? "0", 10);
  const testsPage      = parseInt(searchParams.get("tp") ?? "0", 10);
  const executionsPage = parseInt(searchParams.get("ep") ?? "0", 10);
  const bugsPage       = parseInt(searchParams.get("bp") ?? "0", 10);

  function setTab(t: Tab) {
    setSearchParams((p) => { p.set("tab", t); return p; }, { replace: true });
  }
  function onPagesPageChange(p: number) {
    setSearchParams((s) => { s.set("pp", String(p)); return s; }, { replace: true });
  }
  function onTestsPageChange(p: number) {
    setSearchParams((s) => { s.set("tp", String(p)); return s; }, { replace: true });
  }
  function onExecutionsPageChange(p: number) {
    setSearchParams((s) => { s.set("ep", String(p)); return s; }, { replace: true });
  }
  function onBugsPageChange(p: number) {
    setSearchParams((s) => { s.set("bp", String(p)); return s; }, { replace: true });
  }

  const analysis = useQuery({
    queryKey: ["analysis", id],
    queryFn: () => analysisApi.get(id!),
    refetchInterval: (q) => (q.state.data?.status === "RUNNING" ? 10000 : false),
  });

  const pages = useQuery<PagedResponse<DiscoveredPage>>({
    queryKey: ["pages", id, pagesPage],
    queryFn: () => pageApi.byAnalysis(id!, pagesPage),
    staleTime: 60_000,
  });
  const tests = useQuery<PagedResponse<GeneratedTest>>({
    queryKey: ["tests", id, testsPage],
    queryFn: () => testApi.byAnalysis(id!, testsPage),
    staleTime: 60_000,
  });
  const executions = useQuery<PagedResponse<TestExecution>>({
    queryKey: ["executions", id, executionsPage],
    queryFn: () => executionApi.byAnalysis(id!, executionsPage),
    staleTime: 60_000,
  });
  const bugs = useQuery<PagedResponse<Bug>>({
    queryKey: ["bugs", id, bugsPage],
    queryFn: () => bugApi.byAnalysis(id!, bugsPage),
    staleTime: 60_000,
  });
  const reports    = useQuery({ queryKey: ["reports",    id], queryFn: () => reportApi.byAnalysis(id!),        staleTime: 60_000 });
  const screenshots = useQuery({ queryKey: ["screenshots", id], queryFn: () => screenshotApi.byAnalysis(id!), staleTime: 60_000 });
  const apiEndpoints = useQuery<ApiEndpoint[]>({
    queryKey: ["api-endpoints", id],
    queryFn: () => apiEndpointApi.byAnalysis(id!),
    staleTime: 60_000,
  });
  const deepFindings = useQuery<DeepFinding[]>({
    queryKey: ["deep-findings", id],
    queryFn: () => deepFindingApi.byAnalysis(id!),
    staleTime: 60_000,
    enabled: !!analysis.data?.deepTest,
  });

  const toast = useToast();

  const stopAnalysis = useMutation({
    mutationFn: () => analysisApi.stop(id!),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["analysis", id] });
      toast.success("Analysis stopped successfully.");
    },
    onError: (err) => toast.error(errorMessage(err)),
  });

  const generateReport = useMutation({
    mutationFn: () => reportApi.generate(id!, reportFormat),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["reports", id] });
      toast.success(`${reportFormat} report generated successfully.`);
    },
    onError: (err) => toast.error(errorMessage(err)),
  });

  // Summary stats
  const passedCount  = executions.data?.content.filter((e) => e.status === "PASSED").length ?? 0;
  const failedCount  = executions.data?.content.filter((e) => e.status === "FAILED" || e.status === "ERROR").length ?? 0;
  const qualityScore = reports.data?.find((r) => r.qualityScore != null)?.qualityScore ?? null;

  const a = analysis.data;
  const backTo = `/analysis${sessionStorage.getItem("qaas.search/analysis") ?? ""}`;

  return (
    <div className="p-4 sm:p-6">
      {/* Back link */}
      <Link
        to={backTo}
        className="mb-4 inline-flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-700"
      >
        <ArrowLeft className="h-4 w-4" />
        All analyses
      </Link>

      {/* Header */}
      <div className="mb-4 rounded-md border border-line bg-white p-4">
        {analysis.isLoading ? (
          <div className="h-6 w-64 animate-pulse rounded bg-slate-100" />
        ) : analysis.isError ? (
          <ErrorState message={errorMessage(analysis.error)} />
        ) : a ? (
          <>
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div className="min-w-0">
                <h1 className="truncate text-lg font-semibold text-ink">{a.url}</h1>
                <div className="mt-1 text-xs text-slate-500">
                  Started: {new Date(a.startedAt).toLocaleString()}
                  {a.completedAt && ` · Completed: ${new Date(a.completedAt).toLocaleString()}`}
                </div>
                <div className="mt-0.5 font-mono text-xs text-slate-400">{a.id}</div>
              </div>
              <div className="flex shrink-0 items-center gap-2">
                <StatusPill status={a.status} />
                {a.status === "RUNNING" && (
                  <button
                    type="button"
                    title="Stop analysis"
                    disabled={stopAnalysis.isPending}
                    onClick={() => stopAnalysis.mutate()}
                    className="flex items-center gap-1.5 rounded-md border border-red-200 bg-red-50 px-2.5 py-1 text-xs font-medium text-red-600 transition-colors hover:bg-red-100 disabled:opacity-50"
                  >
                    <StopCircle className="h-3.5 w-3.5" />
                    Stop
                  </button>
                )}
              </div>
            </div>

            {a.status === "RUNNING" && accessToken && (
              <AnalysisProgress
                analysisId={a.id}
                token={accessToken}
                onDone={() => {
                  void queryClient.invalidateQueries({ queryKey: ["analysis", id] });
                  void queryClient.invalidateQueries({ queryKey: ["pages", id] });
                  void queryClient.invalidateQueries({ queryKey: ["tests", id] });
                  void queryClient.invalidateQueries({ queryKey: ["executions", id] });
                  void queryClient.invalidateQueries({ queryKey: ["bugs", id] });
                  void queryClient.invalidateQueries({ queryKey: ["reports", id] });
                  void queryClient.invalidateQueries({ queryKey: ["screenshots", id] });
                  void queryClient.invalidateQueries({ queryKey: ["api-endpoints", id] });
                }}
              />
            )}
          </>
        ) : null}
      </div>

      {/* Summary cards */}
      <div className="mb-4 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
        {[
          { label: "Pages",   value: pages.data?.totalElements      ?? "—", color: "text-ink" },
          { label: "Tests",   value: tests.data?.totalElements      ?? "—", color: "text-ink" },
          { label: "Passed",  value: passedCount,                           color: "text-emerald-700" },
          { label: "Failed",  value: failedCount,                           color: failedCount > 0 ? "text-red-700" : "text-ink" },
          { label: "Bugs",    value: bugs.data?.totalElements        ?? "—", color: (bugs.data?.totalElements ?? 0) > 0 ? "text-red-700" : "text-ink" },
          { label: "Quality", value: qualityScore != null ? `${qualityScore}%` : "—", color: "text-brand" },
        ].map(({ label, value, color }) => (
          <div key={label} className="rounded-md border border-line bg-white p-3 text-center">
            <div className={`text-xl font-bold ${color}`}>{value}</div>
            <div className="mt-0.5 text-xs text-slate-500">{label}</div>
          </div>
        ))}
      </div>

      {/* Tabs */}
      <div className="rounded-md border border-line bg-white">
        <div className="flex overflow-x-auto border-b border-line">
          {TABS.map((t) => (
            <button
              key={t.key}
              type="button"
              onClick={() => setTab(t.key)}
              className={`shrink-0 px-5 py-3 text-sm font-medium transition-colors ${
                tab === t.key
                  ? "border-b-2 border-brand text-brand"
                  : "text-slate-500 hover:text-ink"
              }`}
            >
              {t.label}
              {t.key === "pages"      && pages.data      ? ` (${pages.data.totalElements})`      : ""}
              {t.key === "tests"      && tests.data      ? ` (${tests.data.totalElements})`      : ""}
              {t.key === "executions" && executions.data ? ` (${executions.data.totalElements})` : ""}
              {t.key === "bugs"       && bugs.data       ? ` (${bugs.data.totalElements})`       : ""}
              {t.key === "api-endpoints"  && apiEndpoints.data  ? ` (${apiEndpoints.data.length})`  : ""}
              {t.key === "deep-findings" && deepFindings.data  ? ` (${deepFindings.data.length})` : ""}
              {t.key === "reports"       && reports.data       ? ` (${reports.data.length})`      : ""}
            </button>
          ))}
        </div>

        {/* Pages tab */}
        {tab === "pages" && (
          pages.isLoading ? <LoadingState /> :
          pages.isError   ? <ErrorState message={errorMessage(pages.error)} /> :
          !pages.data?.content.length ? <EmptyState title="No pages discovered yet." /> : (
            <>
              <div className="divide-y divide-line">
                {pages.data.content.map((p) => (
                  <div key={p.id} className="flex items-center justify-between gap-4 px-4 py-3">
                    <div className="min-w-0">
                      <div className="truncate text-sm font-medium text-ink">{p.url}</div>
                      {p.title && <div className="mt-0.5 truncate text-xs text-slate-500">{p.title}</div>}
                    </div>
                    {p.pageType && <StatusPill status={p.pageType} />}
                  </div>
                ))}
              </div>
              <Pagination
                page={pagesPage}
                totalPages={pages.data.totalPages}
                totalElements={pages.data.totalElements}
                onPageChange={onPagesPageChange}
              />
            </>
          )
        )}

        {/* Tests tab */}
        {tab === "tests" && (
          tests.isLoading ? <LoadingState /> :
          tests.isError   ? <ErrorState message={errorMessage(tests.error)} /> :
          !tests.data?.content.length ? <EmptyState title="No tests generated yet." /> : (
            <>
              <div className="divide-y divide-line">
                {tests.data.content.map((t) => (
                  <div key={t.id} className="flex items-center justify-between gap-4 px-4 py-3">
                    <div className="min-w-0">
                      <div className="text-sm font-medium text-ink">{t.name}</div>
                      {t.targetUrl && <div className="mt-0.5 truncate text-xs text-slate-500">{t.targetUrl}</div>}
                    </div>
                    <div className="flex shrink-0 items-center gap-2">
                      <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-600">{t.type}</span>
                      <StatusPill status={t.status.toUpperCase()} />
                    </div>
                  </div>
                ))}
              </div>
              <Pagination
                page={testsPage}
                totalPages={tests.data.totalPages}
                totalElements={tests.data.totalElements}
                onPageChange={onTestsPageChange}
              />
            </>
          )
        )}

        {/* Executions tab */}
        {tab === "executions" && (
          executions.isLoading ? <LoadingState /> :
          executions.isError   ? <ErrorState message={errorMessage(executions.error)} /> :
          !executions.data?.content.length ? <EmptyState title="No executions recorded yet." /> : (
            <div>
              <div className="divide-y divide-line">
                {executions.data.content.map((e) => (
                  <div key={e.id} className="grid gap-2 px-4 py-3 sm:grid-cols-[1fr_auto] sm:items-start">
                    <div className="min-w-0">
                      <div className="text-sm font-medium text-ink">{e.testName}</div>
                      {e.errorMessage && (
                        <div className="mt-1 line-clamp-2 text-xs text-red-600">{e.errorMessage}</div>
                      )}
                      <div className="mt-1 text-xs text-slate-400">
                        {new Date(e.startedAt).toLocaleString()}
                        {e.completedAt && ` · ${Math.round(new Date(e.completedAt).getTime() - new Date(e.startedAt).getTime())}ms`}
                      </div>
                    </div>
                    <StatusPill status={e.status} />
                  </div>
                ))}
              </div>
              <Pagination
                page={executionsPage}
                totalPages={executions.data.totalPages}
                totalElements={executions.data.totalElements}
                onPageChange={onExecutionsPageChange}
              />
              {(screenshots.data?.length ?? 0) > 0 && (
                <div className="border-t border-line p-4">
                  <div className="mb-3 text-sm font-semibold">Screenshots ({screenshots.data!.length})</div>
                  <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5">
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
          )
        )}

        {/* Bugs tab */}
        {tab === "bugs" && (
          bugs.isLoading ? <LoadingState /> :
          bugs.isError   ? <ErrorState message={errorMessage(bugs.error)} /> :
          !bugs.data?.content.length ? <EmptyState title="No bugs detected. All tests passed." /> : (
            <>
              <div className="divide-y divide-line">
                {bugs.data.content.map((b) => (
                  <div key={b.id} className="grid gap-2 px-4 py-3 sm:grid-cols-[1fr_auto_auto] sm:items-center">
                    <div className="min-w-0">
                      <div className="font-medium text-ink">{b.title}</div>
                      {b.description && (
                        <div className="mt-1 line-clamp-2 text-sm text-slate-500">{b.description}</div>
                      )}
                      <div className="mt-1 text-xs text-slate-400">{new Date(b.detectedAt).toLocaleString()}</div>
                    </div>
                    <StatusPill status={b.severity} />
                    <BugStatusSelect bug={b} />
                  </div>
                ))}
              </div>
              <Pagination
                page={bugsPage}
                totalPages={bugs.data.totalPages}
                totalElements={bugs.data.totalElements}
                onPageChange={onBugsPageChange}
              />
            </>
          )
        )}

        {/* API Endpoints tab */}
        {tab === "api-endpoints" && (
          apiEndpoints.isLoading ? <LoadingState /> :
          apiEndpoints.isError   ? <ErrorState message={errorMessage(apiEndpoints.error)} /> :
          !apiEndpoints.data?.length ? <EmptyState title="No API endpoints discovered. Run an analysis to detect endpoints." /> : (
            <div className="divide-y divide-line">
              {apiEndpoints.data.map((ep) => (
                <div key={ep.id} className="flex items-center justify-between gap-4 px-4 py-3">
                  <div className="min-w-0">
                    <div className="truncate text-sm font-medium text-ink">{ep.url}</div>
                    <div className="mt-0.5 text-xs text-slate-400">
                      Discovered {new Date(ep.discoveredAt).toLocaleString()}
                    </div>
                  </div>
                  <div className="flex shrink-0 items-center gap-2">
                    {ep.observedStatus != null && (
                      <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                        ep.observedStatus < 300 ? "bg-emerald-100 text-emerald-700" :
                        ep.observedStatus < 400 ? "bg-yellow-100 text-yellow-700" :
                        "bg-red-100 text-red-700"
                      }`}>
                        {ep.observedStatus}
                      </span>
                    )}
                    <span className="rounded bg-slate-100 px-2 py-0.5 text-xs font-mono font-semibold text-slate-700">
                      {ep.method}
                    </span>
                    {ep.requiresAuth && (
                      <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs text-amber-700">auth</span>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )
        )}

        {/* Deep Findings tab */}
        {tab === "deep-findings" && (
          !analysis.data?.deepTest ? (
            <EmptyState title="This analysis was not run with Deep Test enabled. Start a new analysis with the Deep Test option to see security, accessibility, performance, and broken-link findings." />
          ) : deepFindings.isLoading ? <LoadingState /> :
            deepFindings.isError   ? <ErrorState message={errorMessage(deepFindings.error)} /> :
            !deepFindings.data?.length ? <EmptyState title="No deep findings detected. The application passed all deep checks." /> : (
            <div className="divide-y divide-line">
              {deepFindings.data.map((f: DeepFinding) => (
                <div key={f.id} className="grid gap-2 px-4 py-3 sm:grid-cols-[1fr_auto_auto] sm:items-start">
                  <div className="min-w-0">
                    <div className="font-medium text-ink">{f.title}</div>
                    {f.description && (
                      <div className="mt-1 line-clamp-3 text-sm text-slate-500">{f.description}</div>
                    )}
                    {f.pageUrl && (
                      <div className="mt-1 truncate font-mono text-xs text-slate-400">{f.pageUrl}</div>
                    )}
                    <div className="mt-1 text-xs text-slate-400">{new Date(f.detectedAt).toLocaleString()}</div>
                  </div>
                  <span className={`self-start rounded-full px-2 py-0.5 text-xs font-medium ${CATEGORY_COLORS[f.category]}`}>
                    {f.category.replace("_", " ")}
                  </span>
                  <span className={`self-start rounded-full px-2 py-0.5 text-xs font-medium ${SEVERITY_COLORS[f.severity]}`}>
                    {f.severity}
                  </span>
                </div>
              ))}
            </div>
          )
        )}

        {/* Reports tab */}
        {tab === "reports" && (
          <div className="grid gap-4 p-4 sm:grid-cols-[280px_1fr] sm:items-start">
            {/* Generate panel */}
            <div className="grid gap-3 rounded-md border border-line p-4">
              <div className="text-sm font-semibold">Generate Report</div>
              <div className="flex gap-2">
                {FORMATS.map((f) => (
                  <button
                    key={f.value}
                    type="button"
                    onClick={() => setReportFormat(f.value)}
                    className={`flex-1 rounded-md border px-3 py-2 text-sm font-medium transition-colors ${
                      reportFormat === f.value
                        ? "border-brand bg-teal-50 text-brand"
                        : "border-line text-slate-600 hover:bg-slate-50"
                    }`}
                  >
                    {f.label}
                  </button>
                ))}
              </div>
              <Button
                className="w-full"
                loading={generateReport.isPending}
                onClick={() => generateReport.mutate()}
              >
                <FileText className="h-4 w-4" />
                Generate
              </Button>
            </div>

            {/* Report list */}
            <div className="rounded-md border border-line">
              {reports.isLoading ? <LoadingState /> :
               reports.isError   ? <ErrorState message={errorMessage(reports.error)} /> :
               !reports.data?.length ? <EmptyState title="No reports generated yet." /> : (
                <div className="divide-y divide-line">
                  {reports.data.map((r: Report) => (
                    <div key={r.id} className="flex items-center justify-between gap-3 px-4 py-3">
                      <div>
                        <div className="flex items-center gap-2">
                          <StatusPill status={r.format} />
                          <span className="text-xs text-slate-500">
                            {new Date(r.generatedAt).toLocaleString()}
                          </span>
                        </div>
                        {r.qualityScore != null && (
                          <div className="mt-1 text-xs text-slate-500">
                            Quality: <span className="font-semibold text-ink">{r.qualityScore}%</span>
                            {" · "}Passed: {r.passedTests ?? 0} · Bugs: {r.bugCount ?? 0}
                          </div>
                        )}
                      </div>
                      {r.filePath && (
                        <button
                          type="button"
                          title="Download"
                          onClick={() => void downloadReport(r.id, r.format)}
                          className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-700"
                        >
                          <Download className="h-4 w-4" />
                        </button>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}