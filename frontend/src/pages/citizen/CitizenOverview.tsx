import { Link } from "react-router-dom";
import { AlertCircle, CheckCircle2, Clock, FilePlus2, Files } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { EmptyState, ErrorState, LoadingRows, PageHeader, StatCard, StatusBadge } from "@/components/common";
import { StatusBreakdown } from "@/components/charts";
import { api } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { formatDate } from "@/lib/format";
import { useApi } from "@/lib/useApi";
import type { CitizenDashboard, ComplaintSummary, Page } from "@/lib/types";

export default function CitizenOverview() {
  const { user } = useAuth();
  const stats = useApi(() => api.get<CitizenDashboard>("/dashboard/citizen").then((r) => r.data), []);
  const recent = useApi(() => api.get<Page<ComplaintSummary>>("/complaints?size=5&sort=updatedAt,desc").then((r) => r.data), []);
  const t = stats.data?.totals;

  return (
    <>
      <PageHeader
        title={`Welcome, ${user?.fullName.split(" ")[0] ?? ""}`}
        description="A summary of the grievances you have filed."
        actions={
          <Button asChild>
            <Link to="/app/citizen/new">
              <FilePlus2 /> Submit grievance
            </Link>
          </Button>
        }
      />

      {stats.error ? (
        <Card><ErrorState message={stats.error} onRetry={stats.reload} /></Card>
      ) : (
        <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
          <StatCard label="Total filed" value={t?.total ?? "–"} icon={<Files />} />
          <StatCard label="Open" value={t?.open ?? "–"} icon={<Clock />} />
          <StatCard label="Awaiting your reply" value={t?.needsInformation ?? "–"} tone={t?.needsInformation ? "warn" : "default"} icon={<AlertCircle />} />
          <StatCard label="Resolved" value={t?.resolved ?? "–"} tone="good" icon={<CheckCircle2 />} />
        </div>
      )}

      <div className="mt-6 grid gap-6 lg:grid-cols-[1.4fr_1fr]">
        <Card>
          <CardHeader className="flex-row items-center justify-between">
            <CardTitle>Recently updated</CardTitle>
            <Link to="/app/complaints" className="text-xs font-medium text-primary hover:underline">
              View all
            </Link>
          </CardHeader>
          {recent.loading ? (
            <LoadingRows rows={4} />
          ) : recent.error ? (
            <ErrorState message={recent.error} onRetry={recent.reload} />
          ) : recent.data && recent.data.content.length > 0 ? (
            <ul className="divide-y">
              {recent.data.content.map((c) => (
                <li key={c.id}>
                  <Link to={`/app/complaints/${c.id}`} className="flex items-center justify-between gap-3 px-5 py-3 hover:bg-muted/50">
                    <div className="min-w-0">
                      <div className="truncate text-sm font-medium text-navy">{c.title}</div>
                      <div className="text-xs text-muted-foreground">
                        <span className="font-mono">{c.reference}</span> · {c.departmentName} · updated {formatDate(c.updatedAt)}
                      </div>
                    </div>
                    <StatusBadge status={c.status} />
                  </Link>
                </li>
              ))}
            </ul>
          ) : (
            <EmptyState
              title="You have not filed any grievances"
              description="When you submit one, its progress will appear here."
              action={
                <Button asChild size="sm">
                  <Link to="/app/citizen/new">Submit your first grievance</Link>
                </Button>
              }
            />
          )}
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>By status</CardTitle>
          </CardHeader>
          <CardContent>{stats.loading ? <LoadingRows rows={4} /> : <StatusBreakdown data={stats.data?.byStatus ?? []} />}</CardContent>
        </Card>
      </div>
    </>
  );
}
