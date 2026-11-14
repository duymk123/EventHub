"use client";
import { useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { ArrowRight, Eye, EyeOff, ShieldCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { loginSchema, type LoginForm } from "@/schemas/forms";
import { mock } from "@/lib/mock/engine";
import { useAction } from "@/hooks/use-demo";
import { toast } from "sonner";
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
  const form = useForm<LoginForm>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      name: "",
      email: "minhanh@example.com",
      password: "EventHub2026",
    },
  });
  const submit = form.handleSubmit((v) =>
    run(async () => {
      if (forgot) {
        await new Promise((r) => setTimeout(r, 600));
        toast.success(
          "Đã mô phỏng gửi hướng dẫn đặt lại mật khẩu. Không có email thật được gửi.",
        );
        return;
      }
      if (register) {
        if (v.name.trim().length < 2) {
          form.setError("name", { message: "Vui lòng nhập họ và tên" });
          return;
        }
        await mock.register(v.name, v.email);
        toast.success("Tài khoản trải nghiệm đã được tạo");
        router.push("/");
      } else {
        const user = await mock.login(v.email);
        toast.success(`Chào mừng ${user.name}`);
        router.push(
          user.role === "CUSTOMER"
            ? "/"
            : user.role === "ADMIN"
              ? "/admin"
              : "/organizer",
        );
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
            ? "Nhập email để thử luồng khôi phục tài khoản."
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
          <div className="field">
            <label className="field-label" htmlFor="auth-email">
              Địa chỉ email
            </label>
            <Input id="auth-email" type="email" {...form.register("email")} />
            {form.formState.errors.email && (
              <p className="form-error">
                {form.formState.errors.email.message}
              </p>
            )}
          </div>
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
          <Button type="submit" disabled={busy} className="w-full h-12">
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
            Chế độ trải nghiệm: dùng email mẫu và mật khẩu bất kỳ từ 8 ký tự.
            Mật khẩu không được lưu. Đổi vai trò tại nút Trải nghiệm.
          </p>
        </div>
      </div>
    </div>
  );
}
