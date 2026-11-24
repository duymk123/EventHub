"use client";
import { useState } from "react";
import {
  Search,
  Check,
  X,
  ArrowUpRight,
  LockKeyhole,
  UnlockKeyhole,
  Plus,
  Pencil,
  Save,
  Mail,
  Bell,
  Info,
  ShieldCheck,
  KeyRound,
  Trash2,
  RotateCcw,
  Copy,
} from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
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
  useAdminEvents,
  useAdminRefunds,
  useAdminUsers,
  useCategories,
  useReconciliation,
  useRefresh,
  useSession,
  useTemplates,
  useTicketTypes,
} from "@/hooks/use-api";
import { adminUserApi, eventApi, notificationApi, orderApi } from "@/lib/api/endpoints";
import { roleNames, VENUE } from "@/lib/labels";
import { money, dateLabel } from "@/lib/utils";
import {
  PageTitle,
  DataBoundary,
  Status,
  Pagination,
  ConfirmDialog,
} from "../shared";
import type { Event, EventStatus, Refund, User, Template, Role } from "@/types";
import type { CategoryDto, ReconciliationDto, RefundStatusDto } from "@/types/api";

export function EventReviews() {
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("SUBMITTED");
  const query = useAdminEvents(status === "all" ? undefined : (status as EventStatus));
  const refresh = useRefresh();
  const [page, setPage] = useState(0);
  const [detail, setDetail] = useState<Event | null>(null);
  const [decision, setDecision] = useState<{
    event: Event;
    approve: boolean;
  } | null>(null);
  const [reason, setReason] = useState("");
  const { busy, run } = useAction();
  const list = (query.data ?? []).filter((e) =>
    e.title.toLowerCase().includes(search.trim().toLowerCase()),
  );
  const decide = (event: Event, approve: boolean) => {
    setDetail(null);
    setReason("");
    setDecision({ event, approve });
  };
  return (
    <>
      <PageTitle
        title="Duyệt sự kiện"
        description="Kiểm tra nội dung và cấu hình trước khi sự kiện được mở bán."
      />
      <div className="filters">
        <div className="input-wrap">
          <Search />
          <Input
            aria-label="Tìm sự kiện cần duyệt"
            placeholder="Tìm theo tên sự kiện..."
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
          />
        </div>
        <select
          aria-label="Trạng thái xét duyệt"
          value={status}
          onChange={(e) => {
            setStatus(e.target.value);
            setPage(0);
          }}
        >
          <option value="SUBMITTED">Chờ duyệt</option>
          <option value="all">Tất cả trạng thái</option>
          <option value="APPROVED">Đã duyệt</option>
          <option value="REJECTED">Đã từ chối</option>
          <option value="PUBLISHED">Đang mở bán</option>
          <option value="CLOSED">Đã đóng bán</option>
        </select>
      </div>
      <DataBoundary
        loading={query.isLoading}
        error={query.isError}
        onRetry={() => void query.refetch()}
        empty={!list.length}
        title="Đã xử lý hết sự kiện chờ duyệt"
        description="Bạn có thể đổi bộ lọc để xem những sự kiện khác."
      >
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>SỰ KIỆN</th>
                <th>GỬI LÚC</th>
                <th>THỜI GIAN</th>
                <th>TRẠNG THÁI</th>
                <th>THAO TÁC</th>
              </tr>
            </thead>
            <tbody>
              {list.slice(page * 5, (page + 1) * 5).map((e) => (
                <tr key={e.id}>
                  <td>
                    <strong className="truncate-cell" title={e.title}>
                      {e.title}
                    </strong>
                    <span className="sub">{e.category}</span>
                  </td>
                  <td>{e.createdAt ? dateLabel(e.createdAt, true) : ""}</td>
                  <td>{dateLabel(e.startTime)}</td>
                  <td>
                    <Status value={e.status} />
                  </td>
                  <td>
                    <Button variant="secondary" size="sm" onClick={() => setDetail(e)}>
                      Xem hồ sơ
                      <ArrowUpRight size={13} />
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <Pagination page={page} total={list.length} onChange={setPage} />
      </DataBoundary>
      <Dialog
        open={Boolean(detail)}
        onOpenChange={(open) => {
          if (!open) setDetail(null);
        }}
      >
        <DialogContent className="modal review-modal">
          <DialogHeader>
            <DialogTitle>{detail?.title}</DialogTitle>
            <DialogDescription>Hồ sơ sự kiện</DialogDescription>
          </DialogHeader>
          {detail && <ReviewDetail event={detail} onDecide={decide} />}
        </DialogContent>
      </Dialog>
      <ConfirmDialog
        open={Boolean(decision)}
        onOpenChange={(open) => {
          if (!open) setDecision(null);
        }}
        title={decision?.approve ? "Duyệt sự kiện này?" : "Từ chối hồ sơ sự kiện?"}
        description={
          decision?.approve
            ? "Sự kiện được chuyển sang Đã duyệt. Ban tổ chức sẽ chủ động mở bán sau đó."
            : "Ban tổ chức sẽ nhận được lý do và có thể sửa hồ sơ để gửi duyệt lại."
        }
        confirmLabel={decision?.approve ? "Duyệt" : "Từ chối"}
        destructive={!decision?.approve}
        busy={busy}
        onConfirm={() => {
          if (!decision) return;
          if (!decision.approve && !reason.trim()) {
            toast.error("Vui lòng nhập lý do từ chối");
            return;
          }
          run(
            () =>
              eventApi.review(
                decision.event.id,
                decision.approve ? "APPROVE" : "REJECT",
                decision.approve ? undefined : reason.trim(),
              ),
            () => {
              setDecision(null);
              void refresh(["events"]);
              toast.success("Đã cập nhật kết quả xét duyệt");
            },
          );
        }}
      >
        {decision && !decision.approve && (
          <div>
            <label className="field-label" htmlFor="event-reason">
              Lý do từ chối *
            </label>
            <Textarea
              id="event-reason"
              value={reason}
              maxLength={1000}
              onChange={(e) => setReason(e.target.value)}
              placeholder="Nội dung cần ban tổ chức bổ sung hoặc điều chỉnh..."
            />
          </div>
        )}
      </ConfirmDialog>
    </>
  );
}
function ReviewDetail({
  event,
  onDecide,
}: {
  event: Event;
  onDecide: (event: Event, approve: boolean) => void;
}) {
  const types = useTicketTypes(event.id);
  return (
    <>
      <Status value={event.status} />
      <dl className="detail-facts">
        <div>
          <dt>Thời gian</dt>
          <dd>
            {dateLabel(event.startTime, true)} – {dateLabel(event.endTime, true)}
          </dd>
        </div>
        <div>
          <dt>Địa điểm</dt>
          <dd>{VENUE}</dd>
        </div>
        <div>
          <dt>Danh mục</dt>
          <dd>{event.category}</dd>
        </div>
      </dl>
      <p className="text-sm leading-7 muted">{event.description}</p>
      <div className="table-scroll">
        <table>
          <thead>
            <tr>
              <th>HẠNG VÉ</th>
              <th>GIÁ</th>
              <th>SỐ GHẾ</th>
              <th>HOẠT ĐỘNG</th>
            </tr>
          </thead>
          <tbody>
            {types.isLoading && (
              <tr>
                <td colSpan={4}>Đang tải hạng vé…</td>
              </tr>
            )}
            {!types.isLoading && !(types.data ?? []).length && (
              <tr>
                <td colSpan={4} className="danger-note">
                  Sự kiện chưa cấu hình hạng vé.
                </td>
              </tr>
            )}
            {(types.data ?? []).map((t) => (
              <tr key={t.id}>
                <td>{t.name}</td>
                <td>{money(t.price)}</td>
                <td>
                  {t.quota} ({t.rowCount} × {t.seatsPerRow})
                </td>
                <td>{t.active ? "Có" : "Không"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {event.reviewReason && (
        <p className="danger-note text-sm">Lý do: {event.reviewReason}</p>
      )}
      {event.status === "SUBMITTED" && (
        <div className="form-actions">
          <Button variant="destructive" onClick={() => onDecide(event, false)}>
            <X size={16} />
            Từ chối
          </Button>
          <Button onClick={() => onDecide(event, true)}>
            <Check size={16} />
            Duyệt sự kiện
          </Button>
        </div>
      )}
    </>
  );
}

type RefundAction = "approve" | "reject" | "retry";
export function RefundReviews() {
  const [status, setStatus] = useState<RefundStatusDto | "all">("REQUESTED");
  const query = useAdminRefunds(status === "all" ? undefined : status);
  const refresh = useRefresh();
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const [decision, setDecision] = useState<{
    refund: Refund;
    action: RefundAction;
  } | null>(null);
  const [reason, setReason] = useState("");
  const { busy, run } = useAction();
  const list = (query.data ?? []).filter((r) =>
    r.orderId.toLowerCase().includes(search.trim().toLowerCase()),
  );
  const titles: Record<RefundAction, string> = {
    approve: "Xác nhận hoàn tiền toàn bộ đơn?",
    reject: "Từ chối yêu cầu hoàn tiền?",
    retry: "Thử hoàn tiền lại qua VNPay?",
  };
  return (
    <>
      <PageTitle
        title="Yêu cầu hoàn tiền"
        description="Hoàn 70% giá mua cho toàn bộ đơn đủ điều kiện."
      />
      <div className="notice-box mb-6">
        <ShieldCheck size={18} />
        <p>
          Hệ thống kiểm tra lại khi duyệt: đơn đã thanh toán, tất cả vé chưa sử
          dụng, sự kiện còn ít nhất 48 giờ. Sau khi hoàn thành, toàn bộ vé của
          đơn bị hủy.
        </p>
      </div>
      <div className="filters">
        <div className="input-wrap">
          <Search />
          <Input
            aria-label="Tìm yêu cầu hoàn tiền"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
            placeholder="Tìm theo mã đơn..."
          />
        </div>
        <select
          aria-label="Trạng thái hoàn tiền"
          value={status}
          onChange={(e) => {
            setStatus(e.target.value as RefundStatusDto | "all");
            setPage(0);
          }}
        >
          <option value="REQUESTED">Chờ xử lý</option>
          <option value="all">Tất cả yêu cầu</option>
          <option value="PROCESSING">Đang hoàn tiền</option>
          <option value="FAILED">Hoàn tiền lỗi</option>
          <option value="REFUNDED">Đã hoàn tiền</option>
          <option value="REJECTED">Đã từ chối</option>
        </select>
      </div>
      <DataBoundary
        loading={query.isLoading}
        error={query.isError}
        onRetry={() => void query.refetch()}
        empty={!list.length}
        title="Chưa có yêu cầu phù hợp"
        description="Yêu cầu mới xuất hiện khi khách gửi từ trang Đơn hàng."
      >
        <div className="refund-review-list">
          {list.slice(page * 5, (page + 1) * 5).map((r) => (
            <article className="panel" key={r.id}>
              <div className="section-heading">
                <div>
                  <span className="eyebrow">Đơn {r.orderId}</span>
                  <p className="muted text-xs mt-2">
                    Gửi lúc {dateLabel(r.createdAt, true)}
                  </p>
                </div>
                <Status value={r.status} />
              </div>
              <div className="refund-review-body">
                <div>
                  <span className="field-label">Lý do yêu cầu</span>
                  <p>{r.reason}</p>
                  {r.reviewReason && <p className="muted mt-2">Phản hồi: {r.reviewReason}</p>}
                  {r.failureReason && (
                    <p className="danger-note mt-3">Lỗi hoàn tiền: {r.failureReason}</p>
                  )}
                </div>
                <div>
                  <span className="field-label">Số tiền hoàn (70%)</span>
                  <strong>{money(r.amount)}</strong>
                  {r.refundedAt && <small>Hoàn lúc {dateLabel(r.refundedAt, true)}</small>}
                </div>
              </div>
              {(r.status === "REQUESTED" || r.status === "FAILED") && (
                <div className="form-actions">
                  {r.status === "REQUESTED" ? (
                    <>
                      <Button
                        variant="secondary"
                        onClick={() => {
                          setReason("");
                          setDecision({ refund: r, action: "reject" });
                        }}
                      >
                        Từ chối
                      </Button>
                      <Button
                        variant="destructive"
                        onClick={() => setDecision({ refund: r, action: "approve" })}
                      >
                        Duyệt hoàn tiền
                      </Button>
                    </>
                  ) : (
                    <Button onClick={() => setDecision({ refund: r, action: "retry" })}>
                      <RotateCcw size={15} />
                      Thử hoàn lại
                    </Button>
                  )}
                </div>
              )}
            </article>
          ))}
        </div>
        <Pagination page={page} total={list.length} onChange={setPage} />
      </DataBoundary>
      <Reconciliation />
      <ConfirmDialog
        open={Boolean(decision)}
        onOpenChange={(open) => {
          if (!open) setDecision(null);
        }}
        title={decision ? titles[decision.action] : ""}
        description={
          decision?.action === "reject"
            ? "Khách hàng sẽ nhận thông báo kèm lý do từ chối. Vé vẫn giữ trạng thái hiện tại."
            : `Hoàn ${money(decision?.refund.amount ?? 0)} qua VNPay cho khách. Tất cả vé thuộc đơn sẽ bị hủy và ghế được trả lại. Thao tác này không hoàn tác được.`
        }
        confirmLabel={
          decision?.action === "reject" ? "Từ chối" : decision?.action === "retry" ? "Thử lại" : "Hoàn tiền"
        }
        destructive={decision?.action !== "reject"}
        busy={busy}
        onConfirm={() => {
          if (!decision) return;
          if (decision.action === "reject" && !reason.trim()) {
            toast.error("Vui lòng nhập lý do từ chối");
            return;
          }
          const id = decision.refund.id;
          run(
            () =>
              decision.action === "approve"
                ? orderApi.approveRefund(id)
                : decision.action === "retry"
                  ? orderApi.retryRefund(id)
                  : orderApi.rejectRefund(id, reason.trim()),
            () => {
              setDecision(null);
              void refresh(["refunds"]);
              toast.success("Đã xử lý yêu cầu hoàn tiền");
            },
          );
        }}
      >
        {decision?.action === "reject" && (
          <div>
            <label className="field-label" htmlFor="refund-reason">
              Lý do từ chối *
            </label>
            <Textarea
              id="refund-reason"
              value={reason}
              maxLength={1000}
              onChange={(e) => setReason(e.target.value)}
            />
          </div>
        )}
      </ConfirmDialog>
    </>
  );
}
/** C21: giao dịch VNPay thành công nhưng đơn không nhận (đơn đã hết hạn/đã trả bằng giao dịch khác) cần hoàn tay. */
function Reconciliation() {
  const query = useReconciliation();
  const refresh = useRefresh();
  const { busy, run } = useAction();
  const [target, setTarget] = useState<ReconciliationDto | null>(null);
  const [note, setNote] = useState("");
  const pending = (query.data ?? []).filter((p) => !p.reconciledAt);
  return (
    <section className="panel mt-6">
      <div className="section-heading">
        <div>
          <h2>Đối soát thanh toán</h2>
          <p className="muted text-xs mt-2">
            Tiền đã trừ trên VNPay nhưng đơn không được xác nhận. Hoàn tay trên cổng
            VNPay rồi đánh dấu đã xử lý.
          </p>
        </div>
      </div>
      <DataBoundary
        loading={query.isLoading}
        error={query.isError}
        onRetry={() => void query.refetch()}
        empty={!pending.length}
        title="Không có giao dịch cần đối soát"
      >
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>MÃ GIAO DỊCH</th>
                <th>ĐƠN</th>
                <th>KHÁCH</th>
                <th>SỐ TIỀN</th>
                <th>THỜI GIAN</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {pending.map((p) => (
                <tr key={p.paymentId}>
                  <td>{p.providerTransactionId ?? p.providerTxnRef}</td>
                  <td>
                    {p.orderId}
                    <span className="sub">
                      <Status value={p.orderStatus} />
                    </span>
                  </td>
                  <td>{p.customerEmail}</td>
                  <td>{money(p.amount)}</td>
                  <td>{dateLabel(p.updatedAt, true)}</td>
                  <td>
                    <Button
                      size="sm"
                      variant="secondary"
                      onClick={() => {
                        setNote("");
                        setTarget(p);
                      }}
                    >
                      Đã xử lý
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </DataBoundary>
      <ConfirmDialog
        open={Boolean(target)}
        onOpenChange={(open) => {
          if (!open) setTarget(null);
        }}
        title="Đánh dấu giao dịch đã xử lý?"
        description={`Xác nhận bạn đã hoàn ${money(target?.amount ?? 0)} cho khách trên cổng VNPay. Giao dịch sẽ rời khỏi danh sách đối soát.`}
        confirmLabel="Đánh dấu đã xử lý"
        destructive={false}
        busy={busy}
        onConfirm={() => {
          if (!target) return;
          if (!note.trim()) {
            toast.error("Vui lòng ghi chú cách đã xử lý");
            return;
          }
          run(
            () => orderApi.resolvePayment(target.paymentId, note.trim()),
            () => {
              setTarget(null);
              void refresh(["reconciliation"]);
              toast.success("Đã cập nhật đối soát");
            },
          );
        }}
      >
        <label className="field-label" htmlFor="reconcile-note">
          Ghi chú xử lý *
        </label>
        <Textarea
          id="reconcile-note"
          value={note}
          maxLength={500}
          onChange={(e) => setNote(e.target.value)}
          placeholder="Ví dụ: đã hoàn trên VNPay ngày..."
        />
      </ConfirmDialog>
    </section>
  );
}

const USER_PAGE = 10;
export function UsersAdmin() {
  const { user: me } = useSession();
  const [search, setSearch] = useState("");
  const [role, setRole] = useState<Role | "all">("all");
  const [page, setPage] = useState(0);
  const query = useAdminUsers({
    page,
    size: USER_PAGE,
    search: search.trim() || undefined,
    role: role === "all" ? undefined : role,
  });
  const refresh = useRefresh();
  const [selected, setSelected] = useState<User | null>(null);
  const [roleChange, setRoleChange] = useState<{ user: User; role: Role } | null>(null);
  const [reset, setReset] = useState<User | null>(null);
  const [temporary, setTemporary] = useState<string | null>(null);
  const { busy, run } = useAction();
  const users = query.data?.users ?? [];
  const done = (message: string) => {
    void refresh(["admin-users"]);
    toast.success(message);
  };
  return (
    <>
      <PageTitle
        title="Quản lý tài khoản"
        description="Theo dõi vai trò và quyền truy cập của người dùng."
      />
      <div className="filters">
        <div className="input-wrap">
          <Search />
          <Input
            aria-label="Tìm tài khoản"
            placeholder="Tên hoặc email..."
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
          />
        </div>
        <select
          aria-label="Lọc vai trò"
          value={role}
          onChange={(e) => {
            setRole(e.target.value as Role | "all");
            setPage(0);
          }}
        >
          <option value="all">Tất cả vai trò</option>
          {Object.entries(roleNames).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
      </div>
      <DataBoundary
        loading={query.isLoading}
        error={query.isError}
        onRetry={() => void query.refetch()}
        empty={!users.length}
        title="Không tìm thấy tài khoản"
      >
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>NGƯỜI DÙNG</th>
                <th>VAI TRÒ</th>
                <th>TRẠNG THÁI</th>
                <th>THAO TÁC</th>
              </tr>
            </thead>
            <tbody>
              {users.map((u) => (
                <tr key={u.id}>
                  <td>
                    <div className="user-cell">
                      <span className="avatar">{u.name.split(" ").at(-1)?.slice(0, 1)}</span>
                      <div>
                        <strong>{u.name}</strong>
                        <span className="sub">
                          {u.email}
                          {!u.emailVerified && " · chưa xác minh email"}
                        </span>
                      </div>
                    </div>
                  </td>
                  <td>
                    <select
                      aria-label={`Vai trò của ${u.name}`}
                      disabled={u.id === me?.id || busy}
                      value={u.role}
                      onChange={(e) => setRoleChange({ user: u, role: e.target.value as Role })}
                    >
                      {Object.entries(roleNames).map(([r, label]) => (
                        <option value={r} key={r}>
                          {label}
                        </option>
                      ))}
                    </select>
                  </td>
                  <td>
                    <Status value={u.status} />
                  </td>
                  <td>
                    {u.id === me?.id ? (
                      <span className="muted text-xs">Tài khoản của bạn</span>
                    ) : (
                      <div className="flex flex-wrap gap-2">
                        <Button size="sm" variant="secondary" onClick={() => setSelected(u)}>
                          {u.status === "ACTIVE" ? <LockKeyhole size={14} /> : <UnlockKeyhole size={14} />}{" "}
                          {u.status === "ACTIVE" ? "Khóa" : "Mở khóa"}
                        </Button>
                        <Button size="sm" variant="ghost" onClick={() => setReset(u)}>
                          <KeyRound size={14} />
                          Đặt lại mật khẩu
                        </Button>
                      </div>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <Pagination page={page} total={query.data?.total ?? 0} onChange={setPage} pageSize={USER_PAGE} />
      </DataBoundary>
      <ConfirmDialog
        open={Boolean(selected)}
        onOpenChange={(open) => {
          if (!open) setSelected(null);
        }}
        title={selected?.status === "ACTIVE" ? "Khóa tài khoản này?" : "Mở khóa tài khoản này?"}
        description={
          selected?.status === "ACTIVE"
            ? `${selected.name} sẽ không thể đăng nhập, phiên đang mở bị đăng xuất. Đơn hàng và vé được giữ lại.`
            : "Người dùng có thể tiếp tục đăng nhập và sử dụng tài khoản."
        }
        confirmLabel={selected?.status === "ACTIVE" ? "Khóa tài khoản" : "Mở khóa"}
        destructive={selected?.status === "ACTIVE"}
        busy={busy}
        onConfirm={() =>
          selected &&
          run(
            () => (selected.status === "ACTIVE" ? adminUserApi.lock(selected.id) : adminUserApi.unlock(selected.id)),
            () => {
              setSelected(null);
              done("Đã cập nhật tài khoản");
            },
          )
        }
      />
      <ConfirmDialog
        open={Boolean(roleChange)}
        onOpenChange={(open) => {
          if (!open) setRoleChange(null);
        }}
        title="Chuyển vai trò tài khoản?"
        description={
          roleChange
            ? `${roleChange.user.name} sẽ chuyển từ ${roleNames[roleChange.user.role]} sang ${roleNames[roleChange.role]}. Quyền mới áp dụng từ lần đăng nhập/làm mới phiên tiếp theo.`
            : ""
        }
        confirmLabel="Chuyển vai trò"
        destructive={false}
        busy={busy}
        onConfirm={() =>
          roleChange &&
          run(
            () => adminUserApi.assignRole(roleChange.user.id, roleChange.role),
            () => {
              setRoleChange(null);
              done("Đã cập nhật vai trò");
            },
          )
        }
      />
      <ConfirmDialog
        open={Boolean(reset)}
        onOpenChange={(open) => {
          if (!open) setReset(null);
        }}
        title="Đặt lại mật khẩu?"
        description={`Gửi email đặt lại mật khẩu cho ${reset?.email ?? ""}, hoặc tạo mật khẩu tạm (người dùng phải đổi khi đăng nhập).`}
        confirmLabel="Gửi email đặt lại"
        destructive={false}
        busy={busy}
        onConfirm={() =>
          reset &&
          run(
            () => adminUserApi.resetPassword(reset.id, "SEND_EMAIL"),
            () => {
              setReset(null);
              toast.success("Đã gửi email đặt lại mật khẩu");
            },
          )
        }
      >
        <Button
          variant="secondary"
          disabled={busy}
          onClick={() =>
            reset &&
            run(
              () => adminUserApi.resetPassword(reset.id, "SET_TEMPORARY_PASSWORD"),
              (r) => {
                setReset(null);
                setTemporary(r.temporaryPassword);
              },
            )
          }
        >
          <KeyRound size={15} />
          Tạo mật khẩu tạm thay vì gửi email
        </Button>
      </ConfirmDialog>
      <Dialog
        open={Boolean(temporary)}
        onOpenChange={(open) => {
          if (!open) setTemporary(null);
        }}
      >
        <DialogContent className="modal">
          <DialogHeader>
            <DialogTitle>Mật khẩu tạm</DialogTitle>
            <DialogDescription>
              Chỉ hiển thị một lần. Gửi cho người dùng qua kênh an toàn; họ phải đổi mật
              khẩu khi đăng nhập.
            </DialogDescription>
          </DialogHeader>
          <p className="font-mono text-lg text-center my-4">{temporary}</p>
          <Button
            onClick={() =>
              temporary &&
              navigator.clipboard?.writeText(temporary).then(() => toast.success("Đã sao chép"))
            }
          >
            <Copy size={15} />
            Sao chép
          </Button>
        </DialogContent>
      </Dialog>
    </>
  );
}

export function CategoriesAdmin() {
  const query = useCategories();
  const refresh = useRefresh();
  const [edit, setEdit] = useState<{ id?: string; name: string; description: string } | null>(null);
  const [remove, setRemove] = useState<CategoryDto | null>(null);
  const { busy, run } = useAction();
  const categories = query.data ?? [];
  const done = (message: string) => {
    void refresh(["categories"], ["events"]);
    toast.success(message);
  };
  return (
    <>
      <PageTitle
        title="Danh mục sự kiện"
        description="Giúp khách hàng tìm đúng trải nghiệm họ yêu thích."
        action={
          <Button onClick={() => setEdit({ name: "", description: "" })}>
            <Plus size={16} />
            Thêm danh mục
          </Button>
        }
      />
      <DataBoundary
        loading={query.isLoading}
        error={query.isError}
        onRetry={() => void query.refetch()}
        empty={!categories.length}
        title="Chưa có danh mục"
      >
        <div className="category-admin-grid">
          {categories.map((c, i) => (
            <article className="panel" key={c.id}>
              <span className="category-number">{String(i + 1).padStart(2, "0")}</span>
              <h3>{c.name}</h3>
              <p className="muted text-xs">{c.description || "Chưa có mô tả"}</p>
              <div className="flex gap-2">
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => setEdit({ id: c.id, name: c.name, description: c.description ?? "" })}
                >
                  <Pencil size={13} />
                  Sửa
                </Button>
                <Button variant="ghost" size="sm" className="danger-button" onClick={() => setRemove(c)}>
                  <Trash2 size={13} />
                  Xóa
                </Button>
              </div>
            </article>
          ))}
        </div>
      </DataBoundary>
      <Dialog
        open={Boolean(edit)}
        onOpenChange={(open) => {
          if (!open && !busy) setEdit(null);
        }}
      >
        <DialogContent className="modal">
          <DialogHeader>
            <DialogTitle>{edit?.id ? "Sửa danh mục" : "Thêm danh mục"}</DialogTitle>
            <DialogDescription>Tên danh mục được dùng trong bộ lọc và trang tạo sự kiện.</DialogDescription>
          </DialogHeader>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              if (!edit) return;
              const name = edit.name.trim();
              const description = edit.description.trim() || undefined;
              run(
                () =>
                  edit.id
                    ? eventApi.updateCategory(edit.id, name, description)
                    : eventApi.createCategory(name, description),
                () => {
                  setEdit(null);
                  done("Đã lưu danh mục");
                },
              );
            }}
          >
            <label className="field-label" htmlFor="category-name">
              Tên danh mục *
            </label>
            <Input
              autoFocus
              id="category-name"
              minLength={2}
              maxLength={100}
              required
              value={edit?.name ?? ""}
              onChange={(e) => setEdit((v) => (v ? { ...v, name: e.target.value } : v))}
            />
            <label className="field-label mt-4" htmlFor="category-description">
              Mô tả
            </label>
            <Textarea
              id="category-description"
              maxLength={500}
              value={edit?.description ?? ""}
              onChange={(e) => setEdit((v) => (v ? { ...v, description: e.target.value } : v))}
            />
            <Button className="mt-5 w-full" type="submit" disabled={busy}>
              {busy ? "Đang lưu..." : "Lưu danh mục"}
            </Button>
          </form>
        </DialogContent>
      </Dialog>
      <ConfirmDialog
        open={Boolean(remove)}
        onOpenChange={(open) => {
          if (!open) setRemove(null);
        }}
        title={`Xóa danh mục "${remove?.name ?? ""}"?`}
        description="Chỉ xóa được danh mục chưa có sự kiện nào. Thao tác này không hoàn tác được."
        confirmLabel="Xóa danh mục"
        busy={busy}
        onConfirm={() =>
          remove &&
          run(
            () => eventApi.deleteCategory(remove.id),
            () => {
              setRemove(null);
              done("Đã xóa danh mục");
            },
          )
        }
      />
    </>
  );
}

export function TemplatesAdmin() {
  const query = useTemplates();
  const templates = query.data ?? [];
  const [selected, setSelected] = useState<string | null>(null);
  const template = templates.find((t) => t.id === selected) ?? templates[0];
  return (
    <>
      <PageTitle
        title="Mẫu thông báo"
        description="Nội dung nhất quán cho email và thông báo trong ứng dụng."
      />
      <DataBoundary
        loading={query.isLoading}
        error={query.isError}
        onRetry={() => void query.refetch()}
        empty={!templates.length}
        title="Chưa có mẫu thông báo"
      >
        <div className="templates-layout">
          <div className="template-list">
            {templates.map((t) => (
              <button
                key={t.id}
                className={template?.id === t.id ? "selected" : ""}
                onClick={() => setSelected(t.id)}
              >
                {t.channel === "EMAIL" ? <Mail size={18} /> : <Bell size={18} />}
                <span>
                  <strong>{t.code}</strong>
                  <small>{t.channel === "EMAIL" ? "Email" : "Trong ứng dụng"}</small>
                </span>
              </button>
            ))}
          </div>
          {template && <TemplateEditor key={template.id} template={template} />}
        </div>
      </DataBoundary>
    </>
  );
}
/** Giá trị mẫu để xem trước; Notification thay biến {{...}} bằng dữ liệu thật khi gửi. */
const SAMPLE: Record<string, string> = {
  orderId: "3f2a9c1e-…",
  holderName: "Nguyễn Minh Anh",
  eventTitle: "Echoes of the Night",
  eventStartTime: "19:00 12/12/2026",
  venue: VENUE,
  ticketTypeName: "VIP",
  seatLabel: "B-07",
  amount: "1.050.000 ₫",
  reason: "Sự kiện đổi lịch",
};
function TemplateEditor({ template }: { template: Template }) {
  const [subject, setSubject] = useState(template.subject);
  const [body, setBody] = useState(template.body);
  const { busy, run } = useAction();
  const refresh = useRefresh();
  const preview = (value: string) =>
    value.replace(/\{\{(\w+)\}\}/g, (all, key: string) => SAMPLE[key] ?? all);
  const variables = [...new Set([...`${subject} ${body}`.matchAll(/\{\{(\w+)\}\}/g)].map((m) => m[0]))];
  return (
    <div>
      <form
        className="panel"
        onSubmit={(e) => {
          e.preventDefault();
          run(
            () =>
              notificationApi.saveTemplate(template.code, {
                channel: template.channel,
                subject: subject.trim(),
                bodyTemplate: body,
              }),
            () => {
              void refresh(["templates"]);
              toast.success("Đã lưu mẫu thông báo");
            },
          );
        }}
      >
        <h2 className="text-lg font-semibold mb-5">Chỉnh sửa nội dung</h2>
        <label className="field-label" htmlFor="template-subject">
          Tiêu đề
        </label>
        <Input
          id="template-subject"
          required
          maxLength={255}
          value={subject}
          onChange={(e) => setSubject(e.target.value)}
        />
        <label className="field-label mt-5" htmlFor="template-body">
          Nội dung
        </label>
        <Textarea id="template-body" required rows={10} value={body} onChange={(e) => setBody(e.target.value)} />
        <p className="muted text-xs mt-3">
          Biến trong mẫu: {variables.length ? variables.join(", ") : "không có"}
        </p>
        <div className="form-actions">
          <Button disabled={busy} type="submit">
            <Save size={15} />
            {busy ? "Đang lưu..." : "Lưu thay đổi"}
          </Button>
        </div>
      </form>
      <section className="panel template-preview mt-5">
        <span className="eyebrow">XEM TRƯỚC · {template.channel}</span>
        <h3>{preview(subject)}</h3>
        <p style={{ whiteSpace: "pre-wrap" }}>{preview(body)}</p>
      </section>
      <div className="notice-box mt-5">
        <Info size={17} />
        <p>
          Thay đổi áp dụng cho thông báo phát sinh sau khi lưu. Giữ nguyên các biến
          {" {{...}} "}để hệ thống điền dữ liệu thật.
        </p>
      </div>
    </div>
  );
}
