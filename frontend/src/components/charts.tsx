import { Bar, BarChart, CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis, Legend } from "recharts";
import { EmptyState } from "@/components/common";
import { STATUS_LABEL } from "@/lib/format";
import type { ComplaintStatus, CountItem, TrendPoint } from "@/lib/types";
import { BarChart3 } from "lucide-react";

const AXIS = { fontSize: 11, fill: "hsl(215 12% 42%)" };
const GRID = "hsl(35 12% 90%)";
const TEAL = "#0D6E80";
const NAVY = "#10203A";
const TOOLTIP = {
  contentStyle: { borderRadius: 6, border: "1px solid hsl(35 12% 86%)", fontSize: 12, boxShadow: "none" },
  cursor: { fill: "hsl(40 14% 94%)" },
};

const STATUS_COLOR: Record<string, string> = {
  SUBMITTED: "#64748b",
  ACKNOWLEDGED: "#0284c7",
  ASSIGNED: "#7c3aed",
  IN_PROGRESS: "#0D6E80",
  NEEDS_INFORMATION: "#d97706",
  RESOLVED: "#059669",
  CLOSED: "#a8a29e",
  REJECTED: "#dc2626",
};

const ORDER: ComplaintStatus[] = ["SUBMITTED", "ACKNOWLEDGED", "ASSIGNED", "IN_PROGRESS", "NEEDS_INFORMATION", "RESOLVED", "CLOSED", "REJECTED"];

/** Horizontal bars, one per status, in workflow order. */
export function StatusBreakdown({ data }: { data: CountItem[] }) {
  const total = data.reduce((s, d) => s + d.count, 0);
  if (total === 0) return <EmptyState icon={<BarChart3 className="h-5 w-5" />} title="No complaints yet" description="The status breakdown appears once complaints are filed." />;
  const map = new Map(data.map((d) => [d.key, d.count]));
  return (
    <ul className="space-y-2.5">
      {ORDER.filter((s) => map.has(s)).map((s) => {
        const n = map.get(s) ?? 0;
        const pct = (n / total) * 100;
        return (
          <li key={s} className="grid grid-cols-[130px_1fr_44px] items-center gap-3 text-sm">
            <span className="truncate text-muted-foreground">{STATUS_LABEL[s]}</span>
            <span className="h-2 overflow-hidden rounded-sm bg-muted" aria-hidden>
              <span className="block h-full rounded-sm" style={{ width: `${Math.max(pct, 1.5)}%`, background: STATUS_COLOR[s] }} />
            </span>
            <span className="text-right tabular font-medium">{n}</span>
          </li>
        );
      })}
    </ul>
  );
}

export function TrendChart({ data, height = 220 }: { data: TrendPoint[]; height?: number }) {
  const any = data.some((d) => d.submitted > 0 || d.resolved > 0);
  if (!any) return <EmptyState icon={<BarChart3 className="h-5 w-5" />} title="No activity in this period" description="Submissions and resolutions will be plotted here." />;
  const rows = data.map((d) => ({ ...d, label: new Date(d.date).toLocaleDateString("en-IN", { day: "2-digit", month: "short" }) }));
  return (
    <div style={{ height }}>
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={rows} margin={{ top: 8, right: 8, left: -20, bottom: 0 }}>
          <CartesianGrid stroke={GRID} vertical={false} />
          <XAxis dataKey="label" tick={AXIS} tickLine={false} axisLine={{ stroke: GRID }} interval="preserveStartEnd" minTickGap={24} />
          <YAxis allowDecimals={false} tick={AXIS} tickLine={false} axisLine={false} />
          <Tooltip {...TOOLTIP} />
          <Legend iconType="plainline" wrapperStyle={{ fontSize: 12 }} />
          <Line type="monotone" dataKey="submitted" name="Submitted" stroke={NAVY} strokeWidth={1.75} dot={false} />
          <Line type="monotone" dataKey="resolved" name="Resolved" stroke={TEAL} strokeWidth={1.75} dot={false} />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}

export function HorizontalBars({
  data,
  bars,
  height,
}: {
  data: Record<string, string | number>[];
  bars: { key: string; name: string; color: string }[];
  height?: number;
}) {
  if (data.length === 0) return <EmptyState title="No data" />;
  return (
    <div style={{ height: height ?? Math.max(160, data.length * 38 + 40) }}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} layout="vertical" margin={{ top: 4, right: 12, left: 8, bottom: 0 }} barGap={2}>
          <CartesianGrid stroke={GRID} horizontal={false} />
          <XAxis type="number" allowDecimals={false} tick={AXIS} tickLine={false} axisLine={false} />
          <YAxis type="category" dataKey="name" width={150} tick={AXIS} tickLine={false} axisLine={false} />
          <Tooltip {...TOOLTIP} />
          <Legend iconType="square" wrapperStyle={{ fontSize: 12 }} />
          {bars.map((b) => (
            <Bar key={b.key} dataKey={b.key} name={b.name} fill={b.color} barSize={10} radius={[0, 2, 2, 0]} />
          ))}
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

export const CHART_COLORS = { navy: NAVY, teal: TEAL, amber: "#d97706", red: "#dc2626", stone: "#a8a29e" };
