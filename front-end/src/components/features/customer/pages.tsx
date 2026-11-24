"use client";
import { useState } from "react";
import Image from "next/image";
import Link from "next/link";
import {
  ArrowRight,
  CalendarDays,
  MapPin,
  QrCode,
  Ticket as TicketIcon,
  Search,
  Download,
  CheckCheck,
  Bell,
  ArrowUpRight,
  UserRound,
  Mail,
  Info,
  Copy,
  Check,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { PasswordForm } from "../auth/password-form";
import { Textarea } from "@/components/ui/textarea";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
import {
  useAction,
  useEventsById,
  useMyOrders,
  useMyTickets,
  useNotifications,
  useOrderRefunds,
  useProfile,
  useRefresh,
  useSession,
  useTicketQr,
  useUnreadCount,
} from "@/hooks/use-api";
import { useNow } from "@/hooks/use-now";
import { authApi, notificationApi, orderApi } from "@/lib/api/endpoints";
import { eventImage } from "@/lib/api/adapters";
import { session } from "@/lib/auth/session";
import { VENUE, roleNames } from "@/lib/labels";
import { money, dateLabel, timeLabel, cn } from "@/lib/utils";
import {
  DataBoundary,
  PageTitle,
  RoleGuard,
  Status,
  ConfirmDialog,
  Pagination,
  Disclosure,
} from "../shared";
import { QrImage, downloadQr } from "../ticket/qr-image";
import type { Event, Ticket, Order } from "@/types";
import { toast } from "sonner";

/** Sự kiện đã đóng bán không còn đọc công khai được: dùng ảnh theo id và tiêu đề chung. */
function eventView(events: Map<string, Event>, eventId: string) {
  const e = events.get(eventId);
  return {
    title: e?.title ?? "Sự kiện đã kết thúc bán vé",
    image: e?.image ?? eventImage("", eventId),
    startTime: e?.startTime,
  };
}

export function Tickets() {
  return (
    <RoleGuard roles={["CUSTOMER"]}>
      <TicketList />
    </RoleGuard>
  );
}
function TicketList() {
  const query = useMyTickets();
  const all = query.data ?? [];
  const { events } = useEventsById(all.map((t) => t.eventId));
  const [tab, setTab] = useState("all");
  const [selected, setSelected] = useState<Ticket | null>(null);
  const tickets = all.filter(
    (t) =>
      tab === "all" ||
      (tab === "active"
        ? ["ISSUED", "CHECKED_IN"].includes(t.status)
        : ["CANCELLED", "CHECKED_OUT"].includes(t.status)),
  );
  // Vé đã hủy/đã ra cổng không còn mã QR hợp lệ.
  const usable = selected && ["ISSUED", "CHECKED_IN"].includes(selected.status);
  const qr = useTicketQr(usable ? selected.id : null);
  const selectedEvent = selected ? eventView(events, selected.eventId) : null;
  return (
    <div className="container page-section">
      <PageTitle
        eyebrow="NHỮNG CUỘC HẸN CỦA BẠN"
        title="Ví vé của tôi"
        description="Mọi trải nghiệm, gọn trong một nơi."
        action={
          <Button variant="secondary" asChild>
            <Link href="/explore">
              Khám phá thêm
              <ArrowUpRight size={16} />
            </Link>
          </Button>
        }
      />
      <div className="tab-row">
        {[
          ["all", "Tất cả vé"],
          ["active", "Sắp trải nghiệm"],
          ["past", "Đã sử dụng / Đã hủy"],
        ].map(([v, label]) => (
          <button
            key={v}
            className={tab === v ? "selected" : ""}
            onClick={() => setTab(v)}
          >
            {label}
          </button>
        ))}
      </div>
      <DataBoundary
        loading={query.isLoading}
        error={query.isError}
        onRetry={() => void query.refetch()}
        empty={!tickets.length}
        title="Chưa có vé ở đây"
        description="Khám phá một sự kiện yêu thích và đặt vé để bắt đầu cuộc hẹn tiếp theo."
      >
        <div className="wallet-grid">
          {tickets.map((t) => {
            const e = eventView(events, t.eventId);
            return (
              <article className="wallet-ticket" key={t.id}>
                <div className="wallet-art">
                  <Image
                    src={e.image}
                    alt={e.title}
                    fill
                    sizes="(max-width:760px) 90vw, 40vw"
                  />
                  <div />
                  <span className="ticket-tier">
                    {t.tier}
                    {t.seatLabel && ` · Ghế ${t.seatLabel}`}
                  </span>
                </div>
                <div className="wallet-body">
                  <div className="flex justify-between items-center gap-3 mb-4">
                    <span className="muted text-[10px]">{t.id}</span>
                    <Status value={t.status} />
                  </div>
                  <h2>{e.title}</h2>
                  {e.startTime && (
                    <p>
                      <CalendarDays size={14} />
                      {dateLabel(e.startTime)} · {timeLabel(e.startTime)}
                    </p>
                  )}
                  <p>
                    <MapPin size={14} />
                    {VENUE}
                  </p>
                  <div className="wallet-holder">
                    <span>
                      Người sử dụng<strong>{t.holderName}</strong>
                    </span>
                    <Button
                      size="sm"
                      variant="secondary"
                      onClick={() => setSelected(t)}
                    >
                      <QrCode size={15} />
                      Chi tiết vé
                    </Button>
                  </div>
                </div>
              </article>
            );
          })}
        </div>
      </DataBoundary>
      <Dialog
        open={Boolean(selected)}
        onOpenChange={(open) => {
          if (!open) setSelected(null);
        }}
      >
        <DialogContent className="modal ticket-modal">
          <DialogHeader>
            <DialogTitle>Vé điện tử của bạn</DialogTitle>
            <DialogDescription>
              Đưa mã QR này cho nhân viên soát vé tại cổng.
            </DialogDescription>
          </DialogHeader>
          {selected && selectedEvent && (
            <>
              <h3 className="text-center font-semibold text-lg mt-3">
                {selectedEvent.title}
              </h3>
              <div className="qr-ticket">
                {!usable ? (
                  <>
                    <QrCode size={164} strokeWidth={1.2} />
                    <span>KHÔNG CÒN HIỆU LỰC</span>
                  </>
                ) : qr.data ? (
                  <QrImage value={qr.data.qrCodeValue} />
                ) : qr.isError ? (
                  <Button variant="secondary" onClick={() => void qr.refetch()}>
                    Tải lại mã QR
                  </Button>
                ) : (
                  <div className="skeleton" style={{ width: 220, height: 220 }} />
                )}
              </div>
              <div className="text-center">
                <Status value={selected.status} />
                <p className="font-mono mt-3">{selected.id}</p>
                <p className="muted text-xs mt-2">
                  {selected.holderName} · {selected.tier}
                  {selected.seatLabel && ` · Ghế ${selected.seatLabel}`}
                </p>
              </div>
              <div className="notice-box">
                <Info size={16} />
                <p>
                  Cổng mở từ 60 phút trước giờ diễn. Mỗi vé chỉ vào cổng một
                  lần; vé đã ra cổng không thể vào lại.
                </p>
              </div>
              <div className="flex gap-3">
                <Button
                  variant="secondary"
                  className="flex-1"
                  onClick={() => {
                    navigator.clipboard
                      ?.writeText(selected.id)
                      .then(() => toast.success("Đã sao chép mã vé"));
                  }}
                >
                  <Copy size={15} />
                  Sao chép mã vé
                </Button>
                <Button
                  className="flex-1"
                  disabled={!qr.data}
                  onClick={() =>
                    qr.data &&
                    void downloadQr(qr.data.qrCodeValue, `eventhub-${selected.id}.png`)
                  }
                >
                  <Download size={15} />
                  Lưu mã QR
                </Button>
              </div>
            </>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}

export function Orders() {
  return (
    <RoleGuard roles={["CUSTOMER"]}>
      <OrderList />
    </RoleGuard>
  );
}
const ORDER_PAGE = 5;
function OrderList() {
  const query = useMyOrders(0, 100);
  const all = query.data?.orders ?? [];
  const { events } = useEventsById(all.map((o) => o.eventId));
  const [search, setSearch] = useState("");
  const [filter, setFilter] = useState("all");
  const [page, setPage] = useState(0);
  const [detail, setDetail] = useState<Order | null>(null);
  const [refund, setRefund] = useState<Order | null>(null);
  const [reason, setReason] = useState("");
  const { busy, run } = useAction();
  const refresh = useRefresh();
  const needle = search.trim().toLowerCase();
  const orders = all.filter(
    (o) =>
      (filter === "all" || o.status === filter) &&
      (o.id.toLowerCase().includes(needle) ||
        eventView(events, o.eventId).title.toLowerCase().includes(needle)),
  );
  return (
    <div className="container page-section">
      <PageTitle
        title="Đơn hàng của tôi"
        description="Theo dõi đơn hàng và những trải nghiệm bạn đã lựa chọn."
      />
      <div className="filters">
        <div className="input-wrap">
          <Search />
          <Input
            aria-label="Tìm đơn hàng"
            placeholder="Tìm mã đơn hoặc tên sự kiện..."
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
          />
        </div>
        <select
          aria-label="Lọc trạng thái đơn hàng"
          value={filter}
          onChange={(e) => {
            setFilter(e.target.value);
            setPage(0);
          }}
        >
          <option value="all">Tất cả trạng thái</option>
          <option value="PAID">Đã thanh toán</option>
          <option value="PENDING_PAYMENT">Chờ thanh toán</option>
          <option value="REFUNDED">Đã hoàn tiền</option>
          <option value="EXPIRED">Hết hạn</option>
          <option value="CANCELLED">Đã hủy</option>
        </select>
      </div>
      <DataBoundary
        loading={query.isLoading}
        error={query.isError}
        onRetry={() => void query.refetch()}
        empty={!orders.length}
        title="Chưa có đơn hàng phù hợp"
      >
        <div className="order-list">
          {orders.slice(page * ORDER_PAGE, (page + 1) * ORDER_PAGE).map((o) => (
            <OrderCard
              key={o.id}
              order={o}
              event={events.get(o.eventId)}
              onDetail={() => setDetail(o)}
              onRefund={() => {
                setRefund(o);
                setReason("");
              }}
            />
          ))}
        </div>
        <Pagination
          page={page}
          total={orders.length}
          onChange={setPage}
          pageSize={ORDER_PAGE}
        />
      </DataBoundary>
      <div className="notice-box mt-6">
        <Info size={17} />
        <p>
          Hoàn tiền áp dụng cho cả đơn, còn ít nhất 48 giờ trước sự kiện và tất
          cả vé chưa sử dụng. Số tiền hoàn là 70% giá mua, cần Admin xét duyệt.
        </p>
      </div>
      <ConfirmDialog
        open={Boolean(refund)}
        onOpenChange={(open) => {
          if (!open) setRefund(null);
        }}
        title="Gửi yêu cầu hoàn tiền?"
        description={`Bạn đang yêu cầu hoàn cả đơn ${refund?.id ?? ""}. Khi được duyệt, tất cả vé trong đơn sẽ bị hủy. Số tiền hoàn dự kiến: ${money(Math.round((refund?.total ?? 0) * 0.7))} (70% giá mua), số tiền chính thức do hệ thống xác nhận.`}
        busy={busy}
        confirmLabel="Gửi yêu cầu"
        onConfirm={() => {
          if (!reason.trim()) {
            toast.error("Vui lòng nhập lý do hoàn tiền");
            return;
          }
          if (refund)
            run(
              () => orderApi.requestRefund(refund.id, reason.trim()),
              () => {
                void refresh(["refunds", refund.id], ["orders"]);
                setRefund(null);
                toast.success("Đã gửi yêu cầu đến Admin");
              },
            );
        }}
      >
        <label className="field-label" htmlFor="refund-reason">
          Lý do hoàn tiền *
        </label>
        <Textarea
          id="refund-reason"
          value={reason}
          maxLength={500}
          onChange={(e) => setReason(e.target.value)}
          placeholder="Hãy cho chúng tôi biết lý do..."
        />
      </ConfirmDialog>
      <Dialog
        open={Boolean(detail)}
        onOpenChange={(open) => {
          if (!open) setDetail(null);
        }}
      >
        <DialogContent className="modal">
          <DialogHeader>
            <DialogTitle>Đơn hàng {detail?.id}</DialogTitle>
            <DialogDescription>
              Thông tin đã lưu tại thời điểm đặt vé.
            </DialogDescription>
          </DialogHeader>
          {detail && (
            <>
              <Status value={detail.status} />
              {detail.lines.map((l) => (
                <div className="flex justify-between" key={l.tierId}>
                  <span>
                    {l.quantity} × {l.tier}
                  </span>
                  <strong>{money(l.quantity * l.unitPrice)}</strong>
                </div>
              ))}
              <hr className="border-border" />
              <h3 className="font-semibold">Người sử dụng vé</h3>
              {detail.holders.map((h) => (
                <div key={h.seatId}>
                  <strong className="text-sm">
                    {h.name} · Ghế {h.seatLabel}
                  </strong>
                  <p className="muted text-xs">
                    {h.email || "Gửi vé về email người mua"}
                  </p>
                </div>
              ))}
              <div className="summary-total">
                <span>Tổng thanh toán</span>
                <strong>{money(detail.total)}</strong>
              </div>
            </>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}

const REFUND_TEXT: Record<string, (amount: string, reason?: string) => string> = {
  REQUESTED: (a) => `Đang chờ Admin xét duyệt hoàn ${a}.`,
  APPROVED: (a) => `Đã duyệt hoàn ${a}, đang chuyển tiền qua VNPay.`,
  PROCESSING: (a) => `Đang hoàn ${a} qua VNPay.`,
  REFUNDED: (a) => `Đã hoàn ${a}. Tất cả vé trong đơn đã hủy.`,
  FAILED: (a) => `Hoàn ${a} chưa thành công, Admin đang xử lý lại.`,
  REJECTED: (_, r) => `Yêu cầu bị từ chối${r ? `: ${r}` : "."}`,
};

function OrderCard({
  order: o,
  event,
  onDetail,
  onRefund,
}: {
  order: Order;
  event: Event | undefined;
  onDetail: () => void;
  onRefund: () => void;
}) {
  const now = useNow();
  const withRefund = o.status === "PAID" || o.status === "REFUNDED";
  const refunds = useOrderRefunds(withRefund ? o.id : undefined);
  // Yêu cầu mới nhất quyết định trạng thái hiển thị.
  const r = [...(refunds.data ?? [])].sort((a, b) =>
    b.createdAt.localeCompare(a.createdAt),
  )[0];
  const open = r && !["REJECTED"].includes(r.status);
  // Gợi ý theo điều kiện C09; server kiểm tra lại khi gửi và khi duyệt.
  const eligible =
    o.status === "PAID" &&
    !open &&
    Boolean(event) &&
    Boolean(now) &&
    Date.parse(event!.startTime) - now >= 48 * 3_600_000;
  const view = {
    title: event?.title ?? "Sự kiện đã kết thúc bán vé",
    image: event?.image ?? eventImage("", o.eventId),
  };
  return (
    <article className="order-card">
      <div className="order-card-top">
        <span>
          {o.id}
          <small>{dateLabel(o.createdAt)}</small>
        </span>
        <Status value={r && open && r.status !== "REFUNDED" ? r.status : o.status} />
      </div>
      <div className="order-card-content">
        <Image src={view.image} alt="" width={120} height={90} />
        <div>
          <h3>{view.title}</h3>
          <p>{o.lines.map((l) => `${l.quantity} ${l.tier}`).join(" · ")}</p>
          {event && <span>{dateLabel(event.startTime)}</span>}
        </div>
        <strong>{money(o.total)}</strong>
      </div>
      {r && (
        <div className="order-refund-status">
          {REFUND_TEXT[r.status]?.(money(r.amount), r.reviewReason) ?? ""}
        </div>
      )}
      <div className="order-card-actions">
        <Button variant="ghost" size="sm" onClick={onDetail}>
          Chi tiết đơn
        </Button>
        {o.status === "PENDING_PAYMENT" && (
          <Button asChild size="sm">
            <Link href={`/checkout?order=${o.id}`}>
              Tiếp tục thanh toán
              <ArrowRight size={14} />
            </Link>
          </Button>
        )}
        {o.status === "PAID" && (
          <>
            <Button
              variant="ghost"
              size="sm"
              disabled={!eligible}
              title={
                eligible
                  ? undefined
                  : "Chỉ hoàn được khi còn ít nhất 48 giờ trước sự kiện và chưa có yêu cầu đang xử lý"
              }
              onClick={onRefund}
            >
              Yêu cầu hoàn tiền
            </Button>
            <Button variant="secondary" asChild size="sm">
              <Link href="/tickets">
                <TicketIcon size={14} />
                Xem vé
              </Link>
            </Button>
          </>
        )}
      </div>
    </article>
  );
}

export function Notifications() {
  return (
    <RoleGuard roles={["CUSTOMER", "ORGANIZER", "ADMIN"]}>
      <NoticeList />
    </RoleGuard>
  );
}
function NoticeList() {
  const [unread, setUnread] = useState(false);
  const query = useNotifications(0, 50, unread);
  const count = useUnreadCount();
  const refresh = useRefresh();
  const { busy, run } = useAction();
  const notices = query.data?.notices ?? [];
  function markRead(id: string) {
    void notificationApi
      .markRead(id)
      .then(() => refresh(["notifications"]))
      .catch(() => undefined);
  }
  return (
    <div className="content-narrow">
      <PageTitle
        title="Thông báo"
        description="Những cập nhật dành riêng cho bạn."
        action={
          <Button
            variant="ghost"
            size="sm"
            disabled={busy || !notices.some((n) => !n.read)}
            onClick={() =>
              run(
                () =>
                  Promise.all(
                    notices
                      .filter((n) => !n.read)
                      .map((n) => notificationApi.markRead(n.id)),
                  ),
                () => {
                  void refresh(["notifications"]);
                  toast.success("Đã đánh dấu tất cả là đã đọc");
                },
              )
            }
          >
            <CheckCheck size={16} />
            Đọc tất cả
          </Button>
        }
      />
      <div className="tab-row">
        <button
          className={!unread ? "selected" : ""}
          onClick={() => setUnread(false)}
        >
          Tất cả
        </button>
        <button
          className={unread ? "selected" : ""}
          onClick={() => setUnread(true)}
        >
          Chưa đọc
          <span className="count-badge">{count.data ?? 0}</span>
        </button>
      </div>
      <DataBoundary
        loading={query.isLoading}
        error={query.isError}
        onRetry={() => void query.refetch()}
        empty={!notices.length}
        title="Bạn đã xem hết thông báo"
      >
        <div className="notices">
          {notices.map((n) => (
            <Link
              key={n.id}
              href={n.href}
              className={cn("notice-item", !n.read && "unread")}
              onClick={() => {
                if (!n.read) markRead(n.id);
              }}
            >
              <span className="notice-icon">
                <Bell size={20} />
              </span>
              <div>
                <h3>{n.title}</h3>
                <p>{n.content}</p>
                <small>{dateLabel(n.createdAt)}</small>
              </div>
              <ArrowUpRight size={16} />
            </Link>
          ))}
        </div>
      </DataBoundary>
    </div>
  );
}

export function Profile() {
  return (
    <RoleGuard roles={["CUSTOMER", "ORGANIZER", "ADMIN"]}>
      <ProfileLoader />
    </RoleGuard>
  );
}
function ProfileLoader() {
  const query = useProfile();
  if (query.isLoading || query.isError)
    return (
      <div className="content-narrow">
        <DataBoundary
          loading={query.isLoading}
          error={query.isError}
          onRetry={() => void query.refetch()}
        >
          {null}
        </DataBoundary>
      </div>
    );
  return (
    <ProfileForm
      initialName={query.data?.fullName ?? ""}
      initialPhone={query.data?.phoneNumber ?? ""}
      exists={Boolean(query.data)}
    />
  );
}
function ProfileForm({
  initialName,
  initialPhone,
  exists,
}: {
  initialName: string;
  initialPhone: string;
  exists: boolean;
}) {
  const { user } = useSession();
  const [name, setName] = useState(initialName || user?.name || "");
  const [phone, setPhone] = useState(initialPhone);
  const { busy, run } = useAction();
  const verify = useAction();
  const refresh = useRefresh();
  return (
    <div className="content-narrow">
      <PageTitle
        title="Tài khoản của tôi"
        description="Thông tin của bạn cho những trải nghiệm sắp tới."
      />
      <div className="panel profile-panel">
        <div className="profile-avatar">
          <UserRound size={35} />
          <div>
            <h2>{user?.name}</h2>
            <p>{user ? roleNames[user.role] : ""}</p>
          </div>
        </div>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            run(
              async () => {
                const fullName = name.trim().replace(/\s+/g, " ");
                if (fullName.length < 2)
                  throw new Error("Họ và tên cần ít nhất 2 ký tự");
                const body = { fullName, phoneNumber: phone.trim() || undefined };
                await (exists
                  ? authApi.updateProfile(body)
                  : authApi.initProfile(body));
                await session.reloadUser();
              },
              () => {
                void refresh(["profile"]);
                toast.success("Đã lưu thông tin tài khoản");
              },
            );
          }}
        >
          <div className="field">
            <label className="field-label" htmlFor="profile-name">
              Họ và tên
            </label>
            <Input
              id="profile-name"
              required
              maxLength={100}
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          </div>
          <div className="field">
            <label className="field-label" htmlFor="profile-phone">
              Số điện thoại
            </label>
            <Input
              id="profile-phone"
              type="tel"
              maxLength={20}
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
            />
          </div>
          <div className="field">
            <label className="field-label" htmlFor="profile-email">
              Địa chỉ email
            </label>
            <div className="input-wrap">
              <Mail />
              <Input id="profile-email" readOnly value={user?.email ?? ""} />
            </div>
            {user?.emailVerified ? (
              <p className="muted text-xs mt-2">Email đã được xác minh.</p>
            ) : (
              <div className="flex flex-wrap items-center gap-3 mt-2">
                <p className="muted text-xs">
                  Email chưa xác minh, bạn cần xác minh trước khi đặt vé.
                </p>
                <Button
                  type="button"
                  size="sm"
                  variant="secondary"
                  disabled={verify.busy}
                  onClick={() =>
                    verify.run(
                      () => authApi.resendVerification(),
                      () => toast.success("Đã gửi lại email xác minh"),
                    )
                  }
                >
                  Gửi lại email xác minh
                </Button>
              </div>
            )}
          </div>
          <Button disabled={busy} type="submit">
            {busy ? "Đang lưu..." : "Lưu thay đổi"}
            <Check size={16} />
          </Button>
        </form>
      </div>
      <PasswordForm />
    </div>
  );
}

export function Help() {
  return (
    <div className="content-narrow">
      <PageTitle
        eyebrow="LUÔN ĐỒNG HÀNH CÙNG BẠN"
        title="Một chút hướng dẫn, thật nhiều an tâm"
        description="Thông tin cần biết trước khi bắt đầu trải nghiệm."
      />
      <div className="panel">
        <Disclosure title="Làm thế nào để đặt vé?">
          Khám phá sự kiện → chọn khu và từng ghế → điền thông tin người sử dụng
          vé → giữ ghế → thanh toán qua VNPay trong 10 phút. Vé xuất hiện trong
          Ví vé của tôi và được gửi qua email khi thanh toán thành công.
        </Disclosure>
        <Disclosure title="Hết thời gian thanh toán thì sao?">
          Đơn chuyển sang hết hạn và ghế được trả lại. Nếu thanh toán trên VNPay
          chưa thành công, bạn có thể thanh toán lại cho tới khi hết giờ giữ
          ghế.
        </Disclosure>
        <div id="refund">
          <Disclosure title="Chính sách hoàn tiền là gì?">
            Yêu cầu hoàn cả đơn khi sự kiện còn ít nhất 48 giờ và mọi vé chưa
            dùng. Admin xét duyệt; bạn nhận lại 70% giá đã mua, giữ lại 30%.
            Điều kiện được kiểm tra lại lúc duyệt.
          </Disclosure>
        </div>
        <Disclosure title="Vé có được gửi tới người khác không?">
          Bạn nhập tên và email riêng cho từng người sử dụng. Nếu không nhập
          email, vé được gửi về email người mua. Người mua vẫn là chủ sở hữu và
          quản lý toàn bộ đơn.
        </Disclosure>
        <Disclosure title="Tôi cần xác minh email không?">
          Có. Sau khi đăng ký, mở đường dẫn trong email xác minh. Tài khoản chưa
          xác minh email chưa đặt được vé; bạn có thể gửi lại email xác minh ở
          trang Tài khoản.
        </Disclosure>
      </div>
    </div>
  );
}
