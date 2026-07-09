import { FormEvent, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowRight, Play, RefreshCw } from "lucide-react";
import { Link } from "react-router-dom";
import { AnalysisProgress } from "../components/AnalysisProgress";
import { Button } from "../components/Button";
import { EmptyState, ErrorState, LoadingState } from "../components/DataState";
import { Field, TextInput } from "../components/Field";
import { PageHeader } from "../components/PageHeader";
import { Pagination } from "../components/Pagination";
import { StatusPill } from "../components/StatusPill";
import { analysisApi, projectApi } from "../lib/api";
import { useAuth } from "../state/auth";
import { errorMessage } from "../lib/errors";

export function AnalysisPage() {
  const queryClient = useQueryClient();
  const { accessToken } = useAuth();
  const [selectedProject, setSelectedProject] = useState("");
  const [url, setUrl] = useState("");
  const [page, setPage] = useState(0);

  const projects = useQuery({ queryKey: ["projects"], queryFn: projectApi.list });
  const analyses = useQuery({
    queryKey: ["analyses", selectedProject, page],
    queryFn: () => analysisApi.byProject(selectedProject, page),
    enabled: !!selectedProject,
    refetchInterval: (query) => {
      const hasRunning = query.state.data?.content.some((a) => a.status === "RUNNING");
      return hasRunning ? 10000 : false;
    },
  });

  const start = useMutation({
    mutationFn: () => analysisApi.start(selectedProject, url),
    onSuccess: async () => {
      setUrl("");
      await queryClient.invalidateQueries({ queryKey: ["analyses", selectedProject] });
    },
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    start.mutate();
  }

  return (
    <>
      <PageHeader
        title="Analysis"
        description="Submit an application URL. The platform crawls, generates tests, executes them, and produces a quality report automatically."
      />
      <div className="grid gap-6 p-4 sm:p-6 xl:grid-cols-[380px_1fr]">
        <form
          className="grid gap-4 rounded-md border border-line bg-white p-4 sm:p-5 self-start"
          onSubmit={submit}
        >
          <Field label="Project">
            <select
              className="w-full rounded-md border border-line bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand"
              value={selectedProject}
              onChange={(e) => setSelectedProject(e.target.value)}
              required
            >
              <option value="">Select a project…</option>
              {projects.data?.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Application URL">
            <TextInput
              type="url"
              placeholder="https://myapp.com"
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              required
            />
          </Field>
          {start.isError ? (
            <div className="text-sm text-red-700">{errorMessage(start.error)}</div>
          ) : null}
          <Button className="w-full" loading={start.isPending} type="submit">
            <Play className="h-4 w-4" />
            Start Analysis
          </Button>
        </form>

        <div className="rounded-md border border-line bg-white">
          <div className="flex items-center justify-between border-b border-line px-4 py-3">
            <span className="text-sm font-semibold">Analysis History</span>
            {selectedProject && (
              <Button
                variant="ghost"
                onClick={() =>
                  queryClient.invalidateQueries({ queryKey: ["analyses", selectedProject] })
                }
                title="Refresh"
              >
                <RefreshCw className="h-4 w-4" />
              </Button>
            )}
          </div>

          {!selectedProject ? (
            <EmptyState title="Select a project to view analyses." />
          ) : analyses.isLoading ? (
            <LoadingState />
          ) : analyses.isError ? (
            <ErrorState message={errorMessage(analyses.error)} />
          ) : analyses.data?.totalElements === 0 ? (
            <EmptyState title="No analyses yet. Submit a URL to get started." />
          ) : (
            <>
              <div className="grid gap-3 p-4">
                {analyses.data?.content.map((a) => (
                  <div key={a.id} className="rounded-md border border-line p-3">
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0">
                        <div className="truncate font-medium text-ink">{a.url}</div>
                        <div className="mt-1 text-xs text-slate-500">
                          Started: {new Date(a.startedAt).toLocaleString()}
                          {a.completedAt
                            ? ` · Completed: ${new Date(a.completedAt).toLocaleString()}`
                            : null}
                        </div>
                      </div>
                      <div className="flex shrink-0 items-center gap-2">
                        <StatusPill status={a.status} />
                        <Link
                          to={`/analysis/${a.id}`}
                          title="View details"
                          className="flex h-7 w-7 items-center justify-center rounded-md text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-700"
                        >
                          <ArrowRight className="h-4 w-4" />
                        </Link>
                      </div>
                    </div>

                    {a.status === "RUNNING" && accessToken && (
                      <AnalysisProgress
                        analysisId={a.id}
                        token={accessToken}
                        onDone={() =>
                          queryClient.invalidateQueries({ queryKey: ["analyses", selectedProject] })
                        }
                      />
                    )}
                  </div>
                ))}
              </div>
              <Pagination
                page={page}
                totalPages={analyses.data?.totalPages ?? 1}
                totalElements={analyses.data?.totalElements ?? 0}
                onPageChange={setPage}
              />
            </>
          )}
        </div>
      </div>
    </>
  );
}