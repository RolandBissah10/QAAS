export type Role = "OWNER" | "TESTER" | "VIEWER";

export interface PagedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
export type ExecutionStatus = "RUNNING" | "PASSED" | "FAILED" | "ERROR";
export type Severity = "CRITICAL" | "HIGH" | "MEDIUM" | "LOW";
export type BugStatus = "OPEN" | "CONFIRMED" | "FIXED" | "WONT_FIX";
export type ReportFormat = "PDF" | "HTML" | "JSON";
export type AnalysisStatus = "RUNNING" | "COMPLETED" | "FAILED";

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

export interface Analysis {
  id: string;
  projectId: string;
  url: string;
  status: AnalysisStatus;
  startedAt: string;
  completedAt?: string;
}

export interface DiscoveredPage {
  id: string;
  analysisId: string;
  url: string;
  title?: string;
  pageType?: string;
  discoveredAt: string;
}

export interface GeneratedTest {
  id: string;
  pageId: string;
  name: string;
  type: string;
  status: string;
  targetUrl?: string;
}

export interface TestExecution {
  id: string;
  testId: string;
  testName: string;
  status: ExecutionStatus;
  startedAt: string;
  completedAt?: string;
  errorMessage?: string;
}

export interface Bug {
  id: string;
  executionId: string;
  analysisId: string;
  title: string;
  description?: string;
  severity: Severity;
  status: BugStatus;
  detectedAt: string;
}

export interface Report {
  id: string;
  analysisId: string;
  format: ReportFormat;
  filePath?: string;
  generatedAt: string;
  qualityScore?: number;
  totalTests?: number;
  passedTests?: number;
  failedTests?: number;
  bugCount?: number;
  pagesDiscovered?: number;
}

export interface ProjectSettings {
  id?: string;
  maxPages: number;
  authUrl?: string;
  authUsername?: string;
  authConfigured: boolean;
  excludedPatterns?: string;
  updatedAt?: string;
}

export interface Screenshot {
  id: string;
  analysisId: string;
  path: string;
  capturedAt: string;
}

export interface UIElement {
  id: string;
  pageId: string;
  elementType: string;
  selector: string;
  label: string;
}

export interface ApiEndpoint {
  id: string;
  analysisId: string;
  url: string;
  method: string;
  observedStatus?: number;
  requiresAuth: boolean;
  discoveredAt: string;
}

export interface DashboardSummary {
  applicationsAnalyzed: number;
  pagesDiscovered: number;
  testsExecuted: number;
  passedTests: number;
  failedTests: number;
  passRate: number;
  bugCount: number;
  criticalBugs: number;
  highBugs: number;
  mediumBugs: number;
  lowBugs: number;
}

export interface TrendPoint {
  analysisId: string;
  analysisUrl: string;
  projectId: string;
  projectName: string;
  date: string;
  qualityScore: number | null;
  passedTests: number | null;
  failedTests: number | null;
  bugCount: number | null;
  pagesDiscovered: number | null;
}
