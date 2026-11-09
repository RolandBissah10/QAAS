import { FormEvent, useCallback, useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Play, Plus, Save, Trash2 } from "lucide-react";
import { Button } from "../components/Button";
import { ConfirmDialog } from "../components/ConfirmDialog";
import { EmptyState, ErrorState, LoadingState } from "../components/DataState";
import { Field, SelectInput, TextInput } from "../components/Field";
import { PageHeader } from "../components/PageHeader";
import { Pagination } from "../components/Pagination";
import { StatusPill } from "../components/StatusPill";
import { useToast } from "../components/Toast";
import { errorMessage } from "../lib/errors";
import { environmentApi, projectApi, testSuiteApi } from "../lib/api";
import type { SuiteTest } from "../lib/types";
import { useSearchParams } from "react-router-dom";

export function TestSuitesPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const projectId = searchParams.get("project") ?? "";
  const suiteId = searchParams.get("suite") ?? "";
  const environmentId = searchParams.get("environment") ?? "";
  const requestedPage = Number(searchParams.get("page") ?? 0);
  const testPage = Number.isInteger(requestedPage) && requestedPage >= 0 ? requestedPage : 0;
  const [suiteName, setSuiteName] = useState("");
  const [selectedTestIds, setSelectedTestIds] = useState<string[]>([]);
  const [selectedRunId, setSelectedRunId] = useState("");
  const [pendingDelete, setPendingDelete] = useState<{ id: string; name: string } | null>(null);
  const queryClient = useQueryClient();
  const toast = useToast();

  const updateParams = useCallback((updates: Record<string, string | null>) => {
    const next = new URLSearchParams(searchParams);
    Object.entries(updates).forEach(([key, value]) => {
      if (value) next.set(key, value);
      else next.delete(key);
    });
    setSearchParams(next, { replace: true });
  }, [searchParams, setSearchParams]);

  const projects = useQuery({ queryKey: ["projects"], queryFn: projectApi.list });
  const project = projects.data?.find((item) => item.id === projectId);
  const suites = useQuery({
    queryKey: ["test-suites", projectId],
    queryFn: () => testSuiteApi.list(projectId),
    enabled: !!projectId,
  });
  const environments = useQuery({
    queryKey: ["environments", projectId],
    queryFn: () => environmentApi.list(projectId),
    enabled: !!projectId,
  });
  const tests = useQuery({
    queryKey: ["suite-tests", projectId, testPage],
    queryFn: () => testSuiteApi.tests(projectId, testPage),
    enabled: !!projectId,
  });
  const suite = suites.data?.find((item) => item.id === suiteId);
  const runs = useQuery({
    queryKey: ["test-suite-runs", projectId, suiteId],
    queryFn: () => testSuiteApi.listRuns(projectId, suiteId),
    enabled: !!projectId && !!suiteId,
  });
  const run = useQuery({
    queryKey: ["test-suite-run", projectId, suiteId, selectedRunId],
    queryFn: () => testSuiteApi.getRun(projectId, suiteId, selectedRunId),
    enabled: !!projectId && !!suiteId && !!selectedRunId,
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status === "QUEUED" || status === "RUNNING" ? 2000 : false;
    },
  });

  useEffect(() => {
    if (projectId && suites.data?.length && !suiteId) {
      updateParams({ suite: suites.data[0].id });
    }
  }, [projectId, suites.data, suiteId, updateParams]);

  useEffect(() => {
    setSelectedTestIds(suite?.tests.map((test) => test.id) ?? []);
  }, [suite]);

  useEffect(() => {
    setSelectedRunId("");
  }, [suiteId]);

  useEffect(() => {
    if (!selectedRunId && runs.data?.content.length) {
      setSelectedRunId(runs.data.content[0].id);
    }
  }, [selectedRunId, runs.data]);

  const createSuite = useMutation({
    mutationFn: () => testSuiteApi.create(projectId, { name: suiteName }),
    onSuccess: async (created) => {
      setSuiteName("");
      updateParams({ suite: created.id, page: null });
      await queryClient.invalidateQueries({ queryKey: ["test-suites", projectId] });
      toast.success("Test suite created.");
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const saveTests = useMutation({
    mutationFn: () => testSuiteApi.replaceTests(projectId, suiteId, selectedTestIds),
    onSuccess: async (updated) => {
      setSelectedTestIds(updated.tests.map((test) => test.id));
      await queryClient.invalidateQueries({ queryKey: ["test-suites", projectId] });
      toast.success("Suite tests saved.");
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const removeSuite = useMutation({
    mutationFn: (id: string) => testSuiteApi.remove(projectId, id),
    onSuccess: async () => {
      setSelectedRunId("");
      setSelectedTestIds([]);
      updateParams({ suite: null, page: null });
      setPendingDelete(null);
      await queryClient.invalidateQueries({ queryKey: ["test-suites", projectId] });
      toast.success("Test suite and its run history deleted.");
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const startRun = useMutation({
    mutationFn: () => testSuiteApi.startRun(projectId, suiteId, environmentId || undefined),
    onSuccess: async (started) => {
      setSelectedRunId(started.id);
      await queryClient.invalidateQueries({ queryKey: ["test-suite-runs", projectId, suiteId] });
      toast.success("Suite run queued.");
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  function toggleTest(test: SuiteTest, checked: boolean) {
    setSelectedTestIds((current) => checked
      ? [...new Set([...current, test.id])]
      : current.filter((id) => id !== test.id));
  }

  function submitCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    createSuite.mutate();
  }

  return (
    <>
      <PageHeader
        title="Test Suites"
        description="Organize project tests into suites, run them in the background, and review results."
      />
      <div className="grid gap-4 p-4 sm:p-6">
        <div className="grid gap-4 rounded-md border border-line bg-white p-4 sm:grid-cols-2">
          <Field label="Project">
            <SelectInput
              value={projectId}
              onChange={(event) => {
                setSelectedRunId("");
                setSelectedTestIds([]);
                updateParams({
                  project: event.target.value || null,
                  suite: null,
                  environment: null,
                  page: null,
                });
              }}
            >
              <option value="">Select project…</option>
              {projects.data?.map((project) => (
                <option key={project.id} value={project.id}>{project.name}</option>
              ))}
            </SelectInput>
          </Field>
          <div className="flex items-end gap-2">
            <Field label="Suite" className="min-w-0 flex-1">
              <SelectInput
                value={suiteId}
                disabled={!projectId || !suites.data?.length}
                onChange={(event) => {
                  setSelectedRunId("");
                  updateParams({ suite: event.target.value || null, page: null });
                }}
              >
                <option value="">Select suite…</option>
                {suites.data?.map((item) => (
                  <option key={item.id} value={item.id}>{item.name}</option>
                ))}
              </SelectInput>
            </Field>
            {project?.owner && suite && (
              <Button
                variant="danger"
                aria-label={`Delete ${suite.name}`}
                title="Delete suite"
                disabled={removeSuite.isPending}
                onClick={() => setPendingDelete({ id: suite.id, name: suite.name })}
              >
                <Trash2 className="h-4 w-4" />
                <span className="hidden sm:inline">Delete</span>
              </Button>
            )}
          </div>
          <form className="flex items-end gap-2 sm:col-span-2" onSubmit={submitCreate}>
            <Field label="New suite name" className="flex-1">
              <TextInput
                value={suiteName}
                onChange={(event) => setSuiteName(event.target.value)}
                maxLength={160}
                required
                placeholder="e.g. Checkout regression"
              />
            </Field>
            <Button type="submit" disabled={!projectId} loading={createSuite.isPending}>
              <Plus className="h-4 w-4" />
              Create suite
            </Button>
          </form>
        </div>

        {!projectId ? (
          <EmptyState title="Select a project to manage test suites." />
        ) : suites.isLoading || tests.isLoading ? (
          <LoadingState />
        ) : suites.isError ? (
          <ErrorState message={errorMessage(suites.error)} />
        ) : tests.isError ? (
          <ErrorState message={errorMessage(tests.error)} />
        ) : (
          <>
            <section className="overflow-hidden rounded-md border border-line bg-white">
              <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line px-4 py-3">
                <div>
                  <h2 className="text-sm font-semibold text-ink">
                    {suite?.name ?? "Suite tests"}
                  </h2>
                  <p className="mt-0.5 text-xs text-slate-500">
                    Select project tests to include in this suite.
                  </p>
                </div>
                <Button
                  variant="secondary"
                  disabled={!suite || saveTests.isPending}
                  loading={saveTests.isPending}
                  onClick={() => saveTests.mutate()}
                >
                  <Save className="h-4 w-4" />
                  Save tests
                </Button>
              </div>
              {!suite ? (
                <EmptyState title="Create or select a suite to organize tests." />
              ) : tests.data?.totalElements === 0 ? (
                <EmptyState title="No generated tests are available for this project yet." />
              ) : (
                <>
                  <div className="divide-y divide-line">
                    {tests.data?.content.map((test) => (
                      <label key={test.id} className="flex cursor-pointer items-start gap-3 px-4 py-3 hover:bg-slate-50">
                        <input
                          type="checkbox"
                          className="mt-1 accent-brand"
                          checked={selectedTestIds.includes(test.id)}
                          onChange={(event) => toggleTest(test, event.target.checked)}
                        />
                        <span className="min-w-0 flex-1">
                          <span className="block text-sm font-medium text-ink">{test.name}</span>
                          {test.targetUrl && (
                            <span className="mt-0.5 block truncate text-xs text-slate-500">{test.targetUrl}</span>
                          )}
                        </span>
                        <span className="shrink-0 rounded-full bg-slate-100 px-2 py-1 text-xs text-slate-600">
                          {test.type}
                        </span>
                      </label>
                    ))}
                  </div>
                  <Pagination
                    page={tests.data?.number ?? 0}
                    totalPages={tests.data?.totalPages ?? 1}
                    totalElements={tests.data?.totalElements ?? 0}
                    onPageChange={(page) => updateParams({ page: page ? String(page) : null })}
                  />
                </>
              )}
            </section>

            {suite && (
              <section className="grid gap-4 rounded-md border border-line bg-white p-4">
                <div className="flex flex-wrap items-end justify-between gap-3">
                  <Field label="Environment" className="min-w-[220px] flex-1">
                    <SelectInput
                      value={environmentId}
                      onChange={(event) => updateParams({ environment: event.target.value || null })}
                    >
                      <option value="">Default — use each test's original URL</option>
                      {environments.data?.map((environment) => (
                        <option key={environment.id} value={environment.id}>
                          {environment.name} — {environment.baseUrl}
                        </option>
                      ))}
                    </SelectInput>
                  </Field>
                  <Field label="Run history" className="min-w-[220px] flex-1">
                    <SelectInput
                      value={selectedRunId}
                      disabled={!runs.data?.content.length}
                      onChange={(event) => setSelectedRunId(event.target.value)}
                    >
                      {!runs.data?.content.length && <option value="">No runs yet</option>}
                      {runs.data?.content.map((history) => (
                        <option key={history.id} value={history.id}>
                          {new Date(history.createdAt).toLocaleString()} — {history.environmentName ?? "Default"} — {history.status}
                        </option>
                      ))}
                    </SelectInput>
                  </Field>
                  <Button
                    disabled={!suite.tests.length || startRun.isPending}
                    loading={startRun.isPending}
                    onClick={() => startRun.mutate()}
                  >
                    <Play className="h-4 w-4" />
                    Run suite
                  </Button>
                </div>
                {run.isLoading ? (
                  <LoadingState />
                ) : run.isError ? (
                  <ErrorState message={errorMessage(run.error)} />
                ) : run.data ? (
                  <div className="grid gap-3">
                    <div className="flex flex-wrap items-center justify-between gap-3 rounded-md bg-slate-50 p-3">
                      <StatusPill status={run.data.status} />
                      <div className="flex gap-4 text-xs text-slate-600">
                        <span>Environment: <strong>{run.data.environmentName ?? "Default"}</strong></span>
                        <span>Total: <strong>{run.data.totalTests}</strong></span>
                        <span>Passed: <strong>{run.data.passedTests}</strong></span>
                        <span>Failed: <strong>{run.data.failedTests}</strong></span>
                        <span>Errors: <strong>{run.data.errorTests}</strong></span>
                      </div>
                    </div>
                    <div className="divide-y divide-line rounded-md border border-line">
                      {run.data.items.map((item) => (
                        <div key={item.id} className="flex flex-wrap items-start justify-between gap-3 px-3 py-3">
                          <div className="min-w-0">
                            <div className="text-sm font-medium text-ink">{item.testName}</div>
                            {item.errorMessage && (
                              <div className="mt-1 break-words text-xs text-red-600">{item.errorMessage}</div>
                            )}
                          </div>
                          <StatusPill status={item.status} />
                        </div>
                      ))}
                    </div>
                  </div>
                ) : (
                  <EmptyState title="No runs yet. Run this suite to see results here." />
                )}
                {runs.isError && <ErrorState message={errorMessage(runs.error)} />}
                {environments.isError && <ErrorState message={errorMessage(environments.error)} />}
              </section>
            )}
          </>
        )}
      </div>
      <ConfirmDialog
        open={pendingDelete !== null}
        title="Delete test suite?"
        description={`"${pendingDelete?.name ?? ""}" and its run history will be permanently deleted. Tests themselves will not be deleted.`}
        confirmLabel="Delete suite"
        loading={removeSuite.isPending}
        onConfirm={() => pendingDelete && removeSuite.mutate(pendingDelete.id)}
        onCancel={() => !removeSuite.isPending && setPendingDelete(null)}
      />
    </>
  );
}
