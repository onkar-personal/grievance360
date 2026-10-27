import { useState } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input, Select } from "@/components/ui/input";
import { Label } from "@/components/ui/field";
import { ErrorState, LoadingRows, PageHeader } from "@/components/common";
import { api, errorMessage } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { useApi } from "@/lib/useApi";
import type { Setting } from "@/lib/types";

const LABELS: Record<string, { label: string; unit?: string }> = {
  "escalation.enabled": { label: "Automatic escalation" },
  "escalation.repeat_hours": { label: "Hours between escalation levels", unit: "hours" },
  "escalation.max_level": { label: "Maximum escalation level" },
  "sla.critical_factor_percent": { label: "Critical priority target", unit: "% of category target" },
  "sla.high_factor_percent": { label: "High priority target", unit: "% of category target" },
};

export default function AdminSettings() {
  const settings = useApi(() => api.get<Setting[]>("/admin/settings").then((r) => r.data), []);
  return (
    <>
      <PageHeader title="Escalation rules" description="How overdue complaints are escalated and how priority shortens resolution targets." />
      <Card>
        <CardHeader>
          <CardTitle>Rules</CardTitle>
          <CardDescription>
            A complaint is overdue when it is still open after its target time. The scheduler checks every 15 minutes, raises the escalation level and
            notifies administrators and the assigned officer.
          </CardDescription>
        </CardHeader>
        {settings.loading && !settings.data ? (
          <LoadingRows rows={5} />
        ) : settings.error ? (
          <ErrorState message={settings.error} onRetry={settings.reload} />
        ) : (
          <CardContent className="divide-y p-0">
            {(settings.data ?? []).map((s) => (
              <SettingRow key={s.key} setting={s} onSaved={(n) => settings.setData((d) => d?.map((x) => (x.key === n.key ? n : x)) ?? d)} />
            ))}
          </CardContent>
        )}
      </Card>
    </>
  );
}

function SettingRow({ setting, onSaved }: { setting: Setting; onSaved: (s: Setting) => void }) {
  const [value, setValue] = useState(setting.value);
  const [busy, setBusy] = useState(false);
  const meta = LABELS[setting.key] ?? { label: setting.key };
  const id = `setting-${setting.key}`;
  const save = async () => {
    setBusy(true);
    try {
      const { data } = await api.put<Setting>(`/admin/settings/${encodeURIComponent(setting.key)}`, { value });
      onSaved(data);
      toast.success("Setting saved");
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };
  return (
    <div className="grid gap-3 px-5 py-4 md:grid-cols-[1fr_260px_auto] md:items-center">
      <div>
        <Label htmlFor={id}>{meta.label}</Label>
        <p className="mt-0.5 text-xs text-muted-foreground">{setting.description}</p>
        <p className="mt-0.5 text-xs text-muted-foreground">Last changed {formatDateTime(setting.updatedAt)}</p>
      </div>
      <div className="flex items-center gap-2">
        {setting.key === "escalation.enabled" ? (
          <Select id={id} value={value} onChange={(e) => setValue(e.target.value)}>
            <option value="true">Enabled</option>
            <option value="false">Disabled</option>
          </Select>
        ) : (
          <Input id={id} type="number" value={value} onChange={(e) => setValue(e.target.value)} className="w-28" />
        )}
        {meta.unit && <span className="text-xs text-muted-foreground">{meta.unit}</span>}
      </div>
      <Button size="sm" variant="outline" onClick={save} loading={busy} disabled={value === setting.value}>
        Save
      </Button>
    </div>
  );
}
