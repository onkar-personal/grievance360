import { lazy, Suspense } from "react";
import { Link, Navigate, Route, Routes } from "react-router-dom";
import { AppShell, RequireRole } from "@/components/AppShell";
import { Button } from "@/components/ui/button";
import { homePath, useAuth } from "@/lib/auth";
import { PublicLayout } from "@/pages/public/PublicLayout";
import { Landing } from "@/pages/public/Landing";
import { LoginPage, RegisterPage } from "@/pages/public/Auth";
import { TrackPage } from "@/pages/public/Track";

const CitizenOverview = lazy(() => import("@/pages/citizen/CitizenOverview"));
const SubmitComplaint = lazy(() => import("@/pages/citizen/SubmitComplaint"));
const ComplaintList = lazy(() => import("@/pages/shared/ComplaintList"));
const ComplaintDetailPage = lazy(() => import("@/pages/shared/ComplaintDetail"));
const NotificationsPage = lazy(() => import("@/pages/shared/Notifications"));
const ProfilePage = lazy(() => import("@/pages/shared/Profile"));
const OfficerOverview = lazy(() => import("@/pages/officer/OfficerOverview"));
const AdminOverview = lazy(() => import("@/pages/admin/AdminOverview"));
const AdminAnalytics = lazy(() => import("@/pages/admin/AdminAnalytics"));
const AdminUsers = lazy(() => import("@/pages/admin/AdminUsers"));
const AdminDepartments = lazy(() => import("@/pages/admin/AdminDepartments"));
const AdminSettings = lazy(() => import("@/pages/admin/AdminSettings"));
const AdminAudit = lazy(() => import("@/pages/admin/AdminAudit"));

function AppHome() {
  const { user } = useAuth();
  return user ? <Navigate to={homePath(user.role)} replace /> : <Navigate to="/login" replace />;
}

function NotFound() {
  return (
    <div className="container py-20 text-center">
      <p className="text-sm font-medium text-primary">404</p>
      <h1 className="mt-2 text-xl font-semibold">Page not found</h1>
      <p className="mt-1 text-sm text-muted-foreground">The page you are looking for does not exist or has moved.</p>
      <Button asChild className="mt-6" variant="outline">
        <Link to="/">Go to home</Link>
      </Button>
    </div>
  );
}

const Fallback = <div className="py-16 text-center text-sm text-muted-foreground">Loading…</div>;

export function App() {
  return (
    <Suspense fallback={Fallback}>
      <Routes>
        <Route element={<PublicLayout />}>
          <Route index element={<Landing />} />
          <Route path="login" element={<LoginPage />} />
          <Route path="register" element={<RegisterPage />} />
          <Route path="track" element={<TrackPage />} />
          <Route path="track/:reference" element={<TrackPage />} />
        </Route>

        <Route path="app" element={<AppShell />}>
          <Route index element={<AppHome />} />
          <Route path="citizen" element={<RequireRole roles={["CITIZEN"]}><CitizenOverview /></RequireRole>} />
          <Route path="citizen/new" element={<RequireRole roles={["CITIZEN"]}><SubmitComplaint /></RequireRole>} />
          <Route path="complaints" element={<ComplaintList />} />
          <Route path="complaints/:id" element={<ComplaintDetailPage />} />
          <Route path="notifications" element={<NotificationsPage />} />
          <Route path="profile" element={<ProfilePage />} />
          <Route path="officer" element={<RequireRole roles={["OFFICER"]}><OfficerOverview /></RequireRole>} />
          <Route path="admin" element={<RequireRole roles={["ADMIN"]}><AdminOverview /></RequireRole>} />
          <Route path="admin/analytics" element={<RequireRole roles={["ADMIN"]}><AdminAnalytics /></RequireRole>} />
          <Route path="admin/users" element={<RequireRole roles={["ADMIN"]}><AdminUsers /></RequireRole>} />
          <Route path="admin/departments" element={<RequireRole roles={["ADMIN"]}><AdminDepartments /></RequireRole>} />
          <Route path="admin/settings" element={<RequireRole roles={["ADMIN"]}><AdminSettings /></RequireRole>} />
          <Route path="admin/audit" element={<RequireRole roles={["ADMIN"]}><AdminAudit /></RequireRole>} />
          <Route path="*" element={<NotFound />} />
        </Route>

        <Route path="*" element={<NotFound />} />
      </Routes>
    </Suspense>
  );
}
