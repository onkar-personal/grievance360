import { useEffect, useState } from "react";
import { Search } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Input, Select } from "@/components/ui/input";
import { Table, TBody, TD, TH, THead, TR } from "@/components/ui/table";
import { EmptyState, ErrorState, LoadingRows, PageHeader, Pagination } from "@/components/common";
import { api, toParams } from "@/lib/api";
import { formatDateTime, humanize } from "@/lib/format";
import { useApi } from "@/lib/useApi";
import type { AuditLogItem, Page } from "@/lib/types";

const ENTITY_TYPES = ["USER", "COMPLAINT", "DEPARTMENT", "CATEGORY", "SETTING"];

export default function AdminAudit() {
  const [actorInput, setActorInput] = useState("");
  const [actor, setActor] = useState("");
  const [entityType, setEntityType] = useState("");
  const [page, setPage] = useState(0);

  useEffect(() => {
    const t = setTimeout(() => {
      setActor(actorInput.trim());
      setPage(0);
    }, 350);
    return () => clearTimeout(t);
  }, [actorInput]);

  const logs = useApi(
    () => api.get<Page<AuditLogItem>>(`/admin/audit-logs?${toParams({ actor, entityType, page, size: 25 })}`).then((r) => r.data),
    [actor, entityType, page],
  );

  return (
    <>
      <PageHeader title="Audit log" description="Administrative and sensitive actions, newest first. Records cannot be edited." />
      <Card>
        <div className="flex flex-wrap gap-2 border-b p-3">
          <div className="relative min-w-[220px] flex-1">
            <Search className="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input value={actorInput} onChange={(e) => setActorInput(e.target.value)} placeholder="Filter by actor email" className="pl-8" aria-label="Filter by actor" />
          </div>
          <Select aria-label="Entity type" value={entityType} onChange={(e) => { setEntityType(e.target.value); setPage(0); }} className="w-auto min-w-[150px]">
            <option value="">All entities</option>
            {ENTITY_TYPES.map((t) => (
              <option key={t} value={t}>{humanize(t)}</option>
            ))}
          </Select>
        </div>
        {logs.loading && !logs.data ? (
          <LoadingRows rows={10} />
        ) : logs.error ? (
          <ErrorState message={logs.error} onRetry={logs.reload} />
        ) : logs.data && logs.data.content.length ? (
          <>
            <Table>
              <THead>
                <TR>
                  <TH>When</TH>
                  <TH>Actor</TH>
                  <TH>Action</TH>
                  <TH>Entity</TH>
                  <TH className="hidden md:table-cell">Details</TH>
                  <TH className="hidden lg:table-cell">IP</TH>
                </TR>
              </THead>
              <TBody>
                {logs.data.content.map((l) => (
                  <TR key={l.id}>
                    <TD className="whitespace-nowrap text-muted-foreground tabular">{formatDateTime(l.createdAt)}</TD>
                    <TD className="whitespace-nowrap">{l.actorEmail ?? <span className="text-muted-foreground">System</span>}</TD>
                    <TD className="whitespace-nowrap font-medium text-navy">{humanize(l.action)}</TD>
                    <TD className="whitespace-nowrap text-muted-foreground">
                      {humanize(l.entityType)} {l.entityId && <span className="font-mono text-xs">#{l.entityId}</span>}
                    </TD>
                    <TD className="hidden max-w-xs truncate text-muted-foreground md:table-cell" title={l.details}>{l.details ?? "—"}</TD>
                    <TD className="hidden font-mono text-xs text-muted-foreground lg:table-cell">{l.ipAddress ?? "—"}</TD>
                  </TR>
                ))}
              </TBody>
            </Table>
            <Pagination page={logs.data.page} size={logs.data.size} totalPages={logs.data.totalPages} totalElements={logs.data.totalElements} onPage={setPage} />
          </>
        ) : (
          <EmptyState title="No audit entries" />
        )}
      </Card>
    </>
  );
}
