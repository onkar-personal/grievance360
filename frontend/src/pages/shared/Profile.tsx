import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input, Textarea } from "@/components/ui/input";
import { Field, fieldA11y } from "@/components/ui/field";
import { KeyValue, PageHeader } from "@/components/common";
import { api, apiError } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { formatDate, humanize } from "@/lib/format";
import { passwordChangeSchema, profileSchema, type PasswordChangeValues, type ProfileValues } from "@/lib/schemas";
import type { User } from "@/lib/types";

export default function ProfilePage() {
  const { user, setUser } = useAuth();
  const profile = useForm<ProfileValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: { fullName: user?.fullName ?? "", phone: user?.phone ?? "", address: user?.address ?? "" },
  });
  const pw = useForm<PasswordChangeValues>({ resolver: zodResolver(passwordChangeSchema) });

  if (!user) return null;

  const saveProfile = async (v: ProfileValues) => {
    try {
      const { data } = await api.put<User>("/users/me", v);
      setUser(data);
      profile.reset({ fullName: data.fullName, phone: data.phone ?? "", address: data.address ?? "" });
      toast.success("Profile updated");
    } catch (e) {
      toast.error(apiError(e).message);
    }
  };

  const changePassword = async (v: PasswordChangeValues) => {
    try {
      await api.post("/users/me/password", { currentPassword: v.currentPassword, newPassword: v.newPassword });
      pw.reset({ currentPassword: "", newPassword: "", confirmPassword: "" });
      toast.success("Password changed");
    } catch (e) {
      const err = apiError(e);
      if (err.code === "WRONG_PASSWORD") pw.setError("currentPassword", { message: err.message });
      else toast.error(err.message);
    }
  };

  const pe = profile.formState.errors;
  const we = pw.formState.errors;

  return (
    <>
      <PageHeader title="Profile & security" />
      <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle>Personal details</CardTitle>
              <CardDescription>Shared with officials handling your complaints.</CardDescription>
            </CardHeader>
            <CardContent>
              <form onSubmit={profile.handleSubmit(saveProfile)} noValidate className="grid gap-4 sm:grid-cols-2">
                <Field label="Full name" htmlFor="p-name" error={pe.fullName?.message}>
                  <Input {...fieldA11y("p-name", pe.fullName?.message)} {...profile.register("fullName")} />
                </Field>
                <Field label="Mobile number" htmlFor="p-phone" optional error={pe.phone?.message}>
                  <Input type="tel" {...fieldA11y("p-phone", pe.phone?.message)} {...profile.register("phone")} />
                </Field>
                <Field label="Address" htmlFor="p-address" optional className="sm:col-span-2" error={pe.address?.message}>
                  <Textarea rows={2} {...fieldA11y("p-address", pe.address?.message)} {...profile.register("address")} />
                </Field>
                <div className="sm:col-span-2">
                  <Button type="submit" loading={profile.formState.isSubmitting} disabled={!profile.formState.isDirty}>
                    Save changes
                  </Button>
                </div>
              </form>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Change password</CardTitle>
            </CardHeader>
            <CardContent>
              <form onSubmit={pw.handleSubmit(changePassword)} noValidate className="grid max-w-md gap-4">
                <Field label="Current password" htmlFor="cur-pw" error={we.currentPassword?.message}>
                  <Input type="password" autoComplete="current-password" {...fieldA11y("cur-pw", we.currentPassword?.message)} {...pw.register("currentPassword")} />
                </Field>
                <Field label="New password" htmlFor="new-pw" error={we.newPassword?.message} hint="At least 8 characters with a letter and a number.">
                  <Input type="password" autoComplete="new-password" {...fieldA11y("new-pw", we.newPassword?.message)} {...pw.register("newPassword")} />
                </Field>
                <Field label="Confirm new password" htmlFor="conf-pw" error={we.confirmPassword?.message}>
                  <Input type="password" autoComplete="new-password" {...fieldA11y("conf-pw", we.confirmPassword?.message)} {...pw.register("confirmPassword")} />
                </Field>
                <div>
                  <Button type="submit" variant="navy" loading={pw.formState.isSubmitting}>
                    Update password
                  </Button>
                </div>
              </form>
            </CardContent>
          </Card>
        </div>

        <Card className="self-start">
          <CardHeader>
            <CardTitle>Account</CardTitle>
          </CardHeader>
          <CardContent>
            <dl>
              <KeyValue label="Email">{user.email}</KeyValue>
              <KeyValue label="Role">{humanize(user.role)}</KeyValue>
              {user.departmentName && <KeyValue label="Department">{user.departmentName}</KeyValue>}
              <KeyValue label="Member since">{formatDate(user.createdAt)}</KeyValue>
            </dl>
          </CardContent>
        </Card>
      </div>
    </>
  );
}
