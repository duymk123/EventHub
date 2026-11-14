"use client";
import { useMemo } from "react";
import { useDemo } from "@/hooks/use-demo";
import { useNow } from "@/hooks/use-now";
import {
  addDays,
  dayKey,
  eventBreakdown,
  metricSource,
  overview,
} from "@/lib/mock/platform-analytics";
import type { Granularity } from "@/types/analytics";

export type RangePreset = "7d" | "30d" | "90d" | "12m" | "ytd";
export const rangePresets: { value: RangePreset; label: string }[] = [
  { value: "7d", label: "7 ngày gần nhất" },
  { value: "30d", label: "30 ngày gần nhất" },
  { value: "90d", label: "90 ngày gần nhất" },
  { value: "12m", label: "12 tháng gần nhất" },
  { value: "ytd", label: "Từ đầu năm" },
];
export const defaultGranularity = (p: RangePreset): Granularity =>
  p === "12m" || p === "ytd" ? "MONTH" : "DAY";

export function rangeOf(preset: RangePreset, today: string) {
  if (preset === "ytd")
    return { from: `${today.slice(0, 4)}-01-01`, to: today };
  if (preset === "12m") {
    const d = new Date(`${today.slice(0, 7)}-01T00:00:00Z`);
    d.setUTCMonth(d.getUTCMonth() - 11);
    return { from: d.toISOString().slice(0, 10), to: today };
  }
  const days = { "7d": 7, "30d": 30, "90d": 90 }[preset];
  return { from: addDays(today, 1 - days), to: today };
}

/**
 * Admin analytics data. Today this aggregates mock data on the client; with the
 * real backend it becomes two TanStack queries:
 *   GET /api/v1/analytics/overview?from&to&granularity&eventId
 *   GET /api/v1/analytics/events?from&to&sort&page&size
 */
export function usePlatformAnalytics(
  preset: RangePreset,
  granularity: Granularity,
  eventId: string | null,
) {
  const { events, orders, refunds, tickets, ready } = useDemo();
  const now = useNow();
  return useMemo(() => {
    if (!now || !ready) return null;
    const today = dayKey(now);
    const range = rangeOf(preset, today);
    const { rows, catalog } = metricSource(
      { events, orders, refunds, tickets },
      today,
    );
    return {
      today,
      range,
      catalog,
      overview: overview(rows, { ...range, granularity, eventId }),
      events: eventBreakdown(rows, catalog, range),
    };
  }, [
    now,
    ready,
    events,
    orders,
    refunds,
    tickets,
    preset,
    granularity,
    eventId,
  ]);
}
