import { useEffect } from "react";
import {
  BarChart3,
  Bug,
  FileText,
  FolderKanban,
  Globe2,
  LogOut,
  Moon,
  Network,
  PlayCircle,
  ScanLine,
  Sun,
  TestTube2,
  Users,
  Video,
  Zap,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { Link, NavLink, Outlet, useLocation } from "react-router-dom";
import { Button } from "./Button";
import { useAuth } from "../state/auth";
import { useTheme } from "../state/theme";
import type { Role } from "../lib/types";

interface NavItem {
  to: string;
  label: string;
  icon: LucideIcon;
  roles: Role[];
}

const nav: NavItem[] = [
  { to: "/",            label: "Dashboard",  icon: BarChart3,    roles: ["OWNER", "TESTER", "VIEWER"] },
  { to: "/projects",    label: "Projects",   icon: FolderKanban, roles: ["OWNER", "TESTER", "VIEWER"] },
  { to: "/analysis",    label: "Analysis",   icon: Zap,          roles: ["OWNER", "TESTER"] },
  { to: "/recordings",  label: "Recordings", icon: Video,        roles: ["OWNER", "TESTER", "VIEWER"] },
  { to: "/pages",       label: "Pages",      icon: Globe2,       roles: ["OWNER", "TESTER", "VIEWER"] },
  { to: "/tests",       label: "Tests",      icon: TestTube2,    roles: ["OWNER", "TESTER", "VIEWER"] },
  { to: "/executions",  label: "Executions", icon: PlayCircle,   roles: ["OWNER", "TESTER", "VIEWER"] },
  { to: "/bugs",        label: "Bugs",       icon: Bug,          roles: ["OWNER", "TESTER", "VIEWER"] },
  { to: "/api-endpoints", label: "APIs",     icon: Network,      roles: ["OWNER", "TESTER", "VIEWER"] },
  { to: "/reports",     label: "Reports",    icon: FileText,     roles: ["OWNER", "TESTER", "VIEWER"] },
  { to: "/elements",    label: "Elements",   icon: ScanLine,     roles: ["OWNER", "TESTER", "VIEWER"] },
  { to: "/users",       label: "Team",       icon: Users,        roles: ["OWNER"] },
];

export function AppLayout() {
  const { user, logout } = useAuth();
  const { theme, toggleTheme } = useTheme();
  const ThemeIcon = theme === "dark" ? Sun : Moon;
  const location = useLocation();

  useEffect(() => {
    if (location.search) {
      sessionStorage.setItem(`qaas.search${location.pathname}`, location.search);
    } else {
      sessionStorage.removeItem(`qaas.search${location.pathname}`);
    }
  }, [location.pathname, location.search]);

  const visibleNav = user ? nav.filter((item) => item.roles.includes(user.role)) : [];

  return (
    <div className="min-h-screen bg-panel">

      {/* ── Desktop sidebar ───────────────────────────────────────────────────── */}
      <aside className="fixed inset-y-0 left-0 z-20 hidden w-64 border-r border-line bg-white md:block">
        <div className="flex h-16 items-center border-b border-line px-5">
          <div>
            <div className="text-lg font-semibold text-ink">QAAS AI</div>
            <div className="text-xs uppercase tracking-wide text-slate-500">Autonomous QA Platform</div>
          </div>
        </div>
        <nav className="grid gap-1 p-3">
          {visibleNav.map((item) => {
            const Icon = item.icon;
            const savedSearch = sessionStorage.getItem(`qaas.search${item.to}`) ?? "";
            return (
              <NavLink
                key={item.to}
                to={`${item.to}${savedSearch}`}
                end={item.to === "/"}
                className={({ isActive }) =>
                  `flex h-10 items-center gap-3 rounded-md px-3 text-sm font-medium ${
                    isActive ? "bg-teal-50 text-brand" : "text-slate-600 hover:bg-slate-100 hover:text-ink"
                  }`
                }
              >
                <Icon className="h-4 w-4 shrink-0" />
                {item.label}
              </NavLink>
            );
          })}
        </nav>
      </aside>

      {/* ── Content ───────────────────────────────────────────────────────────── */}
      <div className="md:pl-64">
        <header className="sticky top-0 z-10 flex h-16 items-center justify-between border-b border-line bg-white px-4 md:px-6">
          <div className="min-w-0 md:hidden">
            <div className="font-semibold text-ink">QAAS AI</div>
          </div>
          <div className="ml-auto flex min-w-0 items-center gap-3">
            <Link
              to="/profile"
              className="hidden min-w-0 text-right text-sm sm:block hover:opacity-75 transition-opacity"
            >
              <div className="truncate font-medium text-ink">{user?.displayName || user?.email}</div>
              <div className="text-xs text-slate-500">{user?.role}</div>
            </Link>
            <Button variant="ghost" onClick={toggleTheme} title={theme === "dark" ? "Use light mode" : "Use dark mode"}>
              <ThemeIcon className="h-4 w-4" />
            </Button>
            <Button variant="ghost" onClick={() => void logout()} title="Sign out">
              <LogOut className="h-4 w-4" />
            </Button>
          </div>
        </header>

        <main className="min-h-[calc(100vh-4rem)] pb-20 md:pb-0">
          <Outlet />
        </main>
      </div>

      {/* ── Mobile bottom tab bar ─────────────────────────────────────────────── */}
      <nav className="fixed inset-x-0 bottom-0 z-30 border-t border-line bg-white shadow-soft md:hidden">
        <div className="flex overflow-x-auto scrollbar-none">
          {visibleNav.map((item) => {
            const Icon = item.icon;
            const savedSearch = sessionStorage.getItem(`qaas.search${item.to}`) ?? "";
            return (
              <NavLink
                key={item.to}
                to={`${item.to}${savedSearch}`}
                end={item.to === "/"}
                className={({ isActive }) =>
                  `flex h-16 min-w-[64px] flex-col items-center justify-center gap-0.5 px-1 text-[10px] font-medium transition-colors ${
                    isActive ? "text-brand" : "text-slate-500 hover:text-ink"
                  }`
                }
              >
                {({ isActive }) => (
                  <>
                    <div className={`rounded-lg p-1 transition-colors ${isActive ? "bg-teal-50" : ""}`}>
                      <Icon className="h-5 w-5" />
                    </div>
                    <span className="max-w-[60px] truncate leading-tight">{item.label}</span>
                  </>
                )}
              </NavLink>
            );
          })}
        </div>
      </nav>
    </div>
  );
}