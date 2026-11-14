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
  CheckCircle2,
  Clock3,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { useDemo } from "@/hooks/use-demo";
import { DailyReport } from "./daily-report";
import { useNow } from "@/hooks/use-now";
import { money, dateLabel } from "@/lib/utils";
import { PageTitle, DataBoundary, Status, Pagination } from "../shared";
import { Input } from "@/components/ui/input";
import type { Event, Order } from "@/types";
export function downloadCsv(rows: string[][], filename: string) {
  const safe = (v: string) =>
    `"${(/^[=+@-]/.test(v) ? "'" : "") + v.replaceAll('"', '""')}"`;
  const csv = "\uFEFF" + rows.map((r) => r.map(safe).join(",")).join("\r\n");
  const url = URL.createObjectURL(
    new Blob([csv], { type: "text/csv;charset=utf-8" }),
  );
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}
export function Overview({
  admin = false,
  reports = false,
}: {
  admin?: boolean;
  reports?: boolean;
}) {
  const s = useDemo();
  const now = useNow();
  const [eventId, setEventId] = useState("all");
  const [period, setPeriod] = useState("365");
  const events = s.events.filter((e) => admin || e.organizerId === s.userId);
  const eventIds = new Set(events.map((e) => e.id));
  const minDate = reports ? now - Number(period) * 86400000 : 0;
  const relevantOrders = s.orders.filter(
    (o) =>
      eventIds.has(o.eventId) && (eventId === "all" || o.eventId === eventId),
  );
  const orders = relevantOrders.filter(
    (o) => Date.parse(o.paidAt ?? o.createdAt) >= minDate,
  );
  const paid = orders.filter((o) => ["PAID", "REFUNDED"].includes(o.status));
  const revenue = paid.reduce((sum, o) => sum + o.total, 0);
  const refunds = s.refunds.filter(
    (r) =>
      r.status === "REFUNDED" &&
      Date.parse(r.reviewedAt ?? r.createdAt) >= minDate &&
      relevantOrders.some((o) => o.id === r.orderId),
  );
  const refunded = refunds.reduce((sum, r) => sum + r.amount, 0);
  const tickets = s.tickets.filter(
    (t) =>
      eventIds.has(t.eventId) && (eventId === "all" || t.eventId === eventId),
  );
  const sales = paid.reduce(
    (sum, o) => sum + o.lines.reduce((n, l) => n + l.quantity, 0),
    0,
  );
  const attendance = tickets.filter((t) => t.status === "CHECKED_IN").length;
  const stats = admin
    ? [
        {
          label: "Tổng doanh thu gộp",
          value: money(revenue),
          Icon: Wallet,
          caption: "Từ các đơn đã thanh toán",
        },
        {
          label: "Sự kiện chờ duyệt",
          value: String(events.filter((e) => e.status === "SUBMITTED").length),
          Icon: CalendarDays,
          caption: "Cần xem xét trước khi mở bán",
        },
        {
          label: "Yêu cầu hoàn tiền",
          value: String(
            s.refunds.filter((r) => r.status === "REQUESTED").length,
          ),
          Icon: RotateCcw,
          caption: "Đang chờ Admin xử lý",
        },
        {
          label: "Tài khoản hoạt động",
          value: String(s.users.filter((u) => u.status === "ACTIVE").length),
          Icon: Users,
          caption: "Trong không gian trải nghiệm",
        },
      ]
    : [
        {
          label: reports ? "Doanh thu gộp" : "Tổng doanh thu",
          value: money(revenue),
          Icon: Wallet,
          caption: "Giá trị vé đã thanh toán",
        },
        {
          label: reports ? "Đã hoàn tiền" : "Vé đã bán",
          value: reports ? money(refunded) : String(sales),
          Icon: reports ? RotateCcw : Ticket,
          caption: reports
            ? "Theo kết quả duyệt hoàn"
            : "Tính từ các đơn thanh toán",
        },
        {
          label: reports ? "Doanh thu thuần" : "Đang có mặt",
          value: reports ? money(revenue - refunded) : String(attendance),
          Icon: reports ? Wallet : Users,
          caption: reports
            ? "Doanh thu gộp trừ tiền hoàn"
            : "Đã check-in, chưa check-out",
        },
        {
          label: reports ? "Số vé đã hoàn" : "Sự kiện mở bán",
          value: String(
            reports
              ? tickets.filter((t) =>
                  refunds.some((r) => r.orderId === t.orderId),
                ).length
              : events.filter((e) => e.status === "PUBLISHED").length,
          ),
          Icon: CalendarDays,
          caption: reports
            ? "Hủy cả đơn khi được duyệt"
            : "Đang đón những cuộc hẹn mới",
        },
      ];
  return (
    <>
      <PageTitle
        title={
          reports
            ? "Báo cáo doanh thu"
            : admin
              ? "Tổng quan hệ thống"
              : `Chào ${s.user?.name.split(" ").slice(-2).join(" ")},`
        }
        description={
          reports
            ? "Theo dõi doanh số, hoàn tiền và hiệu quả từng hạng vé."
            : admin
              ? "Những cập nhật cần bạn quan tâm hôm nay."
              : "Cùng tạo nên những trải nghiệm đáng nhớ hôm nay."
        }
        action={
          reports ? (
            <Button
              variant="secondary"
              onClick={() =>
                downloadCsv(
                  [
                    ["Mã đơn", "Sự kiện", "Ngày", "Trạng thái", "Doanh thu"],
                    ...orders.map((o) => [
                      o.id,
                      s.events.find((e) => e.id === o.eventId)?.title ?? "",
                      dateLabel(o.paidAt ?? o.createdAt),
                      o.status,
                      String(o.total),
                    ]),
                  ],
                  "eventhub-doanh-thu.csv",
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
            onChange={(e) => setPeriod(e.target.value)}
          >
            <option value="7">7 ngày gần nhất</option>
            <option value="30">30 ngày gần nhất</option>
            <option value="365">12 tháng gần nhất</option>
          </select>
        )}
        <span>Dữ liệu mô phỏng, cập nhật theo thao tác</span>
      </div>
      <DataBoundary>
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
                  7 ngày gần nhất · Giờ Việt Nam
                </p>
              </div>
              <span className="chart-legend">
                <i />
                Doanh thu gộp
              </span>
            </div>
            <RevenueChart orders={paid} />
          </section>
          <section className="panel distribution-panel">
            <h2>Vé theo hạng</h2>
            <div className="tier-total">
              <Ticket size={24} />
              <strong>{sales}</strong>
              <span>vé đã bán</span>
            </div>
            {(["STANDARD", "PREMIUM", "VIP"] as const).map((tier, i) => {
              const n = paid.reduce(
                (sum, o) =>
                  sum +
                  o.lines
                    .filter((l) => l.tier === tier)
                    .reduce((a, l) => a + l.quantity, 0),
                0,
              );
              return (
                <div className="tier-breakdown" key={tier}>
                  <span>
                    <i className={`tier-color-${i}`} />
                    {tier}
                  </span>
                  <strong>
                    {n} vé{" "}
                    <small>
                      ({sales ? Math.round((n / sales) * 100) : 0}%)
                    </small>
                  </strong>
                </div>
              );
            })}
          </section>
        </div>
        {admin && (
          <div className="admin-task-row">
            <Link href="/admin/events">
              <CalendarDays size={22} />
              <div>
                <strong>Duyệt sự kiện mới</strong>
                <p>
                  {events.filter((e) => e.status === "SUBMITTED").length} sự
                  kiện đang chờ bạn xem xét
                </p>
              </div>
              <ArrowRight size={18} />
            </Link>
            <Link href="/admin/refunds">
              <RotateCcw size={22} />
              <div>
                <strong>Xử lý hoàn tiền</strong>
                <p>
                  {s.refunds.filter((r) => r.status === "REQUESTED").length} yêu
                  cầu đang chờ xử lý
                </p>
              </div>
              <ArrowRight size={18} />
            </Link>
          </div>
        )}
        <section className="panel mt-6">
          <div className="section-heading">
            <h2>{reports ? "Chi tiết giao dịch" : "Sự kiện của bạn"}</h2>
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
            <RecentOrders orders={orders} events={events} />
          ) : (
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>SỰ KIỆN</th>
                    <th>NGÀY DIỄN RA</th>
                    <th>VÉ ĐÃ BÁN</th>
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
                      <td>
                        {s.tickets.filter((t) => t.eventId === e.id).length}
                      </td>
                      <td>
                        <Status value={e.status} />
                      </td>
                      <td>
                        <Link
                          className="icon-button"
                          aria-label={`Quản lý ${e.title}`}
                          href={admin ? "/admin/events" : "/organizer/events"}
                        >
                          <ArrowUpRight size={16} />
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
        {reports && (
          <DailyReport
            orders={relevantOrders}
            refunds={refunds}
            minDate={minDate}
            exportRows={downloadCsv}
          />
        )}
        <div className="dashboard-footnote">
          <CheckCircle2 size={14} />
          Thay đổi đơn hàng, hoàn tiền và check-in được phản ánh trực tiếp trong
          dữ liệu này.
          <Clock3 size={14} />
        </div>
      </DataBoundary>
    </>
  );
}
function RevenueChart({ orders }: { orders: Order[] }) {
  const days = Array.from({ length: 7 }, (_, i) => {
    const d = new Date();
    d.setDate(d.getDate() - 6 + i);
    const key = d.toLocaleDateString("en-CA", { timeZone: "Asia/Ho_Chi_Minh" });
    return {
      label: d.toLocaleDateString("vi-VN", {
        day: "2-digit",
        month: "2-digit",
        timeZone: "Asia/Ho_Chi_Minh",
      }),
      value: orders
        .filter(
          (o) =>
            new Date(o.paidAt ?? o.createdAt).toLocaleDateString("en-CA", {
              timeZone: "Asia/Ho_Chi_Minh",
            }) === key,
        )
        .reduce((s, o) => s + o.total, 0),
    };
  });
  const max = Math.max(...days.map((d) => d.value), 1000000);
  return (
    <div
      className="revenue-chart"
      role="img"
      aria-label={`Biểu đồ doanh thu 7 ngày: ${days.map((d) => `${d.label}: ${money(d.value)}`).join(", ")}`}
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
function RecentOrders({
  orders,
  events,
}: {
  orders: Order[];
  events: Event[];
}) {
  const [query, setQuery] = useState("");
  const [requestedPage, setPage] = useState(0);
  const filtered = orders.filter((o) =>
    (o.id + " " + events.find((e) => e.id === o.eventId)?.title)
      .toLowerCase()
      .includes(query.toLowerCase()),
  );
  const page = Math.min(
    requestedPage,
    Math.max(0, Math.ceil(filtered.length / 8) - 1),
  );
  return (
    <>
      <Input
        className="mb-5"
        aria-label="Tìm giao dịch"
        placeholder="Tìm mã đơn hoặc tên sự kiện..."
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
                <th>MÃ ĐƠN</th>
                <th>SỰ KIỆN</th>
                <th>NGÀY</th>
                <th>GIÁ TRỊ ĐƠN</th>
                <th>TRẠNG THÁI</th>
              </tr>
            </thead>
            <tbody>
              {filtered.slice(page * 8, (page + 1) * 8).map((o) => (
                <tr key={o.id}>
                  <td>{o.id}</td>
                  <td>
                    <span
                      className="truncate-cell"
                      title={events.find((e) => e.id === o.eventId)?.title}
                    >
                      {events.find((e) => e.id === o.eventId)?.title}
                    </span>
                  </td>
                  <td>{dateLabel(o.paidAt ?? o.createdAt, true)}</td>
                  <td>{money(o.total)}</td>
                  <td>
                    <Status value={o.status} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <p className="muted text-sm py-10 text-center">
          Chưa có giao dịch phù hợp.
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
