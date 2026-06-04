import { AlertCircle, Loader2 } from "lucide-react";

export function LoadingState() {
  return (
    <div className="flex min-h-48 items-center justify-center gap-2 text-sm text-slate-500">
      <Loader2 className="h-4 w-4 animate-spin" />
      Loading
    </div>
  );
}

export function ErrorState({ message }: { message: string }) {
  return (
    <div className="flex min-h-32 items-center justify-center gap-2 rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-700">
      <AlertCircle className="h-4 w-4" />
      {message}
    </div>
  );
}

export function EmptyState({ title }: { title: string }) {
  return (
    <div className="flex min-h-32 items-center justify-center rounded-md border border-dashed border-line bg-white p-4 text-sm text-slate-500">
      {title}
    </div>
  );
}
