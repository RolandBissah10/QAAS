import React from "react";
import ReactDOM from "react-dom/client";
import { QueryClient } from "@tanstack/react-query";
import { PersistQueryClientProvider } from "@tanstack/react-query-persist-client";
import type { Persister } from "@tanstack/react-query-persist-client";
import { BrowserRouter } from "react-router-dom";
import { App } from "./App";
import { AuthProvider } from "./state/auth";
import { ThemeProvider } from "./state/theme";
import { ToastProvider } from "./components/Toast";
import "./index.css";

const CACHE_KEY     = "qaas.query-cache";
const CACHE_VERSION = "v2";

// Inline persister — no throttle, immediate writes, explicit error catch
const persister: Persister = {
  persistClient(client) {
    try {
      localStorage.setItem(CACHE_KEY, JSON.stringify(client));
    } catch (err) {
      // localStorage full or unavailable — non-fatal, data loads from network
      console.warn("[qaas] Failed to persist query cache:", err);
    }
  },
  restoreClient() {
    try {
      const raw = localStorage.getItem(CACHE_KEY);
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      return raw ? (JSON.parse(raw) as any) : undefined;
    } catch {
      return undefined;
    }
  },
  removeClient() {
    localStorage.removeItem(CACHE_KEY);
  },
};

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
      gcTime:    1000 * 60 * 60 * 24, // 24 h — kept in cache (and localStorage)
      staleTime: 60_000,               // 1 min — re-fetches in background when stale
    },
  },
});

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <PersistQueryClientProvider
      client={queryClient}
      persistOptions={{
        persister,
        maxAge: 1000 * 60 * 60 * 24,
        buster: CACHE_VERSION,
      }}
    >
      <BrowserRouter>
        <ThemeProvider>
          <ToastProvider>
            <AuthProvider>
              <App />
            </AuthProvider>
          </ToastProvider>
        </ThemeProvider>
      </BrowserRouter>
    </PersistQueryClientProvider>
  </React.StrictMode>,
);