import { useRef, useState, type FormEvent } from "react";
import { Link, useParams } from "react-router-dom";
import { toast } from "sonner";
import {
  ArrowLeft,
  CheckCircle2,
  Download,
  FileText,
  Hand,
  Lock,
  MapPin,
  MessageSquare,
  RotateCcw,
  Route,
  Send,
  Upload,
  UserPlus,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent } from "@/components/ui/dialog";
import { Input, Select, Textarea } from "@/components/ui/input";
import { Field, Label } from "@/components/ui/field";
import { Badge } from "@/components/ui/badge";
import { ErrorState, KeyValue, LoadingRows, OverdueBadge, PriorityBadge, StatusBadge } from "@/components/common";
import { api, errorMessage } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { ALL_PRIORITIES, formatBytes, formatDateTime, humanize, PRIORITY_LABEL, relativeTime, STATUS_LABEL } from "@/lib/format";
import { MAX_FILES, validateFile } from "@/lib/schemas";
import { useApi } from "@/lib/useApi";
import type { Category, CommentItem, ComplaintDetail, ComplaintStatus, HistoryEntry, OfficerOption, Priority } from "@/lib/types";
import { cn } from "@/lib/utils";

export default function ComplaintDetailPage() {
  const { id } = useParams();
  const { user } = useAuth();
  const detail = useApi(() => api.get<ComplaintDetail>(`/complaints/${id}`).then((r) => r.data), [id]);
  const history = useApi(() => api.get<HistoryEntry[]>(`/complaints/${id}/history`).then((r) => r.data), [id]);
  const comments = useApi(() => api.get<CommentItem[]>(`/complaints/${id}/comments`).then((r) => r.data), [id]);
  const [dialog, setDialog] = useState<null | "status" | "assign" | "priority" | "reroute" | "reopen" | "close">(null);

  const refreshAll = (updated?: ComplaintDetail) => {
    if (updated) detail.setData(updated);
    else void detail.reload();
    void history.reload();
    void comments.reload();
  };

  if (detail.loading && !detail.data) return <Card><LoadingRows rows={8} /></Card>;
  if (detail.error || !detail.data) {
    return (
      <Card>
        <ErrorState message={detail.error ?? "Complaint not found"} onRetry={detail.reload} />
      </Card>
    );
  }
  const c = detail.data;
  const a = c.actions;
  const staff = user?.role !== "CITIZEN";
  const isFinal = ["RESOLVED", "CLOSED", "REJECTED"].includes(c.status);

  return (
    <>
      <Link to="/app/complaints" className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-navy">
        <ArrowLeft className="h-4 w-4" /> Back to list
      </Link>

      <div className="mb-6 flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-mono text-xs text-muted-foreground">{c.reference}</span>
            <StatusBadge status={c.status} />
            {staff && <PriorityBadge priority={c.priority} />}
            {c.overdue && <OverdueBadge />}
            {c.escalationLevel > 0 && staff && <Badge tone="amber">Escalation L{c.escalationLevel}</Badge>}
          </div>
          <h1 className="mt-2 text-xl font-semibold">{c.title}</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            {c.categoryName} · {c.departmentName}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          {a.canClaim && <ClaimButton complaint={c} onDone={refreshAll} />}
          {a.canAssign && (
            <Button variant="outline" onClick={() => setDialog("assign")}>
              <UserPlus /> {c.assignedOfficer ? "Reassign" : "Assign"}
            </Button>
          )}
          {a.canChangePriority && (
            <Button variant="outline" onClick={() => setDialog("priority")}>
              Priority
            </Button>
          )}
          {a.canReroute && (
            <Button variant="outline" onClick={() => setDialog("reroute")}>
              <Route /> Reroute
            </Button>
          )}
          {a.statusTargets.length > 0 && <Button onClick={() => setDialog("status")}>Update status</Button>}
          {a.canReopen && (
            <Button variant="outline" onClick={() => setDialog("reopen")}>
              <RotateCcw /> Reopen
            </Button>
          )}
          {a.canConfirmClose && (
            <Button onClick={() => setDialog("close")}>
              <CheckCircle2 /> Confirm resolution
            </Button>
          )}
        </div>
      </div>

      {c.status === "NEEDS_INFORMATION" && !staff && (
        <div className="mb-6 rounded-md border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900" role="status">
          The department needs more information. Read the latest remark in the timeline and reply below — your reply resumes processing.
        </div>
      )}
      {c.status === "RESOLVED" && !staff && (
        <div className="mb-6 rounded-md border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-900" role="status">
          The department marked this complaint as resolved. Confirm if the issue is fixed{a.canReopen ? ", or reopen it with a reason if it is not" : ""}.
        </div>
      )}

      <div className="grid gap-6 lg:grid-cols-[1fr_340px]">
        <div className="min-w-0 space-y-6">
          <Card>
            <CardHeader>
              <CardTitle>Description</CardTitle>
            </CardHeader>
            <CardContent>
              <p className="whitespace-pre-wrap text-sm leading-relaxed">{c.description}</p>
              {(c.locationAddress || c.locationWard || c.locationCity) && (
                <p className="mt-4 flex items-start gap-2 text-sm text-muted-foreground">
                  <MapPin className="mt-0.5 h-4 w-4 shrink-0" />
                  {[c.locationAddress, c.locationWard, c.locationCity, c.locationPincode].filter(Boolean).join(", ")}
                </p>
              )}
            </CardContent>
          </Card>

          {c.resolutionNotes && (
            <Card className="border-emerald-200">
              <CardHeader>
                <CardTitle>Resolution</CardTitle>
              </CardHeader>
              <CardContent>
                <p className="whitespace-pre-wrap text-sm leading-relaxed">{c.resolutionNotes}</p>
                {c.resolvedAt && <p className="mt-2 text-xs text-muted-foreground">Resolved {formatDateTime(c.resolvedAt)}</p>}
              </CardContent>
            </Card>
          )}

          <Attachments complaint={c} onUploaded={() => detail.reload()} />

          <Card>
            <CardHeader>
              <CardTitle>Conversation</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              {comments.loading && !comments.data ? (
                <LoadingRows rows={3} />
              ) : comments.error ? (
                <ErrorState message={comments.error} onRetry={comments.reload} />
              ) : comments.data && comments.data.length > 0 ? (
                <ul className="space-y-3">
                  {comments.data.map((m) => (
                    <li
                      key={m.id}
                      className={cn(
                        "rounded-md border px-3 py-2.5",
                        m.visibility === "INTERNAL" ? "border-amber-200 bg-amber-50/60" : m.mine ? "bg-accent/40" : "bg-surface",
                      )}
                    >
                      <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                        <span className="font-medium text-navy">{m.authorName}</span>
                        <span>{humanize(m.authorRole)}</span>
                        {m.visibility === "INTERNAL" && (
                          <Badge tone="amber">
                            <Lock className="h-3 w-3" /> Internal note
                          </Badge>
                        )}
                        <span className="ml-auto tabular">{formatDateTime(m.createdAt)}</span>
                      </div>
                      <p className="mt-1.5 whitespace-pre-wrap text-sm">{m.body}</p>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="text-sm text-muted-foreground">No messages yet.</p>
              )}
              {a.canComment && <CommentForm complaint={c} onPosted={() => refreshAll()} />}
            </CardContent>
          </Card>
        </div>

        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle>Details</CardTitle>
            </CardHeader>
            <CardContent>
              <dl>
                <KeyValue label="Department">{c.departmentName}</KeyValue>
                <KeyValue label="Category">{c.categoryName}</KeyValue>
                {!staff && <KeyValue label="Priority">{PRIORITY_LABEL[c.priority]}</KeyValue>}
                <KeyValue label="Officer">{c.assignedOfficer?.name ?? <span className="text-muted-foreground">Not yet assigned</span>}</KeyValue>
                <KeyValue label="Filed">{formatDateTime(c.createdAt)}</KeyValue>
                {c.assignedAt && <KeyValue label="Assigned">{formatDateTime(c.assignedAt)}</KeyValue>}
                <KeyValue label="Target">
                  <span className={cn(c.overdue && "text-red-700")}>
                    {formatDateTime(c.dueAt)}
                    {!isFinal && <span className="block text-xs text-muted-foreground">{relativeTime(c.dueAt)}</span>}
                  </span>
                </KeyValue>
                {c.closedAt && <KeyValue label="Closed">{formatDateTime(c.closedAt)}</KeyValue>}
                {c.reopenCount > 0 && <KeyValue label="Reopened">{c.reopenCount}×</KeyValue>}
              </dl>
            </CardContent>
          </Card>

          {staff && (
            <Card>
              <CardHeader>
                <CardTitle>Complainant</CardTitle>
              </CardHeader>
              <CardContent>
                <dl>
                  <KeyValue label="Name">{c.citizen?.name}</KeyValue>
                  <KeyValue label="Email">{c.citizenEmail}</KeyValue>
                  <KeyValue label="Phone">{c.citizenPhone}</KeyValue>
                </dl>
              </CardContent>
            </Card>
          )}

          <Card>
            <CardHeader>
              <CardTitle>Timeline</CardTitle>
            </CardHeader>
            <CardContent>
              {history.loading && !history.data ? (
                <LoadingRows rows={4} />
              ) : history.error ? (
                <ErrorState message={history.error} onRetry={history.reload} />
              ) : (
                <Timeline items={history.data ?? []} />
              )}
            </CardContent>
          </Card>
        </div>
      </div>

      <StatusDialog open={dialog === "status"} onOpenChange={(o) => setDialog(o ? "status" : null)} complaint={c} onDone={refreshAll} />
      {a.canAssign && <AssignDialog open={dialog === "assign"} onOpenChange={(o) => setDialog(o ? "assign" : null)} complaint={c} onDone={refreshAll} />}
      <PriorityDialog open={dialog === "priority"} onOpenChange={(o) => setDialog(o ? "priority" : null)} complaint={c} onDone={refreshAll} />
      {a.canReroute && <RerouteDialog open={dialog === "reroute"} onOpenChange={(o) => setDialog(o ? "reroute" : null)} complaint={c} onDone={refreshAll} />}
      <ReasonDialog
        open={dialog === "reopen"}
        onOpenChange={(o) => setDialog(o ? "reopen" : null)}
        title="Reopen complaint"
        description="Explain why the issue is not resolved. The assigned officer will be notified."
        label="Reason"
        minLength={10}
        submitLabel="Reopen"
        onSubmit={async (reason) => {
          const { data } = await api.post<ComplaintDetail>(`/complaints/${c.id}/reopen`, { reason });
          toast.success("Complaint reopened");
          refreshAll(data);
        }}
      />
      <ReasonDialog
        open={dialog === "close"}
        onOpenChange={(o) => setDialog(o ? "close" : null)}
        title="Confirm resolution"
        description="Closing confirms the issue is fixed. A closed complaint cannot be reopened."
        label="Feedback"
        optional
        submitLabel="Confirm and close"
        onSubmit={async (feedback) => {
          const { data } = await api.post<ComplaintDetail>(`/complaints/${c.id}/close`, { feedback: feedback || undefined });
          toast.success("Thank you. The complaint is now closed.");
          refreshAll(data);
        }}
      />
    </>
  );
}

function Timeline({ items }: { items: HistoryEntry[] }) {
  if (items.length === 0) return <p className="text-sm text-muted-foreground">No history.</p>;
  return (
    <ol>
      {items.map((h, i) => {
        const changed = h.fromStatus !== h.toStatus;
        return (
          <li key={h.id} className="grid grid-cols-[16px_1fr] gap-3 pb-4 last:pb-0">
            <span className="relative flex justify-center">
              <span className={cn("mt-1.5 h-2 w-2 rounded-full", changed ? "bg-primary" : "border border-muted-foreground bg-surface")} />
              {i < items.length - 1 && <span className="absolute top-4 h-full w-px bg-border" />}
            </span>
            <div className="min-w-0">
              <div className="text-sm font-medium text-navy">{changed ? STATUS_LABEL[h.toStatus] : "Update"}</div>
              {h.remark && <p className="mt-0.5 whitespace-pre-wrap text-sm text-foreground/90">{h.remark}</p>}
              <div className="mt-0.5 text-xs text-muted-foreground">
                {h.actorName} {h.actorRole !== "SYSTEM" && `· ${humanize(h.actorRole)}`} · <span className="tabular">{formatDateTime(h.createdAt)}</span>
              </div>
            </div>
          </li>
        );
      })}
    </ol>
  );
}

function Attachments({ complaint, onUploaded }: { complaint: ComplaintDetail; onUploaded: () => void }) {
  const input = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);

  const download = async (attId: number, name: string) => {
    try {
      const r = await api.get(`/complaints/${complaint.id}/attachments/${attId}`, { responseType: "blob" });
      const url = URL.createObjectURL(r.data as Blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = name;
      link.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (e) {
      toast.error(errorMessage(e));
    }
  };

  const upload = async (files: FileList | null) => {
    if (!files?.length) return;
    setBusy(true);
    for (const f of Array.from(files)) {
      const err = validateFile(f);
      if (err) {
        toast.error(err);
        continue;
      }
      const fd = new FormData();
      fd.append("file", f);
      try {
        await api.post(`/complaints/${complaint.id}/attachments`, fd);
        toast.success(`Uploaded ${f.name}`);
      } catch (e) {
        toast.error(errorMessage(e));
      }
    }
    setBusy(false);
    if (input.current) input.current.value = "";
    onUploaded();
  };

  const canAdd = complaint.actions.canUpload && complaint.attachments.length < MAX_FILES;
  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between">
        <CardTitle>Attachments</CardTitle>
        {canAdd && (
          <>
            <input
              ref={input}
              type="file"
              multiple
              className="sr-only"
              accept="image/jpeg,image/png,image/webp,application/pdf"
              onChange={(e) => upload(e.target.files)}
              aria-label="Upload attachment"
            />
            <Button variant="outline" size="sm" onClick={() => input.current?.click()} loading={busy}>
              <Upload /> Add
            </Button>
          </>
        )}
      </CardHeader>
      <CardContent>
        {complaint.attachments.length === 0 ? (
          <p className="text-sm text-muted-foreground">No files attached.</p>
        ) : (
          <ul className="divide-y rounded-md border">
            {complaint.attachments.map((f) => (
              <li key={f.id} className="flex items-center justify-between gap-3 px-3 py-2 text-sm">
                <span className="flex min-w-0 items-center gap-2">
                  <FileText className="h-4 w-4 shrink-0 text-muted-foreground" />
                  <span className="truncate">{f.originalName}</span>
                  <span className="shrink-0 text-xs text-muted-foreground">
                    {formatBytes(f.sizeBytes)} · {f.uploadedBy}
                  </span>
                </span>
                <Button variant="ghost" size="sm" onClick={() => download(f.id, f.originalName)} aria-label={`Download ${f.originalName}`}>
                  <Download />
                </Button>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}

function CommentForm({ complaint, onPosted }: { complaint: ComplaintDetail; onPosted: () => void }) {
  const [body, setBody] = useState("");
  const [internal, setInternal] = useState(false);
  const [busy, setBusy] = useState(false);
  const staff = complaint.actions.canPostInternal;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!body.trim()) return;
    setBusy(true);
    try {
      await api.post(`/complaints/${complaint.id}/comments`, { body: body.trim(), visibility: internal ? "INTERNAL" : "PUBLIC" });
      setBody("");
      toast.success(internal ? "Internal note added" : "Message sent");
      onPosted();
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <form onSubmit={submit} className="space-y-2 border-t pt-4">
      <Label htmlFor="comment">{staff ? (internal ? "Internal note (staff only)" : "Remark to citizen") : "Reply or follow-up"}</Label>
      <Textarea id="comment" rows={3} maxLength={3000} value={body} onChange={(e) => setBody(e.target.value)} className={cn(internal && "border-amber-300 bg-amber-50/40")} />
      <div className="flex flex-wrap items-center justify-between gap-2">
        {staff ? (
          <label className="flex items-center gap-2 text-sm text-muted-foreground">
            <input type="checkbox" checked={internal} onChange={(e) => setInternal(e.target.checked)} className="h-4 w-4 accent-[hsl(var(--primary))]" />
            Internal note — not visible to the citizen
          </label>
        ) : (
          <span className="text-xs text-muted-foreground">Visible to the officials handling this complaint.</span>
        )}
        <Button type="submit" size="sm" loading={busy} disabled={!body.trim()}>
          {internal ? <MessageSquare /> : <Send />} {internal ? "Add note" : "Send"}
        </Button>
      </div>
    </form>
  );
}

function ClaimButton({ complaint, onDone }: { complaint: ComplaintDetail; onDone: (c: ComplaintDetail) => void }) {
  const { user } = useAuth();
  const [busy, setBusy] = useState(false);
  return (
    <Button
      variant="outline"
      loading={busy}
      onClick={async () => {
        setBusy(true);
        try {
          const { data } = await api.post<ComplaintDetail>(`/complaints/${complaint.id}/assignment`, { officerId: user!.id });
          toast.success("Assigned to you");
          onDone(data);
        } catch (e) {
          toast.error(errorMessage(e));
        } finally {
          setBusy(false);
        }
      }}
    >
      <Hand /> Assign to me
    </Button>
  );
}

interface DialogProps {
  open: boolean;
  onOpenChange: (o: boolean) => void;
  complaint: ComplaintDetail;
  onDone: (c: ComplaintDetail) => void;
}

function StatusDialog({ open, onOpenChange, complaint, onDone }: DialogProps) {
  const targets = complaint.actions.statusTargets;
  const [status, setStatus] = useState<ComplaintStatus | "">("");
  const [remark, setRemark] = useState("");
  const [notes, setNotes] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const needsRemark = status === "REJECTED" || status === "NEEDS_INFORMATION";

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!status) return setError("Choose a new status");
    if (needsRemark && !remark.trim()) return setError("A remark is required for this status");
    if (status === "RESOLVED" && notes.trim().length < 10) return setError("Describe the resolution (at least 10 characters)");
    setBusy(true);
    setError(null);
    try {
      const { data } = await api.patch<ComplaintDetail>(`/complaints/${complaint.id}/status`, {
        status,
        remark: remark.trim() || undefined,
        resolutionNotes: status === "RESOLVED" ? notes.trim() : undefined,
      });
      toast.success(`Status changed to ${STATUS_LABEL[status]}`);
      onOpenChange(false);
      setStatus("");
      setRemark("");
      setNotes("");
      onDone(data);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent title="Update status" description={`Currently ${STATUS_LABEL[complaint.status]}. Remarks are visible to the citizen.`}>
        <form onSubmit={submit} className="space-y-4">
          <Field label="New status" htmlFor="new-status">
            <Select id="new-status" value={status} onChange={(e) => setStatus(e.target.value as ComplaintStatus)}>
              <option value="">Select…</option>
              {targets.map((t) => (
                <option key={t} value={t}>
                  {STATUS_LABEL[t]}
                </option>
              ))}
            </Select>
          </Field>
          {status === "RESOLVED" && (
            <Field label="Resolution details" htmlFor="res-notes" hint="What was done to fix the issue.">
              <Textarea id="res-notes" rows={4} maxLength={3000} value={notes} onChange={(e) => setNotes(e.target.value)} />
            </Field>
          )}
          <Field
            label={status === "NEEDS_INFORMATION" ? "What information is needed?" : "Remark to citizen"}
            htmlFor="remark"
            optional={!needsRemark}
          >
            <Textarea id="remark" rows={3} maxLength={2000} value={remark} onChange={(e) => setRemark(e.target.value)} />
          </Field>
          {error && <p className="text-sm text-destructive" role="alert">{error}</p>}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" loading={busy}>
              Save
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function AssignDialog({ open, onOpenChange, complaint, onDone }: DialogProps) {
  const officers = useApi(
    () => (open ? api.get<OfficerOption[]>(`/admin/users/officers?departmentId=${complaint.departmentId}`).then((r) => r.data) : Promise.resolve([])),
    [open, complaint.departmentId],
  );
  const [officerId, setOfficerId] = useState("");
  const [remark, setRemark] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!officerId) return setError("Choose an officer");
    setBusy(true);
    setError(null);
    try {
      const { data } = await api.post<ComplaintDetail>(`/complaints/${complaint.id}/assignment`, {
        officerId: Number(officerId),
        remark: remark.trim() || undefined,
      });
      toast.success("Assignment saved");
      onOpenChange(false);
      setOfficerId("");
      setRemark("");
      onDone(data);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  const options = (officers.data ?? []).filter((o) => o.id !== complaint.assignedOfficer?.id);
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent title={complaint.assignedOfficer ? "Reassign complaint" : "Assign complaint"} description={`Officers in ${complaint.departmentName}.`}>
        <form onSubmit={submit} className="space-y-4">
          <Field label="Officer" htmlFor="officer">
            <Select id="officer" value={officerId} onChange={(e) => setOfficerId(e.target.value)} disabled={officers.loading}>
              <option value="">{officers.loading ? "Loading…" : options.length ? "Select an officer" : "No other active officers in this department"}</option>
              {options.map((o) => (
                <option key={o.id} value={o.id}>
                  {o.fullName}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Note" htmlFor="assign-remark" optional>
            <Input id="assign-remark" maxLength={500} value={remark} onChange={(e) => setRemark(e.target.value)} />
          </Field>
          {error && <p className="text-sm text-destructive" role="alert">{error}</p>}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" loading={busy}>
              Assign
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function PriorityDialog({ open, onOpenChange, complaint, onDone }: DialogProps) {
  const [priority, setPriority] = useState<Priority>(complaint.priority);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    try {
      const { data } = await api.patch<ComplaintDetail>(`/complaints/${complaint.id}/priority`, { priority, reason: reason.trim() || undefined });
      toast.success("Priority updated; resolution target recalculated");
      onOpenChange(false);
      onDone(data);
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent title="Change priority" description="Higher priorities shorten the resolution target.">
        <form onSubmit={submit} className="space-y-4">
          <Field label="Priority" htmlFor="priority">
            <Select id="priority" value={priority} onChange={(e) => setPriority(e.target.value as Priority)}>
              {ALL_PRIORITIES.map((p) => (
                <option key={p} value={p}>
                  {PRIORITY_LABEL[p]}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Reason" htmlFor="prio-reason" optional>
            <Input id="prio-reason" maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} />
          </Field>
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" loading={busy} disabled={priority === complaint.priority}>
              Save
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function RerouteDialog({ open, onOpenChange, complaint, onDone }: DialogProps) {
  const cats = useApi(() => (open ? api.get<Category[]>("/categories").then((r) => r.data) : Promise.resolve([])), [open]);
  const [categoryId, setCategoryId] = useState("");
  const [remark, setRemark] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!categoryId || !remark.trim()) return setError("Choose a category and give a reason");
    setBusy(true);
    setError(null);
    try {
      const { data } = await api.post<ComplaintDetail>(`/complaints/${complaint.id}/reroute`, { categoryId: Number(categoryId), remark: remark.trim() });
      toast.success("Complaint rerouted");
      onOpenChange(false);
      onDone(data);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent title="Reroute complaint" description="Moving to another department removes the current officer assignment.">
        <form onSubmit={submit} className="space-y-4">
          <Field label="New category" htmlFor="reroute-cat">
            <Select id="reroute-cat" value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
              <option value="">Select…</option>
              {(cats.data ?? [])
                .filter((c) => c.id !== complaint.categoryId)
                .map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.departmentName} — {c.name}
                  </option>
                ))}
            </Select>
          </Field>
          <Field label="Reason" htmlFor="reroute-remark">
            <Textarea id="reroute-remark" rows={3} maxLength={2000} value={remark} onChange={(e) => setRemark(e.target.value)} />
          </Field>
          {error && <p className="text-sm text-destructive" role="alert">{error}</p>}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" loading={busy}>
              Reroute
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function ReasonDialog({
  open,
  onOpenChange,
  title,
  description,
  label,
  submitLabel,
  minLength = 0,
  optional,
  onSubmit,
}: {
  open: boolean;
  onOpenChange: (o: boolean) => void;
  title: string;
  description: string;
  label: string;
  submitLabel: string;
  minLength?: number;
  optional?: boolean;
  onSubmit: (text: string) => Promise<void>;
}) {
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (text.trim().length < minLength) return setError(`Please write at least ${minLength} characters`);
    setBusy(true);
    setError(null);
    try {
      await onSubmit(text.trim());
      setText("");
      onOpenChange(false);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent title={title} description={description}>
        <form onSubmit={submit} className="space-y-4">
          <Field label={label} htmlFor="reason-text" optional={optional}>
            <Textarea id="reason-text" rows={4} maxLength={2000} value={text} onChange={(e) => setText(e.target.value)} />
          </Field>
          {error && <p className="text-sm text-destructive" role="alert">{error}</p>}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" loading={busy}>
              {submitLabel}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
