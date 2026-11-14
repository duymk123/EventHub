"use client";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { KeyRound } from "lucide-react";
import { toast } from "sonner";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { passwordSchema, type PasswordFormValues } from "@/schemas/forms";
import { useAction } from "@/hooks/use-demo";
import { wait } from "@/lib/utils";
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
        Dùng mật khẩu thử nghiệm để kiểm tra form. Bản này chỉ mô phỏng xác
        nhận, không kiểm tra hoặc lưu mật khẩu.
      </p>
      <form
        onSubmit={form.handleSubmit(() =>
          run(
            () => wait(600),
            () => {
              form.reset();
              toast.success("Đã mô phỏng đổi mật khẩu thành công");
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
              autoComplete="off"
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
