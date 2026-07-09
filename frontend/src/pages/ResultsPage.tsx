import { useQuery } from "@tanstack/react-query";
import { EmptyState, ErrorState, LoadingState } from "../components/DataState";
import { PageHeader } from "../components/PageHeader";
import { dashboardApi } from "../lib/api";
import { errorMessage } from "../lib/errors";

export function ResultsPage() {
  const summary = useQuery({ queryKey: ["dashboard-summary"], queryFn: dashboardApi.summary });

  return (
    <>
      <PageHeader title="Results Overview" description="High-level quality metrics across all analyses." />
      <div className="p-4 sm:p-6">
        {summary.isLoading ? <LoadingState /> : null}
        {summary.isError ? <ErrorState message={errorMessage(summary.error)} /> : null}
        {summary.data ? (
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            {[
              { label: "Applications Analyzed", value: summary.data.applicationsAnalyzed },
              { label: "Pages Discovered", value: summary.data.pagesDiscovered },
              { label: "Tests Executed", value: summary.data.testsExecuted },
              { label: "Pass Rate", value: `${summary.data.passRate.toFixed(1)}%` },
              { label: "Tests Passed", value: summary.data.passedTests },
              { label: "Tests Failed", value: summary.data.failedTests },
              { label: "Total Bugs", value: summary.data.bugCount },
              { label: "Critical Bugs", value: summary.data.criticalBugs },
            ].map(({ label, value }) => (
              <div key={label} className="rounded-md border border-line bg-white p-4">
                <div className="text-2xl font-bold text-ink">{value}</div>
                <div className="mt-1 text-sm text-slate-500">{label}</div>
              </div>
            ))}
          </div>
        ) : null}
        {summary.data && summary.data.testsExecuted === 0 ? (
          <EmptyState title="No execution data yet. Start an analysis to see results." />
        ) : null}
      </div>
    </>
  );
}
