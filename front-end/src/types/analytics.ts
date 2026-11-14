// Contract for the platform-wide analytics endpoints the admin dashboard needs.
// These mirror the proposed Analytics Service API (see docs in the PR / README):
//   GET /api/v1/analytics/overview?from&to&granularity&eventId
//   GET /api/v1/analytics/events?from&to&sort&page&size
// Money values are VND; dates are yyyy-MM-dd (DAY) or yyyy-MM (MONTH) in Asia/Ho_Chi_Minh.
import type { EventStatus, TierName } from "./index";

export type Granularity = "DAY" | "MONTH";

export interface AnalyticsMetrics {
  grossRevenue: number;
  refundedAmount: number;
  netRevenue: number;
  ticketsSold: number;
  ticketsRefunded: number;
  /** Unique tickets checked in (each ticket counts once). */
  attendees: number;
}

export interface AnalyticsSeriesPoint extends AnalyticsMetrics {
  /** yyyy-MM-dd for DAY, yyyy-MM for MONTH. */
  period: string;
}

export interface TicketTypeMetrics {
  ticketTypeName: TierName;
  ticketsSold: number;
  netRevenue: number;
}

export interface AnalyticsOverviewResponse {
  from: string;
  to: string;
  granularity: Granularity;
  totals: AnalyticsMetrics;
  /** Same-length window right before `from`, for the delta on KPI cards. */
  previousTotals: AnalyticsMetrics;
  /** Zero-filled: every period between from..to is present. */
  series: AnalyticsSeriesPoint[];
  byTicketType: TicketTypeMetrics[];
}

export interface EventAnalyticsRow extends AnalyticsMetrics {
  eventId: string;
  title: string;
  status: EventStatus;
  startTime: string;
  /** Sum of ticket-type quotas. */
  capacity: number;
  /** All-time sold minus refunded (not limited to from..to) - check-in rate denominator. */
  validTicketsLifetime: number;
  /** All-time check-ins, independent of the filter window. */
  attendeesLifetime: number;
}

export type EventAnalyticsSort = "netRevenue" | "ticketsSold" | "attendees";
