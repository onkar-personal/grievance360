import { useEffect, useState, type FormEvent } from "react";
import { toast } from "sonner";
import { KeyRound, Pencil, Plus, Search } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Dialog, DialogContent } from "@/components/ui/dialog";
import { Input, Select } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Badge } from "@/components/ui/badge";
import { Table, TBody, TD, TH, THead, TR } from "@/components/ui/table";
import { EmptyState, ErrorState, LoadingRows, PageHeader, Pagination } from "@/components/common";
import { api, errorMessage, toParams } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { formatDate, humanize } from "@/lib/format";
import { passwordSchema } from "@/lib/schemas";
import { useApi } from "@/lib/useApi";
import type { Department, Page, Role, User } from "@/lib/types";

const ROLES: Role[] = ["CITIZEN", "OFFICER", "ADMIN"];

export default function AdminUsers() {
  const { user: me } = useAuth();
  const [q, setQ] = useState("");
  const [query, setQuery] = useState("");
  const [role, setRole] = useState("");
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<User | "new" | null>(null);
  const [resetting, setResetting] = useState<User | null>(null);

  useEffect(() => {
    const t = setTimeout(() => {
      setQuery(q.trim());
      setPage(0);
    }, 350);
    return () => clearTimeout(t);
  }, [q]);

  const departments = useApi(() => api.get<Department[]>("/admin/departments").then((r) => r.data), []);
  const users = useApi(
    () => api.get<Page<User>>(`/admin/users?${toParams({ q: query, role, page, size: 20, sort: "createdAt,desc" })}`).then((r) => r.data),
    [query, role, page],
  );

  return (
    <>
      <PageHeader
        title="Users & roles"
        description="Create officer and administrator accounts, change roles, and deactivate access. All changes are audited."
        actions={
          <Button onClick={() => setEditing("new")}>
            <Plus /> New user
          </Button>
        }
      />
      <Card>
        <div className="flex flex-wrap gap-2 border-b p-3">
          <div className="relative min-w-[220px] flex-1">
            <Search className="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search name or email" className="pl-8" aria-label="Search users" />
          </div>
          <Select aria-label="Role" value={role} onChange={(e) => { setRole(e.target.value); setPage(0); }} className="w-auto min-w-[140px]">
            <option value="">All roles</option>
            {ROLES.map((r) => (
              <option key={r} value={r}>
                {humanize(r)}
              </option>
            ))}
          </Select>
        </div>
        {users.loading && !users.data ? (
          <LoadingRows rows={8} />
        ) : users.error ? (
          <ErrorState message={users.error} onRetry={users.reload} />
        ) : users.data && users.data.content.length ? (
          <>
            <Table>
              <THead>
                <TR>
                  <TH>Name</TH>
                  <TH>Role</TH>
                  <TH className="hidden md:table-cell">Department</TH>
                  <TH>Status</TH>
                  <TH className="hidden lg:table-cell">Joined</TH>
                  <TH className="hidden lg:table-cell">Last sign-in</TH>
                  <TH className="text-right">
                    <span className="sr-only">Actions</span>
                  </TH>
                </TR>
              </THead>
              <TBody>
                {users.data.content.map((u) => (
                  <TR key={u.id}>
                    <TD>
                      <div className="font-medium text-navy">{u.fullName}</div>
                      <div className="text-xs text-muted-foreground">{u.email}</div>
                    </TD>
                    <TD>
                      <Badge tone={u.role === "ADMIN" ? "violet" : u.role === "OFFICER" ? "teal" : "neutral"}>{humanize(u.role)}</Badge>
                    </TD>
                    <TD className="hidden text-muted-foreground md:table-cell">{u.departmentName ?? "—"}</TD>
                    <TD>{u.active ? <Badge tone="green">Active</Badge> : <Badge tone="red">Deactivated</Badge>}</TD>
                    <TD className="hidden text-muted-foreground tabular lg:table-cell">{formatDate(u.createdAt)}</TD>
                    <TD className="hidden text-muted-foreground tabular lg:table-cell">{formatDate(u.lastLoginAt)}</TD>
                    <TD className="whitespace-nowrap text-right">
                      <Button variant="ghost" size="sm" onClick={() => setEditing(u)} aria-label={`Edit ${u.fullName}`}>
                        <Pencil />
                      </Button>
                      <Button variant="ghost" size="sm" onClick={() => setResetting(u)} aria-label={`Set password for ${u.fullName}`}>
                        <KeyRound />
                      </Button>
                    </TD>
                  </TR>
                ))}
              </TBody>
            </Table>
            <Pagination page={users.data.page} size={users.data.size} totalPages={users.data.totalPages} totalElements={users.data.totalElements} onPage={setPage} />
          </>
        ) : (
          <EmptyState title="No users match" />
        )}
      </Card>

      {editing && (
        <UserDialog
          user={editing === "new" ? null : editing}
          isSelf={editing !== "new" && editing.id === me?.id}
          departments={departments.data ?? []}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            void users.reload();
          }}
        />
      )}
      {resetting && <ResetPasswordDialog user={resetting} onClose={() => setResetting(null)} />}
    </>
  );
}

function UserDialog({
  user,
  isSelf,
  departments,
  onClose,
  onSaved,
}: {
  user: User | null;
  isSelf: boolean;
  departments: Department[];
  onClose: () => void;
  onSaved: () => void;
}) {
  const [fullName, setFullName] = useState(user?.fullName ?? "");
  const [email, setEmail] = useState(user?.email ?? "");
  const [phone, setPhone] = useState(user?.phone ?? "");
  const [role, setRole] = useState<Role>(user?.role ?? "OFFICER");
  const [departmentId, setDepartmentId] = useState(user?.departmentId ? String(user.departmentId) : "");
  const [active, setActive] = useState(user?.active ?? true);
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (role === "OFFICER" && !departmentId) return setError("Officers must belong to a department");
    if (!user) {
      const pw = passwordSchema.safeParse(password);
      if (!pw.success) return setError(`Password: ${pw.error.issues[0].message}`);
    }
    setBusy(true);
    setError(null);
    try {
      const body = { fullName, phone: phone || undefined, role, departmentId: departmentId ? Number(departmentId) : undefined, active };
      if (user) await api.put(`/admin/users/${user.id}`, body);
      else await api.post("/admin/users", { ...body, email, password });
      toast.success(user ? "User updated" : "User created");
      onSaved();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent title={user ? "Edit user" : "New user"} description={user ? user.email : "Share the initial password with the user through a secure channel."}>
        <form onSubmit={submit} className="grid gap-4 sm:grid-cols-2">
          <Field label="Full name" htmlFor="u-name" className="sm:col-span-2">
            <Input id="u-name" required value={fullName} onChange={(e) => setFullName(e.target.value)} />
          </Field>
          {!user && (
            <Field label="Email" htmlFor="u-email" className="sm:col-span-2">
              <Input id="u-email" type="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
            </Field>
          )}
          <Field label="Phone" htmlFor="u-phone" optional>
            <Input id="u-phone" value={phone} onChange={(e) => setPhone(e.target.value)} />
          </Field>
          <Field label="Role" htmlFor="u-role">
            <Select id="u-role" value={role} onChange={(e) => setRole(e.target.value as Role)} disabled={isSelf}>
              {ROLES.map((r) => (
                <option key={r} value={r}>
                  {humanize(r)}
                </option>
              ))}
            </Select>
          </Field>
          {role !== "CITIZEN" && (
            <Field label="Department" htmlFor="u-dept" optional={role === "ADMIN"} className="sm:col-span-2">
              <Select id="u-dept" value={departmentId} onChange={(e) => setDepartmentId(e.target.value)}>
                <option value="">{role === "ADMIN" ? "None (all departments)" : "Select a department"}</option>
                {departments.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name}
                    {!d.active ? " (inactive)" : ""}
                  </option>
                ))}
              </Select>
            </Field>
          )}
          {!user && (
            <Field label="Initial password" htmlFor="u-pw" className="sm:col-span-2" hint="At least 8 characters with a letter and a number.">
              <Input id="u-pw" type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} />
            </Field>
          )}
          {user && (
            <label className="flex items-center gap-2 text-sm sm:col-span-2">
              <input type="checkbox" checked={active} disabled={isSelf} onChange={(e) => setActive(e.target.checked)} className="h-4 w-4 accent-[hsl(var(--primary))]" />
              Account active (deactivated users cannot sign in)
            </label>
          )}
          {isSelf && <p className="text-xs text-muted-foreground sm:col-span-2">You cannot change your own role or deactivate yourself.</p>}
          {error && <p className="text-sm text-destructive sm:col-span-2" role="alert">{error}</p>}
          <div className="flex justify-end gap-2 sm:col-span-2">
            <Button type="button" variant="outline" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" loading={busy}>
              {user ? "Save" : "Create user"}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function ResetPasswordDialog({ user, onClose }: { user: User; onClose: () => void }) {
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const pw = passwordSchema.safeParse(password);
    if (!pw.success) return setError(pw.error.issues[0].message);
    setBusy(true);
    try {
      await api.post(`/admin/users/${user.id}/password`, { password });
      toast.success("Password updated");
      onClose();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent title="Set new password" description={`For ${user.fullName}. The user should change it after signing in.`}>
        <form onSubmit={submit} className="space-y-4">
          <Field label="New password" htmlFor="rp" error={error ?? undefined}>
            <Input id="rp" type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} />
          </Field>
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" loading={busy}>
              Set password
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
