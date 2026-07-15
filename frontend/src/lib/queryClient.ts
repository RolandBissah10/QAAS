import { QueryClient } from "@tanstack/react-query";
import type { Persister } from "@tanstack/react-query-persist-client";

export const CACHE_KEY = "qaas.query-cache";
export const CACHE_VERSION = "v2";

export const persister: Persister = {
  persistClient(client) {
    try {
      localStorage.setItem(CACHE_KEY, JSON.stringify(client));
    } catch (err) {
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

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
      gcTime: 1000 * 60 * 60 * 24,
      staleTime: 60_000,
    },
  },
});