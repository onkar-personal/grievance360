import { useState, type FormEvent } from "react";
import { toast } from "sonner";
import { Pencil, Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent } from "@/components/ui/dialog";
import { Input, Select, Textarea } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Badge } from "@/components/ui/badge";
import { Table, TBody, TD, TH, THead, TR } from "@/components/ui/table";
import { EmptyState, ErrorState, LoadingRows, PageHeader, PriorityBadge } from "@/components/common";
import { api, errorMessage } from "@/lib/api";
import { ALL_PRIORITIES, PRIORITY_LABEL } from "@/lib/format";
import { useApi } from "@/lib/useApi";
import type { Category, Department, Priority } from "@/lib/types";

export default function AdminDepartments() {
  const depts = useApi(() => api.get<Department[]>("/admin/departments").then((r) => r.data), []);
  const cats = useApi(() => api.get<Category[]>("/admin/categories").then((r) => r.data), []);
  const [editDept, setEditDept] = useState<Department | "new" | null>(null);
  const [editCat, setEditCat] = useState<Category | "new" | null>(null);

  return (
    <>
      <PageHeader title="Departments & categories" description="Categories route complaints to a department and set the resolution target (SLA)." />

      <Card>
        <CardHeader className="flex-row items-center justify-between">
          <div>
            <CardTitle>Departments</CardTitle>
            <CardDescription>Deactivated departments stop receiving new complaints.</CardDescription>
          </div>
          <Button size="sm" onClick={() => setEditDept("new")}>
            <Plus /> Department
          </Button>
        </CardHeader>
        {depts.loading && !depts.data ? (
          <LoadingRows rows={4} />
        ) : depts.error ? (
          <ErrorState message={depts.error} onRetry={depts.reload} />
        ) : depts.data && depts.data.length ? (
          <Table>
            <THead>
              <TR>
                <TH>Code</TH>
                <TH>Name</TH>
                <TH className="hidden md:table-cell">Contact</TH>
                <TH>Status</TH>
                <TH className="text-right"><span className="sr-only">Actions</span></TH>
              </TR>
            </THead>
            <TBody>
              {depts.data.map((d) => (
                <TR key={d.id}>
                  <TD className="font-mono text-xs">{d.code}</TD>
                  <TD>
                    <div className="font-medium text-navy">{d.name}</div>
                    {d.description && <div className="max-w-md truncate text-xs text-muted-foreground">{d.description}</div>}
                  </TD>
                  <TD className="hidden text-muted-foreground md:table-cell">{d.contactEmail ?? "—"}</TD>
                  <TD>{d.active ? <Badge tone="green">Active</Badge> : <Badge>Inactive</Badge>}</TD>
                  <TD className="text-right">
                    <Button variant="ghost" size="sm" onClick={() => setEditDept(d)} aria-label={`Edit ${d.name}`}>
                      <Pencil />
                    </Button>
                  </TD>
                </TR>
              ))}
            </TBody>
          </Table>
        ) : (
          <EmptyState title="No departments yet" description="Create a department before adding categories." />
        )}
      </Card>

      <Card className="mt-6">
        <CardHeader className="flex-row items-center justify-between">
          <div>
            <CardTitle>Complaint categories</CardTitle>
            <CardDescription>Target hours apply to Medium/Low priority; High and Critical use a shorter share (see Escalation rules).</CardDescription>
          </div>
          <Button size="sm" onClick={() => setEditCat("new")} disabled={!depts.data?.length}>
            <Plus /> Category
          </Button>
        </CardHeader>
        {cats.loading && !cats.data ? (
          <LoadingRows rows={6} />
        ) : cats.error ? (
          <ErrorState message={cats.error} onRetry={cats.reload} />
        ) : cats.data && cats.data.length ? (
          <Table>
            <THead>
              <TR>
                <TH>Category</TH>
                <TH>Department</TH>
                <TH className="text-right">Target</TH>
                <TH>Default priority</TH>
                <TH>Status</TH>
                <TH className="text-right"><span className="sr-only">Actions</span></TH>
              </TR>
            </THead>
            <TBody>
              {cats.data.map((c) => (
                <TR key={c.id}>
                  <TD className="font-medium text-navy">{c.name}</TD>
                  <TD className="text-muted-foreground">{c.departmentName}</TD>
                  <TD className="text-right tabular">{c.slaHours} h</TD>
                  <TD><PriorityBadge priority={c.defaultPriority} /></TD>
                  <TD>{c.active ? <Badge tone="green">Active</Badge> : <Badge>Inactive</Badge>}</TD>
                  <TD className="text-right">
                    <Button variant="ghost" size="sm" onClick={() => setEditCat(c)} aria-label={`Edit ${c.name}`}>
                      <Pencil />
                    </Button>
                  </TD>
                </TR>
              ))}
            </TBody>
          </Table>
        ) : (
          <EmptyState title="No categories yet" />
        )}
      </Card>

      {editDept && (
        <DepartmentDialog
          dept={editDept === "new" ? null : editDept}
          onClose={() => setEditDept(null)}
          onSaved={() => {
            setEditDept(null);
            void depts.reload();
            void cats.reload();
          }}
        />
      )}
      {editCat && (
        <CategoryDialog
          category={editCat === "new" ? null : editCat}
          departments={depts.data ?? []}
          onClose={() => setEditCat(null)}
          onSaved={() => {
            setEditCat(null);
            void cats.reload();
          }}
        />
      )}
    </>
  );
}

function DepartmentDialog({ dept, onClose, onSaved }: { dept: Department | null; onClose: () => void; onSaved: () => void }) {
  const [code, setCode] = useState(dept?.code ?? "");
  const [name, setName] = useState(dept?.name ?? "");
  const [description, setDescription] = useState(dept?.description ?? "");
  const [contactEmail, setContactEmail] = useState(dept?.contactEmail ?? "");
  const [active, setActive] = useState(dept?.active ?? true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const body = { code, name, description: description || undefined, contactEmail: contactEmail || undefined, active };
      if (dept) await api.put(`/admin/departments/${dept.id}`, body);
      else await api.post("/admin/departments", body);
      toast.success("Department saved");
      onSaved();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent title={dept ? "Edit department" : "New department"}>
        <form onSubmit={submit} className="grid gap-4 sm:grid-cols-[120px_1fr]">
          <Field label="Code" htmlFor="d-code">
            <Input id="d-code" required maxLength={20} value={code} onChange={(e) => setCode(e.target.value.toUpperCase())} className="font-mono" />
          </Field>
          <Field label="Name" htmlFor="d-name">
            <Input id="d-name" required maxLength={120} value={name} onChange={(e) => setName(e.target.value)} />
          </Field>
          <Field label="Description" htmlFor="d-desc" optional className="sm:col-span-2">
            <Textarea id="d-desc" rows={2} maxLength={500} value={description} onChange={(e) => setDescription(e.target.value)} />
          </Field>
          <Field label="Contact email" htmlFor="d-email" optional className="sm:col-span-2">
            <Input id="d-email" type="email" value={contactEmail} onChange={(e) => setContactEmail(e.target.value)} />
          </Field>
          <label className="flex items-center gap-2 text-sm sm:col-span-2">
            <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} className="h-4 w-4 accent-[hsl(var(--primary))]" />
            Active
          </label>
          {error && <p className="text-sm text-destructive sm:col-span-2" role="alert">{error}</p>}
          <div className="flex justify-end gap-2 sm:col-span-2">
            <Button type="button" variant="outline" onClick={onClose}>Cancel</Button>
            <Button type="submit" loading={busy}>Save</Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function CategoryDialog({
  category,
  departments,
  onClose,
  onSaved,
}: {
  category: Category | null;
  departments: Department[];
  onClose: () => void;
  onSaved: () => void;
}) {
  const [departmentId, setDepartmentId] = useState(category ? String(category.departmentId) : String(departments[0]?.id ?? ""));
  const [name, setName] = useState(category?.name ?? "");
  const [description, setDescription] = useState(category?.description ?? "");
  const [slaHours, setSlaHours] = useState(String(category?.slaHours ?? 72));
  const [priority, setPriority] = useState<Priority>(category?.defaultPriority ?? "MEDIUM");
  const [active, setActive] = useState(category?.active ?? true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const hours = Number(slaHours);
    if (!Number.isInteger(hours) || hours < 1 || hours > 2160) return setError("Target must be between 1 and 2160 hours");
    setBusy(true);
    setError(null);
    try {
      const body = { departmentId: Number(departmentId), name, description: description || undefined, slaHours: hours, defaultPriority: priority, active };
      if (category) await api.put(`/admin/categories/${category.id}`, body);
      else await api.post("/admin/categories", body);
      toast.success("Category saved");
      onSaved();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent title={category ? "Edit category" : "New category"} description="Changing the target affects new complaints and priority changes, not existing due dates.">
        <form onSubmit={submit} className="grid gap-4 sm:grid-cols-2">
          <Field label="Department" htmlFor="c-dept" className="sm:col-span-2">
            <Select id="c-dept" value={departmentId} onChange={(e) => setDepartmentId(e.target.value)}>
              {departments.map((d) => (
                <option key={d.id} value={d.id}>{d.name}</option>
              ))}
            </Select>
          </Field>
          <Field label="Name" htmlFor="c-name" className="sm:col-span-2">
            <Input id="c-name" required maxLength={120} value={name} onChange={(e) => setName(e.target.value)} />
          </Field>
          <Field label="Resolution target (hours)" htmlFor="c-sla">
            <Input id="c-sla" type="number" min={1} max={2160} value={slaHours} onChange={(e) => setSlaHours(e.target.value)} />
          </Field>
          <Field label="Default priority" htmlFor="c-prio">
            <Select id="c-prio" value={priority} onChange={(e) => setPriority(e.target.value as Priority)}>
              {ALL_PRIORITIES.map((p) => (
                <option key={p} value={p}>{PRIORITY_LABEL[p]}</option>
              ))}
            </Select>
          </Field>
          <Field label="Description" htmlFor="c-desc" optional className="sm:col-span-2">
            <Textarea id="c-desc" rows={2} maxLength={500} value={description} onChange={(e) => setDescription(e.target.value)} />
          </Field>
          <label className="flex items-center gap-2 text-sm sm:col-span-2">
            <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} className="h-4 w-4 accent-[hsl(var(--primary))]" />
            Active (visible to citizens)
          </label>
          {error && <p className="text-sm text-destructive sm:col-span-2" role="alert">{error}</p>}
          <div className="flex justify-end gap-2 sm:col-span-2">
            <Button type="button" variant="outline" onClick={onClose}>Cancel</Button>
            <Button type="submit" loading={busy}>Save</Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
