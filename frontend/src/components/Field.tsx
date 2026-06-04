import type { InputHTMLAttributes, SelectHTMLAttributes, TextareaHTMLAttributes } from "react";

export function Field({ label, children, className = "" }: { label: string; children: React.ReactNode; className?: string }) {
  return (
    <label className={`grid gap-1.5 text-sm font-medium text-slate-700 ${className}`}>
      <span>{label}</span>
      {children}
    </label>
  );
}

export function TextInput({ className = "", ...props }: InputHTMLAttributes<HTMLInputElement>) {
  return (
    <input
      className={`h-10 w-full min-w-0 rounded-md border border-line bg-white px-3 text-sm text-ink outline-none transition focus:border-brand focus:ring-2 focus:ring-teal-100 ${className}`}
      {...props}
    />
  );
}

export function TextArea({ className = "", ...props }: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return (
    <textarea
      className={`min-h-24 w-full min-w-0 rounded-md border border-line bg-white px-3 py-2 text-sm text-ink outline-none transition focus:border-brand focus:ring-2 focus:ring-teal-100 ${className}`}
      {...props}
    />
  );
}

export function SelectInput({ className = "", ...props }: SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select
      className={`h-10 w-full min-w-0 rounded-md border border-line bg-white px-3 text-sm text-ink outline-none transition focus:border-brand focus:ring-2 focus:ring-teal-100 ${className}`}
      {...props}
    />
  );
}
