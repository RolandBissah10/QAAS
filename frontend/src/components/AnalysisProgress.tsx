import { useEffect, useState } from "react";
import { analysisApi } from "../lib/api";

const STEPS = ["CRAWLING", "DISCOVERING", "GENERATING", "EXECUTING", "REPORTING", "COMPLETED"] as const;
type Step = (typeof STEPS)[number] | "FAILED";

interface ProgressState {
  step: Step;
  message: string;
  progress: number;
}

export function AnalysisProgress({
  analysisId,
  token,
  onDone,
}: {
  analysisId: string;
  token: string;
  onDone: () => void;
}) {
  const [state, setState] = useState<ProgressState | null>(null);

  useEffect(() => {
    const url = analysisApi.progressUrl(analysisId, token);
    const es = new EventSource(url);

    es.addEventListener("progress", (e: MessageEvent) => {
      const data = JSON.parse(e.data as string) as ProgressState;
      setState(data);
      if (data.step === "COMPLETED" || data.step === "FAILED") {
        es.close();
        setTimeout(onDone, 800);
      }
    });

    es.onerror = () => es.close();
    return () => es.close();
  }, [analysisId, token, onDone]);

  if (!state) {
    return (
      <div className="mt-2 flex items-center gap-2 text-xs text-slate-400">
        <span className="inline-block h-1.5 w-1.5 animate-pulse rounded-full bg-slate-300" />
        Connecting…
      </div>
    );
  }

  const isFailed = state.step === "FAILED";

  return (
    <div className="mt-2 grid gap-2">
      <div className="flex flex-wrap gap-1.5">
        {STEPS.map((s, i) => {
          const stepIdx = STEPS.indexOf(state.step as (typeof STEPS)[number]);
          const isActive = s === state.step;
          const isDone = stepIdx > i;
          return (
            <span
              key={s}
              className={`rounded-full px-2 py-0.5 text-xs font-medium transition-colors ${
                isActive
                  ? "bg-teal-100 text-teal-700"
                  : isDone
                  ? "bg-slate-100 text-slate-400 line-through"
                  : "text-slate-300"
              }`}
            >
              {s.charAt(0) + s.slice(1).toLowerCase()}
            </span>
          );
        })}
      </div>
      <div className="h-1.5 overflow-hidden rounded-full bg-slate-100">
        <div
          className={`h-full rounded-full transition-all duration-500 ${
            isFailed ? "bg-red-400" : "bg-teal-500"
          }`}
          style={{ width: `${isFailed ? 100 : state.progress}%` }}
        />
      </div>
      <p className={`text-xs ${isFailed ? "text-red-600" : "text-slate-500"}`}>
        {state.message}
      </p>
    </div>
  );
}