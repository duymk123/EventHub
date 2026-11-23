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
import { useDemo, useAction } from "@/hooks/use-demo";
import { demoStore } from "@/lib/mock/store";
import { mock, refundEligible } from "@/lib/mock/engine";
import { VENUE, roleNames } from "@/lib/mock/seed";
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
import type { Ticket, Order } from "@/types";
import { toast } from "sonner";
export function Tickets() {
  return (
    <RoleGuard roles={["CUSTOMER"]}>
      <TicketList />
    </RoleGuard>
  );
}
function TicketList() {
  const state = useDemo();
  const [tab, setTab] = useState("all");
  const [selected, setSelected] = useState<Ticket | null>(null);
  const tickets = state.tickets.filter(
    (t) =>
      t.customerId === state.userId &&
      (tab === "all" ||
        (tab === "active"
          ? ["ISSUED", "CHECKED_IN"].includes(t.status)
          : ["CANCELLED", "CHECKED_OUT"].includes(t.status))),
  );
  const selectedNow = state.tickets.find((t) => t.id === selected?.id);
  const selectedEvent = state.events.find((e) => e.id === selectedNow?.eventId);
  function download() {
    if (!selectedNow || !selectedEvent) return;
    const body = `EVENTHUB - VE TRAI NGHIEM (KHONG CO GIA TRI VAO CONG)\n\n${selectedEvent.title}\n${selectedNow.id}\n${selectedNow.tier}${selectedNow.seatLabel ? ` - GHE ${selectedNow.seatLabel}` : ""}\n${selectedNow.holderName}\n${dateLabel(selectedEvent.startTime)} - ${timeLabel(selectedEvent.startTime)}\n${VENUE}`;
    const url = URL.createObjectURL(
      new Blob([body], { type: "text/plain;charset=utf-8" }),
    );
    const a = document.createElement("a");
    a.href = url;
    a.download = `${selectedNow.id}.txt`;
    a.click();
    URL.revokeObjectURL(url);
    toast.success("Đã lưu thông tin vé trải nghiệm");
  }
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
        empty={!tickets.length}
        title="Chưa có vé ở đây"
        description="Khám phá một sự kiện yêu thích và đặt vé để bắt đầu cuộc hẹn tiếp theo."
      >
        <div className="wallet-grid">
          {tickets.map((t) => {
            const e = state.events.find((e) => e.id === t.eventId)!;
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
                  <p>
                    <CalendarDays size={14} />
                    {dateLabel(e.startTime)} · {timeLabel(e.startTime)}
                  </p>
                  <p>
                    <MapPin size={14} />
                    EventHub, TP. Hồ Chí Minh
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
              Mã minh họa để trải nghiệm giao diện, không có giá trị vào cổng
              thật.
            </DialogDescription>
          </DialogHeader>
          {selectedNow && selectedEvent && (
            <>
              <h3 className="text-center font-semibold text-lg mt-3">
                {selectedEvent.title}
              </h3>
              <div className="qr-ticket">
                <QrCode size={164} strokeWidth={1.2} />
                <span>DEMO</span>
              </div>
              <div className="text-center">
                <Status value={selectedNow.status} />
                <p className="font-mono mt-3">{selectedNow.id}</p>
                <p className="muted text-xs mt-2">
                  {selectedNow.holderName} · {selectedNow.tier}
                  {selectedNow.seatLabel && ` · Ghế ${selectedNow.seatLabel}`}
                </p>
              </div>
              <div className="notice-box">
                <Info size={16} />
                <p>
                  Để thử check-in, đổi sang Ban tổ chức → Kiểm soát vé và nhập
                  mã vé ở trên. Vé đã ra cổng không thể vào lại.
                </p>
              </div>
              <div className="flex gap-3">
                <Button
                  variant="secondary"
                  className="flex-1"
                  onClick={() => {
                    navigator.clipboard
                      ?.writeText(selectedNow.id)
                      .then(() => toast.success("Đã sao chép mã vé"));
                  }}
                >
                  <Copy size={15} />
                  Sao chép mã
                </Button>
                <Button className="flex-1" onClick={download}>
                  <Download size={15} />
                  Lưu thông tin
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
function OrderList() {
  const state = useDemo();
  const [search, setSearch] = useState("");
  const [filter, setFilter] = useState("all");
  const [page, setPage] = useState(0);
  const [detail, setDetail] = useState<Order | null>(null);
  const [refund, setRefund] = useState<Order | null>(null);
  const [reason, setReason] = useState("");
  const { busy, run } = useAction();
  const orders = state.orders.filter(
    (o) =>
      o.customerId === state.userId &&
      (filter === "all" || o.status === filter) &&
      (o.id.toLowerCase().includes(search.toLowerCase()) ||
        state.events
          .find((e) => e.id === o.eventId)
          ?.title.toLowerCase()
          .includes(search.toLowerCase())),
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
        </select>
      </div>
      <DataBoundary empty={!orders.length} title="Chưa có đơn hàng phù hợp">
        <div className="order-list">
          {orders.slice(page * 5, (page + 1) * 5).map((o) => {
            const e = state.events.find((e) => e.id === o.eventId)!;
            const r = state.refunds.find((r) => r.orderId === o.id);
            return (
              <article className="order-card" key={o.id}>
                <div className="order-card-top">
                  <span>
                    {o.id}
                    <small>{dateLabel(o.createdAt)}</small>
                  </span>
                  <Status
                    value={r?.status === "REQUESTED" ? "REQUESTED" : o.status}
                  />
                </div>
                <div className="order-card-content">
                  <Image src={e.image} alt="" width={120} height={90} />
                  <div>
                    <h3>{e.title}</h3>
                    <p>
                      {o.lines
                        .map((l) => `${l.quantity} ${l.tier}`)
                        .join(" · ")}
                    </p>
                    <span>{dateLabel(e.startTime)}</span>
                  </div>
                  <strong>{money(o.total)}</strong>
                </div>
                {r && (
                  <div className="order-refund-status">
                    {r.status === "REQUESTED"
                      ? `Đang chờ Admin xét duyệt hoàn ${money(r.amount)}.`
                      : r.status === "REJECTED"
                        ? `Yêu cầu bị từ chối: ${r.reviewReason}`
                        : `Đã hoàn ${money(r.amount)}. Tất cả vé trong đơn đã hủy.`}
                  </div>
                )}
                <div className="order-card-actions">
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => setDetail(o)}
                  >
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
                        disabled={
                          !refundEligible(state, o) || r?.status === "REQUESTED"
                        }
                        onClick={() => {
                          setRefund(o);
                          setReason("");
                        }}
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
          })}
        </div>
        <Pagination page={page} total={orders.length} onChange={setPage} />
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
        description={`Bạn đang yêu cầu hoàn cả đơn ${refund?.id ?? ""}. Khi được duyệt, tất cả vé sẽ hủy. Số tiền hoàn: ${money(Math.round((refund?.total ?? 0) * 0.7))}; giữ lại 30% giá mua.`}
        busy={busy}
        confirmLabel="Gửi yêu cầu"
        onConfirm={() => {
          if (!reason.trim()) {
            toast.error("Vui lòng nhập lý do hoàn tiền");
            return;
          }
          if (refund)
            run(
              () => mock.requestRefund(refund.id, reason),
              () => {
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
              {detail.holders.map((h, i) => (
                <div key={i}>
                  <strong className="text-sm">{h.name}</strong>
                  <p className="muted text-xs">{h.email}</p>
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
export function Notifications() {
  const state = useDemo();
  const [unread, setUnread] = useState(false);
  const notices = state.notices.filter(
    (n) => n.userId === state.userId && (!unread || !n.read),
  );
  return (
    <RoleGuard roles={["CUSTOMER", "ORGANIZER", "ADMIN"]}>
      <div className="content-narrow">
        <PageTitle
          title="Thông báo"
          description="Những cập nhật dành riêng cho bạn."
          action={
            <Button
              variant="ghost"
              size="sm"
              onClick={() => {
                demoStore.set((s) => ({
                  ...s,
                  notices: s.notices.map((n) =>
                    n.userId === s.userId ? { ...n, read: true } : n,
                  ),
                }));
                toast.success("Đã đánh dấu tất cả là đã đọc");
              }}
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
            <span className="count-badge">
              {
                state.notices.filter(
                  (n) => n.userId === state.userId && !n.read,
                ).length
              }
            </span>
          </button>
        </div>
        <DataBoundary empty={!notices.length} title="Bạn đã xem hết thông báo">
          <div className="notices">
            {notices.map((n) => (
              <Link
                key={n.id}
                href={n.href}
                className={cn("notice-item", !n.read && "unread")}
                onClick={() =>
                  demoStore.set((s) => ({
                    ...s,
                    notices: s.notices.map((x) =>
                      x.id === n.id ? { ...x, read: true } : x,
                    ),
                  }))
                }
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
    </RoleGuard>
  );
}
export function Profile() {
  return (
    <RoleGuard roles={["CUSTOMER", "ORGANIZER", "ADMIN"]}>
      <ProfileForm />
    </RoleGuard>
  );
}
function ProfileForm() {
  const { user } = useDemo();
  const [name, setName] = useState(user?.name ?? "");
  const { busy, run } = useAction();
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
            <p>{roleNames[user!.role]}</p>
          </div>
        </div>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            run(
              async () => {
                await new Promise((r) => setTimeout(r, 500));
                if (name.trim().length < 2)
                  throw new Error("Họ và tên cần ít nhất 2 ký tự");
                demoStore.set((s) => ({
                  ...s,
                  users: s.users.map((u) =>
                    u.id === user!.id ? { ...u, name: name.trim() } : u,
                  ),
                }));
              },
              () => toast.success("Đã lưu thông tin trải nghiệm"),
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
              value={name}
              onChange={(e) => setName(e.target.value)}
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
            <p className="muted text-xs mt-2">
              Email tài khoản mẫu được giữ cố định trong bản trải nghiệm.
            </p>
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
          Khám phá sự kiện → chọn hạng và số lượng → điền thông tin từng người
          dùng vé → giữ vé → thanh toán trong 10 phút. Vé xuất hiện trong Ví vé
          của tôi khi thanh toán thành công.
        </Disclosure>
        <Disclosure title="Hết thời gian thanh toán thì sao?">
          Đơn chuyển sang hết hạn và vé được trả lại tồn kho. Bạn có thể chọn
          lại vé nếu vẫn còn chỗ.
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
          email, vé dùng email người mua. Người mua vẫn là chủ sở hữu và quản lý
          toàn bộ đơn.
        </Disclosure>
        <Disclosure title="Bản trải nghiệm này có giao dịch thật không?">
          Không. Tất cả dữ liệu và thao tác được mô phỏng trên trình duyệt. Bạn
          có thể đổi vai trò, thử trạng thái lỗi/rỗng/đang tải và khôi phục dữ
          liệu tại bảng Trải nghiệm.
        </Disclosure>
      </div>
    </div>
  );
}
