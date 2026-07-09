import { createContext, useContext, useMemo, useState } from "react";
import { authApi, setAccessToken } from "../lib/api";
import type { AuthResponse, User } from "../lib/types";

interface AuthState {
  user: User | null;
  accessToken: string | null;
  refreshToken: string | null;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  updateUser: (user: User) => void;
}

const STORAGE_KEY = "qaas.auth";
const AuthContext = createContext<AuthState | undefined>(undefined);

function loadSession(): AuthResponse | null {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) return null;
  const s = JSON.parse(raw) as AuthResponse;
  // Set the token synchronously so the first render already has auth headers
  setAccessToken(s.accessToken);
  return s;
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [session, setSession] = useState<AuthResponse | null>(loadSession);

  const value = useMemo<AuthState>(
    () => ({
      user: session?.user ?? null,
      accessToken: session?.accessToken ?? null,
      refreshToken: session?.refreshToken ?? null,
      login: async (email, password) => {
        const result = await authApi.login({ email, password });
        setAccessToken(result.accessToken);
        localStorage.setItem(STORAGE_KEY, JSON.stringify(result));
        setSession(result);
      },
      register: async (email, password) => {
        const result = await authApi.register({ email, password });
        setAccessToken(result.accessToken);
        localStorage.setItem(STORAGE_KEY, JSON.stringify(result));
        setSession(result);
      },
      logout: async () => {
        if (session?.refreshToken) {
          await authApi.logout(session.refreshToken).catch(() => undefined);
        }
        setAccessToken(null);
        localStorage.removeItem(STORAGE_KEY);
        setSession(null);
      },
      updateUser: (user: User) => {
        setSession((prev) => {
          if (!prev) return prev;
          const next = { ...prev, user };
          localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
          return next;
        });
      },
    }),
    [session],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const auth = useContext(AuthContext);
  if (!auth) {
    throw new Error("useAuth must be used inside AuthProvider");
  }
  return auth;
}
