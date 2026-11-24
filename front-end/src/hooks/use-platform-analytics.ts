"use client";
// Số liệu cho trang Phân tích và Tổng quan: GET /analytics/overview + GET /analytics/events (C10).
// ADMIN thấy mọi sự kiện, ORGANIZER chỉ sự kiện của mình (Analytics service tự lọc theo token).
import { useAnalyticsEvents, useAnalyticsOverview } from "@/hooks/use-api";
import { useNow } from "@/hooks/use-now";
import type { AnalyticsOverviewDto } from "@/types/api";
import type { EventAnalyticsRow, Granularity } from "@/types/analytics";

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

const TZ = "Asia/Ho_Chi_Minh";
/** Ngày theo giờ Việt Nam, dạng yyyy-MM-dd (Analytics gom số liệu theo ngày VN). */
export const dayKey = (value: string | number | Date) =>
  new Date(value).toLocaleDateString("en-CA", { timeZone: TZ });
export function addDays(day: string, n: number) {
  const d = new Date(`${day}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
}

export function rangeOf(preset: RangePreset, today: string) {
  if (preset === "ytd") return { from: `${today.slice(0, 4)}-01-01`, to: today };
  if (preset === "12m") {
    const d = new Date(`${today.slice(0, 7)}-01T00:00:00Z`);
    d.setUTCMonth(d.getUTCMonth() - 11);
    return { from: d.toISOString().slice(0, 10), to: today };
  }
  const days = { "7d": 7, "30d": 30, "90d": 90 }[preset];
  return { from: addDays(today, 1 - days), to: today };
}

export interface PlatformAnalytics {
  today: string;
  range: { from: string; to: string };
  /** Sự kiện có trong bảng thống kê, dùng cho ô chọn sự kiện. */
  catalog: { id: string; title: string }[];
  overview: AnalyticsOverviewDto;
  events: EventAnalyticsRow[];
}

export function usePlatformAnalytics(
  preset: RangePreset,
  granularity: Granularity,
  eventId: string | null,
) {
  const now = useNow();
  const today = now ? dayKey(now) : null;
  const range = today ? rangeOf(preset, today) : null;
  const overview = useAnalyticsOverview(
    range ? { ...range, granularity, eventId: eventId ?? undefined } : null,
  );
  // Bảng theo sự kiện sắp xếp phía client trong trang, lấy một lần tối đa 100 sự kiện.
  const events = useAnalyticsEvents(
    range ? { ...range, page: 0, size: 100, sort: "netRevenue" } : null,
  );
  const rows: EventAnalyticsRow[] | undefined = events.data?.content.map((r) => ({
    ...r,
    capacity: r.capacity ?? 0,
  }));
  const data: PlatformAnalytics | null =
    today && range && overview.data && rows
      ? {
          today,
          range,
          catalog: rows.map((r) => ({ id: r.eventId, title: r.title })),
          overview: overview.data,
          events: rows,
        }
      : null;
  return {
    data,
    isLoading: !data && !overview.isError && !events.isError,
    isError: overview.isError || events.isError,
    refetch: () => {
      void overview.refetch();
      void events.refetch();
    },
  };
}
