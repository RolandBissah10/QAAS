import axios from "axios";
import type {
  Analysis,
  ApiEndpoint,
  AuthResponse,
  Bug,
  DashboardSummary,
  DiscoveredPage,
  GeneratedTest,
  PagedResponse,
  Project,
  ProjectSettings,
  Report,
  ReportFormat,
  Role,
  Screenshot,
  TestExecution,
  TrendPoint,
  UIElement,
  User,
} from "./types";

// Empty string = relative URL, routed through the Vite proxy to http://localhost:8090 in dev.
// Set VITE_API_BASE_URL to the full backend URL for production builds.
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";
const AUTH_STORAGE_KEY = "qaas.auth";

export const api = axios.create({ baseURL: API_BASE_URL });

export function setAccessToken(token: string | null) {
  if (token) {
    api.defaults.headers.common.Authorization = `Bearer ${token}`;
  } else {
    delete api.defaults.headers.common.Authorization;
  }
}

// ── Auto-refresh interceptor ─────────────────────────────────────────────────
// When the backend returns 401, swap the expired access token for a fresh one
// using the stored refresh token, then replay the original request.
let _refreshing: Promise<string> | null = null;

api.interceptors.response.use(
  (res) => res,
  async (error: unknown) => {
    if (!axios.isAxiosError(error)) return Promise.reject(error);
    const original = error.config as (typeof error.config & { _retry?: boolean }) | undefined;
    const isAuthEndpoint = original?.url?.includes("/api/auth/");
    if (error.response?.status === 401 && original && !original._retry && !isAuthEndpoint) {
      original._retry = true;
      try {
        // Deduplicate concurrent refresh calls
        if (!_refreshing) {
          _refreshing = (async () => {
            const raw = localStorage.getItem(AUTH_STORAGE_KEY);
            if (!raw) throw new Error("No session");
            const session = JSON.parse(raw) as AuthResponse;
            const { data } = await axios.post<AuthResponse>(
              `${API_BASE_URL}/api/auth/refresh`,
              { refreshToken: session.refreshToken },
            );
            setAccessToken(data.accessToken);
            localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify({ ...session, accessToken: data.accessToken, refreshToken: data.refreshToken }));
            return data.accessToken;
          })().finally(() => { _refreshing = null; });
        }
        const newToken = await _refreshing;
        if (original.headers) original.headers.Authorization = `Bearer ${newToken}`;
        return api(original);
      } catch {
        setAccessToken(null);
        localStorage.removeItem(AUTH_STORAGE_KEY);
        window.location.href = "/login";
      }
    }
    return Promise.reject(error);
  },
);

// ── Report download ───────────────────────────────────────────────────────────
// Fetches via the Axios instance (JWT header included) and triggers a blob
// download — avoids the cross-origin <a download> restriction.
export async function downloadReport(reportId: string, format: string): Promise<void> {
  const response = await api.get(`/api/reports/${reportId}/download`, { responseType: "blob" });
  const url = URL.createObjectURL(response.data as Blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `report.${format.toLowerCase()}`;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

export const authApi = {
  register: (payload: { email: string; password: string }) =>
    api.post<AuthResponse>("/api/auth/register", payload).then((r) => r.data),
  login: (payload: { email: string; password: string }) =>
    api.post<AuthResponse>("/api/auth/login", payload).then((r) => r.data),
  logout: (refreshToken: string) =>
    api.post<void>("/api/auth/logout", { refreshToken }),
};

export const dashboardApi = {
  summary: () =>
    api.get<DashboardSummary>("/api/dashboard/summary").then((r) => r.data),
  trends: () =>
    api.get<TrendPoint[]>("/api/dashboard/trends").then((r) => r.data),
};

export const usersApi = {
  list: (page = 0, size = 20) =>
    api.get<PagedResponse<User>>("/api/users", { params: { page, size } }).then((r) => r.data),
  update: (id: string, payload: { role: Role; displayName?: string }) =>
    api.put<User>(`/api/users/${id}`, payload).then((r) => r.data),
  remove: (id: string) => api.delete<void>(`/api/users/${id}`),
};

export const projectApi = {
  list: () => api.get<Project[]>("/api/projects").then((r) => r.data),
  create: (payload: { name: string; description?: string; baseUrl?: string }) =>
    api.post<Project>("/api/projects", payload).then((r) => r.data),
  update: (id: string, payload: { name: string; description?: string; baseUrl?: string }) =>
    api.put<Project>(`/api/projects/${id}`, payload).then((r) => r.data),
  remove: (id: string) => api.delete<void>(`/api/projects/${id}`),
};

export const analysisApi = {
  start: (projectId: string, url: string) =>
    api
      .post<Analysis>("/api/analysis/start", { url }, { params: { projectId } })
      .then((r) => r.data),
  get: (id: string) =>
    api.get<Analysis>(`/api/analysis/${id}`).then((r) => r.data),
  status: (id: string) =>
    api.get<string>(`/api/analysis/status/${id}`).then((r) => r.data),
  byProject: (projectId: string, page = 0, size = 20) =>
    api
      .get<PagedResponse<Analysis>>(`/api/analysis/project/${projectId}`, { params: { page, size } })
      .then((r) => r.data),
  stop: (id: string) =>
    api.post<void>(`/api/analysis/${id}/stop`).then((r) => r.data),
  progressUrl: (id: string, token: string) =>
    `${API_BASE_URL}/api/analysis/${id}/progress?token=${encodeURIComponent(token)}`,
};

export const pageApi = {
  byAnalysis: (analysisId: string, page = 0, size = 20) =>
    api
      .get<PagedResponse<DiscoveredPage>>(`/api/pages/analysis/${analysisId}`, { params: { page, size } })
      .then((r) => r.data),
};

export const testApi = {
  byAnalysis: (analysisId: string, page = 0, size = 20) =>
    api
      .get<PagedResponse<GeneratedTest>>(`/api/tests/analysis/${analysisId}`, { params: { page, size } })
      .then((r) => r.data),
};

export const executionApi = {
  byAnalysis: (analysisId: string, page = 0, size = 20) =>
    api
      .get<PagedResponse<TestExecution>>(`/api/executions/analysis/${analysisId}`, { params: { page, size } })
      .then((r) => r.data),
};

export const bugApi = {
  byAnalysis: (analysisId: string, page = 0, size = 20) =>
    api
      .get<PagedResponse<Bug>>(`/api/bugs/analysis/${analysisId}`, { params: { page, size } })
      .then((r) => r.data),
  updateStatus: (id: string, status: Bug["status"]) =>
    api.patch<Bug>(`/api/bugs/${id}/status`, { status }).then((r) => r.data),
};

export const reportApi = {
  byAnalysis: (analysisId: string) =>
    api
      .get<Report[]>(`/api/reports/analysis/${analysisId}`)
      .then((r) => r.data),
  generate: (analysisId: string, format: ReportFormat) =>
    api
      .post<Report>(`/api/reports/analysis/${analysisId}`, { format })
      .then((r) => r.data),
};

export const profileApi = {
  get: () => api.get<User>("/api/users/me").then((r) => r.data),
  update: (displayName: string) =>
    api.put<User>("/api/users/me", { displayName }).then((r) => r.data),
};

export const projectSettingsApi = {
  get: (projectId: string) =>
    api.get<ProjectSettings>(`/api/projects/${projectId}/settings`).then((r) => r.data),
  save: (projectId: string, payload: Partial<ProjectSettings> & { authPassword?: string }) =>
    api.put<ProjectSettings>(`/api/projects/${projectId}/settings`, payload).then((r) => r.data),
};

export const apiEndpointApi = {
  byAnalysis: (analysisId: string) =>
    api.get<ApiEndpoint[]>(`/api/api-endpoints/analysis/${analysisId}`).then((r) => r.data),
};

export const uiElementApi = {
  byPage: (pageId: string) =>
    api.get<UIElement[]>(`/api/ui-elements/page/${pageId}`).then((r) => r.data),
};

export const screenshotApi = {
  byAnalysis: (analysisId: string) =>
    api.get<Screenshot[]>(`/api/screenshots/analysis/${analysisId}`).then((r) => r.data),
  imageUrl: (id: string) => `/api/screenshots/${id}/image`,
};

export const playwrightApi = {
  runTests: (analysisId: string) =>
    api.post<GeneratedTest[]>(`/api/playwright/run-tests/${analysisId}`).then((r) => r.data),
  runAll: () =>
    api.post<GeneratedTest[]>(`/api/playwright/run-tests`).then((r) => r.data),
};