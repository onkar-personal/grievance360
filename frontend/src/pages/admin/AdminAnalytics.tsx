import { useState } from "react";
import { Link } from "react-router-dom";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/field";
import { Table, TBody, TD, TH, THead, TR } from "@/components/ui/table";
import { EmptyState, ErrorState, LoadingRows, PageHeader, StatCard } from "@/components/common";
import { CHART_COLORS, HorizontalBars } from "@/components/charts";
import { api, toParams } from "@/lib/api";
import { formatHours, formatPct, PRIORITY_LABEL } from "@/lib/format";
import { useApi } from "@/lib/useApi";
import type { AdminOverview, CategorySlaRow, Priority } from "@/lib/types";
import { cn } from "@/lib/utils";

export default function AdminAnalytics() {
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const overview = useApi(() => api.get<AdminOverview>("/admin/dashboard?days=30").then((r) => r.data), []);
  const sla = useApi(() => api.get<CategorySlaRow[]>(`/admin/reports/sla?${toParams({ from, to })}`).then((r) => r.data), [from, to]);
  const d = overview.data;

  return (
    <>
      <PageHeader title="Analytics & SLA" description="Resolution performance computed from complaint records." />

      {overview.error ? (
        <Card><ErrorState message={overview.error} onRetry={overview.reload} /></Card>
      ) : (
        <>
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <StatCard label="SLA compliance" value={formatPct(d?.sla.compliancePercent)} hint="Resolved on or before target" />
            <StatCard label="Average resolution" value={formatHours(d?.sla.avgResolutionHours)} hint="Filed → resolved" />
            <StatCard label="Median resolution" value={formatHours(d?.sla.medianResolutionHours)} />
            <StatCard label="Escalated (open)" value={d?.totals.escalated ?? "–"} tone={d?.totals.escalated ? "warn" : "default"} hint="Past target, auto-escalated" />
          </div>

          <div className="mt-6 grid gap-6 xl:grid-cols-2">
            <Card>
              <CardHeader>
                <CardTitle>Workload by department</CardTitle>
                <CardDescription>Open vs. overdue complaints.</CardDescription>
              </CardHeader>
              <CardContent>
                {overview.loading ? (
                  <LoadingRows rows={5} />
                ) : (d?.departments ?? []).some((x) => x.open > 0) ? (
                  <HorizontalBars
                    data={(d?.departments ?? []).map((x) => ({ name: x.department, open: x.open, overdue: x.overdue }))}
                    bars={[
                      { key: "open", name: "Open", color: CHART_COLORS.navy },
                      { key: "overdue", name: "Overdue", color: CHART_COLORS.red },
                    ]}
                  />
                ) : (
                  <EmptyState title="No open complaints" />
                )}
              </CardContent>
            </Card>
            <Card>
              <CardHeader>
                <CardTitle>Open complaints by priority</CardTitle>
              </CardHeader>
              <CardContent>
                {overview.loading ? (
                  <LoadingRows rows={4} />
                ) : (d?.byPriority ?? []).length ? (
                  <HorizontalBars
                    height={200}
                    data={(["CRITICAL", "HIGH", "MEDIUM", "LOW"] as Priority[]).map((p) => ({
                      name: PRIORITY_LABEL[p],
                      count: d?.byPriority.find((x) => x.key === p)?.count ?? 0,
                    }))}
                    bars={[{ key: "count", name: "Open complaints", color: CHART_COLORS.teal }]}
                  />
                ) : (
                  <EmptyState title="No open complaints" />
                )}
              </CardContent>
            </Card>
          </div>
        </>
      )}

      <Card className="mt-6">
        <CardHeader className="gap-3 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <CardTitle>SLA report by category</CardTitle>
            <CardDescription>Complaints filed in the selected period. Compliance = resolved within target ÷ resolved.</CardDescription>
          </div>
          <div className="flex items-end gap-2">
            <div className="space-y-1">
              <Label htmlFor="sla-from" className="text-xs text-muted-foreground">From</Label>
              <Input id="sla-from" type="date" value={from} onChange={(e) => setFrom(e.target.value)} className="w-[150px]" />
            </div>
            <div className="space-y-1">
              <Label htmlFor="sla-to" className="text-xs text-muted-foreground">To</Label>
              <Input id="sla-to" type="date" value={to} onChange={(e) => setTo(e.target.value)} className="w-[150px]" />
            </div>
          </div>
        </CardHeader>
        {sla.loading && !sla.data ? (
          <LoadingRows rows={6} />
        ) : sla.error ? (
          <ErrorState message={sla.error} onRetry={sla.reload} />
        ) : (
          <Table>
            <THead>
              <TR>
                <TH>Category</TH>
                <TH>Department</TH>
                <TH className="text-right">Target</TH>
                <TH className="text-right">Filed</TH>
                <TH className="text-right">Resolved</TH>
                <TH className="text-right">Within SLA</TH>
                <TH className="text-right">Compliance</TH>
                <TH className="text-right">Avg. time</TH>
                <TH className="text-right">Overdue now</TH>
              </TR>
            </THead>
            <TBody>
              {(sla.data ?? []).map((r) => (
                <TR key={r.categoryId}>
                  <TD className="font-medium text-navy">{r.category}</TD>
                  <TD className="text-muted-foreground">{r.department}</TD>
                  <TD className="text-right tabular">{r.slaHours} h</TD>
                  <TD className="text-right tabular">{r.total}</TD>
                  <TD className="text-right tabular">{r.resolved}</TD>
                  <TD className="text-right tabular">{r.resolvedWithinSla}</TD>
                  <TD
                    className={cn(
                      "text-right tabular",
                      r.compliancePercent != null && r.compliancePercent < 70 && "font-medium text-red-700",
                      r.compliancePercent != null && r.compliancePercent >= 90 && "text-emerald-700",
                    )}
                  >
                    {formatPct(r.compliancePercent)}
                  </TD>
                  <TD className="text-right tabular">{formatHours(r.avgResolutionHours)}</TD>
                  <TD className="text-right tabular">
                    {r.overdueOpen > 0 ? (
                      <Link to={`/app/complaints?view=overdue`} className="font-medium text-red-700 hover:underline">
                        {r.overdueOpen}
                      </Link>
                    ) : (
                      0
                    )}
                  </TD>
                </TR>
              ))}
            </TBody>
          </Table>
        )}
      </Card>
    </>
  );
}
