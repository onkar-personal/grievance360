import { useEffect, useState, type FormEvent } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Search } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/field";
import { ErrorState, StatusBadge } from "@/components/common";
import { api, apiError } from "@/lib/api";
import { formatDateTime, STATUS_LABEL } from "@/lib/format";
import type { PublicStatus } from "@/lib/types";

const REF = /^G360-\d{4}-\d{6}$/i;

export function TrackPage() {
  const { reference } = useParams();
  const navigate = useNavigate();
  const [value, setValue] = useState(reference ?? "");
  const [result, setResult] = useState<PublicStatus | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notFound, setNotFound] = useState(false);
  const [loading, setLoading] = useState(false);
  const [inputError, setInputError] = useState<string | null>(null);

  useEffect(() => {
    if (!reference) return;
    setLoading(true);
    setError(null);
    setNotFound(false);
    setResult(null);
    api
      .get<PublicStatus>(`/public/complaints/${encodeURIComponent(reference)}`)
      .then((r) => setResult(r.data))
      .catch((e) => {
        const err = apiError(e);
        if (err.status === 404) setNotFound(true);
        else setError(err.message);
      })
      .finally(() => setLoading(false));
  }, [reference]);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const ref = value.trim().toUpperCase();
    if (!REF.test(ref)) {
      setInputError("Enter a reference like G360-2026-000123");
      return;
    }
    setInputError(null);
    navigate(`/track/${ref}`);
  };

  return (
    <div className="container max-w-2xl py-12">
      <h1 className="text-xl font-semibold">Track a complaint</h1>
      <p className="mt-1 text-sm text-muted-foreground">
        Enter the reference number you received. Only the status is shown here — no personal details.
      </p>
      <form onSubmit={submit} className="mt-6 flex flex-col gap-2 sm:flex-row sm:items-start" noValidate>
        <div className="flex-1 space-y-1.5">
          <Label htmlFor="ref" className="sr-only">
            Reference number
          </Label>
          <Input
            id="ref"
            placeholder="G360-2026-000123"
            value={value}
            onChange={(e) => setValue(e.target.value)}
            aria-invalid={inputError ? true : undefined}
            aria-describedby={inputError ? "ref-error" : undefined}
            className="font-mono uppercase"
          />
          {inputError && (
            <p id="ref-error" className="text-xs text-destructive" role="alert">
              {inputError}
            </p>
          )}
        </div>
        <Button type="submit" loading={loading}>
          <Search /> Look up
        </Button>
      </form>

      <div className="mt-8" aria-live="polite">
        {notFound && (
          <div className="rounded-lg border bg-surface px-5 py-6 text-sm">
            <p className="font-medium text-navy">No complaint found for {reference}</p>
            <p className="mt-1 text-muted-foreground">Check the reference for typing errors. References are case-insensitive.</p>
          </div>
        )}
        {error && (
          <div className="rounded-lg border bg-surface">
            <ErrorState message={error} />
          </div>
        )}
        {result && (
          <div className="rounded-lg border bg-surface">
            <div className="flex flex-wrap items-center justify-between gap-3 border-b px-5 py-4">
              <div>
                <div className="font-mono text-sm font-semibold text-navy">{result.reference}</div>
                <div className="text-xs text-muted-foreground">
                  {result.categoryName} · {result.departmentName}
                </div>
              </div>
              <StatusBadge status={result.status} />
            </div>
            <ol className="px-5 py-4">
              {result.timeline.map((s, i) => (
                <li key={i} className="grid grid-cols-[18px_1fr_auto] gap-3 pb-4 last:pb-0">
                  <span className="relative flex justify-center">
                    <span className="mt-1.5 h-2 w-2 rounded-full bg-primary" />
                    {i < result.timeline.length - 1 && <span className="absolute top-4 h-full w-px bg-border" />}
                  </span>
                  <span className="text-sm text-navy">{STATUS_LABEL[s.status]}</span>
                  <span className="text-xs text-muted-foreground tabular">{formatDateTime(s.at)}</span>
                </li>
              ))}
            </ol>
          </div>
        )}
      </div>
    </div>
  );
}
