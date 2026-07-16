import { useQuery } from "@tanstack/react-query";
import { useSearchParams } from "react-router-dom";
import { Activity, Bug, CheckCircle2, Globe2, Layers, XCircle, Zap } from "lucide-react";
import type { ReactNode } from "react";
import {
  ResponsiveContainer,
  LineChart,
  Line,
  BarChart,
  Bar,
  PieChart,
  Pie,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
} from "recharts";
import { PageHeader } from "../components/PageHeader";
import { ErrorState, LoadingState } from "../components/DataState";
import { dashboardApi, projectApi } from "../lib/api";
import { errorMessage } from "../lib/errors";

const BRAND   = "#0d9488";
const PASS    = "#059669";
const FAIL    = "#dc2626";
const BUG     = "#ea580c";
const NEUTRAL = "#6366f1";

const SEVERITY_COLORS: Record<string, string> = {
  Critical: "#dc2626",
  High:     "#ea580c",
  Medium:   "#f59e0b",
  Low:      "#22c55e",
};

function ChartTooltip({ active, payload, label }: {
  active?: boolean;
  payload?: { name: string; value: number; color: string }[];
  label?: string;
}) {
  if (!active || !payload?.length) return null;
  return (
    <div className="rounded-md border border-line bg-white px-3 py-2 shadow-md text-xs">
      {label && <div className="mb-1 font-semibold text-ink">{label}</div>}
      {payload.map((p) => (
        <div key={p.name} className="flex items-center gap-2">
          <span className="inline-block h-2 w-2 rounded-full" style={{ background: p.color }} />
          <span className="text-slate-500">{p.name}:</span>
          <span className="font-medium text-ink">{p.value}</span>
        </div>
      ))}
    </div>
  );
}

function ChartCard({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="rounded-md border border-line bg-white p-4">
      <div className="mb-4 text-sm font-semibold text-ink">{title}</div>
      {children}
    </div>
  );
}

function EmptyChart({ height = 220 }: { height?: number }) {
  return (
    <div
      className="flex flex-col items-center justify-center gap-2 rounded border border-dashed border-slate-200 text-slate-400"
      style={{ height }}
    >
      <span className="text-xs">No data yet — run an analysis to populate this chart</span>
    </div>
  );
}

export function DashboardPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const selectedProject = searchParams.get("project") ?? "";

  const projects = useQuery({ queryKey: ["projects"], queryFn: projectApi.list });

  const summary = useQuery({
    queryKey: ["dashboard-summary", selectedProject],
    queryFn: () => dashboardApi.summary(selectedProject || undefined),
    staleTime: 60_000,
  });
  const trends = useQuery({
    queryKey: ["dashboard-trends", selectedProject],
    queryFn: () => dashboardApi.trends(selectedProject || undefined),
    staleTime: 60_000,
  });

  if (summary.isLoading) return <LoadingState />;
  if (summary.isError)   return <ErrorState message={errorMessage(summary.error)} />;

  const d = summary.data!;

  const cards = [
    { label: "Apps Analyzed",    value: d?.applicationsAnalyzed ?? 0,        icon: Zap,          color: "text-teal-600"    },
    { label: "Pages Discovered", value: d?.pagesDiscovered ?? 0,             icon: Globe2,        color: "text-blue-600"    },
    { label: "Tests Executed",   value: d?.testsExecuted ?? 0,               icon: Layers,        color: "text-violet-600"  },
    { label: "Passed",           value: d?.passedTests ?? 0,                 icon: CheckCircle2,  color: "text-emerald-600" },
    { label: "Failed",           value: d?.failedTests ?? 0,                 icon: XCircle,       color: "text-red-600"     },
    { label: "Pass Rate",        value: `${(d?.passRate ?? 0).toFixed(1)}%`, icon: Activity,      color: "text-teal-600"    },
    { label: "Bugs Found",       value: d?.bugCount ?? 0,                    icon: Bug,           color: "text-orange-600"  },
    { label: "Critical Bugs",    value: d?.criticalBugs ?? 0,                icon: Bug,           color: "text-red-600"     },
  ];

  // Severity donut driven by summary (always available without trend data)
  const bugData = [
    { name: "Critical", value: d?.criticalBugs ?? 0, fill: SEVERITY_COLORS.Critical },
    { name: "High",     value: d?.highBugs     ?? 0, fill: SEVERITY_COLORS.High     },
    { name: "Medium",   value: d?.mediumBugs   ?? 0, fill: SEVERITY_COLORS.Medium   },
    { name: "Low",      value: d?.lowBugs      ?? 0, fill: SEVERITY_COLORS.Low      },
  ].filter((s) => s.value > 0);

  const trendData = trends.data ?? [];

  const selectedProjectName = projects.data?.find((p) => p.id === selectedProject)?.name;

  return (
    <>
      <PageHeader
        title="Dashboard"
        description={
          selectedProjectName
            ? `Quality metrics for ${selectedProjectName}.`
            : "Platform-wide quality metrics across all applications and analyses."
        }
      />
      <div className="grid gap-4 p-4 sm:p-6">

        {/* Project filter */}
        <div className="flex items-center gap-3">
          <label htmlFor="dash-project" className="text-sm font-medium text-slate-600 shrink-0">
            Project
          </label>
          <select
            id="dash-project"
            value={selectedProject}
            onChange={(e) =>
              setSearchParams(e.target.value ? { project: e.target.value } : {}, { replace: true })
            }
            className="rounded-md border border-line bg-white px-3 py-1.5 text-sm text-ink shadow-sm focus:outline-none focus:ring-2 focus:ring-teal-500"
          >
            <option value="">All projects</option>
            {projects.data?.map((p) => (
              <option key={p.id} value={p.id}>{p.name}</option>
            ))}
          </select>
        </div>

        {/* Summary cards */}
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

        {/* Quality score trend */}
        <ChartCard title="Quality Score Over Time">
          {trendData.length === 0 ? <EmptyChart height={240} /> : (
            <ResponsiveContainer width="100%" height={240}>
              <LineChart data={trendData} margin={{ top: 4, right: 16, left: 0, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                <XAxis dataKey="date" tick={{ fontSize: 11 }} />
                <YAxis domain={[0, 100]} tick={{ fontSize: 11 }} unit="%" />
                <Tooltip content={<ChartTooltip />} />
                <Legend wrapperStyle={{ fontSize: 12 }} />
                <Line
                  type="monotone"
                  dataKey="qualityScore"
                  name="Quality Score"
                  stroke={BRAND}
                  strokeWidth={2}
                  dot={{ r: 3 }}
                  activeDot={{ r: 5 }}
                  connectNulls
                />
              </LineChart>
            </ResponsiveContainer>
          )}
        </ChartCard>

        {/* Passed vs Failed */}
        <ChartCard title="Tests Passed vs Failed per Analysis">
          {trendData.length === 0 ? <EmptyChart height={240} /> : (
            <ResponsiveContainer width="100%" height={240}>
              <BarChart data={trendData} margin={{ top: 4, right: 16, left: 0, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                <XAxis dataKey="date" tick={{ fontSize: 11 }} />
                <YAxis allowDecimals={false} tick={{ fontSize: 11 }} />
                <Tooltip content={<ChartTooltip />} cursor={false} />
                <Legend wrapperStyle={{ fontSize: 12 }} />
                <Bar dataKey="passedTests" name="Passed" fill={PASS} radius={[3, 3, 0, 0]} />
                <Bar dataKey="failedTests" name="Failed" fill={FAIL} radius={[3, 3, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </ChartCard>

        {/* Bug count over time + severity donut */}
        <div className="grid gap-4 lg:grid-cols-2">
          <ChartCard title="Bug Count Over Time">
            {trendData.length === 0 ? <EmptyChart /> : (
              <ResponsiveContainer width="100%" height={220}>
                <LineChart data={trendData} margin={{ top: 4, right: 16, left: 0, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                  <XAxis dataKey="date" tick={{ fontSize: 11 }} />
                  <YAxis allowDecimals={false} tick={{ fontSize: 11 }} />
                  <Tooltip content={<ChartTooltip />} />
                  <Legend wrapperStyle={{ fontSize: 12 }} />
                  <Line
                    type="monotone"
                    dataKey="bugCount"
                    name="Bugs"
                    stroke={BUG}
                    strokeWidth={2}
                    dot={{ r: 3 }}
                    activeDot={{ r: 5 }}
                    connectNulls
                  />
                  <Line
                    type="monotone"
                    dataKey="pagesDiscovered"
                    name="Pages"
                    stroke={NEUTRAL}
                    strokeWidth={2}
                    strokeDasharray="4 2"
                    dot={{ r: 3 }}
                    connectNulls
                  />
                </LineChart>
              </ResponsiveContainer>
            )}
          </ChartCard>

          <ChartCard title="Bug Severity Breakdown">
            {bugData.length === 0 ? (
              <EmptyChart />
            ) : (
              <ResponsiveContainer width="100%" height={220}>
                <PieChart>
                  <Pie
                    data={bugData}
                    cx="50%"
                    cy="50%"
                    innerRadius={55}
                    outerRadius={85}
                    paddingAngle={3}
                    dataKey="value"
                  />
                  <Tooltip content={<ChartTooltip />} />
                  <Legend formatter={(value) => <span className="text-xs">{value}</span>} />
                </PieChart>
              </ResponsiveContainer>
            )}
          </ChartCard>
        </div>
      </div>
    </>
  );
}