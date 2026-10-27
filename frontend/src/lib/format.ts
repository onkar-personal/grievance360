import type { ComplaintStatus, Priority } from "./types";

const dateFmt = new Intl.DateTimeFormat("en-IN", { day: "2-digit", month: "short", year: "numeric" });
const dateTimeFmt = new Intl.DateTimeFormat("en-IN", {
  day: "2-digit",
  month: "short",
  year: "numeric",
  hour: "2-digit",
  minute: "2-digit",
});

export const formatDate = (iso?: string | null) => (iso ? dateFmt.format(new Date(iso)) : "—");
export const formatDateTime = (iso?: string | null) => (iso ? dateTimeFmt.format(new Date(iso)) : "—");

export function relativeTime(iso: string, now = Date.now()): string {
  const diff = new Date(iso).getTime() - now;
  const abs = Math.abs(diff);
  const rtf = new Intl.RelativeTimeFormat("en", { numeric: "auto" });
  const mins = Math.round(diff / 60000);
  if (abs < 3600000) return rtf.format(mins, "minute");
  const hours = Math.round(diff / 3600000);
  if (abs < 86400000) return rtf.format(hours, "hour");
  return rtf.format(Math.round(diff / 86400000), "day");
}

export const STATUS_LABEL: Record<ComplaintStatus, string> = {
  SUBMITTED: "Submitted",
  ACKNOWLEDGED: "Acknowledged",
  ASSIGNED: "Assigned",
  IN_PROGRESS: "In progress",
  NEEDS_INFORMATION: "Needs information",
  RESOLVED: "Resolved",
  CLOSED: "Closed",
  REJECTED: "Rejected",
};

export const ALL_STATUSES = Object.keys(STATUS_LABEL) as ComplaintStatus[];
export const OPEN_STATUSES: ComplaintStatus[] = ["SUBMITTED", "ACKNOWLEDGED", "ASSIGNED", "IN_PROGRESS", "NEEDS_INFORMATION"];

export const PRIORITY_LABEL: Record<Priority, string> = {
  LOW: "Low",
  MEDIUM: "Medium",
  HIGH: "High",
  CRITICAL: "Critical",
};
export const ALL_PRIORITIES = Object.keys(PRIORITY_LABEL) as Priority[];

export function formatBytes(n: number): string {
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(0)} KB`;
  return `${(n / (1024 * 1024)).toFixed(1)} MB`;
}

export function formatHours(h: number | null | undefined): string {
  if (h === null || h === undefined) return "—";
  if (h < 48) return `${h.toFixed(1)} h`;
  return `${(h / 24).toFixed(1)} d`;
}

export const formatPct = (p: number | null | undefined) => (p === null || p === undefined ? "—" : `${p.toFixed(1)}%`);

export function humanize(s: string): string {
  const l = s.replace(/_/g, " ").toLowerCase();
  return l.charAt(0).toUpperCase() + l.slice(1);
}
