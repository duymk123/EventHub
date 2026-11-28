"use client";
import { useState } from "react";
import Link from "next/link";
import Image from "next/image";
import { useRouter, useSearchParams } from "next/navigation";
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
  XCircle,
  Users,
  Download,
  Info,
  CalendarDays,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import {
  useAction,
  useCategories,
  useEventAttendance,
  useOrganizerEvent,
  useOrganizerEvents,
  useRefresh,
} from "@/hooks/use-api";
import { usePlatformAnalytics } from "@/hooks/use-platform-analytics";
import { useNow } from "@/hooks/use-now";
import { eventApi, ticketApi } from "@/lib/api/endpoints";
import { eventSchema, type EventForm } from "@/schemas/forms";
import { money, dateLabel, timeLabel } from "@/lib/utils";
import { MAX_ROWS, MAX_SEATS_PER_ROW, rowLabel, withTaken } from "@/lib/seats";
import {
  PageTitle,
  DataBoundary,
  Status,
  ConfirmDialog,
  Pagination,
  Empty,
} from "../shared";
import { QrScanner } from "../ticket/qr-scanner";
import { downloadCsv } from "./overview";
import type { Event, EventStatus, TicketType, TierName } from "@/types";
import { toast } from "sonner";

/** C07: cổng soát vé mở từ 60 phút trước giờ bắt đầu đến giờ kết thúc; server kiểm tra lại khi quét. */
const GATE_OPEN_BEFORE_MS = 3_600_000;
function canScan(event: Event, now: number) {
  return (
    ["PUBLISHED", "CLOSED"].includes(event.status) &&
    now >= Date.parse(event.startTime) - GATE_OPEN_BEFORE_MS &&
    now <= Date.parse(event.endTime)
  );
}

export function OrganizerEvents() {
  const query = useOrganizerEvents();
  const refresh = useRefresh();
  const now = useNow();
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("all");
  const [page, setPage] = useState(0);
  const [confirm, setConfirm] = useState<{
    event: Event;
    next: EventStatus;
  } | null>(null);
  const { busy, run } = useAction();
  const events = (query.data ?? []).filter(
    (e) =>
      (status === "all" || e.status === status) &&
      e.title.toLowerCase().includes(search.trim().toLowerCase()),
  );
  const done = (message: string) => {
    void refresh(["events"]);
    toast.success(message);
  };
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
      <DataBoundary
        loading={query.isLoading}
        error={query.isError}
        onRetry={() => void query.refetch()}
        empty={!events.length}
        title="Chưa có sự kiện phù hợp"
      >
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
                {e.status === "REJECTED" && e.reviewReason && (
                  <p className="danger-note">Lý do: {e.reviewReason}</p>
                )}
              </div>
              <div className="managed-event-actions">
                {now > 0 && canScan(e, now) && (
                  <Button size="sm" asChild>
                    <Link href={`/organizer/checkin?event=${e.id}`}>
                      <ScanLine size={13} />
                      Quét vé
                    </Link>
                  </Button>
                )}
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
                        () => eventApi.submit(e.id),
                        () => done("Đã gửi sự kiện cho Admin duyệt"),
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
        confirmLabel={confirm?.next === "CLOSED" ? "Đóng bán" : "Mở bán"}
        destructive={confirm?.next === "CLOSED"}
        busy={busy}
        onConfirm={() =>
          confirm &&
          run(
            () =>
              confirm.next === "CLOSED"
                ? eventApi.close(confirm.event.id)
                : eventApi.publish(confirm.event.id),
            () => {
              setConfirm(null);
              done("Đã cập nhật trạng thái sự kiện");
            },
          )
        }
      />
    </>
  );
}

const TIERS: TierName[] = ["STANDARD", "PREMIUM", "VIP"];
const DEFAULT_LAYOUT: Record<TierName, [number, number, number]> = {
  STANDARD: [450000, 12, 20],
  PREMIUM: [850000, 6, 16],
  VIP: [1500000, 3, 10],
};
function draftTier(name: TierName): TicketType {
  const [price, rowCount, seatsPerRow] = DEFAULT_LAYOUT[name];
  return withTaken(
    {
      id: `new-${name}`,
      name,
      price,
      rowCount,
      seatsPerRow,
      quota: 0,
      available: 0,
      taken: {},
      active: true,
    },
    {},
  );
}
/** Giờ Việt Nam cho ô datetime-local (sự kiện diễn ra ở Hà Nội, C03). */
const toLocalInput = (iso: string) =>
  new Date(Date.parse(iso) + 7 * 3_600_000).toISOString().slice(0, 16);
const fromLocalInput = (value: string) =>
  new Date(`${value}:00+07:00`).toISOString();

export function EventEditor({ id }: { id?: string }) {
  const categories = useCategories();
  const existing = useOrganizerEvent(id);
  if (categories.isLoading || (id && existing.isLoading))
    return <DataBoundary loading>{null}</DataBoundary>;
  if (categories.isError || (id && existing.isError))
    return (
      <DataBoundary
        error
        onRetry={() => {
          void categories.refetch();
          void existing.refetch();
        }}
      >
        {null}
      </DataBoundary>
    );
  if (
    id &&
    (!existing.data || !["DRAFT", "REJECTED"].includes(existing.data.status))
  )
    return (
      <Empty
        title="Không thể chỉnh sửa sự kiện này"
        description="Chỉ sự kiện nháp hoặc bị từ chối thuộc sở hữu của bạn mới được chỉnh sửa."
      />
    );
  return (
    <EditorForm
      key={existing.data?.id ?? "new"}
      existing={existing.data}
      categories={categories.data ?? []}
    />
  );
}

function EditorForm({
  existing,
  categories,
}: {
  existing: Event | undefined;
  categories: { id: string; name: string }[];
}) {
  const router = useRouter();
  const refresh = useRefresh();
  const { busy, run } = useAction();
  const original = existing?.ticketTypes ?? [];
  const [types, setTypes] = useState<TicketType[]>(() =>
    TIERS.map(
      (name) => original.find((t) => t.name === name) ?? draftTier(name),
    ),
  );
  const form = useForm<EventForm>({
    resolver: zodResolver(eventSchema),
    defaultValues: existing
      ? {
          title: existing.title,
          description: existing.description,
          categoryId: existing.categoryId,
          startTime: toLocalInput(existing.startTime),
          endTime: toLocalInput(existing.endTime),
        }
      : {
          title: "",
          description: "",
          categoryId: categories[0]?.id ?? "",
          startTime: "",
          endTime: "",
        },
  });
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
        i === index ? withTaken({ ...t, [field]: next }, t.taken) : t,
      ),
    );
  }

  /** Lưu hạng vé: chưa có thì tạo cả ba (POST /ticket-types), đã có thì PUT từng hạng thay đổi. */
  async function saveTiers(eventId: string) {
    const fresh = types.filter((t) => t.id.startsWith("new-"));
    if (fresh.length) {
      const created = await ticketApi.createTypes(
        eventId,
        fresh.map((t) => ({
          name: t.name,
          price: t.price,
          rowCount: t.rowCount,
          seatsPerRow: t.seatsPerRow,
        })),
      );
      for (const c of created) {
        const local = fresh.find((t) => t.name === c.name);
        if (local && !local.active)
          await ticketApi.updateType(c.id, { status: "INACTIVE" });
      }
    }
    for (const t of types.filter((t) => !t.id.startsWith("new-"))) {
      const before = original.find((o) => o.id === t.id);
      if (
        before &&
        before.price === t.price &&
        before.rowCount === t.rowCount &&
        before.seatsPerRow === t.seatsPerRow &&
        before.active === t.active
      )
        continue;
      await ticketApi.updateType(t.id, {
        price: t.price,
        rowCount: t.rowCount,
        seatsPerRow: t.seatsPerRow,
        status: t.active ? "ACTIVE" : "INACTIVE",
      });
    }
  }

  const submit = form.handleSubmit((v) =>
    run(
      async () => {
        const body = {
          title: v.title.trim(),
          description: v.description.trim(),
          categoryId: v.categoryId,
          startTime: fromLocalInput(v.startTime),
          endTime: fromLocalInput(v.endTime),
        };
        const saved = existing
          ? await eventApi.update(existing.id, body)
          : await eventApi.create(body);
        try {
          await saveTiers(saved.id);
        } catch (error) {
          // Sự kiện đã lưu nhưng hạng vé lỗi: chuyển sang trang sửa để lưu lại hạng vé.
          void refresh(["events"], ["ticket-types", saved.id]);
          if (!existing) router.replace(`/organizer/events/${saved.id}/edit`);
          throw error;
        }
        return saved;
      },
      () => {
        void refresh(["events"], ["ticket-types"]);
        toast.success("Đã lưu bản nháp sự kiện");
        router.push("/organizer/events");
      },
    ),
  );

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
      {existing?.status === "REJECTED" && existing.reviewReason && (
        <div className="notice-box mb-6">
          <Info size={17} />
          <p>
            Admin đã từ chối: {existing.reviewReason}. Lưu thay đổi sẽ đưa sự
            kiện về bản nháp để gửi duyệt lại.
          </p>
        </div>
      )}
      <form onSubmit={submit}>
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
              <label className="field-label" htmlFor="event-category">
                Danh mục *
              </label>
              <select
                id="event-category"
                className="w-full"
                {...form.register("categoryId")}
              >
                {categories.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
              {form.formState.errors.categoryId && (
                <p className="form-error">
                  {form.formState.errors.categoryId.message}
                </p>
              )}
            </div>
            <div className="form-grid">
              <div className="field">
                <label className="field-label" htmlFor="event-start">
                  Bắt đầu (giờ Việt Nam) *
                </label>
                <Input
                  id="event-start"
                  type="datetime-local"
                  {...form.register("startTime")}
                />
                {form.formState.errors.startTime && (
                  <p className="form-error">
                    {form.formState.errors.startTime.message}
                  </p>
                )}
              </div>
              <div className="field">
                <label className="field-label" htmlFor="event-end">
                  Kết thúc (giờ Việt Nam) *
                </label>
                <Input
                  id="event-end"
                  type="datetime-local"
                  {...form.register("endTime")}
                />
                {form.formState.errors.endTime && (
                  <p className="form-error">
                    {form.formState.errors.endTime.message}
                  </p>
                )}
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
                placeholder="Câu đầu tiên sẽ là lời giới thiệu ngắn trên trang sự kiện. Chia sẻ điều đặc biệt, nghệ sĩ, lịch trình..."
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
                Mọi sự kiện diễn ra tại 141 Đường Chiến Thắng, Hà Nội; ảnh bìa
                chọn theo danh mục. Mỗi hạng vé là một khu ngồi: nhập số hàng và
                số ghế mỗi hàng, hệ thống tự đánh số ghế (A-01, A-02…). Khu có 0
                hàng là chưa mở bán; khi mở bán cần ít nhất một khu đang hoạt
                động có ghế.
              </p>
            </div>
          </section>
          <section className="panel">
            <h2 className="text-lg font-semibold mb-2">Cấu hình hạng vé</h2>
            <p className="muted text-xs mb-5">
              Giá VND · Ghế đã từng được giữ/bán thì không bớt hàng được
            </p>
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
                      <label
                        className="field-label"
                        htmlFor={`tier-seats-${i}`}
                      >
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
                  {money(t.price)} / vé · {t.rowCount * t.seatsPerRow} ghế
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

/**
 * Tình hình khán giả theo sự kiện (Analytics): vé hợp lệ, đã check-in, tỉ lệ. Backend chưa có API danh sách
 * từng người tham dự cho Ban tổ chức nên trang chỉ hiển thị số liệu tổng.
 */
export function Attendees() {
  const mine = useOrganizerEvents();
  const analytics = usePlatformAnalytics("12m", "MONTH", null);
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const [focus, setFocus] = useState<string | null>(null);
  const rows = new Map(analytics.data?.events.map((r) => [r.eventId, r]));
  const list = (mine.data ?? []).filter(
    (e) =>
      ["PUBLISHED", "CLOSED"].includes(e.status) &&
      e.title.toLowerCase().includes(search.trim().toLowerCase()),
  );
  const line = (e: Event) => {
    const r = rows.get(e.id);
    const valid = r?.validTicketsLifetime ?? 0;
    const checked = r?.attendeesLifetime ?? 0;
    return {
      valid,
      checked,
      capacity: r?.capacity ?? 0,
      rate: valid ? checked / valid : 0,
    };
  };
  return (
    <>
      <PageTitle
        title="Người tham dự"
        description="Số vé hợp lệ và lượt vào cổng của từng sự kiện."
        action={
          <Button
            variant="secondary"
            disabled={!list.length}
            onClick={() =>
              downloadCsv(
                [
                  [
                    "Sự kiện",
                    "Ngày diễn ra",
                    "Sức chứa",
                    "Vé hợp lệ",
                    "Đã check-in",
                    "Tỉ lệ check-in",
                  ],
                  ...list.map((e) => {
                    const l = line(e);
                    return [
                      e.title,
                      dateLabel(e.startTime, true),
                      String(l.capacity),
                      String(l.valid),
                      String(l.checked),
                      `${Math.round(l.rate * 100)}%`,
                    ];
                  }),
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
            aria-label="Tìm sự kiện"
            placeholder="Tìm theo tên sự kiện..."
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
          />
        </div>
      </div>
      <DataBoundary
        loading={mine.isLoading || analytics.isLoading}
        error={mine.isError || analytics.isError}
        onRetry={() => {
          void mine.refetch();
          analytics.refetch();
        }}
        empty={!list.length}
        title="Chưa có sự kiện đang mở bán"
        description="Số liệu người tham dự xuất hiện khi sự kiện đã mở bán."
      >
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>SỰ KIỆN</th>
                <th>NGÀY DIỄN RA</th>
                <th>SỨC CHỨA</th>
                <th>VÉ HỢP LỆ</th>
                <th>ĐÃ CHECK-IN</th>
                <th>ĐANG CÓ MẶT</th>
              </tr>
            </thead>
            <tbody>
              {list.slice(page * 8, (page + 1) * 8).map((e) => {
                const l = line(e);
                return (
                  <tr key={e.id}>
                    <td>
                      <strong>{e.title}</strong>
                      <span className="sub">
                        <Status value={e.status} />
                      </span>
                    </td>
                    <td>{dateLabel(e.startTime, true)}</td>
                    <td>{l.capacity}</td>
                    <td>{l.valid}</td>
                    <td>
                      {l.checked}{" "}
                      <small className="muted">
                        ({Math.round(l.rate * 100)}%)
                      </small>
                    </td>
                    <td>
                      {focus === e.id ? (
                        <LiveAttendance eventId={e.id} />
                      ) : (
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => setFocus(e.id)}
                        >
                          Xem
                        </Button>
                      )}
                    </td>
                  </tr>
                );
              })}
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
      <div className="notice-box mt-6">
        <Users size={18} />
        <p>
          Số liệu do Analytics tổng hợp từ lượt check-in/check-out, có thể trễ
          vài giây. Danh sách từng người tham dự sẽ có khi backend bổ sung API.
        </p>
      </div>
    </>
  );
}
function LiveAttendance({ eventId }: { eventId: string }) {
  const q = useEventAttendance(eventId);
  if (q.isLoading) return <span className="muted">…</span>;
  if (q.isError) return <span className="muted">Không tải được</span>;
  return <strong>{q.data?.currentAttendance ?? 0}</strong>;
}

const GATE_KEY = "eventhub.gateId";
/** Mỗi thiết bị quét là một cổng, id sinh một lần và nhớ trên trình duyệt này. */
function readGateId() {
  try {
    const saved = window.localStorage.getItem(GATE_KEY);
    if (saved) return saved;
    const id = crypto.randomUUID();
    window.localStorage.setItem(GATE_KEY, id);
    return id;
  } catch {
    return crypto.randomUUID();
  }
}
const SCAN_TEXT: Record<string, string> = {
  CHECK_IN: "Vé hợp lệ. Mời khách vào cổng.",
  CHECK_OUT: "Đã ghi nhận ra cổng. Vé này không thể vào lại.",
};

type ScanResult =
  | { kind: "checking" }
  | { kind: "ok" | "fail"; title: string; text: string; at: number };

let audio: AudioContext | null = null;
/**
 * Báo kết quả cho nhân viên cổng không cần nhìn màn hình: một tiếng bíp cao khi hợp lệ, hai tiếng trầm khi bị từ
 * chối, kèm rung trên điện thoại. Trình duyệt chặn âm thanh thì bỏ qua.
 */
function gateFeedback(ok: boolean) {
  try {
    navigator.vibrate?.(ok ? 120 : [90, 60, 90]);
    audio ??= new AudioContext();
    const tones = ok ? [{ f: 1046, at: 0 }] : [{ f: 220, at: 0 }, { f: 220, at: 0.2 }];
    for (const tone of tones) {
      const osc = audio.createOscillator();
      const gain = audio.createGain();
      osc.frequency.value = tone.f;
      osc.type = ok ? "sine" : "square";
      gain.gain.value = 0.08;
      osc.connect(gain).connect(audio.destination);
      const start = audio.currentTime + tone.at;
      osc.start(start);
      osc.stop(start + 0.15);
    }
  } catch {
    // Không có âm thanh/rung: kết quả vẫn hiện trên màn hình.
  }
}

/** Ô kết quả lớn ngay trên camera để nhân viên thấy ngay sau mỗi lượt quét. */
function ScanBanner({ result }: { result: ScanResult | null }) {
  if (!result) return null;
  if (result.kind === "checking") {
    return (
      <div className="scan-banner checking" role="status" aria-live="polite">
        <ScanLine size={26} />
        <div>
          <strong>Đang kiểm tra vé...</strong>
        </div>
      </div>
    );
  }
  return (
    <div className={`scan-banner ${result.kind}`} role="status" aria-live="assertive">
      {result.kind === "ok" ? <CheckCircle2 size={30} /> : <XCircle size={30} />}
      <div>
        <strong>{result.title}</strong>
        <p>{result.text}</p>
        <span>
          {new Date(result.at).toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit", second: "2-digit" })}
        </span>
      </div>
    </div>
  );
}

export function Checkin() {
  const mine = useOrganizerEvents();
  const now = useNow();
  const refresh = useRefresh();
  const events = (mine.data ?? []).filter((e) =>
    ["PUBLISHED", "CLOSED"].includes(e.status),
  );
  // Vào từ nút "Quét vé" của một sự kiện thì chọn sẵn sự kiện đó; không thì ưu tiên sự kiện đang diễn ra.
  const fromLink = useSearchParams().get("event");
  const [picked, setPicked] = useState<string | null>(null);
  const eventId =
    picked ??
    events.find((e) => e.id === fromLink)?.id ??
    (now > 0 ? events.find((e) => canScan(e, now))?.id : undefined) ??
    events[0]?.id ??
    "";
  const event = events.find((e) => e.id === eventId);
  const [gateId] = useState(readGateId);
  const [code, setCode] = useState("");
  const [action, setAction] = useState<"check-in" | "check-out">("check-in");
  const [result, setResult] = useState<ScanResult | null>(null);
  const { busy, run } = useAction();
  const attendance = useEventAttendance(eventId || undefined);
  const inWindow = event && now ? canScan(event, now) : true;

  function scan(value: string) {
    if (!value.trim() || busy) return;
    if (!eventId) {
      setResult({ kind: "fail", title: "Chưa chọn sự kiện", text: "Chọn sự kiện cần soát vé rồi quét lại.", at: Date.now() });
      gateFeedback(false);
      return;
    }
    setResult({ kind: "checking" });
    // Lỗi: useAction hiện toast với thông báo theo mã lỗi; ở đây thêm ô kết quả lớn và âm báo.
    void run(
      async () => {
        try {
          return await ticketApi.scan(action, {
            qrCodeValue: value.trim(),
            eventId,
            gateId,
          });
        } catch (error) {
          setResult({
            kind: "fail",
            title: action === "check-in" ? "Check-in thất bại" : "Check-out thất bại",
            text: error instanceof Error ? error.message : "Không kiểm tra được vé.",
            at: Date.now(),
          });
          gateFeedback(false);
          throw error;
        }
      },
      (r) => {
        const title = r.scanType === "CHECK_OUT" ? "Check-out thành công" : "Check-in thành công";
        const text = SCAN_TEXT[r.scanType] ?? "Đã cập nhật trạng thái vé.";
        setResult({ kind: "ok", title, text, at: Date.now() });
        gateFeedback(true);
        toast.success(title, { description: text });
        setCode("");
        void refresh(["analytics", "attendance", eventId]);
      },
    );
  }

  return (
    <>
      <PageTitle
        title="Kiểm soát vé"
        description="Quét mã QR trên vé để cho khách vào hoặc ra cổng."
      />
      <DataBoundary
        loading={mine.isLoading}
        error={mine.isError}
        onRetry={() => void mine.refetch()}
        empty={!events.length}
        title="Chưa có sự kiện để soát vé"
        description="Chỉ sự kiện đã mở bán mới có vé để kiểm tra."
      >
        <div className="dashboard-filters">
          <select
            aria-label="Sự kiện đang soát vé"
            value={eventId}
            onChange={(e) => {
              setPicked(e.target.value);
              setResult(null);
            }}
          >
            {events.map((e) => (
              <option key={e.id} value={e.id}>
                {e.title}
              </option>
            ))}
          </select>
          {event && (
            <span>
              {dateLabel(event.startTime, true)} · {timeLabel(event.startTime)}–
              {timeLabel(event.endTime)} · Cổng {gateId.slice(0, 8)}
            </span>
          )}
        </div>
        {!inWindow && (
          <div className="notice-box mb-5">
            <Info size={17} />
            <p>
              Ngoài khung giờ soát vé (từ 60 phút trước giờ bắt đầu đến khi kết
              thúc). Hệ thống sẽ từ chối lượt quét.
            </p>
          </div>
        )}
        <div className="checkin-layout">
          <section className="panel">
            <div className="tab-row">
              {(["check-in", "check-out"] as const).map((k) => (
                <button
                  key={k}
                  className={action === k ? "selected" : ""}
                  onClick={() => {
                    setAction(k);
                    setResult(null);
                  }}
                >
                  {k === "check-in" ? "Check-in" : "Check-out"}
                </button>
              ))}
            </div>
            <ScanBanner result={result} />
            <QrScanner onDetect={scan} disabled={busy} />
            <form
              onSubmit={(e) => {
                e.preventDefault();
                scan(code);
              }}
            >
              <label className="field-label" htmlFor="scan-code">
                Mã QR (nhập thủ công)
              </label>
              <Input
                id="scan-code"
                value={code}
                autoComplete="off"
                onChange={(e) => {
                  setCode(e.target.value);
                  setResult(null);
                }}
                placeholder="Dán hoặc nhập mã trên vé"
                required
              />
              <Button
                type="submit"
                className="w-full mt-4"
                disabled={busy || !eventId}
              >
                <ScanLine size={16} />
                {busy
                  ? "Đang kiểm tra..."
                  : action === "check-in"
                    ? "Xác nhận check-in"
                    : "Xác nhận check-out"}
              </Button>
            </form>
          </section>
          <section>
            <div className="panel">
              <h2 className="text-lg font-semibold mb-3">Đang có mặt</h2>
              <p className="muted text-xs mb-5">
                Cập nhật mỗi 10 giây từ Analytics.
              </p>
              <div className="metric-card">
                <div>
                  <span>Khán giả trong khu vực</span>
                  <Users size={17} />
                </div>
                <strong>{attendance.data?.currentAttendance ?? 0}</strong>
                <p>
                  Tổng lượt check-in: {attendance.data?.totalCheckedIn ?? 0}
                </p>
              </div>
            </div>
            <div className="notice-box mt-5">
              <Users size={18} />
              <p>
                Mỗi vé chỉ vào một lần và ra một lần. Vé đã ra cổng không thể
                vào lại. Mỗi thiết bị quét được ghi nhận là một cổng riêng.
              </p>
            </div>
          </section>
        </div>
      </DataBoundary>
    </>
  );
}
