import { useMemo, useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useNavigate } from "react-router-dom";
import { toast } from "sonner";
import { Paperclip, Trash2, Upload } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input, Select, Textarea } from "@/components/ui/input";
import { Field, fieldA11y } from "@/components/ui/field";
import { ErrorState, PageHeader } from "@/components/common";
import { api, apiError, errorMessage } from "@/lib/api";
import { formatBytes, PRIORITY_LABEL } from "@/lib/format";
import { complaintSchema, MAX_FILES, validateFile, type ComplaintValues } from "@/lib/schemas";
import { useApi } from "@/lib/useApi";
import type { Category, ComplaintDetail } from "@/lib/types";

export default function SubmitComplaint() {
  const navigate = useNavigate();
  const cats = useApi(() => api.get<Category[]>("/categories").then((r) => r.data), []);
  const [files, setFiles] = useState<File[]>([]);
  const [fileError, setFileError] = useState<string | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);

  const {
    register,
    handleSubmit,
    watch,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ComplaintValues>({ resolver: zodResolver(complaintSchema), defaultValues: { categoryId: undefined } });

  const categoryId = Number(watch("categoryId"));
  const selected = cats.data?.find((c) => c.id === categoryId);
  const description = watch("description") ?? "";

  const grouped = useMemo(() => {
    const m = new Map<string, Category[]>();
    (cats.data ?? []).forEach((c) => m.set(c.departmentName, [...(m.get(c.departmentName) ?? []), c]));
    return [...m.entries()];
  }, [cats.data]);

  const addFiles = (list: FileList | null) => {
    if (!list) return;
    const next = [...files];
    for (const f of Array.from(list)) {
      const err = validateFile(f);
      if (err) {
        setFileError(err);
        continue;
      }
      if (next.length >= MAX_FILES) {
        setFileError(`You can attach up to ${MAX_FILES} files`);
        break;
      }
      next.push(f);
      setFileError(null);
    }
    setFiles(next);
    if (fileInput.current) fileInput.current.value = "";
  };

  const onSubmit = async (v: ComplaintValues) => {
    let created: ComplaintDetail;
    try {
      const { data } = await api.post<ComplaintDetail>("/complaints", {
        ...v,
        locationAddress: v.locationAddress || undefined,
        locationWard: v.locationWard || undefined,
        locationCity: v.locationCity || undefined,
        locationPincode: v.locationPincode || undefined,
      });
      created = data;
    } catch (e) {
      const err = apiError(e);
      if (err.fieldErrors?.length) err.fieldErrors.forEach((f) => setError(f.field as keyof ComplaintValues, { message: f.message }));
      else toast.error(err.message);
      return;
    }
    const failed: string[] = [];
    for (const f of files) {
      const fd = new FormData();
      fd.append("file", f);
      try {
        await api.post(`/complaints/${created.id}/attachments`, fd);
      } catch (e) {
        failed.push(`${f.name}: ${errorMessage(e)}`);
      }
    }
    if (failed.length) toast.warning(`Complaint ${created.reference} submitted, but some files failed: ${failed.join("; ")}`);
    else toast.success(`Complaint submitted. Reference ${created.reference}`);
    navigate(`/app/complaints/${created.id}`, { replace: true });
  };

  if (cats.error) return <ErrorState message={cats.error} onRetry={cats.reload} />;

  return (
    <>
      <PageHeader title="Submit a grievance" description="Provide clear details so the department can act quickly. Fields marked optional can be left blank." />
      <form onSubmit={handleSubmit(onSubmit)} noValidate className="grid gap-6 lg:grid-cols-[1fr_300px]">
        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle>Issue</CardTitle>
              <CardDescription>The category determines which department receives the complaint.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <Field label="Category" htmlFor="categoryId" error={errors.categoryId?.message}>
                <Select {...fieldA11y("categoryId", errors.categoryId?.message)} {...register("categoryId")} disabled={cats.loading} defaultValue="">
                  <option value="" disabled>
                    {cats.loading ? "Loading categories…" : "Select a category"}
                  </option>
                  {grouped.map(([dept, list]) => (
                    <optgroup key={dept} label={dept}>
                      {list.map((c) => (
                        <option key={c.id} value={c.id}>
                          {c.name}
                        </option>
                      ))}
                    </optgroup>
                  ))}
                </Select>
              </Field>
              <Field label="Title" htmlFor="title" error={errors.title?.message} hint="A short summary, e.g. “Street light not working near bus stop”.">
                <Input maxLength={160} {...fieldA11y("title", errors.title?.message)} {...register("title")} />
              </Field>
              <Field label="Description" htmlFor="description" error={errors.description?.message} hint={`${description.length}/5000 characters`}>
                <Textarea rows={6} maxLength={5000} {...fieldA11y("description", errors.description?.message)} {...register("description")} />
              </Field>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Location</CardTitle>
            </CardHeader>
            <CardContent className="grid gap-4 sm:grid-cols-2">
              <Field label="Address or landmark" htmlFor="locationAddress" optional className="sm:col-span-2" error={errors.locationAddress?.message}>
                <Input {...fieldA11y("locationAddress", errors.locationAddress?.message)} {...register("locationAddress")} />
              </Field>
              <Field label="Ward / area" htmlFor="locationWard" optional error={errors.locationWard?.message}>
                <Input {...fieldA11y("locationWard")} {...register("locationWard")} />
              </Field>
              <Field label="City" htmlFor="locationCity" optional error={errors.locationCity?.message}>
                <Input {...fieldA11y("locationCity")} {...register("locationCity")} />
              </Field>
              <Field label="PIN code" htmlFor="locationPincode" optional error={errors.locationPincode?.message}>
                <Input inputMode="numeric" maxLength={6} {...fieldA11y("locationPincode", errors.locationPincode?.message)} {...register("locationPincode")} />
              </Field>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Evidence</CardTitle>
              <CardDescription>Up to {MAX_FILES} files. JPEG, PNG, WEBP or PDF, 5 MB each.</CardDescription>
            </CardHeader>
            <CardContent>
              <input
                ref={fileInput}
                id="files"
                type="file"
                multiple
                accept="image/jpeg,image/png,image/webp,application/pdf"
                className="sr-only"
                onChange={(e) => addFiles(e.target.files)}
              />
              <Button type="button" variant="outline" onClick={() => fileInput.current?.click()} disabled={files.length >= MAX_FILES}>
                <Upload /> Add files
              </Button>
              {fileError && (
                <p className="mt-2 text-xs text-destructive" role="alert">
                  {fileError}
                </p>
              )}
              {files.length > 0 && (
                <ul className="mt-3 divide-y rounded-md border">
                  {files.map((f, i) => (
                    <li key={i} className="flex items-center justify-between gap-3 px-3 py-2 text-sm">
                      <span className="flex min-w-0 items-center gap-2">
                        <Paperclip className="h-4 w-4 shrink-0 text-muted-foreground" />
                        <span className="truncate">{f.name}</span>
                        <span className="shrink-0 text-xs text-muted-foreground">{formatBytes(f.size)}</span>
                      </span>
                      <Button type="button" variant="ghost" size="sm" onClick={() => setFiles(files.filter((_, j) => j !== i))} aria-label={`Remove ${f.name}`}>
                        <Trash2 />
                      </Button>
                    </li>
                  ))}
                </ul>
              )}
            </CardContent>
          </Card>
        </div>

        <aside className="space-y-4 lg:sticky lg:top-20 lg:self-start">
          <Card>
            <CardHeader>
              <CardTitle>Routing</CardTitle>
            </CardHeader>
            <CardContent className="space-y-2 text-sm">
              {selected ? (
                <>
                  <div>
                    <div className="text-xs text-muted-foreground">Department</div>
                    <div className="font-medium text-navy">{selected.departmentName}</div>
                  </div>
                  <div>
                    <div className="text-xs text-muted-foreground">Resolution target</div>
                    <div className="font-medium text-navy">
                      {selected.slaHours < 48 ? `${selected.slaHours} hours` : `${Math.round(selected.slaHours / 24)} days`}
                    </div>
                  </div>
                  <div>
                    <div className="text-xs text-muted-foreground">Initial priority</div>
                    <div className="font-medium text-navy">{PRIORITY_LABEL[selected.defaultPriority]}</div>
                  </div>
                </>
              ) : (
                <p className="text-muted-foreground">Select a category to see which department will handle it.</p>
              )}
            </CardContent>
          </Card>
          <Button type="submit" className="w-full" size="lg" loading={isSubmitting}>
            Submit grievance
          </Button>
          <p className="text-xs leading-relaxed text-muted-foreground">
            You will receive a reference number. Officials handling the complaint can see your name and contact details.
          </p>
        </aside>
      </form>
    </>
  );
}
