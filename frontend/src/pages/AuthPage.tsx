import { FormEvent, useState } from "react";
import { Moon, ShieldCheck, Sun } from "lucide-react";
import { Button } from "../components/Button";
import { Field, TextInput } from "../components/Field";
import { errorMessage } from "../lib/errors";
import { useAuth } from "../state/auth";
import { useTheme } from "../state/theme";

export function AuthPage() {
  const { login, register } = useAuth();
  const { theme, toggleTheme } = useTheme();
  const ThemeIcon = theme === "dark" ? Sun : Moon;
  const [mode, setMode] = useState<"login" | "register">("login");
  const [email, setEmail] = useState("owner@qaas.dev");
  const [password, setPassword] = useState("password123");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    setError(null);
    try {
      if (mode === "login") {
        await login(email, password);
      } else {
        await register(email, password);
      }
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="grid min-h-screen place-items-center bg-panel px-4 py-10">
      <div className="w-full max-w-md rounded-md border border-line bg-white p-4 shadow-soft sm:p-6">
        <div className="mb-6 flex items-center gap-3">
          <div className="grid h-11 w-11 place-items-center rounded-md bg-teal-50 text-brand">
            <ShieldCheck className="h-6 w-6" />
          </div>
          <div className="min-w-0 flex-1">
            <h1 className="text-xl font-semibold text-ink">QAAS Console</h1>
            <p className="text-sm text-slate-500">Sign in to manage API quality runs.</p>
          </div>
          <Button variant="ghost" type="button" onClick={toggleTheme} title={theme === "dark" ? "Use light mode" : "Use dark mode"}>
            <ThemeIcon className="h-4 w-4" />
          </Button>
        </div>
        <div className="mb-5 grid grid-cols-2 rounded-md border border-line bg-panel p-1">
          <button
            className={`h-9 rounded text-sm font-medium ${mode === "login" ? "bg-white text-ink shadow-sm" : "text-slate-500"}`}
            onClick={() => setMode("login")}
          >
            Login
          </button>
          <button
            className={`h-9 rounded text-sm font-medium ${mode === "register" ? "bg-white text-ink shadow-sm" : "text-slate-500"}`}
            onClick={() => setMode("register")}
          >
            Register
          </button>
        </div>
        <form className="grid gap-4" onSubmit={(event) => void submit(event)}>
          <Field label="Email">
            <TextInput type="email" value={email} onChange={(event) => setEmail(event.target.value)} required />
          </Field>
          <Field label="Password">
            <TextInput
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              minLength={8}
              required
            />
          </Field>
          {mode === "register" ? (
            <div className="rounded-md border border-slate-200 bg-slate-50 p-3 text-sm text-slate-600">
              New accounts are created as Viewer by default. Owners can promote users from the Users page.
            </div>
          ) : null}
          {error ? <div className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{error}</div> : null}
          <Button loading={loading} type="submit" className="w-full">
            {mode === "login" ? "Login" : "Create account"}
          </Button>
        </form>
      </div>
    </div>
  );
}
