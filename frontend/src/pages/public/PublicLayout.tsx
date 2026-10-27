import { Link, NavLink, Outlet } from "react-router-dom";
import { Logo } from "@/components/common";
import { Button } from "@/components/ui/button";
import { homePath, useAuth } from "@/lib/auth";
import { cn } from "@/lib/utils";

export function PublicLayout() {
  const { user } = useAuth();
  const link = ({ isActive }: { isActive: boolean }) =>
    cn("text-sm text-muted-foreground hover:text-navy", isActive && "text-navy font-medium");
  return (
    <div className="flex min-h-screen flex-col">
      <header className="border-b bg-surface">
        <div className="container flex h-14 items-center justify-between gap-4">
          <Link to="/" aria-label="Grievance360 home">
            <Logo />
          </Link>
          <nav className="flex items-center gap-4 sm:gap-6" aria-label="Public">
            <NavLink to="/track" className={link}>
              Track status
            </NavLink>
            {user ? (
              <Button size="sm" asChild>
                <Link to={homePath(user.role)}>Open dashboard</Link>
              </Button>
            ) : (
              <>
                <NavLink to="/login" className={link}>
                  Sign in
                </NavLink>
                <Button size="sm" asChild className="hidden sm:inline-flex">
                  <Link to="/register">Create account</Link>
                </Button>
              </>
            )}
          </nav>
        </div>
      </header>
      <main className="flex-1">
        <Outlet />
      </main>
      <footer className="border-t bg-surface">
        <div className="container flex flex-col gap-2 py-6 text-xs text-muted-foreground sm:flex-row sm:justify-between">
          <span>Grievance360 — public grievance redressal platform (prototype).</span>
          <span>Not affiliated with any government body. Demo data is fictional.</span>
        </div>
      </footer>
    </div>
  );
}
