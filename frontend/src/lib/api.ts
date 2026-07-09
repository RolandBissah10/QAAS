import axios from "axios";
import type {
  Analysis,
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
  UIElement,
  User,
} from "./types";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8090";

export const api = axios.create({ baseURL: API_BASE_URL });

export function setAccessToken(token: string | null) {
  if (token) {
    api.defaults.headers.common.Authorization = `Bearer ${token}`;
  } else {
    delete api.defaults.headers.common.Authorization;
  }
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
  create: (payload: { name: string; description?: string }) =>
    api.post<Project>("/api/projects", payload).then((r) => r.data),
  update: (id: string, payload: { name: string; description?: string }) =>
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
  downloadUrl: (reportId: string) =>
    `${API_BASE_URL}/api/reports/${reportId}/download`,
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

export const uiElementApi = {
  byPage: (pageId: string) =>
    api.get<UIElement[]>(`/api/ui-elements/page/${pageId}`).then((r) => r.data),
};

export const screenshotApi = {
  byAnalysis: (analysisId: string) =>
    api.get<Screenshot[]>(`/api/screenshots/analysis/${analysisId}`).then((r) => r.data),
  imageUrl: (id: string) => `/api/screenshots/${id}/image`,
};
