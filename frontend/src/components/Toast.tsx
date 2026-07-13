import { CheckCircle2, Info, X, XCircle } from "lucide-react";
import { createContext, useCallback, useContext, useMemo, useRef, useState } from "react";

type ToastVariant = "success" | "error" | "info";

interface ToastItem {
  id: number;
  message: string;
  variant: ToastVariant;
}

interface ToastCtx {
  success: (message: string) => void;
  error: (message: string) => void;
  info: (message: string) => void;
}

const ToastContext = createContext<ToastCtx | undefined>(undefined);

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const counter = useRef(0);

  const dismiss = useCallback((id: number) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const push = useCallback(
    (message: string, variant: ToastVariant) => {
      const id = ++counter.current;
      setToasts((prev) => [...prev, { id, message, variant }]);
      setTimeout(() => dismiss(id), 4500);
    },
    [dismiss],
  );

  const ctx = useMemo<ToastCtx>(
    () => ({
      success: (m) => push(m, "success"),
      error: (m) => push(m, "error"),
      info: (m) => push(m, "info"),
    }),
    [push],
  );

  return (
    <ToastContext.Provider value={ctx}>
      {children}
      {toasts.length > 0 && (
        <div className="fixed bottom-4 right-4 z-50 flex w-80 flex-col gap-2">
          {toasts.map((t) => (
            <ToastBanner key={t.id} item={t} dismiss={dismiss} />
          ))}
        </div>
      )}
    </ToastContext.Provider>
  );
}

export function useToast(): ToastCtx {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error("useToast must be used inside ToastProvider");
  return ctx;
}

const CONFIG: Record<ToastVariant, { Icon: typeof CheckCircle2; cls: string }> = {
  success: {
    Icon: CheckCircle2,
    cls: "border-emerald-200 bg-emerald-50 text-emerald-800",
  },
  error: {
    Icon: XCircle,
    cls: "border-red-200 bg-red-50 text-red-800",
  },
  info: {
    Icon: Info,
    cls: "border-line bg-white text-ink",
  },
};

function ToastBanner({
  item,
  dismiss,
}: {
  item: ToastItem;
  dismiss: (id: number) => void;
}) {
  const { Icon, cls } = CONFIG[item.variant];
  return (
    <div
      className={`flex items-start gap-3 rounded-md border px-4 py-3 text-sm shadow-lg ${cls}`}
    >
      <Icon className="mt-0.5 h-4 w-4 shrink-0" />
      <span className="flex-1">{item.message}</span>
      <button
        onClick={() => dismiss(item.id)}
        className="ml-1 shrink-0 opacity-60 transition-opacity hover:opacity-100"
        aria-label="Dismiss"
      >
        <X className="h-4 w-4" />
      </button>
    </div>
  );
}