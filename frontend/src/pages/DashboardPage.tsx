import { useQuery } from "@tanstack/react-query";
import { Activity, CheckCircle2, Gauge, Timer, XCircle } from "lucide-react";
import { PageHeader } from "../components/PageHeader";
import { ErrorState, LoadingState } from "../components/DataState";
import { StatusPill } from "../components/StatusPill";
import { dashboardApi } from "../lib/api";
import { errorMessage } from "../lib/errors";

export function DashboardPage() {
  const summary = useQuery({ queryKey: ["dashboard-summary"], queryFn: dashboardApi.summary });
  const trends = useQuery({ queryKey: ["dashboard-trends"], queryFn: dashboardApi.trends });

  if (summary.isLoading || trends.isLoading) {
    return <LoadingState />;
  }

  if (summary.isError || trends.isError) {
    return <ErrorState message={errorMessage(summary.error ?? trends.error)} />;
  }

  const cards = [
    { label: "Total Tests", value: summary.data?.totalTests ?? 0, icon: Gauge },
    { label: "Passed Runs", value: summary.data?.passedTests ?? 0, icon: CheckCircle2 },
    { label: "Failed Runs", value: summary.data?.failedTests ?? 0, icon: XCircle },
    { label: "Pass Rate", value: `${(summary.data?.passRate ?? 0).toFixed(1)}%`, icon: Activity },
    { label: "Avg Response", value: `${(summary.data?.averageResponseTime ?? 0).toFixed(0)} ms`, icon: Timer },
  ];

  return (
    <>
      <PageHeader title="Dashboard" description="Operational view of test coverage, run health, and recent execution state." />
      <div className="grid gap-4 p-4 sm:p-6">
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
          {cards.map((card) => {
            const Icon = card.icon;
            return (
              <div key={card.label} className="rounded-md border border-line bg-white p-4">
                <div className="flex items-center justify-between">
                  <div className="text-sm font-medium text-slate-500">{card.label}</div>
                  <Icon className="h-4 w-4 text-brand" />
                </div>
                <div className="mt-3 text-2xl font-semibold text-ink">{card.value}</div>
              </div>
            );
          })}
        </div>
        <div className="rounded-md border border-line bg-white">
          <div className="border-b border-line px-4 py-3 text-sm font-semibold text-ink">Recent Trends</div>
          <div className="grid gap-2 p-4">
            {trends.data?.length ? (
              trends.data.map((trend) => (
                <div key={`${trend.executedAt}-${trend.status}`} className="flex flex-col gap-2 rounded-md bg-panel px-3 py-2 text-sm sm:flex-row sm:items-center sm:justify-between">
                  <span className="text-slate-600">{new Date(trend.executedAt).toLocaleString()}</span>
                  <StatusPill status={trend.status} />
                </div>
              ))
            ) : (
              <div className="text-sm text-slate-500">No executions yet.</div>
            )}
          </div>
        </div>
      </div>
    </>
  );
}
