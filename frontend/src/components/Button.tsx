import { Loader2 } from "lucide-react";
import type { ButtonHTMLAttributes } from "react";

type Variant = "primary" | "secondary" | "danger" | "ghost";

const variants: Record<Variant, string> = {
  primary: "bg-brand text-white hover:bg-teal-800",
  secondary: "border border-line bg-white text-ink hover:bg-panel",
  danger: "bg-red-600 text-white hover:bg-red-700",
  ghost: "text-slate-700 hover:bg-slate-100",
};

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  loading?: boolean;
}

export function Button({ children, className = "", variant = "primary", loading, disabled, ...props }: ButtonProps) {
  return (
    <button
      type="button"
      className={`inline-flex h-10 items-center justify-center gap-2 rounded-md px-3 text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-60 ${variants[variant]} ${className}`}
      disabled={disabled || loading}
      {...props}
    >
      {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
      {children}
    </button>
  );
}
