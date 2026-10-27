import { useState } from "react";
import { Link } from "react-router-dom";
import { AlarmClock, CheckCircle2, Files, Inbox, TrendingUp, UserX } from "lucide-react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Select } from "@/components/ui/input";
import { Table, TBody, TD, TH, THead, TR } from "@/components/ui/table";
import { ErrorState, LoadingRows, PageHeader, StatCard } from "@/components/common";
import { StatusBreakdown, TrendChart } from "@/components/charts";
import { api } from "@/lib/api";
import { formatHours, formatPct } from "@/lib/format";
import { useApi } from "@/lib/useApi";
import type { AdminOverview as Overview } from "@/lib/types";
import { cn } from "@/lib/utils";

export default function AdminOverview() {
  const [days, setDays] = useState(30);
  const dash = useApi(() => api.get<Overview>(`/admin/dashboard?days=${days}`).then((r) => r.data), [days]);

  if (dash.error) return <Card><ErrorState message={dash.error} onRetry={dash.reload} /></Card>;
  const d = dash.data;
  const t = d?.totals;

  return (
    <>
      <PageHeader title="System overview" description="Live figures across all departments." />
      <div className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6">
        <StatCard label="Total complaints" value={t?.total ?? "–"} icon={<Files />} />
        <StatCard label="Open" value={t?.open ?? "–"} icon={<Inbox />} />
        <StatCard label="Overdue" value={t?.overdue ?? "–"} tone={t?.overdue ? "danger" : "default"} icon={<AlarmClock />} />
        <StatCard label="Unassigned (open)" value={t?.unassigned ?? "–"} tone={t?.unassigned ? "warn" : "default"} icon={<UserX />} />
        <StatCard label="Resolved" value={t?.resolved ?? "–"} tone="good" icon={<CheckCircle2 />} />
        <StatCard
          label="Within SLA"
          value={formatPct(d?.sla.compliancePercent)}
          hint={d?.sla.resolvedCount ? `of ${d.sla.resolvedCount} resolved` : "No resolutions yet"}
          icon={<TrendingUp />}
        />
      </div>

      <div className="mt-6 grid gap-6 xl:grid-cols-[1.5fr_1fr]">
        <Card>
          <CardHeader className="flex-row items-center justify-between">
            <div>
              <CardTitle>Submitted vs. resolved</CardTitle>
              <CardDescription>Daily counts, UTC dates.</CardDescription>
            </div>
            <Select aria-label="Period" value={days} onChange={(e) => setDays(Number(e.target.value))} className="w-auto">
              <option value={7}>Last 7 days</option>
              <option value={30}>Last 30 days</option>
              <option value={90}>Last 90 days</option>
            </Select>
          </CardHeader>
          <CardContent>{dash.loading && !d ? <LoadingRows rows={6} /> : <TrendChart data={d?.trend ?? []} height={240} />}</CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Status breakdown</CardTitle>
            <CardDescription>All complaints, by current status.</CardDescription>
          </CardHeader>
          <CardContent>{dash.loading && !d ? <LoadingRows rows={6} /> : <StatusBreakdown data={d?.byStatus ?? []} />}</CardContent>
        </Card>
      </div>

      <Card className="mt-6">
        <CardHeader className="flex-row items-center justify-between">
          <div>
            <CardTitle>Department workload</CardTitle>
            <CardDescription>SLA compliance counts resolved complaints only.</CardDescription>
          </div>
          <Link to="/app/admin/analytics" className="text-xs font-medium text-primary hover:underline">
            Full analytics
          </Link>
        </CardHeader>
        {dash.loading && !d ? (
          <LoadingRows rows={5} />
        ) : (
          <Table>
            <THead>
              <TR>
                <TH>Department</TH>
                <TH className="text-right">Total</TH>
                <TH className="text-right">Open</TH>
                <TH className="text-right">Overdue</TH>
                <TH className="text-right">Resolved</TH>
                <TH className="text-right">Within SLA</TH>
                <TH className="text-right">Avg. resolution</TH>
              </TR>
            </THead>
            <TBody>
              {(d?.departments ?? []).map((r) => (
                <TR key={r.departmentId}>
                  <TD className="font-medium text-navy">
                    <Link to={`/app/complaints?departmentId=${r.departmentId}`} className="hover:underline">
                      {r.department}
                    </Link>
                  </TD>
                  <TD className="text-right tabular">{r.total}</TD>
                  <TD className="text-right tabular">{r.open}</TD>
                  <TD className={cn("text-right tabular", r.overdue > 0 && "font-medium text-red-700")}>{r.overdue}</TD>
                  <TD className="text-right tabular">{r.resolved}</TD>
                  <TD className="text-right tabular">{formatPct(r.compliancePercent)}</TD>
                  <TD className="text-right tabular">{formatHours(r.avgResolutionHours)}</TD>
                </TR>
              ))}
            </TBody>
          </Table>
        )}
      </Card>

      <Card className="mt-6">
        <CardHeader>
          <CardTitle>Officer workload</CardTitle>
          <CardDescription>Active officers ordered by open assignments.</CardDescription>
        </CardHeader>
        {dash.loading && !d ? (
          <LoadingRows rows={5} />
        ) : d && d.officers.length ? (
          <Table>
            <THead>
              <TR>
                <TH>Officer</TH>
                <TH>Department</TH>
                <TH className="text-right">Open</TH>
                <TH className="text-right">Overdue</TH>
                <TH className="text-right">Resolved (30d)</TH>
                <TH className="text-right">Avg. resolution</TH>
              </TR>
            </THead>
            <TBody>
              {d.officers.map((o) => (
                <TR key={o.officerId}>
                  <TD className="font-medium text-navy">
                    <Link to={`/app/complaints?officerId=${o.officerId}`} className="hover:underline">
                      {o.officer}
                    </Link>
                  </TD>
                  <TD className="text-muted-foreground">{o.department ?? "—"}</TD>
                  <TD className="text-right tabular">{o.open}</TD>
                  <TD className={cn("text-right tabular", o.overdue > 0 && "font-medium text-red-700")}>{o.overdue}</TD>
                  <TD className="text-right tabular">{o.resolvedLast30}</TD>
                  <TD className="text-right tabular">{formatHours(o.avgResolutionHours)}</TD>
                </TR>
              ))}
            </TBody>
          </Table>
        ) : (
          <CardContent>
            <p className="text-sm text-muted-foreground">No active officers. Add officers under Users & roles.</p>
          </CardContent>
        )}
      </Card>
    </>
  );
}
