"use client";
import { useState } from "react";
import Link from "next/link";
import Image from "next/image";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import {
  Search,
  Plus,
  ArrowUpRight,
  ArrowLeft,
  Save,
  Send,
  ScanLine,
  CheckCircle2,
  Users,
  Download,
  Info,
  Ticket,
  CalendarDays,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { useDemo, useAction } from "@/hooks/use-demo";
import { mock } from "@/lib/mock/engine";
import { eventSchema, type EventForm } from "@/schemas/forms";
import { money, dateLabel, cn } from "@/lib/utils";
import {
  MAX_ROWS,
  MAX_SEATS_PER_ROW,
  rowLabel,
  withTaken,
} from "@/lib/seats";
import {
  PageTitle,
  DataBoundary,
  Status,
  ConfirmDialog,
  Pagination,
  Empty,
} from "../shared";
import { downloadCsv } from "./overview";
import type { Event, EventStatus, TicketType } from "@/types";
import { toast } from "sonner";
export function OrganizerEvents() {
  const s = useDemo();
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("all");
  const [page, setPage] = useState(0);
  const [confirm, setConfirm] = useState<{
    event: Event;
    next: EventStatus;
  } | null>(null);
  const { busy, run } = useAction();
  const events = s.events.filter(
    (e) =>
      e.organizerId === s.userId &&
      (status === "all" || e.status === status) &&
      e.title.toLowerCase().includes(search.toLowerCase()),
  );
  return (
    <>
      <PageTitle
        title="Sự kiện của tôi"
        description="Từ một ý tưởng đến một trải nghiệm đáng nhớ."
        action={
          <Button asChild>
            <Link href="/organizer/events/new">
              <Plus size={16} />
              Tạo sự kiện
            </Link>
          </Button>
        }
      />
      <div className="filters">
        <div className="input-wrap">
          <Search />
          <Input
            aria-label="Tìm sự kiện của tôi"
            placeholder="Tìm theo tên sự kiện..."
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
          />
        </div>
        <select
          aria-label="Lọc trạng thái sự kiện"
          value={status}
          onChange={(e) => {
            setStatus(e.target.value);
            setPage(0);
          }}
        >
          <option value="all">Tất cả trạng thái</option>
          <option value="DRAFT">Bản nháp</option>
          <option value="SUBMITTED">Chờ duyệt</option>
          <option value="APPROVED">Đã duyệt</option>
          <option value="PUBLISHED">Đang mở bán</option>
          <option value="REJECTED">Từ chối</option>
          <option value="CLOSED">Đã đóng bán</option>
        </select>
      </div>
      <DataBoundary empty={!events.length} title="Chưa có sự kiện phù hợp">
        <div className="managed-events">
          {events.slice(page * 5, (page + 1) * 5).map((e) => (
            <article key={e.id}>
              <Image src={e.image} alt="" width={150} height={120} />
              <div className="managed-event-info">
                <Status value={e.status} />
                <h3>{e.title}</h3>
                <p>
                  <CalendarDays size={13} />
                  {dateLabel(e.startTime)}
                  <span>· {e.category}</span>
                </p>
                {e.status === "REJECTED" && (
                  <p className="danger-note">Lý do: {e.reviewReason}</p>
                )}
              </div>
              <div className="managed-event-actions">
                {["DRAFT", "REJECTED"].includes(e.status) && (
                  <Button asChild variant="secondary" size="sm">
                    <Link href={`/organizer/events/${e.id}/edit`}>
                      Chỉnh sửa
                    </Link>
                  </Button>
                )}
                {e.status === "DRAFT" && (
                  <Button
                    size="sm"
                    disabled={busy}
                    onClick={() =>
                      run(
                        () => mock.transition(e.id, "SUBMITTED"),
                        () => toast.success("Đã gửi sự kiện cho Admin duyệt"),
                      )
                    }
                  >
                    <Send size={13} />
                    Gửi duyệt
                  </Button>
                )}
                {e.status === "APPROVED" && (
                  <Button
                    size="sm"
                    onClick={() => setConfirm({ event: e, next: "PUBLISHED" })}
                  >
                    Mở bán vé
                  </Button>
                )}
                {e.status === "PUBLISHED" && (
                  <>
                    <Button variant="secondary" size="sm" asChild>
                      <Link href={`/events/${e.id}`}>
                        Xem trang
                        <ArrowUpRight size={13} />
                      </Link>
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      className="danger-button"
                      onClick={() => setConfirm({ event: e, next: "CLOSED" })}
                    >
                      Đóng bán
                    </Button>
                  </>
                )}
                {e.status === "SUBMITTED" && (
                  <span className="muted text-xs">Chờ Admin xét duyệt</span>
                )}
              </div>
            </article>
          ))}
        </div>
        <Pagination page={page} total={events.length} onChange={setPage} />
      </DataBoundary>
      <ConfirmDialog
        open={Boolean(confirm)}
        onOpenChange={(open) => {
          if (!open) setConfirm(null);
        }}
        title={
          confirm?.next === "CLOSED"
            ? "Đóng bán sự kiện?"
            : "Công bố và mở bán sự kiện?"
        }
        description={
          confirm?.next === "CLOSED"
            ? "Sự kiện sẽ ngừng nhận đơn mới. Vé đã phát hành vẫn có hiệu lực, không tự hủy hoặc hoàn tiền."
            : "Sự kiện sẽ xuất hiện ở trang Khám phá và khách hàng có thể đặt vé."
        }
        destructive={confirm?.next === "CLOSED"}
        busy={busy}
        onConfirm={() =>
          confirm &&
          run(
            () => mock.transition(confirm.event.id, confirm.next),
            () => {
              setConfirm(null);
              toast.success("Đã cập nhật trạng thái sự kiện");
            },
          )
        }
      />
    </>
  );
}
export function EventEditor({ id }: { id?: string }) {
  const s = useDemo();
  const existing = s.events.find((e) => e.id === id);
  const router = useRouter();
  const { busy, run } = useAction();
  const [types, setTypes] = useState<TicketType[]>(
    existing?.ticketTypes ??
      (["STANDARD", "PREMIUM", "VIP"] as const).map((name, i) =>
        withTaken(
          {
            id: `new-${name}`,
            name,
            price: [450000, 850000, 1500000][i],
            rowCount: [12, 6, 3][i],
            seatsPerRow: [20, 16, 10][i],
            quota: 0,
            available: 0,
            taken: {},
            active: true,
          },
          {},
        ),
      ),
  );
  const local = (date: string) =>
    new Date(Date.parse(date) + 7 * 3600000).toISOString().slice(0, 16);
  const form = useForm<EventForm>({
    resolver: zodResolver(eventSchema),
    defaultValues: existing
      ? {
          title: existing.title,
          subtitle: existing.subtitle,
          description: existing.description,
          category: existing.category,
          startTime: local(existing.startTime),
          endTime: local(existing.endTime),
        }
      : {
          title: "",
          subtitle: "",
          description: "",
          category: s.categories[0],
          startTime: "2026-12-12T19:00",
          endTime: "2026-12-12T23:00",
        },
  });
  if (
    id &&
    (!existing ||
      existing.organizerId !== s.userId ||
      !["DRAFT", "REJECTED"].includes(existing.status))
  )
    return (
      <Empty
        title="Không thể chỉnh sửa sự kiện này"
        description="Chỉ sự kiện nháp hoặc bị từ chối thuộc sở hữu của bạn mới được chỉnh sửa."
      />
    );
  function updateTier(
    index: number,
    field: "price" | "rowCount" | "seatsPerRow",
    value: string,
  ) {
    const limit =
      field === "rowCount"
        ? MAX_ROWS
        : field === "seatsPerRow"
          ? MAX_SEATS_PER_ROW
          : Number.MAX_SAFE_INTEGER;
    const next = Math.min(limit, Math.max(0, Math.floor(Number(value) || 0)));
    setTypes((ts) =>
      ts.map((t, i) =>
        i === index ? withTaken({ ...t, [field]: next }, {}) : t,
      ),
    );
  }
  return (
    <>
      <Link className="text-link mb-6" href="/organizer/events">
        <ArrowLeft size={14} />
        Sự kiện của tôi
      </Link>
      <PageTitle
        title={existing ? "Chỉnh sửa sự kiện" : "Tạo cuộc hẹn mới"}
        description="Lưu bản nháp trước, gửi duyệt khi bạn đã sẵn sàng."
      />
      <form
        onSubmit={form.handleSubmit((v) =>
          run(
            () =>
              mock.saveEvent(
                {
                  ...v,
                  startTime: new Date(v.startTime + ":00+07:00").toISOString(),
                  endTime: new Date(v.endTime + ":00+07:00").toISOString(),
                },
                types,
                id,
              ),
            () => {
              toast.success("Đã lưu bản nháp sự kiện");
              router.push("/organizer/events");
            },
          ),
        )}
      >
        <div className="editor-layout">
          <section className="panel">
            <h2 className="text-lg font-semibold mb-6">Thông tin sự kiện</h2>
            <div className="field">
              <label className="field-label" htmlFor="event-title">
                Tên sự kiện *
              </label>
              <Input
                id="event-title"
                {...form.register("title")}
                placeholder="Tên của trải nghiệm bạn muốn tạo"
              />
              {form.formState.errors.title && (
                <p className="form-error">
                  {form.formState.errors.title.message}
                </p>
              )}
            </div>
            <div className="field">
              <label className="field-label" htmlFor="event-subtitle">
                Giới thiệu ngắn *
              </label>
              <Input
                id="event-subtitle"
                {...form.register("subtitle")}
                placeholder="Một câu để khơi gợi cảm xúc"
              />
              {form.formState.errors.subtitle && (
                <p className="form-error">
                  {form.formState.errors.subtitle.message}
                </p>
              )}
            </div>
            <div className="field">
              <label className="field-label" htmlFor="event-category">
                Danh mục
              </label>
              <select
                id="event-category"
                className="w-full"
                {...form.register("category")}
              >
                {s.categories.map((c) => (
                  <option key={c}>{c}</option>
                ))}
              </select>
            </div>
            <div className="form-grid">
              <div className="field">
                <label className="field-label" htmlFor="event-start">
                  Bắt đầu (giờ Việt Nam)
                </label>
                <Input
                  id="event-start"
                  type="datetime-local"
                  {...form.register("startTime")}
                />
              </div>
              <div className="field">
                <label className="field-label" htmlFor="event-end">
                  Kết thúc (giờ Việt Nam)
                </label>
                <Input
                  id="event-end"
                  type="datetime-local"
                  {...form.register("endTime")}
                />
              </div>
            </div>
            <div className="field">
              <label className="field-label" htmlFor="event-description">
                Mô tả trải nghiệm *
              </label>
              <Textarea
                id="event-description"
                rows={7}
                {...form.register("description")}
                placeholder="Chia sẻ điều đặc biệt của sự kiện, nghệ sĩ, lịch trình..."
              />
              {form.formState.errors.description && (
                <p className="form-error">
                  {form.formState.errors.description.message}
                </p>
              )}
            </div>
            <div className="notice-box">
              <Info size={17} />
              <p>
                Bản trải nghiệm dùng ảnh sân khấu mẫu và địa điểm minh họa
                chung. Mỗi hạng vé là một khu ngồi: nhập số hàng và số ghế mỗi
                hàng, hệ thống tự đánh số ghế (A-01, A-02…). Khu có 0 hàng là
                chưa mở bán; khi mở bán cần ít nhất một khu đang hoạt động có ghế.
              </p>
            </div>
          </section>
          <section className="panel">
            <h2 className="text-lg font-semibold mb-2">Cấu hình hạng vé</h2>
            <p className="muted text-xs mb-5">Giá VND · Không phí bổ sung</p>
            {types.map((t, i) => (
              <div className="tier-editor" key={t.id}>
                <div className="flex justify-between items-center mb-4">
                  <strong>{t.name}</strong>
                  <label className="check-line">
                    <input
                      type="checkbox"
                      checked={t.active}
                      onChange={(e) =>
                        setTypes((ts) =>
                          ts.map((x, index) =>
                            index === i
                              ? { ...x, active: e.target.checked }
                              : x,
                          ),
                        )
                      }
                    />
                    Mở bán
                  </label>
                </div>
                <div className="form-grid">
                  <div>
                    <label className="field-label" htmlFor={`tier-price-${i}`}>
                      Giá vé
                    </label>
                    <Input
                      id={`tier-price-${i}`}
                      type="number"
                      min="0"
                      step="1000"
                      value={t.price}
                      onChange={(e) => updateTier(i, "price", e.target.value)}
                    />
                  </div>
                  <div className="tier-layout-inputs">
                    <div>
                      <label className="field-label" htmlFor={`tier-rows-${i}`}>
                        Số hàng
                      </label>
                      <Input
                        id={`tier-rows-${i}`}
                        type="number"
                        min="0"
                        max={MAX_ROWS}
                        step="1"
                        value={t.rowCount}
                        onChange={(e) =>
                          updateTier(i, "rowCount", e.target.value)
                        }
                      />
                    </div>
                    <div>
                      <label className="field-label" htmlFor={`tier-seats-${i}`}>
                        Ghế mỗi hàng
                      </label>
                      <Input
                        id={`tier-seats-${i}`}
                        type="number"
                        min="0"
                        max={MAX_SEATS_PER_ROW}
                        step="1"
                        value={t.seatsPerRow}
                        onChange={(e) =>
                          updateTier(i, "seatsPerRow", e.target.value)
                        }
                      />
                    </div>
                  </div>
                </div>
                <p className="text-xs muted mt-3">
                  {money(t.price)} / vé · {t.quota} ghế
                  {t.rowCount > 0 && t.seatsPerRow > 0
                    ? ` (hàng A–${rowLabel(t.rowCount)}, ghế 1–${t.seatsPerRow})`
                    : " · chưa mở bán khu này"}
                </p>
              </div>
            ))}
          </section>
        </div>
        <div className="form-actions">
          <Button asChild variant="secondary">
            <Link href="/organizer/events">Quay lại</Link>
          </Button>
          <Button disabled={busy} type="submit">
            <Save size={16} />
            {busy ? "Đang lưu..." : "Lưu bản nháp"}
          </Button>
        </div>
      </form>
    </>
  );
}
export function Attendees() {
  const s = useDemo();
  const [search, setSearch] = useState("");
  const [eventId, setEventId] = useState("all");
  const [page, setPage] = useState(0);
  const events = s.events.filter((e) => e.organizerId === s.userId);
  const list = s.tickets.filter(
    (t) =>
      events.some((e) => e.id === t.eventId) &&
      (eventId === "all" || t.eventId === eventId) &&
      `${t.holderName} ${t.id}`.toLowerCase().includes(search.toLowerCase()),
  );
  return (
    <>
      <PageTitle
        title="Người tham dự"
        description="Danh sách người dùng vé và trạng thái vào cổng."
        action={
          <Button
            variant="secondary"
            onClick={() =>
              downloadCsv(
                [
                  [
                    "Mã vé",
                    "Người tham dự",
                    "Sự kiện",
                    "Hạng vé",
                    "Trạng thái",
                  ],
                  ...list.map((t) => [
                    t.id,
                    t.holderName,
                    events.find((e) => e.id === t.eventId)?.title ?? "",
                    t.tier,
                    t.status,
                  ]),
                ],
                "eventhub-nguoi-tham-du.csv",
              )
            }
          >
            <Download size={15} />
            Xuất CSV
          </Button>
        }
      />
      <div className="filters">
        <div className="input-wrap">
          <Search />
          <Input
            aria-label="Tìm người tham dự"
            placeholder="Tìm tên hoặc mã vé..."
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
          />
        </div>
        <select
          aria-label="Lọc theo sự kiện"
          value={eventId}
          onChange={(e) => {
            setEventId(e.target.value);
            setPage(0);
          }}
        >
          <option value="all">Tất cả sự kiện</option>
          {events.map((e) => (
            <option value={e.id} key={e.id}>
              {e.title}
            </option>
          ))}
        </select>
      </div>
      <DataBoundary empty={!list.length} title="Chưa có người tham dự">
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>NGƯỜI THAM DỰ</th>
                <th>MÃ VÉ</th>
                <th>SỰ KIỆN</th>
                <th>HẠNG VÉ</th>
                <th>TRẠNG THÁI</th>
              </tr>
            </thead>
            <tbody>
              {list.slice(page * 8, (page + 1) * 8).map((t) => (
                <tr key={t.id}>
                  <td>
                    <strong>{t.holderName}</strong>
                    <span className="sub">{t.holderEmail}</span>
                  </td>
                  <td>{t.id}</td>
                  <td>{events.find((e) => e.id === t.eventId)?.title}</td>
                  <td>{t.tier}</td>
                  <td>
                    <Status value={t.status} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <Pagination
          total={list.length}
          page={page}
          onChange={setPage}
          pageSize={8}
        />
      </DataBoundary>
    </>
  );
}
export function Checkin() {
  const s = useDemo();
  const [code, setCode] = useState("");
  const [action, setAction] = useState<"in" | "out">("in");
  const [result, setResult] = useState("");
  const { busy, run } = useAction();
  const tickets = s.tickets.filter((t) =>
    s.events.some((e) => e.id === t.eventId && e.organizerId === s.userId),
  );
  return (
    <>
      <PageTitle
        title="Kiểm soát vé"
        description="Mô phỏng thao tác kiểm tra vé tại cổng."
      />
      <div className="checkin-layout">
        <section className="panel">
          <div className="tab-row">
            <button
              className={action === "in" ? "selected" : ""}
              onClick={() => {
                setAction("in");
                setResult("");
              }}
            >
              Check-in
            </button>
            <button
              className={action === "out" ? "selected" : ""}
              onClick={() => {
                setAction("out");
                setResult("");
              }}
            >
              Check-out
            </button>
          </div>
          <div className="scanner-preview">
            <ScanLine size={90} strokeWidth={1} />
            <p>Nhập mã vé để quét thử</p>
            <span>Không sử dụng camera trong bản trải nghiệm</span>
          </div>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              run(
                () => mock.scan(code, action),
                (ticket) => {
                  setResult(
                    `${action === "in" ? "Chào mừng" : "Tạm biệt"} ${ticket.holderName}! ${action === "in" ? "Vé đã check-in thành công." : "Vé đã check-out. Không được vào lại."}`,
                  );
                  toast.success("Đã cập nhật trạng thái vé");
                },
              );
            }}
          >
            <label className="field-label" htmlFor="scan-code">
              Mã vé
            </label>
            <Input
              id="scan-code"
              value={code}
              onChange={(e) => {
                setCode(e.target.value);
                setResult("");
              }}
              placeholder="Ví dụ: EH-T001"
              required
            />
            <Button type="submit" className="w-full mt-4" disabled={busy}>
              <ScanLine size={16} />
              {busy
                ? "Đang kiểm tra..."
                : action === "in"
                  ? "Xác nhận check-in"
                  : "Xác nhận check-out"}
            </Button>
          </form>
          {result && (
            <div className="scan-result">
              <CheckCircle2 size={22} />
              {result}
            </div>
          )}
        </section>
        <section>
          <div className="panel">
            <h2 className="text-lg font-semibold mb-3">
              Vé có thể trải nghiệm
            </h2>
            <p className="muted text-xs mb-5">
              Chọn một vé để điền mã và thử quy trình.
            </p>
            <DataBoundary empty={!tickets.length}>
              {tickets.slice(0, 8).map((t) => (
                <button
                  className={cn("scan-ticket", code === t.id && "selected")}
                  key={t.id}
                  onClick={() => {
                    setCode(t.id);
                    setResult("");
                  }}
                >
                  <Ticket size={18} />
                  <span>
                    <strong>{t.holderName}</strong>
                    <small>
                      {t.id} · {t.tier}
                    </small>
                  </span>
                  <Status value={t.status} />
                </button>
              ))}
            </DataBoundary>
          </div>
          <div className="notice-box mt-5">
            <Users size={18} />
            <p>
              Vé chỉ được vào một lần và ra một lần. Các thao tác cập nhật ngay
              số người đang có mặt trên dashboard.
            </p>
          </div>
        </section>
      </div>
    </>
  );
}
