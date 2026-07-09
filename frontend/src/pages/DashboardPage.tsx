import { useQuery } from "@tanstack/react-query";
import { Activity, Bug, CheckCircle2, Globe2, Layers, XCircle, Zap } from "lucide-react";
import { PageHeader } from "../components/PageHeader";
import { ErrorState, LoadingState } from "../components/DataState";
import { dashboardApi } from "../lib/api";
import { errorMessage } from "../lib/errors";

export function DashboardPage() {
  const summary = useQuery({ queryKey: ["dashboard-summary"], queryFn: dashboardApi.summary });

  if (summary.isLoading) return <LoadingState />;
  if (summary.isError) return <ErrorState message={errorMessage(summary.error)} />;

  const d = summary.data;

  const cards = [
    { label: "Apps Analyzed", value: d?.applicationsAnalyzed ?? 0, icon: Zap, color: "text-teal-600" },
    { label: "Pages Discovered", value: d?.pagesDiscovered ?? 0, icon: Globe2, color: "text-blue-600" },
    { label: "Tests Executed", value: d?.testsExecuted ?? 0, icon: Layers, color: "text-violet-600" },
    { label: "Passed", value: d?.passedTests ?? 0, icon: CheckCircle2, color: "text-emerald-600" },
    { label: "Failed", value: d?.failedTests ?? 0, icon: XCircle, color: "text-red-600" },
    { label: "Pass Rate", value: `${(d?.passRate ?? 0).toFixed(1)}%`, icon: Activity, color: "text-teal-600" },
    { label: "Bugs Found", value: d?.bugCount ?? 0, icon: Bug, color: "text-orange-600" },
    { label: "Critical Bugs", value: d?.criticalBugs ?? 0, icon: Bug, color: "text-red-600" },
  ];

  return (
    <>
      <PageHeader
        title="Dashboard"
        description="Platform-wide quality metrics across all applications and analyses."
      />
      <div className="grid gap-4 p-4 sm:p-6">
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          {cards.map((card) => {
            const Icon = card.icon;
            return (
              <div key={card.label} className="rounded-md border border-line bg-white p-4">
                <div className="flex items-center justify-between">
                  <div className="text-sm font-medium text-slate-500">{card.label}</div>
                  <Icon className={`h-4 w-4 ${card.color}`} />
                </div>
                <div className="mt-3 text-2xl font-semibold text-ink">{card.value}</div>
              </div>
            );
          })}
        </div>

        {d?.testsExecuted === 0 && (
          <div className="rounded-md border border-dashed border-line bg-white p-8 text-center">
            <Zap className="mx-auto mb-3 h-8 w-8 text-slate-300" />
            <div className="text-sm font-medium text-slate-600">No analyses run yet</div>
            <div className="mt-1 text-xs text-slate-400">
              Go to Analysis, submit an application URL, and the platform will handle the rest.
            </div>
          </div>
        )}
      </div>
    </>
  );
}
