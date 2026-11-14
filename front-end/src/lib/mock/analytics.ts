import type { Order, Refund, TierName } from "@/types";
export interface SalesRow {
  day: string;
  tier: TierName;
  sold: number;
  refundedTickets: number;
  gross: number;
  refund: number;
  net: number;
}
const dayOf = (date: string) =>
  new Date(date).toLocaleDateString("en-CA", { timeZone: "Asia/Ho_Chi_Minh" });
// Sales stay on paidAt; refunds are recorded on reviewedAt, preserving history.
export function dailySales(
  orders: Order[],
  refunds: Refund[],
  minDate: number,
): SalesRow[] {
  const rows = new Map<string, SalesRow>();
  function row(day: string, tier: TierName) {
    const key = `${day}-${tier}`;
    if (!rows.has(key))
      rows.set(key, {
        day,
        tier,
        sold: 0,
        refundedTickets: 0,
        gross: 0,
        refund: 0,
        net: 0,
      });
    return rows.get(key)!;
  }
  for (const o of orders) {
    if (
      !["PAID", "REFUNDED"].includes(o.status) ||
      Date.parse(o.paidAt ?? o.createdAt) < minDate
    )
      continue;
    for (const l of o.lines) {
      const r = row(dayOf(o.paidAt ?? o.createdAt), l.tier);
      r.sold += l.quantity;
      r.gross += l.quantity * l.unitPrice;
    }
  }
  for (const refund of refunds) {
    if (
      refund.status !== "REFUNDED" ||
      Date.parse(refund.reviewedAt ?? refund.createdAt) < minDate
    )
      continue;
    const o = orders.find((o) => o.id === refund.orderId);
    if (!o) continue;
    let remainder = refund.amount;
    o.lines.forEach((l, i) => {
      const amount =
        i === o.lines.length - 1
          ? remainder
          : Math.floor(
              o.total
                ? (refund.amount * l.quantity * l.unitPrice) / o.total
                : 0,
            );
      remainder -= amount;
      const r = row(dayOf(refund.reviewedAt ?? refund.createdAt), l.tier);
      r.refund += amount;
      r.refundedTickets += l.quantity;
    });
  }
  return [...rows.values()]
    .map((r) => ({ ...r, net: r.gross - r.refund }))
    .sort((a, b) => b.day.localeCompare(a.day) || a.tier.localeCompare(b.tier));
}
