import { Navigate, Route, Routes } from "react-router-dom";
import { AppLayout } from "./components/AppLayout";
import { useAuth } from "./state/auth";
import { AuthPage } from "./pages/AuthPage";
import { DashboardPage } from "./pages/DashboardPage";
import { ProjectsPage } from "./pages/ProjectsPage";
import { EnvironmentsPage } from "./pages/EnvironmentsPage";
import { TestsPage } from "./pages/TestsPage";
import { CollectionsPage } from "./pages/CollectionsPage";
import { ExecutionsPage } from "./pages/ExecutionsPage";
import { ResultsPage } from "./pages/ResultsPage";
import { UsersPage } from "./pages/UsersPage";
import type { Role } from "./lib/types";

function ProtectedRoute() {
  const { accessToken } = useAuth();
  return accessToken ? <AppLayout /> : <Navigate to="/login" replace />;
}


function RoleRoute({ allowedRoles, element }: { allowedRoles: Role[]; element: JSX.Element }) {
  const { accessToken, user } = useAuth();
  if (!accessToken) {
    return <Navigate to="/login" replace />;
  }
  return user && allowedRoles.includes(user.role) ? element : <Navigate to="/" replace />;
}

export function App() {
  const { accessToken } = useAuth();

  return (
    <Routes>
      <Route path="/login" element={accessToken ? <Navigate to="/" replace /> : <AuthPage />} />
      <Route element={<ProtectedRoute />}>
        <Route path="/" element={<RoleRoute allowedRoles={["OWNER", "TESTER", "VIEWER"]} element={<DashboardPage />} />} />
        <Route path="/projects" element={<RoleRoute allowedRoles={["OWNER"]} element={<ProjectsPage />} />} />
        <Route path="/environments" element={<RoleRoute allowedRoles={["OWNER"]} element={<EnvironmentsPage />} />} />
        <Route path="/tests" element={<RoleRoute allowedRoles={["OWNER", "TESTER"]} element={<TestsPage />} />} />
        <Route path="/collections" element={<RoleRoute allowedRoles={["OWNER", "TESTER"]} element={<CollectionsPage />} />} />
        <Route path="/executions" element={<RoleRoute allowedRoles={["OWNER", "TESTER"]} element={<ExecutionsPage />} />} />
        <Route path="/results" element={<RoleRoute allowedRoles={["OWNER", "TESTER", "VIEWER"]} element={<ResultsPage />} />} />
        <Route path="/users" element={<RoleRoute allowedRoles={["OWNER"]} element={<UsersPage />} />} />
      </Route>
      <Route path="*" element={<Navigate to={accessToken ? "/" : "/login"} replace />} />
    </Routes>
  );
}
