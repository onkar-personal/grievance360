import { Link } from "react-router-dom";
import { ArrowRight, BellRing, Building2, CheckCircle2, FileText, Search, ShieldCheck, Timer, UserCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useApi } from "@/lib/useApi";
import { api } from "@/lib/api";
import type { Department } from "@/lib/types";

const STEPS = [
  { icon: <FileText />, title: "Submit", text: "Describe the issue, choose a category and attach photos or documents. You receive a reference number immediately." },
  { icon: <Building2 />, title: "Routed", text: "The complaint goes to the responsible department, which acknowledges it and assigns an officer." },
  { icon: <Timer />, title: "Tracked against SLA", text: "Each category has a resolution target. Overdue complaints are escalated automatically." },
  { icon: <CheckCircle2 />, title: "Resolved & confirmed", text: "The officer records the resolution. You confirm it, or reopen it with a reason if the problem remains." },
];

const FEATURES = [
  { icon: <BellRing />, title: "Status updates", text: "In-app notifications whenever your complaint moves forward or the department needs more information." },
  { icon: <UserCheck />, title: "Clear accountability", text: "Every status change records who made it and when, in a timeline you can read at any time." },
  { icon: <ShieldCheck />, title: "Your data stays yours", text: "Only you and the officials handling your complaint can see its details. Public lookup shows status only." },
];

export function Landing() {
  const { data: departments } = useApi(() => api.get<Department[]>("/departments").then((r) => r.data), []);
  return (
    <>
      <section className="border-b bg-surface">
        <div className="container grid gap-10 py-14 lg:grid-cols-[1.2fr_1fr] lg:py-20">
          <div>
            <p className="mb-3 text-xs font-medium uppercase tracking-wider text-primary">Public grievance redressal</p>
            <h1 className="max-w-xl text-3xl font-semibold leading-tight sm:text-4xl">
              Report a civic problem once. Follow it until it is fixed.
            </h1>
            <p className="mt-4 max-w-lg text-base leading-relaxed text-muted-foreground">
              Grievance360 connects residents with the departments responsible for water, roads, sanitation, power and public
              health — with a reference number, a visible timeline and a resolution target for every complaint.
            </p>
            <div className="mt-7 flex flex-wrap gap-3">
              <Button size="lg" asChild>
                <Link to="/register">
                  File a grievance <ArrowRight />
                </Link>
              </Button>
              <Button size="lg" variant="outline" asChild>
                <Link to="/track">
                  <Search /> Track with reference
                </Link>
              </Button>
            </div>
          </div>
          <div className="rounded-lg border bg-background p-5">
            <p className="text-xs font-medium uppercase tracking-wider text-muted-foreground">Example timeline</p>
            <ol className="mt-4 space-y-4">
              {[
                ["Submitted", "Garbage not collected — Ward 7", "09:12"],
                ["Acknowledged", "Sanitation & Solid Waste", "10:40"],
                ["Assigned", "Field officer allocated", "11:05"],
                ["Resolved", "Area cleared, bin replaced", "Next day 16:20"],
              ].map(([s, t, time], i) => (
                <li key={s} className="grid grid-cols-[18px_1fr_auto] gap-3">
                  <span className="relative flex justify-center">
                    <span className={`mt-1 h-2.5 w-2.5 rounded-full ${i === 3 ? "bg-emerald-600" : "bg-primary"}`} />
                    {i < 3 && <span className="absolute top-4 h-[calc(100%+4px)] w-px bg-border" />}
                  </span>
                  <span>
                    <span className="block text-sm font-medium text-navy">{s}</span>
                    <span className="block text-xs text-muted-foreground">{t}</span>
                  </span>
                  <span className="text-xs text-muted-foreground tabular">{time}</span>
                </li>
              ))}
            </ol>
            <p className="mt-5 border-t pt-3 text-xs text-muted-foreground">Illustrative only.</p>
          </div>
        </div>
      </section>

      <section className="container py-14">
        <h2 className="text-xl font-semibold">How it works</h2>
        <ol className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {STEPS.map((s, i) => (
            <li key={s.title} className="rounded-lg border bg-surface p-5">
              <div className="flex items-center gap-2 text-primary [&_svg]:h-4 [&_svg]:w-4">
                {s.icon}
                <span className="text-xs font-medium text-muted-foreground">Step {i + 1}</span>
              </div>
              <h3 className="mt-3 text-sm font-semibold">{s.title}</h3>
              <p className="mt-1.5 text-sm leading-relaxed text-muted-foreground">{s.text}</p>
            </li>
          ))}
        </ol>
      </section>

      <section className="border-y bg-surface">
        <div className="container grid gap-8 py-14 md:grid-cols-3">
          {FEATURES.map((f) => (
            <div key={f.title}>
              <div className="text-primary [&_svg]:h-5 [&_svg]:w-5">{f.icon}</div>
              <h3 className="mt-3 text-sm font-semibold">{f.title}</h3>
              <p className="mt-1.5 text-sm leading-relaxed text-muted-foreground">{f.text}</p>
            </div>
          ))}
        </div>
      </section>

      {departments && departments.length > 0 && (
        <section className="container py-14">
          <h2 className="text-xl font-semibold">Participating departments</h2>
          <ul className="mt-6 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {departments.map((d) => (
              <li key={d.id} className="rounded-lg border bg-surface px-4 py-3">
                <div className="text-sm font-medium text-navy">{d.name}</div>
                {d.description && <div className="mt-0.5 text-xs text-muted-foreground">{d.description}</div>}
              </li>
            ))}
          </ul>
        </section>
      )}
    </>
  );
}
