import React from "react";
import ReactDOM from "react-dom/client";
import { QueryClient } from "@tanstack/react-query";
import { PersistQueryClientProvider } from "@tanstack/react-query-persist-client";
import { createSyncStoragePersister } from "@tanstack/query-sync-storage-persister";
import { BrowserRouter } from "react-router-dom";
import { App } from "./App";
import { AuthProvider } from "./state/auth";
import { ThemeProvider } from "./state/theme";
import "./index.css";

const CACHE_VERSION = "v1";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
      // Keep data in memory (and localStorage) for 24 h between uses
      gcTime: 1000 * 60 * 60 * 24,
      staleTime: 60_000,
    },
  },
});

const persister = createSyncStoragePersister({
  storage: window.localStorage,
  key: "qaas.query-cache",
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
          <AuthProvider>
            <App />
          </AuthProvider>
        </ThemeProvider>
      </BrowserRouter>
    </PersistQueryClientProvider>
  </React.StrictMode>,
);