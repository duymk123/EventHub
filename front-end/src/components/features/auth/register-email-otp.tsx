"use client";
// Ô email của form đăng ký kèm xác thực OTP: "Lấy mã" → nhập mã 6 số → "Xác thực". Server trả verificationToken,
// form đăng ký gửi kèm token này; đổi email sau khi xác thực thì phải xác thực lại.
import { useEffect, useState, type ComponentProps } from "react";
import { CheckCircle2, MailCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useAction } from "@/hooks/use-api";
import { authApi } from "@/lib/api/endpoints";
import { toast } from "sonner";

/** Khớp eventhub.auth.otp.resend-cooldown của auth-service; server vẫn tự chặn nếu gửi sớm hơn. */
const RESEND_SECONDS = 60;

export function normalizeEmail(email: string) {
  return email.trim().toLowerCase();
}

export function RegisterEmailOtp({
  email,
  inputProps,
  error,
  validateEmail,
  verifiedEmail,
  onVerified,
  onChangeEmail,
}: {
  /** Giá trị hiện tại của ô email. */
  email: string;
  inputProps: ComponentProps<"input">;
  error?: string;
  /** Kiểm tra định dạng email trước khi gửi mã (react-hook-form trigger). */
  validateEmail: () => Promise<boolean>;
  /** Email đã xác thực xong (đã chuẩn hóa), null nếu chưa. */
  verifiedEmail: string | null;
  onVerified: (email: string, token: string) => void;
  onChangeEmail: () => void;
}) {
  const current = normalizeEmail(email);
  const verified = verifiedEmail !== null && verifiedEmail === current;
  const [sentTo, setSentTo] = useState<string | null>(null);
  const [code, setCode] = useState("");
  const [cooldownUntil, setCooldownUntil] = useState(0);
  const [now, setNow] = useState(() => Date.now());
  const sending = useAction();
  const checking = useAction();

  const remaining = Math.max(0, Math.ceil((cooldownUntil - now) / 1000));
  useEffect(() => {
    if (cooldownUntil <= Date.now()) return;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [cooldownUntil]);

  // Mã chỉ dùng cho đúng email đã nhận mã; sửa email thì ẩn ô nhập mã.
  const codeSentHere = sentTo === current;

  async function sendCode() {
    if (!(await validateEmail())) return;
    await sending.run(
      () => authApi.sendRegisterOtp(current),
      () => {
        setSentTo(current);
        setCode("");
        setCooldownUntil(Date.now() + RESEND_SECONDS * 1000);
        setNow(Date.now());
        toast.success(`Đã gửi mã xác thực tới ${current}. Mã có hiệu lực 5 phút.`);
      },
    );
  }

  function verify() {
    if (!/^\d{6}$/.test(code)) {
      toast.error("Mã xác thực gồm 6 chữ số.");
      return;
    }
    void checking.run(
      () => authApi.verifyRegisterOtp(current, code),
      (result) => {
        onVerified(current, result.verificationToken);
        setSentTo(null);
        setCode("");
        toast.success("Email đã được xác thực.");
      },
    );
  }

  return (
    <div className="field">
      <label className="field-label" htmlFor="auth-email">
        Địa chỉ email
      </label>
      <div className="flex gap-2">
        <Input
          id="auth-email"
          type="email"
          autoComplete="email"
          {...inputProps}
          readOnly={verified}
          aria-describedby="auth-email-status"
        />
        {verified ? (
          <Button type="button" variant="secondary" className="shrink-0" onClick={onChangeEmail}>
            Đổi email
          </Button>
        ) : (
          <Button
            type="button"
            variant="secondary"
            className="shrink-0"
            disabled={sending.busy || remaining > 0 || !current}
            onClick={() => void sendCode()}
          >
            {sending.busy
              ? "Đang gửi..."
              : remaining > 0
                ? `Gửi lại (${remaining}s)`
                : codeSentHere
                  ? "Gửi lại mã"
                  : "Lấy mã"}
          </Button>
        )}
      </div>
      {error && <p className="form-error">{error}</p>}
      <div id="auth-email-status" aria-live="polite">
        {verified ? (
          <p className="mt-2 flex items-center gap-2 text-sm text-emerald-400">
            <CheckCircle2 size={16} />
            Email đã được xác thực
          </p>
        ) : (
          codeSentHere && (
            <div className="mt-3">
              <label className="field-label" htmlFor="auth-otp">
                Mã xác thực (6 số trong email)
              </label>
              <div className="flex gap-2">
                <Input
                  id="auth-otp"
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  maxLength={6}
                  placeholder="______"
                  value={code}
                  onChange={(e) => setCode(e.target.value.replace(/\D/g, "").slice(0, 6))}
                  onKeyDown={(e) => {
                    // Enter trong ô mã chỉ xác thực, không gửi cả form đăng ký.
                    if (e.key === "Enter") {
                      e.preventDefault();
                      verify();
                    }
                  }}
                  className="tracking-[0.4em]"
                />
                <Button
                  type="button"
                  className="shrink-0"
                  disabled={checking.busy || code.length !== 6}
                  onClick={verify}
                >
                  <MailCheck size={15} />
                  {checking.busy ? "Đang kiểm tra..." : "Xác thực"}
                </Button>
              </div>
              <p className="muted mt-2 text-xs">Không thấy thư? Kiểm tra mục Spam hoặc bấm gửi lại mã.</p>
            </div>
          )
        )}
      </div>
    </div>
  );
}
