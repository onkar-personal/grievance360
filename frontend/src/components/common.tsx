import type { ReactNode } from "react";
import { AlertTriangle, ChevronLeft, ChevronRight, Inbox, RefreshCw } from "lucide-react";
import { Badge, type Tone } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { PRIORITY_LABEL, STATUS_LABEL } from "@/lib/format";
import type { ComplaintStatus, Priority } from "@/lib/types";
import { cn } from "@/lib/utils";

const STATUS_TONE: Record<ComplaintStatus, Tone> = {
  SUBMITTED: "slate",
  ACKNOWLEDGED: "blue",
  ASSIGNED: "violet",
  IN_PROGRESS: "teal",
  NEEDS_INFORMATION: "amber",
  RESOLVED: "green",
  CLOSED: "neutral",
  REJECTED: "red",
};

export function StatusBadge({ status }: { status: ComplaintStatus }) {
  return <Badge tone={STATUS_TONE[status]}>{STATUS_LABEL[status]}</Badge>;
}

const PRIORITY_DOT: Record<Priority, string> = {
  LOW: "bg-stone-400",
  MEDIUM: "bg-sky-500",
  HIGH: "bg-amber-500",
  CRITICAL: "bg-red-600",
};

export function PriorityBadge({ priority }: { priority: Priority }) {
  return (
    <span className="inline-flex items-center gap-1.5 whitespace-nowrap text-xs text-foreground">
      <span className={cn("h-2 w-2 rounded-full", PRIORITY_DOT[priority])} aria-hidden />
      {PRIORITY_LABEL[priority]}
    </span>
  );
}

export function OverdueBadge() {
  return <Badge tone="red">Overdue</Badge>;
}

export function PageHeader({ title, description, actions }: { title: string; description?: string; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h1 className="text-xl font-semibold">{title}</h1>
        {description && <p className="mt-1 text-sm text-muted-foreground">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  );
}

export function EmptyState({ title, description, action, icon }: { title: string; description?: string; action?: ReactNode; icon?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center px-6 py-12 text-center">
      <div className="mb-3 rounded-full bg-muted p-2.5 text-muted-foreground">{icon ?? <Inbox className="h-5 w-5" />}</div>
      <p className="text-sm font-medium text-navy">{title}</p>
      {description && <p className="mt-1 max-w-sm text-sm text-muted-foreground">{description}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div role="alert" className="flex flex-col items-center justify-center px-6 py-10 text-center">
      <div className="mb-3 rounded-full bg-red-50 p-2.5 text-destructive">
        <AlertTriangle className="h-5 w-5" />
      </div>
      <p className="text-sm font-medium text-navy">Could not load this section</p>
      <p className="mt-1 max-w-sm text-sm text-muted-foreground">{message}</p>
      {onRetry && (
        <Button variant="outline" size="sm" className="mt-4" onClick={onRetry}>
          <RefreshCw /> Try again
        </Button>
      )}
    </div>
  );
}

export function LoadingRows({ rows = 5 }: { rows?: number }) {
  return (
    <div className="space-y-3 p-5" aria-label="Loading" role="status">
      {Array.from({ length: rows }).map((_, i) => (
        <Skeleton key={i} className="h-5 w-full" />
      ))}
    </div>
  );
}

export function StatCard({
  label,
  value,
  hint,
  tone,
  icon,
}: {
  label: string;
  value: ReactNode;
  hint?: string;
  tone?: "default" | "warn" | "danger" | "good";
  icon?: ReactNode;
}) {
  const color =
    tone === "danger" ? "text-red-700" : tone === "warn" ? "text-amber-700" : tone === "good" ? "text-emerald-700" : "text-navy";
  return (
    <div className="rounded-lg border bg-card px-4 py-3.5">
      <div className="flex items-center justify-between text-xs font-medium text-muted-foreground">
        <span>{label}</span>
        {icon && <span className="text-muted-foreground/70 [&_svg]:h-4 [&_svg]:w-4">{icon}</span>}
      </div>
      <div className={cn("mt-1.5 text-2xl font-semibold tabular", color)}>{value}</div>
      {hint && <div className="mt-0.5 text-xs text-muted-foreground">{hint}</div>}
    </div>
  );
}

export function Pagination({
  page,
  totalPages,
  totalElements,
  size,
  onPage,
}: {
  page: number;
  totalPages: number;
  totalElements: number;
  size: number;
  onPage: (p: number) => void;
}) {
  if (totalElements === 0) return null;
  const from = page * size + 1;
  const to = Math.min(totalElements, (page + 1) * size);
  return (
    <div className="flex items-center justify-between border-t px-4 py-2.5 text-xs text-muted-foreground">
      <span className="tabular">
        {from}–{to} of {totalElements}
      </span>
      <div className="flex items-center gap-1">
        <Button variant="ghost" size="sm" disabled={page <= 0} onClick={() => onPage(page - 1)} aria-label="Previous page">
          <ChevronLeft />
        </Button>
        <span className="tabular px-1">
          Page {page + 1} of {Math.max(totalPages, 1)}
        </span>
        <Button variant="ghost" size="sm" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)} aria-label="Next page">
          <ChevronRight />
        </Button>
      </div>
    </div>
  );
}

export function Logo({ className, light }: { className?: string; light?: boolean }) {
  return (
    <span className={cn("inline-flex items-center gap-2 font-semibold", light ? "text-white" : "text-navy", className)}>
      <svg viewBox="0 0 32 32" className="h-7 w-7" aria-hidden>
        <rect width="32" height="32" rx="6" fill={light ? "#ffffff" : "#10203A"} />
        <path d="M9 16a7 7 0 1 0 7-7" fill="none" stroke={light ? "#0D6E80" : "#5EC4D6"} strokeWidth="2.6" strokeLinecap="round" />
        <path d="M16 5.5v7l3.5-3.5z" fill={light ? "#0D6E80" : "#5EC4D6"} />
      </svg>
      <span className="text-[15px] tracking-tight">Grievance360</span>
    </span>
  );
}

export function KeyValue({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid grid-cols-[minmax(0,140px)_1fr] gap-3 py-1.5 text-sm">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="min-w-0 break-words text-foreground">{children ?? "—"}</dd>
    </div>
  );
}
