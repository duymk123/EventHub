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
import { useDemo, useAction } from "@/hooks/use-demo";
import { mock, refundEligible } from "@/lib/mock/engine";
import { roleNames, VENUE } from "@/lib/mock/seed";
import { money, dateLabel } from "@/lib/utils";
import {
  PageTitle,
  DataBoundary,
  Status,
  Pagination,
  ConfirmDialog,
} from "../shared";
import type { Event, Refund, User, Template, Role } from "@/types";

export function EventReviews() {
  const s = useDemo();
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("SUBMITTED");
  const [page, setPage] = useState(0);
  const [detail, setDetail] = useState<Event | null>(null);
  const [decision, setDecision] = useState<{
    event: Event;
    approve: boolean;
  } | null>(null);
  const [reason, setReason] = useState("");
  const { busy, run } = useAction();
  const list = s.events.filter(
    (e) =>
      (status === "all" || e.status === status) &&
      e.title.toLowerCase().includes(search.toLowerCase()),
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
        </select>
      </div>
      <DataBoundary
        empty={!list.length}
        title="Đã xử lý hết sự kiện chờ duyệt"
        description="Bạn có thể đổi bộ lọc để xem những sự kiện khác."
      >
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>SỰ KIỆN</th>
                <th>BAN TỔ CHỨC</th>
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
                  <td>{e.organizerName}</td>
                  <td>{dateLabel(e.startTime)}</td>
                  <td>
                    <Status value={e.status} />
                  </td>
                  <td>
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={() => setDetail(e)}
                    >
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
            <DialogDescription>
              Hồ sơ sự kiện · {detail?.organizerName}
            </DialogDescription>
          </DialogHeader>
          {detail && (
            <>
              <Status value={detail.status} />
              <dl className="detail-facts">
                <div>
                  <dt>Thời gian</dt>
                  <dd>{dateLabel(detail.startTime, true)}</dd>
                </div>
                <div>
                  <dt>Địa điểm minh họa</dt>
                  <dd>{VENUE}</dd>
                </div>
                <div>
                  <dt>Danh mục</dt>
                  <dd>{detail.category}</dd>
                </div>
              </dl>
              <p className="text-sm leading-7 muted">{detail.description}</p>
              <div className="table-scroll">
                <table>
                  <thead>
                    <tr>
                      <th>HẠNG VÉ</th>
                      <th>GIÁ</th>
                      <th>QUOTA</th>
                      <th>HOẠT ĐỘNG</th>
                    </tr>
                  </thead>
                  <tbody>
                    {detail.ticketTypes.map((t) => (
                      <tr key={t.id}>
                        <td>{t.name}</td>
                        <td>{money(t.price)}</td>
                        <td>{t.quota}</td>
                        <td>{t.active ? "Có" : "Không"}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              {detail.reviewReason && (
                <p className="danger-note text-sm">
                  Lý do: {detail.reviewReason}
                </p>
              )}
              {detail.status === "SUBMITTED" && (
                <div className="form-actions">
                  <Button
                    variant="destructive"
                    onClick={() => decide(detail, false)}
                  >
                    <X size={16} />
                    Từ chối
                  </Button>
                  <Button onClick={() => decide(detail, true)}>
                    <Check size={16} />
                    Duyệt sự kiện
                  </Button>
                </div>
              )}
            </>
          )}
        </DialogContent>
      </Dialog>
      <ConfirmDialog
        open={Boolean(decision)}
        onOpenChange={(open) => {
          if (!open) setDecision(null);
        }}
        title={
          decision?.approve ? "Duyệt sự kiện này?" : "Từ chối hồ sơ sự kiện?"
        }
        description={
          decision?.approve
            ? "Sự kiện được chuyển sang Đã duyệt. Ban tổ chức sẽ chủ động mở bán sau đó."
            : "Ban tổ chức sẽ nhận được lý do và có thể sửa hồ sơ để gửi duyệt lại."
        }
        destructive={!decision?.approve}
        busy={busy}
        onConfirm={() =>
          decision &&
          run(
            () =>
              mock.transition(
                decision.event.id,
                decision.approve ? "APPROVED" : "REJECTED",
                reason,
              ),
            () => {
              setDecision(null);
              toast.success("Đã cập nhật kết quả xét duyệt");
            },
          )
        }
      >
        {decision && !decision.approve && (
          <div>
            <label className="field-label" htmlFor="event-reason">
              Lý do từ chối *
            </label>
            <Textarea
              id="event-reason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="Nội dung cần ban tổ chức bổ sung hoặc điều chỉnh..."
            />
          </div>
        )}
      </ConfirmDialog>
    </>
  );
}

export function RefundReviews() {
  const s = useDemo();
  const [status, setStatus] = useState("REQUESTED");
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const [decision, setDecision] = useState<{
    refund: Refund;
    approve: boolean;
  } | null>(null);
  const [reason, setReason] = useState("");
  const { busy, run } = useAction();
  const list = s.refunds.filter(
    (r) =>
      (status === "all" || r.status === status) &&
      r.orderId.toLowerCase().includes(search.toLowerCase()),
  );
  return (
    <>
      <PageTitle
        title="Yêu cầu hoàn tiền"
        description="Hoàn 70% giá mua cho toàn bộ đơn đủ điều kiện."
      />
      <div className="notice-box mb-6">
        <ShieldCheck size={18} />
        <p>
          Kiểm tra lại khi duyệt: đơn đã thanh toán, tất cả vé chưa sử dụng, sự
          kiện còn ít nhất 48 giờ. Sau khi hoàn thành, toàn bộ vé của đơn bị
          hủy.
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
            setStatus(e.target.value);
            setPage(0);
          }}
        >
          <option value="REQUESTED">Chờ xử lý</option>
          <option value="all">Tất cả yêu cầu</option>
          <option value="REFUNDED">Đã hoàn tiền</option>
          <option value="REJECTED">Đã từ chối</option>
        </select>
      </div>
      <DataBoundary
        empty={!list.length}
        title="Chưa có yêu cầu phù hợp"
        description="Đổi sang vai trò Khách hàng và gửi yêu cầu từ trang Đơn hàng để trải nghiệm luồng duyệt."
      >
        <div className="refund-review-list">
          {list.slice(page * 5, (page + 1) * 5).map((r) => {
            const o = s.orders.find((o) => o.id === r.orderId);
            const e = s.events.find((e) => e.id === o?.eventId);
            const eligible = Boolean(o && refundEligible(s, o));
            return (
              <article className="panel" key={r.id}>
                <div className="section-heading">
                  <div>
                    <span className="eyebrow">{r.orderId}</span>
                    <h3>{e?.title}</h3>
                    <p className="muted text-xs mt-2">
                      {o?.customerEmail} · {dateLabel(r.createdAt, true)}
                    </p>
                  </div>
                  <Status value={r.status} />
                </div>
                <div className="refund-review-body">
                  <div>
                    <span className="field-label">Lý do yêu cầu</span>
                    <p>{r.reason}</p>
                    {r.reviewReason && (
                      <p className="muted mt-2">Phản hồi: {r.reviewReason}</p>
                    )}
                    {r.status === "REQUESTED" && !eligible && (
                      <p className="danger-note mt-3">
                        Đơn không còn đủ điều kiện hoàn tiền.
                      </p>
                    )}
                  </div>
                  <div>
                    <span className="field-label">Số tiền hoàn (70%)</span>
                    <strong>{money(r.amount)}</strong>
                    <small>Giá trị đơn: {money(o?.total ?? 0)}</small>
                  </div>
                </div>
                {r.status === "REQUESTED" && (
                  <div className="form-actions">
                    <Button
                      variant="secondary"
                      onClick={() => {
                        setReason("");
                        setDecision({ refund: r, approve: false });
                      }}
                    >
                      Từ chối
                    </Button>
                    <Button
                      variant="destructive"
                      disabled={!eligible}
                      onClick={() => {
                        setReason("");
                        setDecision({ refund: r, approve: true });
                      }}
                    >
                      Duyệt hoàn tiền
                    </Button>
                  </div>
                )}
              </article>
            );
          })}
        </div>
        <Pagination page={page} total={list.length} onChange={setPage} />
      </DataBoundary>
      <ConfirmDialog
        open={Boolean(decision)}
        onOpenChange={(open) => {
          if (!open) setDecision(null);
        }}
        title={
          decision?.approve
            ? "Xác nhận hoàn tiền toàn bộ đơn?"
            : "Từ chối yêu cầu hoàn tiền?"
        }
        description={
          decision?.approve
            ? `Hoàn ${money(decision.refund.amount)} trong bản mô phỏng. Tất cả vé thuộc đơn sẽ bị hủy và số lượng vé được trả về. Không có giao dịch tiền thật.`
            : "Khách hàng sẽ nhận thông báo kèm lý do từ chối. Vé vẫn giữ trạng thái hiện tại."
        }
        busy={busy}
        onConfirm={() =>
          decision &&
          run(
            () =>
              mock.reviewRefund(decision.refund.id, decision.approve, reason),
            () => {
              setDecision(null);
              toast.success("Đã xử lý yêu cầu hoàn tiền");
            },
          )
        }
      >
        {decision && !decision.approve && (
          <div>
            <label className="field-label" htmlFor="refund-reason">
              Lý do từ chối *
            </label>
            <Textarea
              id="refund-reason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
          </div>
        )}
      </ConfirmDialog>
    </>
  );
}

export function UsersAdmin() {
  const s = useDemo();
  const [search, setSearch] = useState("");
  const [role, setRole] = useState("all");
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<User | null>(null);
  const [roleChange, setRoleChange] = useState<{
    user: User;
    role: Role;
  } | null>(null);
  const { busy, run } = useAction();
  const users = s.users.filter(
    (u) =>
      (role === "all" || u.role === role) &&
      `${u.name} ${u.email}`.toLowerCase().includes(search.toLowerCase()),
  );
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
            setRole(e.target.value);
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
      <DataBoundary empty={!users.length} title="Không tìm thấy tài khoản">
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
              {users.slice(page * 5, (page + 1) * 5).map((u) => (
                <tr key={u.id}>
                  <td>
                    <div className="user-cell">
                      <span className="avatar">
                        {u.name.split(" ").at(-1)?.slice(0, 1)}
                      </span>
                      <div>
                        <strong>{u.name}</strong>
                        <span className="sub">{u.email}</span>
                      </div>
                    </div>
                  </td>
                  <td>
                    <select
                      aria-label={`Vai trò của ${u.name}`}
                      disabled={u.id === s.userId || busy}
                      value={u.role}
                      onChange={(e) =>
                        setRoleChange({ user: u, role: e.target.value as Role })
                      }
                    >
                      {Object.entries(roleNames).map(([role, label]) => (
                        <option value={role} key={role}>
                          {label}
                        </option>
                      ))}
                    </select>
                  </td>
                  <td>
                    <Status value={u.status} />
                  </td>
                  <td>
                    {u.id === s.userId ? (
                      <span className="muted text-xs">Tài khoản của bạn</span>
                    ) : (
                      <Button
                        size="sm"
                        variant="secondary"
                        onClick={() => setSelected(u)}
                      >
                        {u.status === "ACTIVE" ? (
                          <LockKeyhole size={14} />
                        ) : (
                          <UnlockKeyhole size={14} />
                        )}{" "}
                        {u.status === "ACTIVE" ? "Khóa tài khoản" : "Mở khóa"}
                      </Button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <Pagination page={page} total={users.length} onChange={setPage} />
      </DataBoundary>
      <ConfirmDialog
        open={Boolean(selected)}
        onOpenChange={(open) => {
          if (!open) setSelected(null);
        }}
        title={
          selected?.status === "ACTIVE"
            ? "Khóa tài khoản này?"
            : "Mở khóa tài khoản này?"
        }
        description={
          selected?.status === "ACTIVE"
            ? `${selected.name} sẽ không thể đăng nhập hoặc thao tác trong bản trải nghiệm. Đơn hàng và vé được giữ lại.`
            : "Người dùng có thể tiếp tục đăng nhập và sử dụng tài khoản."
        }
        destructive={selected?.status === "ACTIVE"}
        busy={busy}
        onConfirm={() =>
          selected &&
          run(
            () => mock.userStatus(selected.id, selected.status === "ACTIVE"),
            () => {
              setSelected(null);
              toast.success("Đã cập nhật tài khoản");
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
            ? `${roleChange.user.name} sẽ chuyển từ ${roleNames[roleChange.user.role]} sang ${roleNames[roleChange.role]}. Quyền truy cập sẽ thay đổi ngay trong bản trải nghiệm.`
            : ""
        }
        busy={busy}
        onConfirm={() =>
          roleChange &&
          run(
            () => mock.userRole(roleChange.user.id, roleChange.role),
            () => {
              setRoleChange(null);
              toast.success("Đã cập nhật vai trò");
            },
          )
        }
      />
    </>
  );
}

export function CategoriesAdmin() {
  const s = useDemo();
  const [edit, setEdit] = useState<{ previous?: string; name: string } | null>(
    null,
  );
  const { busy, run } = useAction();
  return (
    <>
      <PageTitle
        title="Danh mục sự kiện"
        description="Giúp khách hàng tìm đúng trải nghiệm họ yêu thích."
        action={
          <Button onClick={() => setEdit({ name: "" })}>
            <Plus size={16} />
            Thêm danh mục
          </Button>
        }
      />
      <DataBoundary empty={!s.categories.length}>
        <div className="category-admin-grid">
          {s.categories.map((c, i) => (
            <article className="panel" key={c}>
              <span className="category-number">0{i + 1}</span>
              <h3>{c}</h3>
              <p className="muted text-xs">
                {s.events.filter((e) => e.category === c).length} sự kiện
              </p>
              <Button
                variant="secondary"
                size="sm"
                onClick={() => setEdit({ previous: c, name: c })}
              >
                <Pencil size={13} />
                Đổi tên
              </Button>
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
            <DialogTitle>
              {edit?.previous ? "Đổi tên danh mục" : "Thêm danh mục"}
            </DialogTitle>
            <DialogDescription>
              Tên danh mục sẽ được dùng trong bộ lọc và trang tạo sự kiện.
            </DialogDescription>
          </DialogHeader>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              if (edit)
                run(
                  () => mock.saveCategory(edit.name, edit.previous),
                  () => {
                    setEdit(null);
                    toast.success("Đã lưu danh mục");
                  },
                );
            }}
          >
            <label className="field-label" htmlFor="category-name">
              Tên danh mục
            </label>
            <Input
              autoFocus
              id="category-name"
              minLength={2}
              maxLength={50}
              required
              value={edit?.name ?? ""}
              onChange={(e) =>
                setEdit((v) => (v ? { ...v, name: e.target.value } : v))
              }
            />
            <Button className="mt-5 w-full" type="submit" disabled={busy}>
              {busy ? "Đang lưu..." : "Lưu danh mục"}
            </Button>
          </form>
        </DialogContent>
      </Dialog>
    </>
  );
}

export function TemplatesAdmin() {
  const { templates } = useDemo();
  const [selected, setSelected] = useState(templates[0].id);
  const template = templates.find((t) => t.id === selected) ?? templates[0];
  return (
    <>
      <PageTitle
        title="Mẫu thông báo"
        description="Nội dung nhất quán cho email và thông báo trong ứng dụng."
      />
      <DataBoundary>
        <div className="templates-layout">
          <div className="template-list">
            {templates.map((t) => (
              <button
                key={t.id}
                className={selected === t.id ? "selected" : ""}
                onClick={() => setSelected(t.id)}
              >
                {t.channel === "EMAIL" ? (
                  <Mail size={18} />
                ) : (
                  <Bell size={18} />
                )}
                <span>
                  <strong>{t.code}</strong>
                  <small>
                    {t.channel === "EMAIL" ? "Email" : "Trong ứng dụng"}
                  </small>
                </span>
              </button>
            ))}
          </div>
          <TemplateEditor key={template.id} template={template} />
        </div>
      </DataBoundary>
    </>
  );
}
function TemplateEditor({ template }: { template: Template }) {
  const [subject, setSubject] = useState(template.subject);
  const [body, setBody] = useState(template.body);
  const { busy, run } = useAction();
  const preview = (value: string) =>
    value
      .replaceAll("{{customerName}}", "Nguyễn Minh Anh")
      .replaceAll("{{orderId}}", "EH-260901");
  return (
    <div>
      <form
        className="panel"
        onSubmit={(e) => {
          e.preventDefault();
          run(
            () => mock.saveTemplate(template.id, subject, body),
            () => toast.success("Đã lưu mẫu thông báo"),
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
          maxLength={200}
          value={subject}
          onChange={(e) => setSubject(e.target.value)}
        />
        <label className="field-label mt-5" htmlFor="template-body">
          Nội dung
        </label>
        <Textarea
          id="template-body"
          required
          rows={6}
          value={body}
          onChange={(e) => setBody(e.target.value)}
        />
        <p className="muted text-xs mt-3">
          Biến xem trước: {"{{customerName}}, {{orderId}}"}
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
        <p>{preview(body)}</p>
      </section>
      <div className="notice-box mt-5">
        <Info size={17} />
        <p>
          Nội dung được lưu để đánh giá giao diện. Bản trải nghiệm chưa gửi
          email và chưa áp dụng mẫu này vào các thông báo phát sinh.
        </p>
      </div>
    </div>
  );
}
