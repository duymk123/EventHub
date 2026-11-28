"use client";
import { useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { ArrowRight, Eye, EyeOff, ShieldCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { loginSchema, type LoginForm } from "@/schemas/forms";
import { useAction } from "@/hooks/use-api";
import { authApi } from "@/lib/api/endpoints";
import { session } from "@/lib/auth/session";
import { RegisterEmailOtp, normalizeEmail } from "./register-email-otp";
import type { Role } from "@/types";
import { toast } from "sonner";

function homeOf(role: Role) {
  return role === "CUSTOMER" ? "/" : role === "ADMIN" ? "/admin" : "/organizer";
}

/** Chỉ cho quay lại đường dẫn nội bộ (tránh open redirect qua ?next=). */
function safeNext(next: string | null) {
  return next && next.startsWith("/") && !next.startsWith("//") ? next : null;
}
export function AuthPage({
  register = false,
  forgot = false,
}: {
  register?: boolean;
  forgot?: boolean;
}) {
  const [visible, setVisible] = useState(false);
  const { busy, run } = useAction();
  const router = useRouter();
  const next = safeNext(useSearchParams().get("next"));
  const form = useForm<LoginForm>({
    resolver: zodResolver(loginSchema),
    defaultValues: { name: "", email: "", password: forgot ? "--------" : "" },
  });
  // Đăng ký: email phải xác thực bằng OTP trước; token chỉ hợp lệ cho đúng email đã xác thực.
  const [verification, setVerification] = useState<{ email: string; token: string } | null>(null);
  const emailValue = useWatch({ control: form.control, name: "email" });
  const verifiedToken =
    verification && verification.email === normalizeEmail(emailValue ?? "") ? verification.token : null;
  const submit = form.handleSubmit((v) =>
    run(async () => {
      if (forgot) {
        await authApi.forgotPassword(v.email.trim());
        // Backend luôn trả 204 (không tiết lộ email có tồn tại hay không).
        toast.success("Nếu email đã đăng ký, hướng dẫn đặt lại mật khẩu sẽ được gửi tới hộp thư của bạn.");
        router.push("/login");
        return;
      }
      if (register) {
        if (v.password.length < 8) {
          form.setError("password", { message: "Mật khẩu cần ít nhất 8 ký tự" });
          return;
        }
        if (v.name.trim().length < 2) {
          form.setError("name", { message: "Vui lòng nhập họ và tên" });
          return;
        }
        if (!verifiedToken) {
          form.setError("email", { message: "Vui lòng lấy mã và xác thực email trước khi tạo tài khoản" });
          return;
        }
        await authApi.register({
          email: normalizeEmail(v.email),
          password: v.password,
          fullName: v.name.trim(),
          verificationToken: verifiedToken,
        });
        const user = await session.login(normalizeEmail(v.email), v.password);
        toast.success("Tạo tài khoản thành công.");
        router.push(next ?? homeOf(user.role));
      } else {
        const user = await session.login(v.email.trim(), v.password);
        toast.success(`Chào mừng ${user.name}`);
        router.push(next ?? homeOf(user.role));
      }
    }),
  );
  return (
    <div className="auth-layout">
      <div className="auth-visual">
        <Image
          src="/images/concert.jpg"
          alt="Đêm nhạc với ánh đèn sân khấu"
          fill
          sizes="50vw"
        />
        <div />
        <section>
          <h2>
            Mỗi tấm vé.
            <br />
            Một câu chuyện mới.
          </h2>
          <p>Hẹn nhau ở những khoảnh khắc đáng nhớ.</p>
        </section>
      </div>
      <div className="auth-form">
        <p className="eyebrow">CHÀO MỪNG ĐẾN VỚI EVENTHUB</p>
        <h1>
          {forgot
            ? "Tìm lại mật khẩu"
            : register
              ? "Bắt đầu hành trình"
              : "Rất vui được gặp lại bạn."}
        </h1>
        <p className="muted mt-3 mb-8">
          {forgot
            ? "Nhập email đã đăng ký, chúng tôi sẽ gửi đường dẫn đặt lại mật khẩu."
            : register
              ? "Tạo tài khoản để lưu giữ những trải nghiệm của bạn."
              : "Đăng nhập để tiếp tục những cuộc hẹn còn đang chờ."}
        </p>
        <form onSubmit={submit}>
          {register && (
            <div className="field">
              <label className="field-label" htmlFor="auth-name">
                Họ và tên
              </label>
              <Input
                id="auth-name"
                {...form.register("name")}
                placeholder="Nguyễn Minh Anh"
              />
              {form.formState.errors.name && (
                <p className="form-error">
                  {form.formState.errors.name.message}
                </p>
              )}
            </div>
          )}
          {register ? (
            <RegisterEmailOtp
              email={emailValue ?? ""}
              inputProps={form.register("email")}
              error={form.formState.errors.email?.message}
              validateEmail={() => form.trigger("email")}
              verifiedEmail={verifiedToken ? verification!.email : null}
              onVerified={(email, token) => {
                setVerification({ email, token });
                form.clearErrors("email");
              }}
              onChangeEmail={() => setVerification(null)}
            />
          ) : (
            <div className="field">
              <label className="field-label" htmlFor="auth-email">
                Địa chỉ email
              </label>
              <Input id="auth-email" type="email" autoComplete="email" {...form.register("email")} />
              {form.formState.errors.email && (
                <p className="form-error">
                  {form.formState.errors.email.message}
                </p>
              )}
            </div>
          )}
          {!forgot && (
            <div className="field">
              <label className="field-label" htmlFor="auth-password">
                Mật khẩu
              </label>
              <div className="password-field">
                <Input
                  id="auth-password"
                  type={visible ? "text" : "password"}
                  {...form.register("password")}
                  autoComplete={register ? "new-password" : "current-password"}
                />
                <button
                  type="button"
                  aria-label={visible ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
                  onClick={() => setVisible(!visible)}
                >
                  {visible ? <EyeOff size={17} /> : <Eye size={17} />}
                </button>
              </div>
              {form.formState.errors.password && (
                <p className="form-error">
                  {form.formState.errors.password.message}
                </p>
              )}
            </div>
          )}
          {!register && !forgot && (
            <div className="text-right mb-6">
              <Link href="/forgot-password" className="text-link">
                Quên mật khẩu?
              </Link>
            </div>
          )}
          <Button type="submit" disabled={busy || (register && !verifiedToken)} className="w-full h-12">
            {busy
              ? "Đang xử lý..."
              : forgot
                ? "Gửi hướng dẫn"
                : register
                  ? "Tạo tài khoản"
                  : "Đăng nhập"}
            <ArrowRight size={16} />
          </Button>
        </form>
        <p className="auth-alternative">
          {register ? "Đã có tài khoản?" : "Chưa có tài khoản?"}{" "}
          <Link href={register ? "/login" : "/register"}>
            {register ? "Đăng nhập" : "Đăng ký ngay"}
          </Link>
        </p>
        <div className="notice-box">
          <ShieldCheck size={17} />
          <p>
            Phiên đăng nhập được giữ an toàn bằng cookie bảo mật. Mật khẩu chỉ
            được gửi tới máy chủ xác thực, không lưu trên trình duyệt.
          </p>
        </div>
      </div>
    </div>
  );
}
