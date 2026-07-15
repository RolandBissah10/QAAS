import React from "react";
import ReactDOM from "react-dom/client";
import { PersistQueryClientProvider } from "@tanstack/react-query-persist-client";
import { BrowserRouter } from "react-router-dom";
import { App } from "./App";
import { AuthProvider } from "./state/auth";
import { ThemeProvider } from "./state/theme";
import { ToastProvider } from "./components/Toast";
import { queryClient, persister, CACHE_VERSION } from "./lib/queryClient";
import "./index.css";

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