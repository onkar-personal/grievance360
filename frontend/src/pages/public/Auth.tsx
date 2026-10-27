import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Link, Navigate, useLocation, useNavigate } from "react-router-dom";
import { AlertCircle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field, fieldA11y } from "@/components/ui/field";
import { apiError } from "@/lib/api";
import { homePath, useAuth } from "@/lib/auth";
import { loginSchema, registerSchema, type LoginValues, type RegisterValues } from "@/lib/schemas";

function AuthCard({ title, subtitle, children, footer }: { title: string; subtitle: string; children: React.ReactNode; footer: React.ReactNode }) {
  return (
    <div className="container flex justify-center py-12 sm:py-16">
      <div className="w-full max-w-sm">
        <h1 className="text-xl font-semibold">{title}</h1>
        <p className="mt-1 text-sm text-muted-foreground">{subtitle}</p>
        <div className="mt-6 rounded-lg border bg-surface p-5">{children}</div>
        <p className="mt-4 text-center text-sm text-muted-foreground">{footer}</p>
      </div>
    </div>
  );
}

function FormError({ message }: { message: string | null }) {
  if (!message) return null;
  return (
    <div role="alert" className="mb-4 flex gap-2 rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">
      <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
      {message}
    </div>
  );
}

export function LoginPage() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [error, setError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginValues>({ resolver: zodResolver(loginSchema) });

  if (user) return <Navigate to={homePath(user.role)} replace />;

  const onSubmit = async (v: LoginValues) => {
    setError(null);
    try {
      const u = await login(v.email, v.password);
      const from = (location.state as { from?: string } | null)?.from;
      navigate(from && from.startsWith("/app") ? from : homePath(u.role), { replace: true });
    } catch (e) {
      setError(apiError(e).message);
    }
  };

  return (
    <AuthCard
      title="Sign in"
      subtitle="Citizens, officers and administrators use the same sign-in."
      footer={
        <>
          New here?{" "}
          <Link to="/register" className="font-medium text-primary hover:underline">
            Create a citizen account
          </Link>
        </>
      }
    >
      <FormError message={error} />
      <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
        <Field label="Email" htmlFor="email" error={errors.email?.message}>
          <Input type="email" autoComplete="email" {...fieldA11y("email", errors.email?.message)} {...register("email")} />
        </Field>
        <Field label="Password" htmlFor="password" error={errors.password?.message}>
          <Input type="password" autoComplete="current-password" {...fieldA11y("password", errors.password?.message)} {...register("password")} />
        </Field>
        <Button type="submit" className="w-full" loading={isSubmitting}>
          Sign in
        </Button>
      </form>
    </AuthCard>
  );
}

export function RegisterPage() {
  const { user, register: registerUser } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<RegisterValues>({ resolver: zodResolver(registerSchema) });

  if (user) return <Navigate to={homePath(user.role)} replace />;

  const onSubmit = async (v: RegisterValues) => {
    setError(null);
    try {
      await registerUser({ fullName: v.fullName, email: v.email, phone: v.phone || undefined, password: v.password });
      navigate("/app/citizen", { replace: true });
    } catch (e) {
      const err = apiError(e);
      if (err.code === "EMAIL_IN_USE") setFieldError("email", { message: err.message });
      else if (err.fieldErrors?.length) {
        err.fieldErrors.forEach((f) => setFieldError(f.field as keyof RegisterValues, { message: f.message }));
      } else setError(err.message);
    }
  };

  return (
    <AuthCard
      title="Create your account"
      subtitle="Register as a citizen to file and track grievances."
      footer={
        <>
          Already registered?{" "}
          <Link to="/login" className="font-medium text-primary hover:underline">
            Sign in
          </Link>
        </>
      }
    >
      <FormError message={error} />
      <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
        <Field label="Full name" htmlFor="fullName" error={errors.fullName?.message}>
          <Input autoComplete="name" {...fieldA11y("fullName", errors.fullName?.message)} {...register("fullName")} />
        </Field>
        <Field label="Email" htmlFor="email" error={errors.email?.message}>
          <Input type="email" autoComplete="email" {...fieldA11y("email", errors.email?.message)} {...register("email")} />
        </Field>
        <Field label="Mobile number" htmlFor="phone" optional error={errors.phone?.message}>
          <Input type="tel" autoComplete="tel" {...fieldA11y("phone", errors.phone?.message)} {...register("phone")} />
        </Field>
        <Field label="Password" htmlFor="password" error={errors.password?.message} hint="At least 8 characters with a letter and a number.">
          <Input type="password" autoComplete="new-password" {...fieldA11y("password", errors.password?.message)} {...register("password")} />
        </Field>
        <Field label="Confirm password" htmlFor="confirmPassword" error={errors.confirmPassword?.message}>
          <Input type="password" autoComplete="new-password" {...fieldA11y("confirmPassword", errors.confirmPassword?.message)} {...register("confirmPassword")} />
        </Field>
        <Button type="submit" className="w-full" loading={isSubmitting}>
          Create account
        </Button>
      </form>
    </AuthCard>
  );
}
