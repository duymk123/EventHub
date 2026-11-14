"use client";
import { useState, useEffect } from "react";
import Image from "next/image";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { motion } from "framer-motion";
import {
  ArrowLeft,
  ArrowRight,
  Clock3,
  Check,
  ShieldCheck,
  CreditCard,
  Smartphone,
  QrCode,
  Ticket,
  CalendarDays,
  MapPin,
  Info,
  LoaderCircle,
  CheckCircle2,
} from "lucide-react";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { useDemo, useAction } from "@/hooks/use-demo";
import { mock } from "@/lib/mock/engine";
import { money, dateLabel, cn } from "@/lib/utils";
import { VENUE } from "@/lib/mock/seed";
import { holderSchema, type HolderForm } from "@/schemas/forms";
import { Empty, RoleGuard, ConfirmDialog } from "../shared";
import { toast } from "sonner";
export function Checkout() {
  return (
    <RoleGuard roles={["CUSTOMER"]}>
      <CheckoutContent />
    </RoleGuard>
  );
}
function Countdown({ expiresAt }: { expiresAt: string }) {
  const [remaining, setRemaining] = useState(180);
  useEffect(() => {
    const tick = () =>
      setRemaining(
        Math.max(0, Math.ceil((Date.parse(expiresAt) - Date.now()) / 1000)),
      );
    tick();
    const interval = setInterval(tick, 1000);
    return () => clearInterval(interval);
  }, [expiresAt]);
  return (
    <div className={cn("countdown", remaining < 30 && "urgent")}>
      <Clock3 size={18} />
      <span>Vé được giữ trong</span>
      <b>
        {String(Math.floor(remaining / 60)).padStart(2, "0")}:
        {String(remaining % 60).padStart(2, "0")}
      </b>
    </div>
  );
}
function CheckoutContent() {
  const state = useDemo();
  const router = useRouter();
  const params = useSearchParams();
  const { busy, run } = useAction();
  const [method, setMethod] = useState("qr");
  const [failPayment, setFailPayment] = useState(false);
  const [cancel, setCancel] = useState(false);
  const order = state.orders.find(
    (o) => o.id === params.get("order") && o.customerId === state.user?.id,
  );
  const event = state.events.find(
    (e) => e.id === (order?.eventId ?? params.get("event")),
  );
  const quantities = Object.fromEntries(
    (event?.ticketTypes ?? []).map((t) => [
      t.id,
      Math.min(6, Math.max(0, Math.floor(Number(params.get(t.id)) || 0))),
    ]),
  );
  const holders = (event?.ticketTypes ?? []).flatMap((t) =>
    Array.from({ length: quantities[t.id] }, (_, i) => ({
      tierId: t.id,
      name: i === 0 ? (state.user?.name ?? "") : "",
      email: i === 0 ? (state.user?.email ?? "") : "",
    })),
  );
  const form = useForm<HolderForm>({
    resolver: zodResolver(holderSchema),
    defaultValues: { holders, consent: false },
  });
  if (!event || (!order && !holders.length))
    return (
      <div className="container page-section">
        <Empty
          title="Bạn chưa chọn vé"
          action={
            <Button asChild>
              <Link href="/explore">Khám phá sự kiện</Link>
            </Button>
          }
        />
      </div>
    );
  const lines =
    order?.lines ??
    event.ticketTypes
      .filter((t) => quantities[t.id] > 0)
      .map((t) => ({
        tierId: t.id,
        tier: t.name,
        quantity: quantities[t.id],
        unitPrice: t.price,
      }));
  const total =
    order?.total ?? lines.reduce((s, l) => s + l.unitPrice * l.quantity, 0);
  if (order?.status === "PAID")
    return (
      <div className="checkout-success container">
        <motion.div
          initial={{ scale: 0.8, opacity: 0 }}
          animate={{ scale: 1, opacity: 1 }}
        >
          <span className="success-mark">
            <CheckCircle2 size={44} />
          </span>
          <p className="eyebrow">CUỘC HẸN ĐÃ ĐƯỢC XÁC NHẬN</p>
          <h1>Hẹn gặp bạn ở đó!</h1>
          <p>
            Đặt vé thành công cho <strong>{event.title}</strong>.<br />
            Vé của bạn đã sẵn sàng trong Ví vé của tôi.
          </p>
          <div className="success-summary">
            <span>
              Mã đơn <b>{order.id}</b>
            </span>
            <span>
              {lines.reduce((s, l) => s + l.quantity, 0)} vé{" "}
              <b>{money(total)}</b>
            </span>
          </div>
          <div className="flex justify-center gap-3 mt-7">
            <Button asChild>
              <Link href="/tickets">
                <Ticket size={17} />
                Xem vé của tôi
              </Link>
            </Button>
            <Button asChild variant="secondary">
              <Link href="/orders">Chi tiết đơn hàng</Link>
            </Button>
          </div>
          <p className="text-xs muted mt-8">
            Thanh toán mô phỏng thành công. Không có tiền thật được chuyển.
          </p>
        </motion.div>
      </div>
    );
  if (order && order.status !== "PENDING_PAYMENT")
    return (
      <div className="container page-section">
        <Empty
          title={
            order.status === "EXPIRED"
              ? "Thời gian giữ vé đã kết thúc"
              : "Đơn hàng đã kết thúc"
          }
          description="Vé đã được trả lại để người khác có thể đặt. Bạn có thể chọn lại nếu vẫn còn vé."
          action={
            <Button asChild>
              <Link href={`/events/${event.id}`}>
                Chọn lại vé
                <ArrowRight size={16} />
              </Link>
            </Button>
          }
        />
      </div>
    );
  return (
    <div className="container page-section checkout-page">
      <Link className="text-link" href={`/events/${event.id}`}>
        <ArrowLeft size={14} />
        Trở lại sự kiện
      </Link>
      <div className="checkout-top">
        <h1>Chỉ còn một bước tới cuộc hẹn.</h1>
        <div className="checkout-steps">
          {["Chọn vé", "Thông tin", "Thanh toán"].map((name, i) => (
            <span
              key={name}
              className={cn(
                (order ? 2 : 1) === i && "current",
                (order ? 2 : 1) > i && "done",
              )}
            >
              <b>{(order ? 2 : 1) > i ? <Check size={12} /> : i + 1}</b>
              {name}
            </span>
          ))}
        </div>
      </div>
      <div className="checkout-layout">
        <div>
          {order ? (
            <motion.section
              className="panel"
              initial={{ opacity: 0, x: 12 }}
              animate={{ opacity: 1, x: 0 }}
            >
              <Countdown expiresAt={order.expiresAt} />
              <h2 className="text-xl font-semibold mb-2">
                Phương thức thanh toán
              </h2>
              <p className="muted text-xs mb-6">
                Chọn cách thanh toán phù hợp với bạn.
              </p>
              <div className="payment-methods">
                {[
                  { id: "qr", name: "Chuyển khoản QR", Icon: QrCode },
                  { id: "card", name: "Thẻ ngân hàng", Icon: CreditCard },
                  { id: "wallet", name: "Ví điện tử", Icon: Smartphone },
                ].map(({ id, name, Icon }) => (
                  <button
                    key={id}
                    className={cn(method === id && "selected")}
                    onClick={() => setMethod(id)}
                  >
                    <Icon size={23} />
                    <span>{name}</span>
                    {method === id && <Check size={13} />}
                  </button>
                ))}
              </div>
              <div className="payment-preview">
                <div className="demo-qr">
                  <QrCode size={130} strokeWidth={1.1} />
                  <span>DEMO</span>
                </div>
                <div>
                  <h3>
                    {method === "qr"
                      ? "Thanh toán bằng mã QR"
                      : method === "card"
                        ? "Thanh toán bằng thẻ"
                        : "Thanh toán qua ví điện tử"}
                  </h3>
                  <p>
                    Đây là bước mô phỏng để bạn thử luồng thanh toán. Không cần
                    nhập thông tin ngân hàng hoặc chuyển tiền.
                  </p>
                  <strong>{money(total)}</strong>
                  <small>Nội dung: {order.id}</small>
                </div>
              </div>
              <label className="check-line mb-6">
                <input
                  type="checkbox"
                  checked={failPayment}
                  onChange={(e) => setFailPayment(e.target.checked)}
                />
                Thử tình huống thanh toán thất bại
              </label>
              {busy && (
                <div className="payment-progress">
                  <Check size={15} />
                  Đã giữ vé
                  <Chevron />
                  <LoaderCircle size={15} className="animate-spin" />
                  Đang xác nhận thanh toán
                </div>
              )}
              <Button
                disabled={busy}
                className="w-full h-12"
                onClick={() =>
                  run(
                    () => mock.pay(order.id, !failPayment),
                    () => toast.success("Thanh toán mô phỏng thành công"),
                  )
                }
              >
                {busy ? "Đang xác nhận..." : "Xác nhận thanh toán mô phỏng"}
                <ArrowRight size={17} />
              </Button>
              <Button
                disabled={busy}
                variant="ghost"
                className="mt-3 w-full muted"
                onClick={() => setCancel(true)}
              >
                Hủy giữ vé
              </Button>
            </motion.section>
          ) : (
            <motion.form
              className="panel"
              initial={{ opacity: 0, x: 12 }}
              animate={{ opacity: 1, x: 0 }}
              onSubmit={form.handleSubmit((values) =>
                run(
                  () => mock.reserve(event.id, quantities, values.holders),
                  (id) => {
                    router.replace(`/checkout?order=${id}`);
                    toast.success("Đã giữ vé trong 3 phút");
                  },
                ),
              )}
            >
              <h2 className="text-xl font-semibold">Thông tin người nhận vé</h2>
              <p className="muted text-xs mt-2 mb-6">
                Mỗi vé dành cho một người. Email để trống sẽ dùng email của bạn.
              </p>
              <div className="buyer-info">
                <span className="avatar">MA</span>
                <div>
                  <strong>{state.user?.name}</strong>
                  <p>{state.user?.email} · Người mua</p>
                </div>
                <ShieldCheck size={18} />
              </div>
              {holders.map((h, i) => (
                <div key={`${h.tierId}-${i}`} className="holder-form">
                  <h3>
                    <span>{String(i + 1).padStart(2, "0")}</span>Vé{" "}
                    {event.ticketTypes.find((t) => t.id === h.tierId)?.name}
                  </h3>
                  <div className="form-grid">
                    <div>
                      <label className="field-label" htmlFor={`holder-${i}`}>
                        Họ và tên người sử dụng *
                      </label>
                      <Input
                        id={`holder-${i}`}
                        {...form.register(`holders.${i}.name`)}
                        placeholder="Họ và tên"
                      />
                      {form.formState.errors.holders?.[i]?.name && (
                        <p className="form-error">
                          {form.formState.errors.holders[i]?.name?.message}
                        </p>
                      )}
                    </div>
                    <div>
                      <label className="field-label" htmlFor={`email-${i}`}>
                        Email nhận vé
                      </label>
                      <Input
                        id={`email-${i}`}
                        {...form.register(`holders.${i}.email`)}
                        placeholder="Để trống để dùng email người mua"
                      />
                      {form.formState.errors.holders?.[i]?.email && (
                        <p className="form-error">
                          {form.formState.errors.holders[i]?.email?.message}
                        </p>
                      )}
                    </div>
                  </div>
                </div>
              ))}
              <div className="notice-box mt-6">
                <Info size={17} />
                <p>
                  Vé được giữ trong 3 phút kể từ khi bạn tiếp tục sang bước
                  thanh toán. Hãy kiểm tra thông tin trước khi xác nhận.
                </p>
              </div>
              <label className="check-line mt-6">
                <input type="checkbox" {...form.register("consent")} />
                <span>
                  Tôi đồng ý với{" "}
                  <Link href="/help#refund" target="_blank">
                    điều khoản đặt vé và chính sách hoàn tiền
                  </Link>
                  .
                </span>
              </label>
              {form.formState.errors.consent && (
                <p className="form-error">
                  {form.formState.errors.consent.message}
                </p>
              )}
              <Button
                disabled={busy}
                className="w-full mt-6 h-12"
                type="submit"
              >
                {busy ? "Đang giữ vé..." : "Giữ vé & tiếp tục"}
                <ArrowRight size={17} />
              </Button>
            </motion.form>
          )}
        </div>
        <aside className="order-summary panel">
          <div className="summary-event">
            <Image src={event.image} alt="" width={90} height={90} />
            <div>
              <span>{event.category}</span>
              <h3>{event.title}</h3>
            </div>
          </div>
          <p>
            <CalendarDays size={15} />
            {dateLabel(event.startTime)}
          </p>
          <p>
            <MapPin size={15} />
            {VENUE}
          </p>
          <div className="summary-lines">
            {lines.map((l) => (
              <div key={l.tierId}>
                <span>
                  {l.tier}
                  <small>
                    {l.quantity} × {money(l.unitPrice)}
                  </small>
                </span>
                <strong>{money(l.quantity * l.unitPrice)}</strong>
              </div>
            ))}
          </div>
          <div className="summary-total">
            <span>Tổng cộng</span>
            <strong>{money(total)}</strong>
          </div>
          <span className="summary-note">
            <ShieldCheck size={15} />
            Không có phí phát sinh trong bản trải nghiệm
          </span>
          <div className="refund-note">
            <h4>Yên tâm lên kế hoạch</h4>
            <p>
              Yêu cầu hoàn 70% giá mua nếu còn ít nhất 48 giờ trước sự kiện và
              tất cả vé chưa sử dụng. Admin xét duyệt cả đơn.
            </p>
          </div>
        </aside>
      </div>
      <ConfirmDialog
        open={cancel}
        onOpenChange={setCancel}
        title="Hủy giữ vé?"
        description="Các vé trong đơn sẽ được trả lại. Bạn cần chọn lại vé nếu muốn tiếp tục mua."
        busy={busy}
        onConfirm={() =>
          order &&
          run(
            () => mock.cancelOrder(order.id),
            () => {
              setCancel(false);
              toast.success("Đã hủy giữ vé");
            },
          )
        }
      />
    </div>
  );
}
function Chevron() {
  return <ArrowRight size={13} className="muted" />;
}
