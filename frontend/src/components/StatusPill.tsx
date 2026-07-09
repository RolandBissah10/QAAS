const colorMap: Record<string, string> = {
  PASSED: "bg-emerald-50 text-emerald-700 ring-emerald-200",
  COMPLETED: "bg-emerald-50 text-emerald-700 ring-emerald-200",
  FIXED: "bg-emerald-50 text-emerald-700 ring-emerald-200",
  CONFIRMED: "bg-emerald-50 text-emerald-700 ring-emerald-200",
  FAILED: "bg-amber-50 text-amber-700 ring-amber-200",
  OPEN: "bg-amber-50 text-amber-700 ring-amber-200",
  ERROR: "bg-red-50 text-red-700 ring-red-200",
  CRITICAL: "bg-red-50 text-red-700 ring-red-200",
  HIGH: "bg-orange-50 text-orange-700 ring-orange-200",
  RUNNING: "bg-blue-50 text-blue-700 ring-blue-200",
  MEDIUM: "bg-yellow-50 text-yellow-700 ring-yellow-200",
  LOW: "bg-slate-50 text-slate-600 ring-slate-200",
  WONT_FIX: "bg-slate-50 text-slate-500 ring-slate-200",
  PDF: "bg-violet-50 text-violet-700 ring-violet-200",
  HTML: "bg-sky-50 text-sky-700 ring-sky-200",
  JSON: "bg-teal-50 text-teal-700 ring-teal-200",
};

export function StatusPill({ status }: { status: string }) {
  const style = colorMap[status] ?? "bg-slate-50 text-slate-600 ring-slate-200";
  return (
    <span className={`rounded-full px-2 py-1 text-xs font-semibold ring-1 ${style}`}>
      {status.replace("_", " ")}
    </span>
  );
}
