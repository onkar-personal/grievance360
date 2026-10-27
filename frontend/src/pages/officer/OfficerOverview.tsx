import { Link } from "react-router-dom";
import { AlarmClock, CheckCircle2, ClipboardList, Inbox, Timer } from "lucide-react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { EmptyState, ErrorState, LoadingRows, OverdueBadge, PageHeader, PriorityBadge, StatCard, StatusBadge } from "@/components/common";
import { StatusBreakdown, TrendChart } from "@/components/charts";
import { api } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { formatHours, formatPct, relativeTime } from "@/lib/format";
import { useApi } from "@/lib/useApi";
import type { ComplaintSummary, OfficerDashboard, Page } from "@/lib/types";

export default function OfficerOverview() {
  const { user } = useAuth();
  const dash = useApi(() => api.get<OfficerDashboard>("/officer/dashboard").then((r) => r.data), []);
  const urgent = useApi(
    () => api.get<Page<ComplaintSummary>>("/complaints?mine=true&size=6&sort=dueAt,asc&status=ASSIGNED&status=IN_PROGRESS&status=NEEDS_INFORMATION").then((r) => r.data),
    [],
  );

  if (dash.error) return <Card><ErrorState message={dash.error} onRetry={dash.reload} /></Card>;
  const d = dash.data;

  return (
    <>
      <PageHeader
        title="My workload"
        description={user?.departmentName ? `${user.departmentName}` : "Complaints assigned to you"}
        actions={
          <Button asChild>
            <Link to="/app/complaints?view=mine">
              <ClipboardList /> Open my queue
            </Link>
          </Button>
        }
      />

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-5">
        <StatCard label="Open, assigned to me" value={d?.mine.open ?? "–"} icon={<Inbox />} />
        <StatCard label="Overdue (mine)" value={d?.mine.overdue ?? "–"} tone={d?.mine.overdue ? "danger" : "default"} icon={<AlarmClock />} />
        <StatCard label="Unassigned in department" value={d?.departmentUnassigned ?? "–"} tone={d?.departmentUnassigned ? "warn" : "default"} />
        <StatCard label="Resolved by me" value={d?.mySla.resolvedCount ?? "–"} tone="good" icon={<CheckCircle2 />} />
        <StatCard
          label="Within SLA"
          value={formatPct(d?.mySla.compliancePercent)}
          hint={d?.mySla.avgResolutionHours != null ? `Avg. ${formatHours(d.mySla.avgResolutionHours)} to resolve` : "No resolutions yet"}
          icon={<Timer />}
        />
      </div>

      <div className="mt-6 grid gap-6 lg:grid-cols-[1.4fr_1fr]">
        <Card>
          <CardHeader className="flex-row items-center justify-between">
            <div>
              <CardTitle>Due soonest</CardTitle>
              <CardDescription>Your active complaints ordered by resolution target.</CardDescription>
            </div>
            {d && d.departmentOverdue > 0 && (
              <Link to="/app/complaints?view=overdue" className="text-xs font-medium text-red-700 hover:underline">
                {d.departmentOverdue} overdue in department
              </Link>
            )}
          </CardHeader>
          {urgent.loading ? (
            <LoadingRows rows={4} />
          ) : urgent.error ? (
            <ErrorState message={urgent.error} onRetry={urgent.reload} />
          ) : urgent.data && urgent.data.content.length ? (
            <ul className="divide-y">
              {urgent.data.content.map((c) => (
                <li key={c.id}>
                  <Link to={`/app/complaints/${c.id}`} className="flex items-center justify-between gap-3 px-5 py-3 hover:bg-muted/50">
                    <div className="min-w-0">
                      <div className="truncate text-sm font-medium text-navy">{c.title}</div>
                      <div className="mt-0.5 flex items-center gap-2 text-xs text-muted-foreground">
                        <span className="font-mono">{c.reference}</span>
                        <PriorityBadge priority={c.priority} />
                      </div>
                    </div>
                    <div className="flex shrink-0 flex-col items-end gap-1">
                      <div className="flex gap-1">
                        <StatusBadge status={c.status} />
                        {c.overdue && <OverdueBadge />}
                      </div>
                      <span className={`text-xs tabular ${c.overdue ? "text-red-700" : "text-muted-foreground"}`}>due {relativeTime(c.dueAt)}</span>
                    </div>
                  </Link>
                </li>
              ))}
            </ul>
          ) : (
            <EmptyState title="Nothing in progress" description="Claim an unassigned complaint from the department queue." action={<Button asChild size="sm" variant="outline"><Link to="/app/complaints?view=unassigned">View unassigned</Link></Button>} />
          )}
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>My complaints by status</CardTitle>
          </CardHeader>
          <CardContent>{dash.loading ? <LoadingRows rows={4} /> : <StatusBreakdown data={d?.byStatus ?? []} />}</CardContent>
        </Card>
      </div>

      <Card className="mt-6">
        <CardHeader>
          <CardTitle>Last 30 days</CardTitle>
          <CardDescription>Complaints assigned to you that were filed or resolved each day.</CardDescription>
        </CardHeader>
        <CardContent>{dash.loading ? <LoadingRows rows={5} /> : <TrendChart data={d?.trend ?? []} />}</CardContent>
      </Card>
    </>
  );
}
