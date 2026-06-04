export type Role = "OWNER" | "TESTER" | "VIEWER";
export type HttpMethod = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
export type ExecutionStatus = "PASSED" | "FAILED" | "ERROR";

export interface User {
  id: string;
  email: string;
  role: Role;
  displayName?: string;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  user: User;
}

export interface Project {
  id: string;
  name: string;
  description?: string;
  ownerId: string;
  createdAt: string;
}

export interface EnvironmentConfig {
  id: string;
  projectId: string;
  name: string;
  baseUrl: string;
}

export interface ApiTest {
  id: string;
  projectId: string;
  environmentId: string;
  name: string;
  method: HttpMethod;
  endpoint: string;
  headers: Record<string, unknown>;
  requestBody: Record<string, unknown>;
  expectedStatusCode: number;
  expectedResponse: Record<string, unknown>;
}

export interface TestCollection {
  id: string;
  projectId: string;
  name: string;
  description?: string;
  testIds: string[];
}

export interface Result {
  id: string;
  executionId: string;
  status: ExecutionStatus;
  responseTime: number;
  responseBody: Record<string, unknown>;
  passed: boolean;
  executedAt: string;
}

export interface DashboardSummary {
  totalTests: number;
  passedTests: number;
  failedTests: number;
  passRate: number;
  averageResponseTime: number;
}

export interface TrendPoint {
  executedAt: string;
  status: ExecutionStatus;
}
