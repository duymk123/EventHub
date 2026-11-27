"use client";
import { useEffect, useRef, useState } from "react";
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
  Ticket,
  CalendarDays,
  MapPin,
  Info,
  LoaderCircle,
  CheckCircle2,
  MailWarning,
  RefreshCw,
} from "lucide-react";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import {
  useAction,
  useOrder,
  usePaymentVerify,
  usePublicEvent,
  useRefresh,
  useSession,
} from "@/hooks/use-api";
import { authApi, orderApi } from "@/lib/api/endpoints";
import { session } from "@/lib/auth/session";
import { money, dateLabel, cn } from "@/lib/utils";
import { VENUE } from "@/lib/labels";
import { holderSchema, type HolderForm } from "@/schemas/forms";
import { ApiError } from "@/lib/api/errors";
import { MAX_SEATS_PER_ORDER, hasSeat, parseSeatId, seatId } from "@/lib/seats";
import { DataBoundary, Empty, RoleGuard, ConfirmDialog } from "../shared";
import type { Event, Order, OrderLine } from "@/types";
import type { CreateOrderDto } from "@/types/api";
import { toast } from "sonner";

/** Lỗi tạm thời: thử lại với cùng Idempotency-Key để server trả lại đúng đơn đã tạo (C14). */
const TRANSIENT = new Set([
  "NETWORK_ERROR",
  "GATEWAY_TIMEOUT",
  "SERVICE_UNAVAILABLE",
  "INTERNAL_ERROR",
]);
const PENDING_KEY = "eventhub.pendingPayment";

function rememberPendingPayment(orderId: string) {
  try {
    window.sessionStorage.setItem(PENDING_KEY, orderId);
  } catch {
    // Không có sessionStorage thì trang kết quả vẫn đọc mã đơn từ vnp_OrderInfo.
  }
}

function pendingPayment() {
  try {
    return window.sessionStorage.getItem(PENDING_KEY);
  } catch {
    return null;
  }
}

const UUID = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i;

export function Checkout() {
  return (
    <RoleGuard roles={["CUSTOMER"]}>
      <CheckoutContent />
    </RoleGuard>
  );
}

function CheckoutContent() {
  const params = useSearchParams();
  const orderId = params.get("order");
  const eventId = params.get("event");
  if (orderId) return <OrderStep orderId={orderId} />;
  if (eventId)
    return (
      <HolderStepLoader
        eventId={eventId}
        rawSeats={params.get("seats") ?? ""}
      />
    );
  return <NothingSelected />;
}

function NothingSelected() {
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
}

function Countdown({
  expiresAt,
  onExpire,
}: {
  expiresAt: string;
  onExpire: () => void;
}) {
  const [remaining, setRemaining] = useState(600);
  const expired = useRef(false);
  useEffect(() => {
    const tick = () => {
      const left = Math.max(
        0,
        Math.ceil((Date.parse(expiresAt) - Date.now()) / 1000),
      );
      setRemaining(left);
      // Đồng hồ chỉ để hiển thị: hết giờ thì hỏi lại server trạng thái thật của đơn.
      if (left === 0 && !expired.current) {
        expired.current = true;
        onExpire();
      }
    };
    tick();
    const interval = setInterval(tick, 1000);
    return () => clearInterval(interval);
  }, [expiresAt, onExpire]);
  return (
    <div className={cn("countdown", remaining < 60 && "urgent")}>
      <Clock3 size={18} />
      <span>Ghế được giữ trong</span>
      <b>
        {String(Math.floor(remaining / 60)).padStart(2, "0")}:
        {String(remaining % 60).padStart(2, "0")}
      </b>
    </div>
  );
}

function Steps({ current }: { current: number }) {
  return (
    <div className="checkout-steps">
      {["Chọn vé", "Thông tin", "Thanh toán"].map((name, i) => (
        <span
          key={name}
          className={cn(current === i && "current", current > i && "done")}
        >
          <b>{current > i ? <Check size={12} /> : i + 1}</b>
          {name}
        </span>
      ))}
    </div>
  );
}

function Summary({
  event,
  lines,
  seatsOf,
  total,
  estimate = false,
}: {
  event: Event | undefined;
  lines: OrderLine[];
  seatsOf: (tierId: string) => string;
  total: number;
  estimate?: boolean;
}) {
  return (
    <aside className="order-summary panel">
      {event && (
        <>
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
        </>
      )}
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
                {l.quantity} × {money(l.unitPrice)} · Ghế {seatsOf(l.tierId)}
              </small>
            </span>
            <strong>{money(l.quantity * l.unitPrice)}</strong>
          </div>
        ))}
      </div>
      <div className="summary-total">
        <span>{estimate ? "Tạm tính" : "Tổng cộng"}</span>
        <strong>{money(total)}</strong>
      </div>
      <span className="summary-note">
        <ShieldCheck size={15} />
        {estimate
          ? "Tổng tiền chính thức do hệ thống xác nhận khi giữ ghế"
          : "Thanh toán an toàn qua cổng VNPay"}
      </span>
      <div className="refund-note">
        <h4>Yên tâm lên kế hoạch</h4>
        <p>
          Yêu cầu hoàn 70% giá mua nếu còn ít nhất 48 giờ trước sự kiện và tất
          cả vé chưa sử dụng. Admin xét duyệt cả đơn.
        </p>
      </div>
    </aside>
  );
}

// ---------------------------------------------------------------- bước 2: thông tin người dùng vé
function HolderStepLoader({
  eventId,
  rawSeats,
}: {
  eventId: string;
  rawSeats: string;
}) {
  // Cần sơ đồ ghế để đổi nhãn ghế (B-07) sang id ghế mà Order yêu cầu.
  const query = usePublicEvent(eventId, { withSeats: true });
  if (query.isLoading || query.isError || !query.data)
    return (
      <div className="container page-section">
        <DataBoundary
          loading={query.isLoading}
          error={query.isError}
          onRetry={() => void query.refetch()}
          empty
          title="Sự kiện không còn mở bán"
          description="Sự kiện này đã đóng bán hoặc chưa được công bố."
        >
          {null}
        </DataBoundary>
      </div>
    );
  return <HolderStep event={query.data} rawSeats={rawSeats} />;
}

function HolderStep({ event, rawSeats }: { event: Event; rawSeats: string }) {
  const { user } = useSession();
  const router = useRouter();
  const refresh = useRefresh();
  const { busy, run } = useAction();
  const verifyAction = useAction();
  const idempotencyKey = useRef<string | null>(null);
  // Ghế khách chọn ở trang sự kiện (C12): "<tierId>:<B-07>", bỏ ghế lạ hoặc trùng.
  const [seatIds] = useState(() =>
    [...new Set(rawSeats.split(","))]
      .filter((s) => {
        const { tierId, code } = parseSeatId(s);
        const t = event.ticketTypes.find((t) => t.id === tierId);
        return Boolean(t && hasSeat(t, code));
      })
      .slice(0, MAX_SEATS_PER_ORDER),
  );
  const holders = seatIds.map((id, i) => {
    const { tierId, code } = parseSeatId(id);
    return {
      tierId,
      seatId: id,
      seatLabel: code,
      name: i === 0 ? (user?.name ?? "") : "",
      email: "",
    };
  });
  const form = useForm<HolderForm>({
    resolver: zodResolver(holderSchema),
    defaultValues: { holders, consent: false },
  });
  if (!holders.length) return <NothingSelected />;
  const lines = event.ticketTypes
    .map((t) => ({
      tierId: t.id,
      tier: t.name,
      quantity: holders.filter((h) => h.tierId === t.id).length,
      unitPrice: t.price,
    }))
    .filter((l) => l.quantity > 0);
  const seatsOf = (tierId: string) =>
    holders
      .filter((h) => h.tierId === tierId)
      .map((h) => h.seatLabel)
      .join(", ");
  const estimate = lines.reduce((s, l) => s + l.unitPrice * l.quantity, 0);

  function toRequest(values: HolderForm): CreateOrderDto {
    const items = new Map<string, CreateOrderDto["items"][number]>();
    for (const h of values.holders) {
      const tier = event.ticketTypes.find((t) => t.id === h.tierId);
      const uuid = tier?.seatIds?.[h.seatLabel];
      if (!tier || !uuid) throw new ApiError("SEAT_NOT_FOUND");
      const item = items.get(tier.id) ?? {
        ticketTypeId: tier.id,
        quantity: 0,
        holders: [],
      };
      item.quantity += 1;
      item.holders.push({
        seatId: uuid,
        holderName: h.name.trim(),
        holderEmail: h.email.trim() || undefined,
      });
      items.set(tier.id, item);
    }
    return { eventId: event.id, items: [...items.values()] };
  }

  /** SEAT_TAKEN trả id ghế (UUID): đổi về "<tierId>:<nhãn>" để bỏ khỏi lựa chọn rồi quay lại sơ đồ ghế. */
  function backToSeatMap(takenSeatIds: string[]) {
    const taken = new Set<string>();
    for (const t of event.ticketTypes)
      for (const [label, uuid] of Object.entries(t.seatIds ?? {}))
        if (takenSeatIds.includes(uuid)) taken.add(seatId(t.id, label));
    const keep = seatIds.filter((s) => !taken.has(s));
    void refresh(["seat-map"], ["ticket-types"]);
    router.replace(
      `/events/${event.id}?seats=${encodeURIComponent(keep.join(","))}#seats`,
    );
  }

  const placeOrder = (values: HolderForm) =>
    run(
      async () => {
        // Một key cho một lần giữ ghế; lỗi mạng gửi lại giữ nguyên key.
        idempotencyKey.current ??= crypto.randomUUID();
        try {
          return await orderApi.create(
            toRequest(values),
            idempotencyKey.current,
          );
        } catch (error) {
          const code = error instanceof ApiError ? error.code : "";
          if (!TRANSIENT.has(code)) idempotencyKey.current = null;
          if (error instanceof ApiError && error.code === "SEAT_TAKEN")
            backToSeatMap(error.takenSeatIds);
          throw error;
        }
      },
      (order) => {
        void refresh(["orders"], ["seat-map"]);
        router.replace(`/checkout?order=${order.id}`);
        toast.success("Đã giữ ghế trong 10 phút");
      },
    );

  return (
    <div className="container page-section checkout-page">
      <Link className="text-link" href={`/events/${event.id}`}>
        <ArrowLeft size={14} />
        Trở lại sự kiện
      </Link>
      <div className="checkout-top">
        <h1>Chỉ còn một bước tới cuộc hẹn.</h1>
        <Steps current={1} />
      </div>
      <div className="checkout-layout">
        <div>
          <motion.form
            className="panel"
            initial={{ opacity: 0, x: 12 }}
            animate={{ opacity: 1, x: 0 }}
            onSubmit={(e) => void form.handleSubmit(placeOrder)(e)}
          >
            <h2 className="text-xl font-semibold">Thông tin người nhận vé</h2>
            <p className="muted text-xs mt-2 mb-6">
              Mỗi vé dành cho một người. Email để trống sẽ gửi vé về email của
              bạn.
            </p>
            <div className="buyer-info">
              <span className="avatar">
                {(user?.name ?? "")
                  .split(" ")
                  .slice(-2)
                  .map((x) => x[0])
                  .join("")}
              </span>
              <div>
                <strong>{user?.name}</strong>
                <p>{user?.email} · Người mua</p>
              </div>
              <ShieldCheck size={18} />
            </div>
            {user && !user.emailVerified && (
              <div className="notice-box mb-6">
                <MailWarning size={17} />
                <div>
                  <p>
                    Bạn cần xác minh email trước khi đặt vé. Mở đường dẫn trong
                    email chúng tôi đã gửi, sau đó bấm “Tôi đã xác minh”.
                  </p>
                  <div className="flex flex-wrap gap-2 mt-3">
                    <Button
                      type="button"
                      size="sm"
                      variant="secondary"
                      disabled={verifyAction.busy}
                      onClick={() =>
                        verifyAction.run(
                          () => authApi.resendVerification(),
                          () => toast.success("Đã gửi lại email xác minh"),
                        )
                      }
                    >
                      Gửi lại email
                    </Button>
                    <Button
                      type="button"
                      size="sm"
                      disabled={verifyAction.busy}
                      onClick={() =>
                        verifyAction.run(
                          () => session.revalidate(),
                          (u) =>
                            u?.emailVerified
                              ? toast.success("Email đã được xác minh")
                              : toast.info(
                                  "Email chưa được xác minh. Hãy mở đường dẫn trong email trước.",
                                ),
                        )
                      }
                    >
                      <RefreshCw size={14} />
                      Tôi đã xác minh
                    </Button>
                  </div>
                </div>
              </div>
            )}
            {holders.map((h, i) => (
              <div key={h.seatId} className="holder-form">
                <h3>
                  <span>{String(i + 1).padStart(2, "0")}</span>Vé{" "}
                  {event.ticketTypes.find((t) => t.id === h.tierId)?.name}
                  <em className="holder-seat">Ghế {h.seatLabel}</em>
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
                      type="email"
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
                Ghế được giữ trong 10 phút kể từ khi bạn tiếp tục sang bước
                thanh toán. Nếu có ghế vừa được người khác giữ, bạn sẽ quay lại
                sơ đồ để chọn ghế khác.
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
              disabled={busy || (user ? !user.emailVerified : true)}
              className="w-full mt-6 h-12"
              type="submit"
            >
              {busy ? "Đang giữ ghế..." : "Giữ ghế & tiếp tục"}
              <ArrowRight size={17} />
            </Button>
          </motion.form>
        </div>
        <Summary
          event={event}
          lines={lines}
          seatsOf={seatsOf}
          total={estimate}
          estimate
        />
      </div>
    </div>
  );
}

// ---------------------------------------------------------------- bước 3: thanh toán VNPay
function OrderStep({ orderId }: { orderId: string }) {
  const query = useOrder(orderId);
  const order = query.data;
  // Sự kiện đã đóng bán không còn đọc công khai được: tóm tắt đơn vẫn hiển thị, chỉ thiếu ảnh/tên.
  const event = usePublicEvent(order?.eventId).data;
  if (!order)
    return (
      <div className="container page-section">
        <DataBoundary
          loading={query.isLoading}
          error={query.isError}
          onRetry={() => void query.refetch()}
          empty
          title="Không tìm thấy đơn hàng"
          description="Đơn hàng không tồn tại hoặc không thuộc tài khoản của bạn."
        >
          {null}
        </DataBoundary>
      </div>
    );
  if (order.status === "PAID")
    return <PaidScreen order={order} event={event} />;
  if (order.status !== "PENDING_PAYMENT")
    return (
      <div className="container page-section">
        <Empty
          title={
            order.status === "EXPIRED"
              ? "Thời gian giữ ghế đã kết thúc"
              : "Đơn hàng đã kết thúc"
          }
          description="Ghế đã được trả lại để người khác có thể đặt. Bạn có thể chọn lại nếu vẫn còn ghế."
          action={
            <Button asChild>
              <Link href={`/events/${order.eventId}`}>
                Chọn lại ghế
                <ArrowRight size={16} />
              </Link>
            </Button>
          }
        />
      </div>
    );
  return (
    <PaymentStep
      order={order}
      event={event}
      reload={() => void query.refetch()}
    />
  );
}

function PaymentStep({
  order,
  event,
  reload,
}: {
  order: Order;
  event: Event | undefined;
  reload: () => void;
}) {
  const refresh = useRefresh();
  const { busy, run } = useAction();
  const [cancel, setCancel] = useState(false);
  const [redirecting, setRedirecting] = useState(false);
  const seatsOf = (tierId: string) =>
    order.holders
      .filter((h) => h.tierId === tierId)
      .map((h) => h.seatLabel)
      .join(", ");
  const stale = (error: unknown) =>
    error instanceof ApiError &&
    ["ORDER_EXPIRED", "ORDER_NOT_PAYABLE"].includes(error.code);

  function pay() {
    run(
      async () => {
        try {
          // Mỗi lần bấm là một lượt thanh toán mới (C18: thanh toán lại dùng key mới).
          return await orderApi.pay(order.id, crypto.randomUUID());
        } catch (error) {
          if (stale(error)) reload();
          throw error;
        }
      },
      (payment) => {
        setRedirecting(true);
        rememberPendingPayment(order.id);
        window.location.assign(payment.paymentUrl);
      },
    );
  }

  return (
    <div className="container page-section checkout-page">
      <Link className="text-link" href="/orders">
        <ArrowLeft size={14} />
        Đơn hàng của tôi
      </Link>
      <div className="checkout-top">
        <h1>Chỉ còn một bước tới cuộc hẹn.</h1>
        <Steps current={2} />
      </div>
      <div className="checkout-layout">
        <div>
          <motion.section
            className="panel"
            initial={{ opacity: 0, x: 12 }}
            animate={{ opacity: 1, x: 0 }}
          >
            <Countdown expiresAt={order.expiresAt} onExpire={reload} />
            <h2 className="text-xl font-semibold mb-2">Thanh toán qua VNPay</h2>
            <p className="muted text-xs mb-6">
              Bạn sẽ được chuyển sang cổng VNPay để thanh toán bằng QR, thẻ ATM
              nội địa hoặc thẻ quốc tế.
            </p>
            <div className="payment-preview">
              <div className="demo-qr">
                <CreditCard size={110} strokeWidth={1.1} />
                <span>VNPAY</span>
              </div>
              <div>
                <h3>Số tiền cần thanh toán</h3>
                <p>
                  Nếu thanh toán chưa thành công hoặc bạn hủy trên VNPay, đơn
                  vẫn được giữ và bạn có thể thanh toán lại trước khi hết giờ.
                </p>
                <strong>{money(order.total)}</strong>
                <small>Mã đơn: {order.id}</small>
              </div>
            </div>
            {(busy || redirecting) && (
              <div className="payment-progress">
                <Check size={15} />
                Đã giữ ghế
                <ArrowRight size={13} className="muted" />
                <LoaderCircle size={15} className="animate-spin" />
                {redirecting ? "Đang chuyển tới VNPay" : "Đang tạo giao dịch"}
              </div>
            )}
            <Button
              disabled={busy || redirecting}
              className="w-full h-12"
              onClick={pay}
            >
              {busy || redirecting ? "Đang xử lý..." : "Thanh toán qua VNPay"}
              <ArrowRight size={17} />
            </Button>
            <Button
              disabled={busy || redirecting}
              variant="ghost"
              className="mt-3 w-full muted"
              onClick={() => setCancel(true)}
            >
              Hủy giữ ghế
            </Button>
          </motion.section>
        </div>
        <Summary
          event={event}
          lines={order.lines}
          seatsOf={seatsOf}
          total={order.total}
        />
      </div>
      <ConfirmDialog
        open={cancel}
        onOpenChange={setCancel}
        title="Hủy giữ ghế?"
        description="Các ghế trong đơn sẽ được trả lại ngay. Bạn cần chọn lại ghế nếu muốn tiếp tục mua."
        busy={busy}
        confirmLabel="Hủy giữ ghế"
        onConfirm={() =>
          run(
            () => orderApi.cancel(order.id),
            () => {
              setCancel(false);
              reload();
              void refresh(["orders"], ["seat-map"]);
              toast.success("Đã hủy giữ ghế");
            },
          )
        }
      />
    </div>
  );
}

function PaidScreen({
  order,
  event,
}: {
  order: Order;
  event: Event | undefined;
}) {
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
          Đặt vé thành công
          {event ? (
            <>
              {" "}
              cho <strong>{event.title}</strong>
            </>
          ) : null}
          .
          <br />
          Vé sẽ có trong Ví vé của tôi và được gửi qua email trong giây lát.
        </p>
        <div className="success-summary">
          <span>
            Mã đơn <b>{order.id}</b>
          </span>
          <span>
            {order.lines.reduce((s, l) => s + l.quantity, 0)} vé{" "}
            <b>{money(order.total)}</b>
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
      </motion.div>
    </div>
  );
}

// ---------------------------------------------------------------- trang returnUrl của VNPay
export function CheckoutResult() {
  return (
    <RoleGuard roles={["CUSTOMER"]}>
      <ResultContent />
    </RoleGuard>
  );
}

function ResultContent() {
  const params = useSearchParams();
  const router = useRouter();
  const refresh = useRefresh();
  // Không tự tin tham số URL: chuyển nguyên các vnp_* cho server kiểm chữ ký VNPay; trạng thái thật lấy từ verify.
  const [orderId] = useState(
    () => UUID.exec(params.get("vnp_OrderInfo") ?? "")?.[0] ?? pendingPayment(),
  );
  const [returnParams] = useState(() =>
    Object.fromEntries([...params.entries()].filter(([key]) => key.startsWith("vnp_"))),
  );
  const verify = usePaymentVerify(orderId, returnParams);
  const handled = useRef(false);
  useEffect(() => {
    const order = verify.data;
    if (!order || handled.current) return;
    handled.current = true;
    try {
      window.sessionStorage.removeItem(PENDING_KEY);
    } catch {
      // bỏ qua
    }
    void refresh(["orders"], ["tickets"]);
    if (order.status === "PAID") toast.success("Thanh toán thành công");
    else if (order.status === "PENDING_PAYMENT")
      toast.info(
        "Thanh toán chưa hoàn tất. Bạn có thể thanh toán lại trước khi hết giờ giữ ghế.",
      );
    else toast.error("Đơn hàng đã kết thúc, ghế đã được trả lại.");
    router.replace(`/checkout?order=${order.id}`);
  }, [verify.data, refresh, router]);
  if (!orderId)
    return (
      <div className="container page-section">
        <Empty
          title="Không tìm thấy đơn hàng cần xác nhận"
          description="Bạn có thể xem trạng thái thanh toán trong Đơn hàng của tôi."
          action={
            <Button asChild>
              <Link href="/orders">Đơn hàng của tôi</Link>
            </Button>
          }
        />
      </div>
    );
  if (verify.isError)
    return (
      <div className="container page-section">
        <Empty
          title="Chưa xác nhận được thanh toán"
          description={
            verify.error instanceof Error
              ? verify.error.message
              : "Cổng thanh toán đang bận, vui lòng thử lại."
          }
          action={
            <div className="flex flex-wrap justify-center gap-3">
              <Button onClick={() => void verify.refetch()}>
                <RefreshCw size={16} />
                Thử lại
              </Button>
              <Button asChild variant="secondary">
                <Link href={`/checkout?order=${orderId}`}>Xem đơn hàng</Link>
              </Button>
            </div>
          }
        />
      </div>
    );
  return (
    <div className="container page-section">
      <div className="empty-state" role="status">
        <LoaderCircle size={36} className="animate-spin" />
        <h3>Đang xác nhận thanh toán</h3>
        <div className="payment-progress">
          <Check size={15} />
          Đã nhận phản hồi từ VNPay
          <ArrowRight size={13} className="muted" />
          <LoaderCircle size={15} className="animate-spin" />
          Đang đối soát với hệ thống
        </div>
      </div>
    </div>
  );
}
