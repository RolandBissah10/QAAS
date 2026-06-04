import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Boxes, PlayCircle } from "lucide-react";
import { Button } from "../components/Button";
import { EmptyState, ErrorState, LoadingState } from "../components/DataState";
import { PageHeader } from "../components/PageHeader";
import { StatusPill } from "../components/StatusPill";
import { collectionApi, executionApi, testApi } from "../lib/api";
import { errorMessage } from "../lib/errors";

export function ExecutionsPage() {
  const queryClient = useQueryClient();
  const tests = useQuery({ queryKey: ["tests"], queryFn: testApi.list });
  const collections = useQuery({ queryKey: ["collections"], queryFn: collectionApi.list });
  const runTest = useMutation({
    mutationFn: executionApi.runTest,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["dashboard-summary"] });
      await queryClient.invalidateQueries({ queryKey: ["results"] });
    },
  });
  const runCollection = useMutation({
    mutationFn: executionApi.runCollection,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["dashboard-summary"] });
      await queryClient.invalidateQueries({ queryKey: ["results"] });
    },
  });
  const [runningTestId, setRunningTestId] = useState<string | null>(null);
  const [runningCollectionId, setRunningCollectionId] = useState<string | null>(null);

  // attach lifecycle handlers to set per-item running state
  runTest.onMutate = (variables) => setRunningTestId(variables as string);
  runTest.onSettled = () => setRunningTestId(null);
  runCollection.onMutate = (variables) => setRunningCollectionId(variables as string);
  runCollection.onSettled = () => setRunningCollectionId(null);

  return (
    <>
      <PageHeader title="Executions" description="Run single API tests or complete suites on demand." />
      <div className="grid gap-6 p-4 sm:p-6 xl:grid-cols-2">
        <section className="rounded-md border border-line bg-white">
          <div className="border-b border-line px-4 py-3 text-sm font-semibold">Single Tests</div>
          {tests.isLoading ? <LoadingState /> : null}
          {tests.isError ? <ErrorState message={errorMessage(tests.error)} /> : null}
          {tests.data && tests.data.length === 0 ? <EmptyState title="No tests available." /> : null}
          <div className="grid gap-3 p-4">
            {tests.data?.map((test) => (
              <div key={test.id} className="grid gap-3 rounded-md border border-line p-3 lg:grid-cols-[1fr_auto] lg:items-center">
                <div className="min-w-0">
                  <div className="font-medium text-ink">{test.name}</div>
                  <div className="break-all text-sm text-slate-500">{test.method} {test.endpoint}</div>
                </div>
                <Button className="w-full sm:w-fit" loading={runTest.isPending && runningTestId === test.id} onClick={() => runTest.mutate(test.id)}>
                  <PlayCircle className="h-4 w-4" />
                  Run
                </Button>
              </div>
            ))}
          </div>
        </section>
        <section className="rounded-md border border-line bg-white">
          <div className="border-b border-line px-4 py-3 text-sm font-semibold">Collections</div>
          {collections.isLoading ? <LoadingState /> : null}
          {collections.isError ? <ErrorState message={errorMessage(collections.error)} /> : null}
          {collections.data && collections.data.length === 0 ? <EmptyState title="No collections available." /> : null}
          <div className="grid gap-3 p-4">
            {collections.data?.map((collection) => (
              <div key={collection.id} className="grid gap-3 rounded-md border border-line p-3 lg:grid-cols-[1fr_auto] lg:items-center">
                <div className="min-w-0">
                  <div className="font-medium text-ink">{collection.name}</div>
                  <div className="text-sm text-slate-500">{collection.testIds.length} tests</div>
                </div>
                <Button className="w-full sm:w-fit" loading={runCollection.isPending && runningCollectionId === collection.id} onClick={() => runCollection.mutate(collection.id)}>
                  <Boxes className="h-4 w-4" />
                  Run suite
                </Button>
              </div>
            ))}
          </div>
        </section>
        {(runTest.data || runCollection.data || runTest.isError || runCollection.isError) ? (
          <section className="rounded-md border border-line bg-white xl:col-span-2">
            <div className="border-b border-line px-4 py-3 text-sm font-semibold">Latest Execution</div>
            <div className="grid gap-3 p-4">
              {runTest.isError || runCollection.isError ? (
                <ErrorState message={errorMessage(runTest.error ?? runCollection.error)} />
              ) : null}
              {runTest.data ? (
                <div className="flex flex-col gap-2 rounded-md bg-panel px-3 py-2 text-sm sm:flex-row sm:items-center sm:justify-between">
                  <span>{runTest.data.responseTime} ms</span>
                  <StatusPill status={runTest.data.status} />
                </div>
              ) : null}
              {runCollection.data?.results.map((result) => (
                <div key={result.id} className="flex flex-col gap-2 rounded-md bg-panel px-3 py-2 text-sm sm:flex-row sm:items-center sm:justify-between">
                  <span>{result.responseTime} ms</span>
                  <StatusPill status={result.status} />
                </div>
              ))}
            </div>
          </section>
        ) : null}
      </div>
    </>
  );
}
