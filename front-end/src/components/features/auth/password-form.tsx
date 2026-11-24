"use client";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { KeyRound } from "lucide-react";
import { toast } from "sonner";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { passwordSchema, type PasswordFormValues } from "@/schemas/forms";
import { useAction } from "@/hooks/use-api";
import { authApi } from "@/lib/api/endpoints";
export function PasswordForm() {
  const { busy, run } = useAction();
  const form = useForm<PasswordFormValues>({
    resolver: zodResolver(passwordSchema),
    defaultValues: { current: "", next: "", confirm: "" },
  });
  return (
    <section className="panel mt-6">
      <h2 className="text-xl font-semibold mb-3">Đổi mật khẩu</h2>
      <p className="muted text-xs mb-6">
        Mật khẩu mới cần ít nhất 8 ký tự và khác mật khẩu hiện tại.
      </p>
      <form
        onSubmit={form.handleSubmit((v) =>
          run(
            () => authApi.changePassword({ currentPassword: v.current, newPassword: v.next }),
            () => {
              form.reset();
              toast.success("Đã đổi mật khẩu");
            },
          ),
        )}
      >
        {(
          [
            { name: "current", label: "Mật khẩu hiện tại" },
            { name: "next", label: "Mật khẩu mới" },
            { name: "confirm", label: "Nhập lại mật khẩu mới" },
          ] as const
        ).map(({ name, label }) => (
          <div className="field" key={name}>
            <label className="field-label" htmlFor={`password-${name}`}>
              {label}
            </label>
            <Input
              id={`password-${name}`}
              type="password"
              autoComplete={name === "current" ? "current-password" : "new-password"}
              {...form.register(name)}
            />
            {form.formState.errors[name] && (
              <p className="form-error">
                {form.formState.errors[name]?.message}
              </p>
            )}
          </div>
        ))}
        <Button disabled={busy} type="submit">
          <KeyRound size={16} />
          {busy ? "Đang xác nhận..." : "Xác nhận đổi mật khẩu"}
        </Button>
      </form>
    </section>
  );
}
