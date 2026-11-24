"use client";
import { useState } from "react";
import Link from "next/link";
import {
  ArrowUpRight,
  ArrowRight,
  Ticket,
  Wallet,
  Users,
  CalendarDays,
  Download,
  Plus,
  RotateCcw,
  Clock3,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  useAdminEvents,
  useAdminRefunds,
  useAdminUsers,
  useOrganizerEvents,
  useSession,
} from "@/hooks/use-api";
import {
  usePlatformAnalytics,
  type RangePreset,
} from "@/hooks/use-platform-analytics";
import { DailyReport } from "./daily-report";
import { money, dateLabel } from "@/lib/utils";
import { PageTitle, DataBoundary, Status, Pagination } from "../shared";
import { Input } from "@/components/ui/input";
import type { AnalyticsSeriesPoint, EventAnalyticsRow } from "@/types/analytics";
export function downloadCsv(rows: string[][], filename: string) {
  const safe = (v: string) =>
    `"${(/^[=+@-]/.test(v) ? "'" : "") + v.replaceAll('"', '""')}"`;
  const csv = "﻿" + rows.map((r) => r.map(safe).join(",")).join("\r\n");
  const url = URL.createObjectURL(
    new Blob([csv], { type: "text/csv;charset=utf-8" }),
  );
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}
const PERIODS: { value: RangePreset; label: string }[] = [
  { value: "7d", label: "7 ngày gần nhất" },
  { value: "30d", label: "30 ngày gần nhất" },
  { value: "12m", label: "12 tháng gần nhất" },
];
export function Overview({
  admin = false,
  reports = false,
}: {
  admin?: boolean;
  reports?: boolean;
}) {
  return admin ? (
    <AdminOverview />
  ) : (
    <OrganizerOverview reports={reports} />
  );
}
function AdminOverview() {
  const submitted = useAdminEvents("SUBMITTED");
  const refunds = useAdminRefunds("REQUESTED");
  const activeUsers = useAdminUsers({ page: 0, size: 1, status: "ACTIVE" });
  const all = useAdminEvents();
  const pendingEvents = submitted.data?.length ?? 0;
  const pendingRefunds = refunds.data?.length ?? 0;
  return (
    <Dashboard
      admin
      events={all.data ?? []}
      eventsLoading={all.isLoading}
      counters={{
        pendingEvents,
        pendingRefunds,
        activeUsers: activeUsers.data?.total ?? 0,
      }}
    />
  );
}
function OrganizerOverview({ reports }: { reports: boolean }) {
  const mine = useOrganizerEvents();
  return (
    <Dashboard
      reports={reports}
      events={mine.data ?? []}
      eventsLoading={mine.isLoading}
    />
  );
}
function Dashboard({
  admin = false,
  reports = false,
  events,
  eventsLoading,
  counters,
}: {
  admin?: boolean;
  reports?: boolean;
  events: { id: string; title: string; category: string; startTime: string; status: string }[];
  eventsLoading: boolean;
  counters?: { pendingEvents: number; pendingRefunds: number; activeUsers: number };
}) {
  const { user } = useSession();
  const [eventId, setEventId] = useState("all");
  const [period, setPeriod] = useState<RangePreset>(reports ? "12m" : "7d");
  const granularity = period === "12m" ? "MONTH" : "DAY";
  const analytics = usePlatformAnalytics(
    period,
    granularity,
    eventId === "all" ? null : eventId,
  );
  const data = analytics.data;
  const totals = data?.overview.totals;
  const rowsById = new Map(data?.events.map((r) => [r.eventId, r]));
  const stats = admin
    ? [
        {
          label: "Doanh thu gộp",
          value: money(totals?.grossRevenue ?? 0),
          Icon: Wallet,
          caption: "Đơn đã thanh toán trong 7 ngày",
        },
        {
          label: "Sự kiện chờ duyệt",
          value: String(counters?.pendingEvents ?? 0),
          Icon: CalendarDays,
          caption: "Cần xem xét trước khi mở bán",
        },
        {
          label: "Yêu cầu hoàn tiền",
          value: String(counters?.pendingRefunds ?? 0),
          Icon: RotateCcw,
          caption: "Đang chờ Admin xử lý",
        },
        {
          label: "Tài khoản hoạt động",
          value: String(counters?.activeUsers ?? 0),
          Icon: Users,
          caption: "Không tính tài khoản đã khóa",
        },
      ]
    : reports
      ? [
          {
            label: "Doanh thu gộp",
            value: money(totals?.grossRevenue ?? 0),
            Icon: Wallet,
            caption: "Giá trị vé đã thanh toán",
          },
          {
            label: "Đã hoàn tiền",
            value: money(totals?.refundedAmount ?? 0),
            Icon: RotateCcw,
            caption: "Theo kết quả hoàn tiền",
          },
          {
            label: "Doanh thu thuần",
            value: money(totals?.netRevenue ?? 0),
            Icon: Wallet,
            caption: "Doanh thu gộp trừ tiền hoàn",
          },
          {
            label: "Số vé đã hoàn",
            value: String(totals?.ticketsRefunded ?? 0),
            Icon: CalendarDays,
            caption: "Hủy cả đơn khi được duyệt",
          },
        ]
      : [
          {
            label: "Doanh thu thuần",
            value: money(totals?.netRevenue ?? 0),
            Icon: Wallet,
            caption: "7 ngày gần nhất, đã trừ hoàn tiền",
          },
          {
            label: "Vé đã bán",
            value: String(totals?.ticketsSold ?? 0),
            Icon: Ticket,
            caption: "7 ngày gần nhất",
          },
          {
            label: "Khán giả check-in",
            value: String(totals?.attendees ?? 0),
            Icon: Users,
            caption: "7 ngày gần nhất",
          },
          {
            label: "Sự kiện mở bán",
            value: String(events.filter((e) => e.status === "PUBLISHED").length),
            Icon: CalendarDays,
            caption: "Đang đón những cuộc hẹn mới",
          },
        ];
  const sold = (data?.overview.byTicketType ?? []).reduce(
    (s, t) => s + t.ticketsSold,
    0,
  );
  return (
    <>
      <PageTitle
        title={
          reports
            ? "Báo cáo doanh thu"
            : admin
              ? "Tổng quan hệ thống"
              : `Chào ${(user?.name ?? "").split(" ").slice(-2).join(" ")},`
        }
        description={
          reports
            ? "Theo dõi doanh số, hoàn tiền và hiệu quả từng sự kiện."
            : admin
              ? "Những cập nhật cần bạn quan tâm hôm nay."
              : "Cùng tạo nên những trải nghiệm đáng nhớ hôm nay."
        }
        action={
          reports ? (
            <Button
              variant="secondary"
              disabled={!data}
              onClick={() =>
                data &&
                downloadCsv(
                  [
                    ["Sự kiện", "Ngày diễn ra", "Vé bán", "Vé hoàn", "Doanh thu gộp", "Hoàn tiền", "Doanh thu thuần"],
                    ...data.events.map((r) => [
                      r.title,
                      dateLabel(r.startTime, true),
                      String(r.ticketsSold),
                      String(r.ticketsRefunded),
                      String(r.grossRevenue),
                      String(r.refundedAmount),
                      String(r.netRevenue),
                    ]),
                  ],
                  `eventhub-doanh-thu-${data.range.from}-${data.range.to}.csv`,
                )
              }
            >
              <Download size={15} />
              Xuất CSV
            </Button>
          ) : !admin ? (
            <Button asChild>
              <Link href="/organizer/events/new">
                <Plus size={16} />
                Tạo sự kiện
              </Link>
            </Button>
          ) : undefined
        }
      />
      <div className="dashboard-filters">
        <select
          aria-label="Chọn sự kiện báo cáo"
          value={eventId}
          onChange={(e) => setEventId(e.target.value)}
        >
          <option value="all">Tất cả sự kiện</option>
          {events.map((e) => (
            <option key={e.id} value={e.id}>
              {e.title}
            </option>
          ))}
        </select>
        {reports && (
          <select
            aria-label="Khoảng thời gian"
            value={period}
            onChange={(e) => setPeriod(e.target.value as RangePreset)}
          >
            {PERIODS.map((p) => (
              <option key={p.value} value={p.value}>
                {p.label}
              </option>
            ))}
          </select>
        )}
        <span>
          {data
            ? `${dateLabel(data.range.from, true)} – ${dateLabel(data.range.to, true)} · Giờ Việt Nam`
            : "Đang tải số liệu"}
        </span>
      </div>
      <DataBoundary
        loading={analytics.isLoading}
        error={analytics.isError}
        onRetry={analytics.refetch}
      >
        <div className="metric-grid">
          {stats.map(({ label, value, Icon, caption }) => (
            <div className="metric-card" key={label}>
              <div>
                <span>{label}</span>
                <Icon size={17} />
              </div>
              <strong>{value}</strong>
              <p>{caption}</p>
            </div>
          ))}
        </div>
        <div className="dashboard-charts">
          <section className="panel revenue-panel">
            <div className="section-heading">
              <div>
                <h2>Nhịp độ bán vé</h2>
                <p className="muted text-[10px] mt-2">
                  {granularity === "MONTH" ? "Theo tháng" : "Theo ngày"} · Giờ
                  Việt Nam
                </p>
              </div>
              <span className="chart-legend">
                <i />
                Doanh thu gộp
              </span>
            </div>
            <RevenueChart
              points={data?.overview.series ?? []}
              monthly={granularity === "MONTH"}
            />
          </section>
          <section className="panel distribution-panel">
            <h2>Vé theo hạng</h2>
            <div className="tier-total">
              <Ticket size={24} />
              <strong>{sold}</strong>
              <span>vé hợp lệ</span>
            </div>
            {(["STANDARD", "PREMIUM", "VIP"] as const).map((tier, i) => {
              const n =
                data?.overview.byTicketType.find((t) => t.ticketTypeName === tier)
                  ?.ticketsSold ?? 0;
              return (
                <div className="tier-breakdown" key={tier}>
                  <span>
                    <i className={`tier-color-${i}`} />
                    {tier}
                  </span>
                  <strong>
                    {n} vé{" "}
                    <small>({sold ? Math.round((n / sold) * 100) : 0}%)</small>
                  </strong>
                </div>
              );
            })}
          </section>
        </div>
        {admin && counters && (
          <div className="admin-task-row">
            <Link href="/admin/events">
              <CalendarDays size={22} />
              <div>
                <strong>Duyệt sự kiện mới</strong>
                <p>{counters.pendingEvents} sự kiện đang chờ bạn xem xét</p>
              </div>
              <ArrowRight size={18} />
            </Link>
            <Link href="/admin/refunds">
              <RotateCcw size={22} />
              <div>
                <strong>Xử lý hoàn tiền</strong>
                <p>{counters.pendingRefunds} yêu cầu đang chờ xử lý</p>
              </div>
              <ArrowRight size={18} />
            </Link>
          </div>
        )}
        <section className="panel mt-6">
          <div className="section-heading">
            <h2>{reports ? "Chi tiết theo sự kiện" : "Sự kiện của bạn"}</h2>
            {!reports && (
              <Link
                href={admin ? "/admin/events" : "/organizer/events"}
                className="text-link"
              >
                Xem tất cả
                <ArrowRight size={14} />
              </Link>
            )}
          </div>
          {reports ? (
            <EventRows rows={data?.events ?? []} />
          ) : eventsLoading ? (
            <div className="skeleton" />
          ) : events.length ? (
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>SỰ KIỆN</th>
                    <th>NGÀY DIỄN RA</th>
                    <th>VÉ HỢP LỆ</th>
                    <th>TRẠNG THÁI</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {events.slice(0, 5).map((e) => (
                    <tr key={e.id}>
                      <td>
                        <strong>{e.title}</strong>
                        <span className="sub">{e.category}</span>
                      </td>
                      <td>{dateLabel(e.startTime, true)}</td>
                      <td>{rowsById.get(e.id)?.validTicketsLifetime ?? 0}</td>
                      <td>
                        <Status value={e.status} />
                      </td>
                      <td>
                        <Link
                          className="icon-button"
                          aria-label={`Quản lý ${e.title}`}
                          href={
                            admin ? "/admin/events" : `/organizer/events/${e.id}/edit`
                          }
                        >
                          <ArrowUpRight size={16} />
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <p className="muted text-sm py-10 text-center">
              Chưa có sự kiện nào.
            </p>
          )}
        </section>
        {reports && data && (
          <DailyReport
            points={data.overview.series}
            monthly={granularity === "MONTH"}
            exportRows={downloadCsv}
          />
        )}
        <div className="dashboard-footnote">
          <Clock3 size={14} />
          Số liệu do Analytics tổng hợp từ sự kiện đặt vé, hoàn tiền và check-in;
          có thể trễ vài giây so với thao tác thực tế.
        </div>
      </DataBoundary>
    </>
  );
}
function periodText(period: string, monthly: boolean) {
  const parts = period.split("-");
  return monthly ? `${parts[1]}/${parts[0].slice(2)}` : `${parts[2]}/${parts[1]}`;
}
function RevenueChart({
  points,
  monthly,
}: {
  points: AnalyticsSeriesPoint[];
  monthly: boolean;
}) {
  // Nhiều kỳ quá thì chỉ hiện các kỳ gần nhất cho vừa khung.
  const days = points.slice(-12).map((p) => ({
    label: periodText(p.period, monthly),
    value: p.grossRevenue,
  }));
  const max = Math.max(...days.map((d) => d.value), 1000000);
  return (
    <div
      className="revenue-chart"
      role="img"
      aria-label={`Biểu đồ doanh thu: ${days.map((d) => `${d.label}: ${money(d.value)}`).join(", ")}`}
    >
      <div className="chart-y">
        <span>{money(max)}</span>
        <span>{money(max / 2)}</span>
        <span>0 ₫</span>
      </div>
      <div className="chart-bars">
        {days.map((d) => (
          <div key={d.label} className="chart-column">
            <div className="chart-bar-space">
              <div
                className="chart-bar"
                style={{ height: `${Math.max(1, (d.value / max) * 100)}%` }}
                title={`${d.label}: ${money(d.value)}`}
              >
                {d.value > 0 && <span>{money(d.value)}</span>}
              </div>
            </div>
            <small>{d.label}</small>
          </div>
        ))}
      </div>
    </div>
  );
}
function EventRows({ rows }: { rows: EventAnalyticsRow[] }) {
  const [query, setQuery] = useState("");
  const [requestedPage, setPage] = useState(0);
  const filtered = rows.filter((r) =>
    r.title.toLowerCase().includes(query.trim().toLowerCase()),
  );
  const page = Math.min(
    requestedPage,
    Math.max(0, Math.ceil(filtered.length / 8) - 1),
  );
  return (
    <>
      <Input
        className="mb-5"
        aria-label="Tìm sự kiện"
        placeholder="Tìm theo tên sự kiện..."
        value={query}
        onChange={(e) => {
          setQuery(e.target.value);
          setPage(0);
        }}
      />
      {filtered.length ? (
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>SỰ KIỆN</th>
                <th>NGÀY DIỄN RA</th>
                <th>VÉ BÁN</th>
                <th>VÉ HOÀN</th>
                <th>DOANH THU THUẦN</th>
                <th>TRẠNG THÁI</th>
              </tr>
            </thead>
            <tbody>
              {filtered.slice(page * 8, (page + 1) * 8).map((r) => (
                <tr key={r.eventId}>
                  <td>
                    <span className="truncate-cell" title={r.title}>
                      {r.title}
                    </span>
                  </td>
                  <td>{dateLabel(r.startTime, true)}</td>
                  <td>{r.ticketsSold}</td>
                  <td>{r.ticketsRefunded}</td>
                  <td>{money(r.netRevenue)}</td>
                  <td>
                    <Status value={r.status} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <p className="muted text-sm py-10 text-center">
          Chưa có số liệu trong khoảng thời gian này.
        </p>
      )}
      <Pagination
        page={page}
        total={filtered.length}
        pageSize={8}
        onChange={setPage}
      />
    </>
  );
}
