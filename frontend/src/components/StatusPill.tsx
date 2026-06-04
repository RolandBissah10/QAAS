import type { ExecutionStatus } from "../lib/types";

export function StatusPill({ status }: { status: ExecutionStatus }) {
  const style =
    status === "PASSED"
      ? "bg-emerald-50 text-emerald-700 ring-emerald-200"
      : status === "FAILED"
        ? "bg-amber-50 text-amber-700 ring-amber-200"
        : "bg-red-50 text-red-700 ring-red-200";

  return <span className={`rounded-full px-2 py-1 text-xs font-semibold ring-1 ${style}`}>{status}</span>;
}
