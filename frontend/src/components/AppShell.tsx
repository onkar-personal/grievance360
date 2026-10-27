import { useEffect, useState, type ReactNode } from "react";
import { Link, NavLink, Navigate, Outlet, useLocation, useNavigate } from "react-router-dom";
import {
  BarChart3,
  Bell,
  Building2,
  ClipboardList,
  FilePlus2,
  Gauge,
  History,
  LayoutDashboard,
  LogOut,
  Menu,
  Settings2,
  User as UserIcon,
  Users,
  X,
} from "lucide-react";
import { Logo } from "@/components/common";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { api } from "@/lib/api";
import { homePath, useAuth } from "@/lib/auth";
import type { Role } from "@/lib/types";
import { cn } from "@/lib/utils";

interface NavItem {
  to: string;
  label: string;
  icon: ReactNode;
  end?: boolean;
}

const NAV: Record<Role, { section?: string; items: NavItem[] }[]> = {
  CITIZEN: [
    {
      items: [
        { to: "/app/citizen", label: "Overview", icon: <LayoutDashboard />, end: true },
        { to: "/app/citizen/new", label: "Submit grievance", icon: <FilePlus2 /> },
        { to: "/app/complaints", label: "My complaints", icon: <ClipboardList /> },
      ],
    },
  ],
  OFFICER: [
    {
      items: [
        { to: "/app/officer", label: "Workload", icon: <Gauge />, end: true },
        { to: "/app/complaints", label: "Work queue", icon: <ClipboardList /> },
      ],
    },
  ],
  ADMIN: [
    {
      items: [
        { to: "/app/admin", label: "Overview", icon: <LayoutDashboard />, end: true },
        { to: "/app/complaints", label: "Complaints", icon: <ClipboardList /> },
        { to: "/app/admin/analytics", label: "Analytics & SLA", icon: <BarChart3 /> },
      ],
    },
    {
      section: "Administration",
      items: [
        { to: "/app/admin/users", label: "Users & roles", icon: <Users /> },
        { to: "/app/admin/departments", label: "Departments", icon: <Building2 /> },
        { to: "/app/admin/settings", label: "Escalation rules", icon: <Settings2 /> },
        { to: "/app/admin/audit", label: "Audit log", icon: <History /> },
      ],
    },
  ],
};

const ROLE_LABEL: Record<Role, string> = { CITIZEN: "Citizen", OFFICER: "Officer", ADMIN: "Administrator" };

function useUnreadCount() {
  const [count, setCount] = useState(0);
  const location = useLocation();
  useEffect(() => {
    let active = true;
    api
      .get<{ count: number }>("/notifications/unread-count")
      .then((r) => active && setCount(r.data.count))
      .catch(() => undefined);
    return () => {
      active = false;
    };
  }, [location.pathname]);
  return count;
}

export function AppShell() {
  const { user, loading, logout } = useAuth();
  const [open, setOpen] = useState(false);
  const location = useLocation();
  const navigate = useNavigate();
  const unread = useUnreadCount();

  useEffect(() => setOpen(false), [location.pathname]);

  if (loading) {
    return <div className="flex min-h-screen items-center justify-center text-sm text-muted-foreground">Loading…</div>;
  }
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;

  const groups = NAV[user.role];

  const sidebar = (
    <nav aria-label="Main" className="flex h-full flex-col">
      <div className="flex h-14 items-center px-4">
        <Link to={homePath(user.role)}>
          <Logo light />
        </Link>
      </div>
      <div className="flex-1 space-y-5 overflow-y-auto px-3 py-3">
        {groups.map((g, i) => (
          <div key={i}>
            {g.section && <div className="mb-1.5 px-2 text-[11px] font-medium uppercase tracking-wider text-white/45">{g.section}</div>}
            <ul className="space-y-0.5">
              {g.items.map((item) => (
                <li key={item.to}>
                  <NavLink
                    to={item.to}
                    end={item.end}
                    className={({ isActive }) =>
                      cn(
                        "flex items-center gap-2.5 rounded-md px-2 py-1.5 text-sm text-white/75 transition-colors hover:bg-white/10 hover:text-white [&_svg]:h-4 [&_svg]:w-4",
                        isActive && "bg-white/12 bg-white/10 font-medium text-white",
                      )
                    }
                  >
                    {item.icon}
                    {item.label}
                  </NavLink>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
      <div className="border-t border-white/10 px-4 py-3 text-xs text-white/50">
        {user.departmentName ? user.departmentName : ROLE_LABEL[user.role]}
      </div>
    </nav>
  );

  return (
    <div className="min-h-screen lg:grid lg:grid-cols-[232px_1fr]">
      <a href="#main" className="sr-only focus:not-sr-only focus:fixed focus:left-2 focus:top-2 focus:z-50 focus:rounded focus:bg-surface focus:px-3 focus:py-2">
        Skip to content
      </a>
      <aside className="sticky top-0 hidden h-screen bg-navy lg:block">{sidebar}</aside>

      {open && (
        <div className="fixed inset-0 z-40 lg:hidden" role="dialog" aria-modal="true" aria-label="Navigation">
          <div className="absolute inset-0 bg-navy/40" onClick={() => setOpen(false)} />
          <aside className="absolute inset-y-0 left-0 w-64 bg-navy">
            <button className="absolute right-2 top-3 rounded p-1.5 text-white/70 hover:text-white" onClick={() => setOpen(false)} aria-label="Close menu">
              <X className="h-5 w-5" />
            </button>
            {sidebar}
          </aside>
        </div>
      )}

      <div className="flex min-w-0 flex-col">
        <header className="sticky top-0 z-30 flex h-14 items-center gap-2 border-b bg-surface/95 px-4 backdrop-blur supports-[backdrop-filter]:bg-surface/80 lg:px-8">
          <Button variant="ghost" size="icon" className="lg:hidden" onClick={() => setOpen(true)} aria-label="Open menu">
            <Menu />
          </Button>
          <div className="flex-1" />
          <Button variant="ghost" size="icon" asChild>
            <Link to="/app/notifications" aria-label={`Notifications${unread ? `, ${unread} unread` : ""}`} className="relative">
              <Bell />
              {unread > 0 && (
                <span className="absolute right-1 top-1 min-w-[16px] rounded-full bg-primary px-1 text-center text-[10px] font-semibold leading-4 text-white">
                  {unread > 99 ? "99+" : unread}
                </span>
              )}
            </Link>
          </Button>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <button className="flex items-center gap-2 rounded-md px-2 py-1 text-left hover:bg-muted" aria-label="Account menu">
                <span className="flex h-7 w-7 items-center justify-center rounded-full bg-accent text-xs font-semibold text-accent-foreground">
                  {user.fullName
                    .split(" ")
                    .map((p) => p[0])
                    .slice(0, 2)
                    .join("")
                    .toUpperCase()}
                </span>
                <span className="hidden text-sm sm:block">
                  <span className="block font-medium leading-tight">{user.fullName}</span>
                  <span className="block text-xs leading-tight text-muted-foreground">{ROLE_LABEL[user.role]}</span>
                </span>
              </button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuLabel>{user.email}</DropdownMenuLabel>
              <DropdownMenuSeparator />
              <DropdownMenuItem onSelect={() => navigate("/app/profile")}>
                <UserIcon /> Profile & security
              </DropdownMenuItem>
              <DropdownMenuItem onSelect={() => navigate("/app/notifications")}>
                <Bell /> Notifications
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem
                onSelect={() => {
                  logout();
                  navigate("/login");
                }}
              >
                <LogOut /> Sign out
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </header>
        <main id="main" className="mx-auto w-full max-w-[1200px] flex-1 px-4 py-6 lg:px-8 lg:py-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

export function RequireRole({ roles, children }: { roles: Role[]; children: ReactNode }) {
  const { user, loading } = useAuth();
  if (loading) return null;
  if (!user) return <Navigate to="/login" replace />;
  if (!roles.includes(user.role)) return <Navigate to={homePath(user.role)} replace />;
  return <>{children}</>;
}
