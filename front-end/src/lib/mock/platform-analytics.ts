// Simulates the platform-wide Analytics Service until the real endpoints exist.
// Source data has the same grain as `sales_metrics` (event × ticket type × VN day),
// plus a check-in count per day, so the aggregation below is what the backend
// would run in SQL (GROUP BY period / event_id).
import type { DemoState, EventStatus, TierName } from "@/types";
import type {
  AnalyticsMetrics,
  AnalyticsOverviewResponse,
  EventAnalyticsRow,
  Granularity,
  TicketTypeMetrics,
} from "@/types/analytics";

export interface MetricRow {
  eventId: string;
  tier: TierName;
  day: string;
  gross: number;
  refund: number;
  sold: number;
  refundedTickets: number;
  attendees: number;
}
export interface CatalogEvent {
  id: string;
  title: string;
  status: EventStatus;
  startTime: string;
  capacity: number;
}

export type AnalyticsState = Pick<
  DemoState,
  "events" | "orders" | "refunds" | "tickets"
>;
const TIERS: TierName[] = ["STANDARD", "PREMIUM", "VIP"];
const TZ = "Asia/Ho_Chi_Minh";

export const dayKey = (value: string | number | Date) =>
  new Date(value).toLocaleDateString("en-CA", { timeZone: TZ });
export function addDays(day: string, n: number) {
  const d = new Date(`${day}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
}
const daysBetween = (a: string, b: string) =>
  Math.round(
    (Date.parse(`${b}T00:00:00Z`) - Date.parse(`${a}T00:00:00Z`)) / 86400000,
  );
export const periodOf = (day: string, g: Granularity) =>
  g === "MONTH" ? day.slice(0, 7) : day;

function rng(seedText: string) {
  let seed = 0;
  for (const ch of seedText) seed = (seed * 31 + ch.charCodeAt(0)) | 0;
  return () => {
    seed = (seed + 0x6d2b79f5) | 0;
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

interface SimEvent extends CatalogEvent {
  prices: Record<TierName, number>;
  quotas: Record<TierName, number>;
  salesDays: number;
  demand: number;
}

// Past editions so the 12-month view has history and real check-in numbers.
const PAST = [
  ["past-saigon", "Sài Gòn Mùa Gió", 330, 1, 0.95],
  ["past-jazz", "Jazz bên sông", 285, 0.8, 0.7],
  ["past-tech", "Future Builders Summit", 240, 0.6, 0.8],
  ["past-summer", "Summer Wave Festival", 175, 1.3, 1],
  ["past-lights", "Lights & Lines", 130, 0.5, 0.6],
  ["past-rock", "Rock Không Ngủ", 88, 1.1, 0.9],
  ["past-poetry", "Đêm thơ & nhạc", 52, 0.4, 0.55],
  ["past-autumn", "Autumn Unplugged", 19, 0.9, 0.85],
] as const;

function simEvents(state: AnalyticsState, today: string): SimEvent[] {
  const past: SimEvent[] = PAST.map(([id, title, ago, mult, demand]) => {
    const quotas = { STANDARD: 800, PREMIUM: 300, VIP: 60 };
    return {
      id,
      title,
      status: "CLOSED",
      startTime: `${addDays(today, -ago)}T12:00:00.000Z`,
      capacity: 1160,
      prices: {
        STANDARD: 450000 * mult,
        PREMIUM: 850000 * mult,
        VIP: 1500000 * mult,
      },
      quotas,
      salesDays: 50,
      demand,
    };
  });
  const live: SimEvent[] = state.events
    .filter((e) => e.status === "PUBLISHED" || e.status === "CLOSED")
    .map((e) => {
      const price = (t: TierName) =>
        e.ticketTypes.find((x) => x.name === t)?.price ?? 0;
      const quota = (t: TierName) =>
        e.ticketTypes.find((x) => x.name === t)?.quota ?? 0;
      return {
        id: e.id,
        title: e.title,
        status: e.status,
        startTime: e.startTime,
        capacity: e.ticketTypes.reduce((s, t) => s + t.quota, 0),
        prices: {
          STANDARD: price("STANDARD"),
          PREMIUM: price("PREMIUM"),
          VIP: price("VIP"),
        },
        quotas: {
          STANDARD: quota("STANDARD"),
          PREMIUM: quota("PREMIUM"),
          VIP: quota("VIP"),
        },
        salesDays: 40,
        demand: 0.75,
      };
    });
  return [...past, ...live];
}

function simulate(ev: SimEvent, today: string): MetricRow[] {
  const rand = rng(ev.id);
  const start = dayKey(ev.startTime);
  const salesFrom = addDays(start, -ev.salesDays);
  const lastSale = daysBetween(today, start) > 0 ? today : addDays(start, -1);
  const rows: MetricRow[] = [];
  const net: Record<TierName, number> = { STANDARD: 0, PREMIUM: 0, VIP: 0 };
  for (const tier of TIERS) {
    let left = Math.round(ev.quotas[tier] * ev.demand);
    for (let i = 0; i < ev.salesDays; i++) {
      const day = addDays(salesFrom, i);
      if (day > lastSale) break;
      const t = i / ev.salesDays;
      const ramp = (0.4 + 2.2 * t * t) / 1.13;
      const expected =
        ((ev.quotas[tier] * ev.demand) / ev.salesDays) *
        ramp *
        (0.6 + rand() * 0.8);
      const sold = Math.min(left, Math.round(expected));
      left -= sold;
      const refunded =
        rand() < 0.06 && net[tier] > 2 ? 1 + Math.floor(rand() * 2) : 0;
      net[tier] += sold - refunded;
      if (!sold && !refunded) continue;
      rows.push({
        eventId: ev.id,
        tier,
        day,
        sold,
        gross: sold * ev.prices[tier],
        refundedTickets: refunded,
        refund: refunded * ev.prices[tier],
        attendees: 0,
      });
    }
    if (start <= today && net[tier] > 0)
      rows.push({
        eventId: ev.id,
        tier,
        day: start,
        sold: 0,
        gross: 0,
        refund: 0,
        refundedTickets: 0,
        attendees: Math.round(net[tier] * (0.82 + rand() * 0.13)),
      });
  }
  return rows;
}

let cache: { key: string; rows: MetricRow[]; catalog: CatalogEvent[] } | null =
  null;

/** Synthetic history + the demo store's live orders, refunds and check-ins. */
export function metricSource(state: AnalyticsState, today: string) {
  const key = today + state.events.map((e) => e.id + e.status).join();
  if (cache?.key !== key) {
    const sims = simEvents(state, today);
    cache = {
      key,
      rows: sims.flatMap((e) => simulate(e, today)),
      catalog: sims.map(({ id, title, status, startTime, capacity }) => ({
        id,
        title,
        status,
        startTime,
        capacity,
      })),
    };
  }
  const live: MetricRow[] = [];
  const blank = (eventId: string, tier: TierName, day: string): MetricRow => ({
    eventId,
    tier,
    day,
    gross: 0,
    refund: 0,
    sold: 0,
    refundedTickets: 0,
    attendees: 0,
  });
  for (const o of state.orders) {
    if (!["PAID", "REFUNDED"].includes(o.status)) continue;
    const day = dayKey(o.paidAt ?? o.createdAt);
    for (const l of o.lines)
      live.push({
        ...blank(o.eventId, l.tier, day),
        sold: l.quantity,
        gross: l.quantity * l.unitPrice,
      });
  }
  for (const r of state.refunds) {
    if (r.status !== "REFUNDED") continue;
    const o = state.orders.find((x) => x.id === r.orderId);
    if (!o) continue;
    const day = dayKey(r.reviewedAt ?? r.createdAt);
    let remainder = r.amount;
    o.lines.forEach((l, i) => {
      const amount =
        i === o.lines.length - 1
          ? remainder
          : Math.floor(
              o.total ? (r.amount * l.quantity * l.unitPrice) / o.total : 0,
            );
      remainder -= amount;
      live.push({
        ...blank(o.eventId, l.tier, day),
        refund: amount,
        refundedTickets: l.quantity,
      });
    });
  }
  for (const t of state.tickets)
    if (t.scannedAt && ["CHECKED_IN", "CHECKED_OUT"].includes(t.status))
      live.push({
        ...blank(t.eventId, t.tier, dayKey(t.scannedAt)),
        attendees: 1,
      });
  return { rows: [...cache.rows, ...live], catalog: cache.catalog };
}

const zero = (): AnalyticsMetrics => ({
  grossRevenue: 0,
  refundedAmount: 0,
  netRevenue: 0,
  ticketsSold: 0,
  ticketsRefunded: 0,
  attendees: 0,
});
function add(m: AnalyticsMetrics, r: MetricRow) {
  m.grossRevenue += r.gross;
  m.refundedAmount += r.refund;
  m.netRevenue += r.gross - r.refund;
  m.ticketsSold += r.sold;
  m.ticketsRefunded += r.refundedTickets;
  m.attendees += r.attendees;
}

export interface AnalyticsQuery {
  from: string;
  to: string;
  granularity: Granularity;
  eventId: string | null;
}

/** What GET /api/v1/analytics/overview would return. */
export function overview(
  rows: MetricRow[],
  q: AnalyticsQuery,
): AnalyticsOverviewResponse {
  const scoped = q.eventId ? rows.filter((r) => r.eventId === q.eventId) : rows;
  const length = daysBetween(q.from, q.to) + 1;
  const prevFrom = addDays(q.from, -length);
  const totals = zero();
  const previousTotals = zero();
  const buckets = new Map<string, AnalyticsMetrics>();
  for (let d = q.from; d <= q.to; d = addDays(d, 1)) {
    const p = periodOf(d, q.granularity);
    if (!buckets.has(p)) buckets.set(p, zero());
  }
  const tiers = new Map<TierName, TicketTypeMetrics>(
    TIERS.map((t) => [t, { ticketTypeName: t, ticketsSold: 0, netRevenue: 0 }]),
  );
  for (const r of scoped) {
    if (r.day >= prevFrom && r.day < q.from) add(previousTotals, r);
    if (r.day < q.from || r.day > q.to) continue;
    add(totals, r);
    add(buckets.get(periodOf(r.day, q.granularity))!, r);
    const t = tiers.get(r.tier)!;
    t.ticketsSold += r.sold - r.refundedTickets;
    t.netRevenue += r.gross - r.refund;
  }
  return {
    from: q.from,
    to: q.to,
    granularity: q.granularity,
    totals,
    previousTotals,
    series: [...buckets].map(([period, m]) => ({ period, ...m })),
    byTicketType: [...tiers.values()],
  };
}

/** What GET /api/v1/analytics/events would return (unpaged; the table pages it). */
export function eventBreakdown(
  rows: MetricRow[],
  catalog: CatalogEvent[],
  q: Pick<AnalyticsQuery, "from" | "to">,
): EventAnalyticsRow[] {
  const byEvent = new Map<string, AnalyticsMetrics>();
  const lifetime = new Map<string, AnalyticsMetrics>();
  for (const r of rows) {
    if (!lifetime.has(r.eventId)) lifetime.set(r.eventId, zero());
    add(lifetime.get(r.eventId)!, r);
    if (r.day < q.from || r.day > q.to) continue;
    if (!byEvent.has(r.eventId)) byEvent.set(r.eventId, zero());
    add(byEvent.get(r.eventId)!, r);
  }
  return catalog
    .filter((e) => byEvent.has(e.id))
    .map((e) => ({
      eventId: e.id,
      title: e.title,
      status: e.status,
      startTime: e.startTime,
      capacity: e.capacity,
      validTicketsLifetime:
        lifetime.get(e.id)!.ticketsSold - lifetime.get(e.id)!.ticketsRefunded,
      attendeesLifetime: lifetime.get(e.id)!.attendees,
      ...byEvent.get(e.id)!,
    }));
}
