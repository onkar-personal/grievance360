import { useEffect, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { ArrowDown, ArrowUp, FilePlus2, Search, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input, Select } from "@/components/ui/input";
import { Table, TBody, TD, TH, THead, TR } from "@/components/ui/table";
import { EmptyState, ErrorState, LoadingRows, OverdueBadge, PageHeader, Pagination, PriorityBadge, StatusBadge } from "@/components/common";
import { api, toParams } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { ALL_PRIORITIES, ALL_STATUSES, formatDate, PRIORITY_LABEL, relativeTime, STATUS_LABEL } from "@/lib/format";
import { useApi } from "@/lib/useApi";
import type { ComplaintSummary, Department, Page } from "@/lib/types";
import { cn } from "@/lib/utils";

const QUICK_OFFICER = [
  { key: "", label: "All in department" },
  { key: "mine", label: "Assigned to me" },
  { key: "unassigned", label: "Unassigned" },
  { key: "overdue", label: "Overdue" },
];

const QUICK_ADMIN = [
  { key: "", label: "All" },
  { key: "unassigned", label: "Unassigned" },
  { key: "overdue", label: "Overdue" },
  { key: "escalated", label: "Escalated" },
];

export default function ComplaintList() {
  const { user } = useAuth();
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  const role = user!.role;
  const [q, setQ] = useState(params.get("q") ?? "");

  const page = Number(params.get("page") ?? 0);
  const sort = params.get("sort") ?? "createdAt,desc";
  const view = params.get("view") ?? "";
  const filters = {
    q: params.get("q") ?? "",
    status: params.get("status") ?? "",
    priority: params.get("priority") ?? "",
    departmentId: params.get("departmentId") ?? "",
    officerId: params.get("officerId") ?? "",
    from: params.get("from") ?? "",
    to: params.get("to") ?? "",
  };

  const update = (patch: Record<string, string>, resetPage = true) => {
    const next = new URLSearchParams(params);
    Object.entries(patch).forEach(([k, v]) => (v ? next.set(k, v) : next.delete(k)));
    if (resetPage) next.delete("page");
    setParams(next, { replace: true });
  };

  // Debounced free-text search
  useEffect(() => {
    const t = setTimeout(() => {
      if ((params.get("q") ?? "") !== q.trim()) update({ q: q.trim() });
    }, 350);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [q]);

  const departments = useApi(
    () => (role === "ADMIN" ? api.get<Department[]>("/departments").then((r) => r.data) : Promise.resolve([] as Department[])),
    [role],
  );

  const list = useApi(() => {
    const query = toParams({
      ...filters,
      status: filters.status ? [filters.status] : undefined,
      mine: view === "mine",
      unassigned: view === "unassigned",
      overdue: view === "overdue",
      escalated: view === "escalated",
      page,
      size: 20,
      sort,
    });
    return api.get<Page<ComplaintSummary>>(`/complaints?${query}`).then((r) => r.data);
  }, [params.toString()]);

  const toggleSort = (field: string) => {
    const [f, dir] = sort.split(",");
    update({ sort: `${field},${f === field && dir === "desc" ? "asc" : "desc"}` }, false);
  };
  const SortHead = ({ field, children, className }: { field: string; children: React.ReactNode; className?: string }) => {
    const [f, dir] = sort.split(",");
    return (
      <TH className={className} aria-sort={f === field ? (dir === "asc" ? "ascending" : "descending") : "none"}>
        <button className="inline-flex items-center gap-1 hover:text-foreground" onClick={() => toggleSort(field)}>
          {children}
          {f === field && (dir === "asc" ? <ArrowUp className="h-3 w-3" /> : <ArrowDown className="h-3 w-3" />)}
        </button>
      </TH>
    );
  };

  const hasFilters = Object.values(filters).some(Boolean) || !!view;
  const quick = role === "OFFICER" ? QUICK_OFFICER : role === "ADMIN" ? QUICK_ADMIN : null;
  const title = role === "CITIZEN" ? "My complaints" : role === "OFFICER" ? "Work queue" : "Complaints";

  return (
    <>
      <PageHeader
        title={title}
        description={
          role === "CITIZEN"
            ? "Every grievance you have submitted, with its current status."
            : role === "OFFICER"
              ? "Complaints routed to your department and those assigned to you."
              : "All complaints across departments."
        }
        actions={
          role === "CITIZEN" && (
            <Button asChild>
              <Link to="/app/citizen/new">
                <FilePlus2 /> Submit grievance
              </Link>
            </Button>
          )
        }
      />

      {quick && (
        <div className="mb-3 flex flex-wrap gap-1 border-b" role="tablist" aria-label="Queue views">
          {quick.map((v) => (
            <button
              key={v.key}
              role="tab"
              aria-selected={view === v.key}
              onClick={() => update({ view: v.key })}
              className={cn(
                "-mb-px border-b-2 px-3 py-2 text-sm text-muted-foreground hover:text-foreground",
                view === v.key ? "border-primary font-medium text-navy" : "border-transparent",
              )}
            >
              {v.label}
            </button>
          ))}
        </div>
      )}

      <Card>
        <div className="flex flex-wrap items-center gap-2 border-b p-3">
          <div className="relative min-w-[200px] flex-1">
            <Search className="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search reference or title" className="pl-8" aria-label="Search complaints" />
          </div>
          <Select aria-label="Status" value={filters.status} onChange={(e) => update({ status: e.target.value })} className="w-auto min-w-[150px]">
            <option value="">All statuses</option>
            {ALL_STATUSES.map((s) => (
              <option key={s} value={s}>
                {STATUS_LABEL[s]}
              </option>
            ))}
          </Select>
          {role !== "CITIZEN" && (
            <Select aria-label="Priority" value={filters.priority} onChange={(e) => update({ priority: e.target.value })} className="w-auto min-w-[130px]">
              <option value="">All priorities</option>
              {ALL_PRIORITIES.map((p) => (
                <option key={p} value={p}>
                  {PRIORITY_LABEL[p]}
                </option>
              ))}
            </Select>
          )}
          {role === "ADMIN" && (
            <Select aria-label="Department" value={filters.departmentId} onChange={(e) => update({ departmentId: e.target.value })} className="w-auto min-w-[170px]">
              <option value="">All departments</option>
              {(departments.data ?? []).map((d) => (
                <option key={d.id} value={d.id}>
                  {d.name}
                </option>
              ))}
            </Select>
          )}
          <div className="flex items-center gap-1">
            <Input type="date" aria-label="Created from" value={filters.from} onChange={(e) => update({ from: e.target.value })} className="w-[140px]" />
            <span className="text-xs text-muted-foreground">to</span>
            <Input type="date" aria-label="Created to" value={filters.to} onChange={(e) => update({ to: e.target.value })} className="w-[140px]" />
          </div>
          {hasFilters && (
            <Button
              variant="ghost"
              size="sm"
              onClick={() => {
                setQ("");
                setParams({}, { replace: true });
              }}
            >
              <X /> Clear
            </Button>
          )}
        </div>

        {list.loading && !list.data ? (
          <LoadingRows rows={8} />
        ) : list.error ? (
          <ErrorState message={list.error} onRetry={list.reload} />
        ) : list.data && list.data.content.length > 0 ? (
          <div className={cn(list.loading && "opacity-60 transition-opacity")}>
            <Table>
              <THead>
                <TR>
                  <SortHead field="reference">Reference</SortHead>
                  <TH>Title</TH>
                  {role !== "CITIZEN" && <TH>Priority</TH>}
                  <SortHead field="status">Status</SortHead>
                  <TH className="hidden md:table-cell">Department</TH>
                  {role !== "CITIZEN" && <TH className="hidden lg:table-cell">Officer</TH>}
                  <SortHead field="createdAt">Filed</SortHead>
                  {role !== "CITIZEN" && <SortHead field="dueAt">Due</SortHead>}
                </TR>
              </THead>
              <TBody>
                {list.data.content.map((c) => (
                  <TR
                    key={c.id}
                    className="cursor-pointer hover:bg-muted/40"
                    onClick={() => navigate(`/app/complaints/${c.id}`)}
                  >
                    <TD className="whitespace-nowrap font-mono text-xs">
                      <Link to={`/app/complaints/${c.id}`} className="text-primary hover:underline" onClick={(e) => e.stopPropagation()}>
                        {c.reference}
                      </Link>
                    </TD>
                    <TD className="max-w-[280px]">
                      <div className="truncate font-medium text-navy">{c.title}</div>
                      <div className="truncate text-xs text-muted-foreground">{c.categoryName}</div>
                    </TD>
                    {role !== "CITIZEN" && (
                      <TD>
                        <PriorityBadge priority={c.priority} />
                      </TD>
                    )}
                    <TD>
                      <div className="flex flex-wrap items-center gap-1">
                        <StatusBadge status={c.status} />
                        {c.overdue && role !== "CITIZEN" && <OverdueBadge />}
                      </div>
                    </TD>
                    <TD className="hidden whitespace-nowrap text-muted-foreground md:table-cell">{c.departmentName}</TD>
                    {role !== "CITIZEN" && (
                      <TD className="hidden whitespace-nowrap lg:table-cell">
                        {c.assignedOfficer ? c.assignedOfficer.name : <span className="text-muted-foreground">Unassigned</span>}
                      </TD>
                    )}
                    <TD className="whitespace-nowrap text-muted-foreground tabular">{formatDate(c.createdAt)}</TD>
                    {role !== "CITIZEN" && (
                      <TD className={cn("whitespace-nowrap tabular", c.overdue ? "text-red-700" : "text-muted-foreground")}>
                        {["RESOLVED", "CLOSED", "REJECTED"].includes(c.status) ? "—" : relativeTime(c.dueAt)}
                      </TD>
                    )}
                  </TR>
                ))}
              </TBody>
            </Table>
            <Pagination
              page={list.data.page}
              size={list.data.size}
              totalPages={list.data.totalPages}
              totalElements={list.data.totalElements}
              onPage={(p) => update({ page: String(p) }, false)}
            />
          </div>
        ) : (
          <EmptyState
            title={hasFilters ? "No complaints match these filters" : role === "CITIZEN" ? "No complaints yet" : "The queue is empty"}
            description={hasFilters ? "Try removing a filter or widening the date range." : undefined}
          />
        )}
      </Card>
    </>
  );
}
