import { Navigate, Route, Routes } from "react-router-dom";
import { AppLayout } from "./components/AppLayout";
import { useAuth } from "./state/auth";
import { AuthPage } from "./pages/AuthPage";
import { DashboardPage } from "./pages/DashboardPage";
import { ProjectsPage } from "./pages/ProjectsPage";
import { AnalysisPage } from "./pages/AnalysisPage";
import { PagesPage } from "./pages/PagesPage";
import { TestsPage } from "./pages/TestsPage";
import { ExecutionsPage } from "./pages/ExecutionsPage";
import { BugsPage } from "./pages/BugsPage";
import { ReportsPage } from "./pages/ReportsPage";
import { ApiEndpointsPage } from "./pages/ApiEndpointsPage";
import { UIElementsPage } from "./pages/UIElementsPage";
import { UsersPage } from "./pages/UsersPage";
import { AnalysisDetailPage } from "./pages/AnalysisDetailPage";
import { ProfilePage } from "./pages/ProfilePage";

function ProtectedRoute() {
  const { accessToken } = useAuth();
  return accessToken ? <AppLayout /> : <Navigate to="/login" replace />;
}

export function App() {
  const { accessToken } = useAuth();

  return (
    <Routes>
      <Route path="/login" element={accessToken ? <Navigate to="/" replace /> : <AuthPage />} />
      <Route element={<ProtectedRoute />}>
        <Route path="/" element={<DashboardPage />} />
        <Route path="/projects" element={<ProjectsPage />} />
        <Route path="/analysis" element={<AnalysisPage />} />
        <Route path="/analysis/:id" element={<AnalysisDetailPage />} />
        <Route path="/pages" element={<PagesPage />} />
        <Route path="/tests" element={<TestsPage />} />
        <Route path="/executions" element={<ExecutionsPage />} />
        <Route path="/bugs" element={<BugsPage />} />
        <Route path="/reports" element={<ReportsPage />} />
        <Route path="/api-endpoints" element={<ApiEndpointsPage />} />
        <Route path="/elements" element={<UIElementsPage />} />
        <Route path="/users" element={<UsersPage />} />
        <Route path="/profile" element={<ProfilePage />} />
      </Route>
      <Route path="*" element={<Navigate to={accessToken ? "/" : "/login"} replace />} />
    </Routes>
  );
}
