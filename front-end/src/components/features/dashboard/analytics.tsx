"use client";
import { useState } from "react";
import {
  Download,
  Wallet,
  Ticket,
  Users,
  RotateCcw,
  TrendingUp,
  TrendingDown,
  X,
  ArrowDownUp,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn, dateLabel, money } from "@/lib/utils";
import {
  defaultGranularity,
  rangePresets,
  usePlatformAnalytics,
  type RangePreset,
} from "@/hooks/use-platform-analytics";
import type {
  AnalyticsSeriesPoint,
  EventAnalyticsRow,
  EventAnalyticsSort,
  Granularity,
} from "@/types/analytics";
import { DataBoundary, Empty, PageTitle, Pagination, Status } from "../shared";
import { downloadCsv } from "./overview";

const SERIES = {
  revenue: "var(--viz-revenue)",
  sold: "var(--viz-series-1)",
  attendees: "var(--viz-series-2)",
};
const TIER_COLOR = {
  STANDARD: "var(--viz-series-1)",
  PREMIUM: "var(--viz-series-2)",
  VIP: "var(--viz-series-3)",
} as const;

const count = (n: number) => new Intl.NumberFormat("vi-VN").format(n);
const pct = (n: number) =>
  new Intl.NumberFormat("vi-VN", {
    style: "percent",
    maximumFractionDigits: 1,
  }).format(n);
function compactMoney(n: number) {
  const f = (v: number) =>
    new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 1 }).format(v);
  if (n >= 1e9) return `${f(n / 1e9)} tỷ`;
  if (n >= 1e6) return `${f(n / 1e6)} tr`;
  if (n >= 1e3) return `${f(n / 1e3)} N`;
  return f(n);
}
function niceMax(v: number) {
  if (v <= 0) return 1;
  const p = 10 ** Math.floor(Math.log10(v));
  return ([1, 1.2, 1.5, 2, 2.5, 3, 4, 5, 6, 8, 10].find((m) => m * p >= v) ?? 10) * p;
}
function periodLabel(period: string, g: Granularity, long = false) {
  if (g === "MONTH") {
    const [y, m] = period.split("-");
    return long ? `Tháng ${Number(m)}/${y}` : `T${Number(m)}/${y.slice(2)}`;
  }
  const [y, m, d] = period.split("-");
  return long ? `${d}/${m}/${y}` : `${d}/${m}`;
}

export function AnalyticsAdmin() {
  const [preset, setPreset] = useState<RangePreset>("12m");
  const [granularity, setGranularity] = useState<Granularity>("MONTH");
  const [eventId, setEventId] = useState<string | null>(null);
  const data = usePlatformAnalytics(preset, granularity, eventId);
  const unit = granularity === "MONTH" ? "tháng" : "ngày";
  const selected = data?.catalog.find((e) => e.id === eventId);

  return (
    <>
      <PageTitle
        title="Phân tích & thống kê"
        description="Doanh thu, vé bán ra và khán giả tham gia trên toàn hệ thống — theo sự kiện, theo tháng và theo ngày."
        action={
          data && (
            <Button
              variant="secondary"
              onClick={() =>
                downloadCsv(
                  [
                    [
                      "Kỳ",
                      "Doanh thu gộp",
                      "Hoàn tiền",
                      "Doanh thu thuần",
                      "Vé bán",
                      "Vé hoàn",
                      "Khán giả check-in",
                    ],
                    ...data.overview.series.map((p) => [
                      p.period,
                      String(p.grossRevenue),
                      String(p.refundedAmount),
                      String(p.netRevenue),
                      String(p.ticketsSold),
                      String(p.ticketsRefunded),
                      String(p.attendees),
                    ]),
                  ],
                  `eventhub-phan-tich-${data.range.from}-${data.range.to}.csv`,
                )
              }
            >
              <Download size={15} />
              Xuất CSV
            </Button>
          )
        }
      />
      <div className="analytics-filters">
        <select
          aria-label="Khoảng thời gian"
          value={preset}
          onChange={(e) => {
            const p = e.target.value as RangePreset;
            setPreset(p);
            setGranularity(defaultGranularity(p));
          }}
        >
          {rangePresets.map((p) => (
            <option key={p.value} value={p.value}>
              {p.label}
            </option>
          ))}
        </select>
        <div className="segmented" role="group" aria-label="Nhóm theo">
          {(["DAY", "MONTH"] as const).map((g) => (
            <button
              key={g}
              type="button"
              aria-pressed={granularity === g}
              className={cn(granularity === g && "selected")}
              disabled={g === "DAY" && (preset === "12m" || preset === "ytd")}
              title={
                g === "DAY" && (preset === "12m" || preset === "ytd")
                  ? "Chọn khoảng ≤ 90 ngày để xem theo ngày"
                  : undefined
              }
              onClick={() => setGranularity(g)}
            >
              Theo {g === "DAY" ? "ngày" : "tháng"}
            </button>
          ))}
        </div>
        <select
          aria-label="Sự kiện"
          value={eventId ?? "all"}
          onChange={(e) =>
            setEventId(e.target.value === "all" ? null : e.target.value)
          }
        >
          <option value="all">Tất cả sự kiện</option>
          {data?.catalog.map((e) => (
            <option key={e.id} value={e.id}>
              {e.title}
            </option>
          ))}
        </select>
        {selected && (
          <button
            type="button"
            className="filter-chip"
            onClick={() => setEventId(null)}
          >
            {selected.title}
            <X size={13} aria-label="Bỏ lọc sự kiện" />
          </button>
        )}
        {data && (
          <span className="analytics-range">
            {dateLabel(data.range.from, true)} –{" "}
            {dateLabel(data.range.to, true)} · Giờ Việt Nam
          </span>
        )}
      </div>
      <DataBoundary>
        {!data ? (
          <div aria-label="Đang tải dữ liệu" className="skeleton-grid">
            {Array.from({ length: 4 }, (_, i) => (
              <div className="skeleton" key={i} />
            ))}
          </div>
        ) : (
          <>
            <Kpis data={data.overview} />
            <div className="dashboard-charts">
              <section className="panel analytics-main-chart">
                <div className="section-heading">
                  <div>
                    <h2>Doanh thu thuần theo {unit}</h2>
                    <p className="muted text-[10px] mt-2">
                      Doanh thu gộp trừ tiền hoàn · ghi nhận theo ngày thanh
                      toán / ngày hoàn
                    </p>
                  </div>
                </div>
                <BarChart
                  points={data.overview.series}
                  granularity={granularity}
                  series={[
                    {
                      name: "Doanh thu thuần",
                      color: SERIES.revenue,
                      value: (p) => p.netRevenue,
                    },
                  ]}
                  format={money}
                  axis={compactMoney}
                  extra={(p) => [
                    ["Doanh thu gộp", money(p.grossRevenue)],
                    ["Hoàn tiền", money(p.refundedAmount)],
                  ]}
                />
              </section>
              <section className="panel analytics-side">
                <h2>Theo hạng vé</h2>
                <p className="muted text-[10px] mt-2 mb-6">
                  Vé hợp lệ (đã trừ vé hoàn)
                </p>
                <TierBreakdown items={data.overview.byTicketType} />
              </section>
              <section className="panel analytics-wide">
                <div className="section-heading">
                  <div>
                    <h2>Vé bán ra & khán giả tham gia theo {unit}</h2>
                    <p className="muted text-[10px] mt-2">
                      Khán giả = số vé đã check-in, ghi nhận vào ngày diễn ra sự
                      kiện
                    </p>
                  </div>
                </div>
                <BarChart
                  points={data.overview.series}
                  granularity={granularity}
                  series={[
                    {
                      name: "Vé bán ra",
                      color: SERIES.sold,
                      value: (p) => p.ticketsSold,
                    },
                    {
                      name: "Khán giả check-in",
                      color: SERIES.attendees,
                      value: (p) => p.attendees,
                    },
                  ]}
                  format={(n) => `${count(n)}`}
                  axis={(n) => count(n)}
                  extra={(p) => [["Vé hoàn", count(p.ticketsRefunded)]]}
                />
              </section>
            </div>
            <EventTable
              rows={data.events}
              now={data.today}
              selected={eventId}
              onSelect={(id) => setEventId(id === eventId ? null : id)}
            />
            <PeriodTable
              points={data.overview.series}
              granularity={granularity}
            />
          </>
        )}
      </DataBoundary>
    </>
  );
}

function Kpis({
  data,
}: {
  data: NonNullable<ReturnType<typeof usePlatformAnalytics>>["overview"];
}) {
  const { totals: t, previousTotals: p } = data;
  const cards = [
    {
      label: "Doanh thu thuần",
      value: money(t.netRevenue),
      caption: `Gộp ${money(t.grossRevenue)}`,
      Icon: Wallet,
      cur: t.netRevenue,
      prev: p.netRevenue,
    },
    {
      label: "Vé bán ra",
      value: count(t.ticketsSold),
      caption: `${count(t.ticketsSold - t.ticketsRefunded)} vé hợp lệ`,
      Icon: Ticket,
      cur: t.ticketsSold,
      prev: p.ticketsSold,
    },
    {
      label: "Khán giả tham gia",
      value: count(t.attendees),
      caption: "Vé đã check-in tại cổng",
      Icon: Users,
      cur: t.attendees,
      prev: p.attendees,
    },
    {
      label: "Hoàn tiền",
      value: money(t.refundedAmount),
      caption: `${count(t.ticketsRefunded)} vé đã hoàn`,
      Icon: RotateCcw,
      cur: t.refundedAmount,
      prev: p.refundedAmount,
      invert: true,
    },
  ];
  return (
    <div className="metric-grid">
      {cards.map(({ label, value, caption, Icon, cur, prev, invert }) => {
        const delta = prev ? (cur - prev) / prev : null;
        const good = delta !== null && (invert ? delta <= 0 : delta >= 0);
        return (
          <div className="metric-card" key={label}>
            <div>
              <span>{label}</span>
              <Icon size={17} />
            </div>
            <strong>{value}</strong>
            <p>{caption}</p>
            <p
              className={cn(
                "metric-delta",
                delta !== null && (good ? "up" : "down"),
              )}
            >
              {delta === null ? (
                "Kỳ trước chưa có dữ liệu"
              ) : (
                <>
                  {delta >= 0 ? (
                    <TrendingUp size={12} />
                  ) : (
                    <TrendingDown size={12} />
                  )}
                  {delta >= 0 ? "+" : ""}
                  {pct(delta)} so với kỳ trước
                </>
              )}
            </p>
          </div>
        );
      })}
    </div>
  );
}

interface Series {
  name: string;
  color: string;
  value: (p: AnalyticsSeriesPoint) => number;
}
function BarChart({
  points,
  series,
  granularity,
  format,
  axis,
  extra,
}: {
  points: AnalyticsSeriesPoint[];
  series: Series[];
  granularity: Granularity;
  format: (n: number) => string;
  axis: (n: number) => string;
  extra?: (p: AnalyticsSeriesPoint) => [string, string][];
}) {
  const [hover, setHover] = useState<number | null>(null);
  const max = niceMax(
    Math.max(0, ...points.flatMap((p) => series.map((s) => s.value(p)))),
  );
  const step = Math.ceil(points.length / 12);
  const active = hover === null ? null : points[hover];
  const all = points.every((p) => series.every((s) => !s.value(p)));
  if (all)
    return (
      <p className="muted text-sm text-center py-16">
        Chưa có số liệu trong khoảng thời gian này.
      </p>
    );
  return (
    <div className="viz">
      {series.length > 1 && (
        <div className="viz-legend">
          {series.map((s) => (
            <span key={s.name}>
              <i style={{ background: s.color }} />
              {s.name}
            </span>
          ))}
        </div>
      )}
      <div className="viz-plot" onPointerLeave={() => setHover(null)}>
        <div className="viz-grid" aria-hidden>
          {[max, max / 2, 0].map((v) => (
            <span key={v}>
              <small>{axis(v)}</small>
            </span>
          ))}
        </div>
        <div className={cn("viz-cols", points.length > 31 && "dense")}>
          {points.map((p, i) => (
            <div
              key={p.period}
              className={cn("viz-col", hover === i && "hovered")}
              tabIndex={0}
              aria-label={`${periodLabel(p.period, granularity, true)}: ${series
                .map((s) => `${s.name} ${format(s.value(p))}`)
                .join(", ")}`}
              onPointerEnter={() => setHover(i)}
              onFocus={() => setHover(i)}
              onBlur={() => setHover(null)}
            >
              <div className="viz-bars">
                {series.map((s) => (
                  <span
                    key={s.name}
                    style={{
                      background: s.color,
                      height: `${(s.value(p) / max) * 100}%`,
                    }}
                  />
                ))}
              </div>
            </div>
          ))}
        </div>
        {active && hover !== null && (
          <div
            className="viz-tooltip"
            role="status"
            style={{
              left: `${((hover + 0.5) / points.length) * 100}%`,
              transform: `translateX(${hover < points.length / 4 ? "-10%" : hover > (points.length * 3) / 4 ? "-90%" : "-50%"})`,
            }}
          >
            <small>{periodLabel(active.period, granularity, true)}</small>
            {series.map((s) => (
              <div key={s.name}>
                <i style={{ background: s.color }} />
                <b>{format(s.value(active))}</b>
                <span>{s.name}</span>
              </div>
            ))}
            {extra?.(active).map(([k, v]) => (
              <div key={k} className="viz-tooltip-extra">
                <b>{v}</b>
                <span>{k}</span>
              </div>
            ))}
          </div>
        )}
      </div>
      <div className="viz-x" aria-hidden>
        {points.map((p, i) => (
          <small key={p.period}>
            {i % step === 0 ? periodLabel(p.period, granularity) : ""}
          </small>
        ))}
      </div>
    </div>
  );
}

function TierBreakdown({
  items,
}: {
  items: NonNullable<
    ReturnType<typeof usePlatformAnalytics>
  >["overview"]["byTicketType"];
}) {
  const total = items.reduce((s, t) => s + t.ticketsSold, 0);
  if (!total)
    return <p className="muted text-sm py-8 text-center">Chưa có vé bán ra.</p>;
  return (
    <div className="tier-bars">
      {items.map((t) => (
        <div key={t.ticketTypeName}>
          <div>
            <span>
              <i style={{ background: TIER_COLOR[t.ticketTypeName] }} />
              {t.ticketTypeName}
            </span>
            <b>
              {count(t.ticketsSold)} vé{" "}
              <small>({pct(t.ticketsSold / total)})</small>
            </b>
          </div>
          <div className="tier-track">
            <span
              style={{
                width: `${(t.ticketsSold / total) * 100}%`,
                background: TIER_COLOR[t.ticketTypeName],
              }}
            />
          </div>
          <small>{money(t.netRevenue)}</small>
        </div>
      ))}
    </div>
  );
}

function EventTable({
  rows,
  now,
  selected,
  onSelect,
}: {
  rows: EventAnalyticsRow[];
  now: string;
  selected: string | null;
  onSelect: (id: string) => void;
}) {
  const [sort, setSort] = useState<EventAnalyticsSort>("netRevenue");
  const [requestedPage, setPage] = useState(0);
  const sorted = [...rows].sort((a, b) => b[sort] - a[sort]);
  const page = Math.min(
    requestedPage,
    Math.max(0, Math.ceil(sorted.length / 8) - 1),
  );
  const head = (key: EventAnalyticsSort, label: string) => (
    <th aria-sort={sort === key ? "descending" : "none"}>
      <button
        type="button"
        className={cn("th-sort", sort === key && "active")}
        onClick={() => {
          setSort(key);
          setPage(0);
        }}
      >
        {label}
        <ArrowDownUp size={11} />
      </button>
    </th>
  );
  return (
    <section className="panel mt-6">
      <div className="section-heading">
        <div>
          <h2>Thống kê theo sự kiện</h2>
          <p className="muted text-[10px] mt-2">
            Số liệu phát sinh trong khoảng đã chọn · tỉ lệ check-in tính trên
            toàn bộ vé của sự kiện · bấm tên sự kiện để lọc biểu đồ
          </p>
        </div>
        <Button
          variant="secondary"
          size="sm"
          disabled={!rows.length}
          onClick={() =>
            downloadCsv(
              [
                [
                  "Sự kiện",
                  "Trạng thái",
                  "Ngày diễn ra",
                  "Doanh thu gộp",
                  "Hoàn tiền",
                  "Doanh thu thuần",
                  "Vé bán",
                  "Vé hoàn",
                  "Sức chứa",
                  "Khán giả check-in",
                ],
                ...sorted.map((r) => [
                  r.title,
                  r.status,
                  r.startTime.slice(0, 10),
                  String(r.grossRevenue),
                  String(r.refundedAmount),
                  String(r.netRevenue),
                  String(r.ticketsSold),
                  String(r.ticketsRefunded),
                  String(r.capacity),
                  String(r.attendees),
                ]),
              ],
              "eventhub-thong-ke-su-kien.csv",
            )
          }
        >
          <Download size={14} />
          CSV
        </Button>
      </div>
      {sorted.length ? (
        <>
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>SỰ KIỆN</th>
                  <th>TRẠNG THÁI</th>
                  {head("netRevenue", "DOANH THU THUẦN")}
                  <th>HOÀN TIỀN</th>
                  {head("ticketsSold", "VÉ BÁN / SỨC CHỨA")}
                  {head("attendees", "KHÁN GIẢ")}
                  <th title="Khán giả check-in / vé hợp lệ của toàn sự kiện">
                    TỈ LỆ CHECK-IN
                  </th>
                </tr>
              </thead>
              <tbody>
                {sorted.slice(page * 8, (page + 1) * 8).map((r) => {
                  const valid = r.validTicketsLifetime;
                  const held = r.startTime.slice(0, 10) <= now;
                  return (
                    <tr
                      key={r.eventId}
                      className={cn(selected === r.eventId && "row-selected")}
                    >
                      <td>
                        <button
                          type="button"
                          className="row-link truncate-cell"
                          title={r.title}
                          aria-pressed={selected === r.eventId}
                          onClick={() => onSelect(r.eventId)}
                        >
                          {r.title}
                        </button>
                        <span className="sub">
                          {dateLabel(r.startTime, true)}
                        </span>
                      </td>
                      <td>
                        <Status value={r.status} />
                      </td>
                      <td>
                        <strong>{money(r.netRevenue)}</strong>
                        <span className="sub">Gộp {money(r.grossRevenue)}</span>
                      </td>
                      <td>{money(r.refundedAmount)}</td>
                      <td>
                        <strong>
                          {count(r.ticketsSold)} / {count(r.capacity)}
                        </strong>
                        <span className="fill-track" aria-hidden>
                          <span
                            style={{
                              width: `${Math.min(100, (valid / Math.max(1, r.capacity)) * 100)}%`,
                            }}
                          />
                        </span>
                      </td>
                      <td>{held ? count(r.attendees) : "—"}</td>
                      <td>
                        {held ? (
                          valid ? (
                            pct(r.attendeesLifetime / valid)
                          ) : (
                            "—"
                          )
                        ) : (
                          <span className="muted">Chưa diễn ra</span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <Pagination
            page={page}
            total={sorted.length}
            pageSize={8}
            onChange={setPage}
          />
        </>
      ) : (
        <Empty
          title="Không có sự kiện phát sinh giao dịch"
          description="Thử mở rộng khoảng thời gian để xem thêm số liệu."
        />
      )}
    </section>
  );
}

function PeriodTable({
  points,
  granularity,
}: {
  points: AnalyticsSeriesPoint[];
  granularity: Granularity;
}) {
  const [requestedPage, setPage] = useState(0);
  const rows = [...points].reverse();
  const page = Math.min(
    requestedPage,
    Math.max(0, Math.ceil(rows.length / 10) - 1),
  );
  return (
    <section className="panel mt-6">
      <div className="section-heading">
        <div>
          <h2>Chi tiết theo {granularity === "MONTH" ? "tháng" : "ngày"}</h2>
          <p className="muted text-[10px] mt-2">Mới nhất ở trên cùng</p>
        </div>
      </div>
      <div className="table-scroll">
        <table>
          <thead>
            <tr>
              <th>{granularity === "MONTH" ? "THÁNG" : "NGÀY"}</th>
              <th>DOANH THU GỘP</th>
              <th>HOÀN TIỀN</th>
              <th>DOANH THU THUẦN</th>
              <th>VÉ BÁN</th>
              <th>VÉ HOÀN</th>
              <th>KHÁN GIẢ</th>
            </tr>
          </thead>
          <tbody>
            {rows.slice(page * 10, (page + 1) * 10).map((p) => (
              <tr key={p.period}>
                <td>{periodLabel(p.period, granularity, true)}</td>
                <td>{money(p.grossRevenue)}</td>
                <td>{money(p.refundedAmount)}</td>
                <td>
                  <strong>{money(p.netRevenue)}</strong>
                </td>
                <td>{count(p.ticketsSold)}</td>
                <td>{count(p.ticketsRefunded)}</td>
                <td>{count(p.attendees)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Pagination
        page={page}
        total={rows.length}
        pageSize={10}
        onChange={setPage}
      />
    </section>
  );
}
