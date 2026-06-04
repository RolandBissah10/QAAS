import axios from "axios";
import type {
  ApiTest,
  AuthResponse,
  DashboardSummary,
  EnvironmentConfig,
  Project,
  Result,
  Role,
  TestCollection,
  TrendPoint,
  User,
} from "./types";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8081";

export const api = axios.create({
  baseURL: API_BASE_URL,
});

export function setAccessToken(token: string | null) {
  if (token) {
    api.defaults.headers.common.Authorization = `Bearer ${token}`;
  } else {
    delete api.defaults.headers.common.Authorization;
  }
}

export const authApi = {
  register: (payload: { email: string; password: string }) =>
    api.post<AuthResponse>("/api/auth/register", payload).then((res) => res.data),
  login: (payload: { email: string; password: string }) =>
    api.post<AuthResponse>("/api/auth/login", payload).then((res) => res.data),
  logout: (refreshToken: string) => api.post<void>("/api/auth/logout", { refreshToken }),
};

export const userApi = {
  me: () => api.get<User>("/api/users/me").then((res) => res.data),
  list: () => api.get<User[]>("/api/users").then((res) => res.data),
  update: (id: string, payload: { role: Role; displayName?: string }) =>
    api.put<User>(`/api/users/${id}`, payload).then((res) => res.data),
  remove: (id: string) => api.delete<void>(`/api/users/${id}`),
};

export const dashboardApi = {
  summary: () => api.get<DashboardSummary>("/api/dashboard/summary").then((res) => res.data),
  trends: () => api.get<TrendPoint[]>("/api/dashboard/trends").then((res) => res.data),
};

export const projectApi = {
  list: () => api.get<Project[]>("/api/projects").then((res) => res.data),
  create: (payload: { name: string; description?: string }) =>
    api.post<Project>("/api/projects", payload).then((res) => res.data),
  update: (id: string, payload: { name: string; description?: string }) =>
    api.put<Project>(`/api/projects/${id}`, payload).then((res) => res.data),
  remove: (id: string) => api.delete<void>(`/api/projects/${id}`),
};

export const environmentApi = {
  list: () => api.get<EnvironmentConfig[]>("/api/environments").then((res) => res.data),
  create: (payload: { projectId: string; name: string; baseUrl: string }) =>
    api.post<EnvironmentConfig>("/api/environments", payload).then((res) => res.data),
  update: (id: string, payload: { projectId: string; name: string; baseUrl: string }) =>
    api.put<EnvironmentConfig>(`/api/environments/${id}`, payload).then((res) => res.data),
  remove: (id: string) => api.delete<void>(`/api/environments/${id}`),
};

export const testApi = {
  list: () => api.get<ApiTest[]>("/api/tests").then((res) => res.data),
  create: (payload: Omit<ApiTest, "id">) => api.post<ApiTest>("/api/tests", payload).then((res) => res.data),
  update: (id: string, payload: Omit<ApiTest, "id">) =>
    api.put<ApiTest>(`/api/tests/${id}`, payload).then((res) => res.data),
  remove: (id: string) => api.delete<void>(`/api/tests/${id}`),
};

export const collectionApi = {
  list: () => api.get<TestCollection[]>("/api/collections").then((res) => res.data),
  create: (payload: { projectId: string; name: string; description?: string; testIds: string[] }) =>
    api.post<TestCollection>("/api/collections", payload).then((res) => res.data),
  update: (id: string, payload: { projectId: string; name: string; description?: string; testIds: string[] }) =>
    api.put<TestCollection>(`/api/collections/${id}`, payload).then((res) => res.data),
  remove: (id: string) => api.delete<void>(`/api/collections/${id}`),
};

export const executionApi = {
  runTest: (id: string) => api.post<Result>(`/api/executions/test/${id}`).then((res) => res.data),
  runCollection: (id: string) =>
    api.post<{ results: Result[]; passed: number; failed: number }>(`/api/executions/collection/${id}`).then((res) => res.data),
};

export const resultApi = {
  list: () => api.get<Result[]>("/api/results").then((res) => res.data),
};
