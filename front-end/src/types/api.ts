// Kiểu dữ liệu đúng như response/request của backend (docs/api/*-openapi.yaml). UUID và thời gian là string,
// tiền (BigDecimal) là number. Giao diện không dùng trực tiếp các kiểu này mà qua lib/api/adapters.ts.
import type { EventStatus, OrderStatus, Role, TicketStatus, TierName } from ".";

/** Page của Spring Data (serialization-mode via-dto): Event, Order, Notification, Analytics. */
export interface SpringPage<T> {
  content: T[];
  page: { number: number; size: number; totalElements: number; totalPages: number };
}

/** Page riêng của Auth Service. */
export interface AuthPage<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// ---------------------------------------------------------------- Auth
export interface AccessTokenDto {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}
export interface UserDto {
  id: string;
  email: string;
  fullName: string | null;
  role: Role;
  status: "ACTIVE" | "LOCKED";
  emailVerified: boolean;
  createdAt: string | null;
}
export interface UserDetailDto extends UserDto {
  username: string;
  requiredActions: string[];
}
export interface ProfileDto {
  keycloakSub?: string;
  fullName: string | null;
  phoneNumber: string | null;
}
export interface TemporaryPasswordDto {
  userId: string;
  temporaryPassword: string | null;
}

// ---------------------------------------------------------------- Event
export interface CategoryDto {
  id: string;
  name: string;
  description?: string | null;
}
export interface EventSummaryDto {
  id: string;
  title: string;
  category: CategoryDto | null;
  startTime: string;
  endTime: string;
}
export interface EventDto extends EventSummaryDto {
  organizerId: string;
  description: string | null;
  status: EventStatus;
  rejectReason: string | null;
  createdAt: string;
  updatedAt: string;
}
export interface EventStatusDto {
  id: string;
  status: EventStatus;
  rejectReason: string | null;
  updatedAt: string;
}
export interface EventRequestDto {
  title: string;
  description: string;
  categoryId: string;
  startTime: string;
  endTime: string;
}

// ---------------------------------------------------------------- Ticket
export interface TicketTypeDto {
  id: string;
  eventId: string;
  name: TierName;
  price: number;
  rowCount: number;
  seatsPerRow: number;
  totalQuota: number;
  availableSeats: number;
  heldSeats: number;
  soldSeats: number;
  status: "ACTIVE" | "INACTIVE";
}
export interface SeatDto {
  id: string;
  seatNumber: number;
  label: string;
  status: "AVAILABLE" | "HELD" | "SOLD";
}
export interface SeatMapDto {
  ticketTypeId: string;
  name: TierName;
  price: number;
  rows: { rowIndex: number; rowLabel: string; seats: SeatDto[] }[];
  generatedAt: string;
}
export interface TicketDto {
  id: string;
  orderId: string;
  eventId: string;
  ticketTypeId: string;
  ticketTypeName: TierName;
  seatId: string;
  seatLabel: string;
  holderName: string | null;
  status: TicketStatus;
  issuedAt: string;
}
export interface QrDto {
  ticketId: string;
  qrCodeValue: string;
}
export interface GateScanDto {
  ticketId: string;
  status: TicketStatus;
  scanType: "CHECK_IN" | "CHECK_OUT";
  scannedAt: string;
}

// ---------------------------------------------------------------- Order
export interface OrderHolderDto {
  id: string;
  seatId: string;
  seatLabel: string;
  holderName: string;
  holderEmail: string | null;
  holderPhone: string | null;
}
export interface OrderItemDto {
  ticketTypeId: string;
  ticketTypeName: TierName;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
  holders: OrderHolderDto[];
}
export interface OrderDto {
  id: string;
  customerId: string;
  eventId: string;
  reservationId: string;
  status: OrderStatus;
  items: OrderItemDto[];
  subtotal: number;
  totalAmount: number;
  currency: string;
  expiresAt: string;
  paidAt: string | null;
  createdAt: string;
}
export interface CreateOrderDto {
  eventId: string;
  items: {
    ticketTypeId: string;
    quantity: number;
    holders: { seatId: string; holderName: string; holderEmail?: string; holderPhone?: string }[];
  }[];
}
export interface PaymentDto {
  paymentId: string;
  orderId: string;
  status: "CREATED" | "PENDING" | "SUCCEEDED" | "FAILED";
  provider: string;
  providerTxnRef: string;
  paymentUrl: string;
  amount: number;
  expiresAt: string;
}
export type RefundStatusDto = "REQUESTED" | "APPROVED" | "REJECTED" | "PROCESSING" | "REFUNDED" | "FAILED";
export interface RefundDto {
  id: string;
  orderId: string;
  status: RefundStatusDto;
  amount: number;
  reason: string;
  requestedBy: string;
  approvedBy: string | null;
  requestedAt: string;
  reviewedAt: string | null;
  refundedAt: string | null;
  reviewReason: string | null;
  failureReason: string | null;
}
export interface ReconciliationDto {
  paymentId: string;
  orderId: string;
  orderStatus: OrderStatus;
  orderPaymentId: string | null;
  customerEmail: string;
  amount: number;
  providerTxnRef: string;
  providerTransactionId: string | null;
  providerPayDate: string | null;
  updatedAt: string;
  reconciledAt: string | null;
  reconciliationNote: string | null;
}

// ---------------------------------------------------------------- Notification
export interface NotificationDto {
  id: string;
  channel: "IN_APP" | "EMAIL";
  templateCode: string;
  title: string;
  content: string;
  status: string;
  readAt: string | null;
  createdAt: string;
}
export interface TemplateDto {
  id: string;
  code: string;
  channel: "IN_APP" | "EMAIL";
  subject: string;
  bodyTemplate: string;
  updatedAt: string;
}

// ---------------------------------------------------------------- Analytics
export interface AnalyticsMetricsDto {
  grossRevenue: number;
  refundedAmount: number;
  netRevenue: number;
  ticketsSold: number;
  ticketsRefunded: number;
  attendees: number;
}
export interface AnalyticsSeriesPointDto extends AnalyticsMetricsDto {
  period: string;
}
export interface AnalyticsOverviewDto {
  from: string;
  to: string;
  granularity: "DAY" | "MONTH";
  eventId: string | null;
  totals: AnalyticsMetricsDto;
  previousFrom: string;
  previousTo: string;
  previousTotals: AnalyticsMetricsDto;
  series: AnalyticsSeriesPointDto[];
  byTicketType: { ticketTypeName: TierName; ticketsSold: number; netRevenue: number }[];
}
export interface EventAnalyticsRowDto {
  eventId: string;
  title: string;
  status: EventStatus;
  startTime: string;
  capacity: number | null;
  grossRevenue: number;
  refundedAmount: number;
  netRevenue: number;
  ticketsSold: number;
  ticketsRefunded: number;
  attendees: number;
  validTicketsLifetime: number;
  attendeesLifetime: number;
}
export interface SalesReportDto {
  eventId: string;
  from: string | null;
  to: string | null;
  totalRevenue: number;
  totalRefunded: number;
  netRevenue: number;
  totalTicketsSold: number;
  totalTicketsRefunded: number;
  daily: { date: string; revenue: number; refundedAmount: number; netRevenue: number; ticketsSold: number; ticketsRefunded: number }[];
  byTicketType: { ticketTypeId: string; ticketTypeName: string; revenue: number; refundedAmount: number; netRevenue: number; ticketsSold: number; ticketsRefunded: number }[];
}
export interface AttendanceDto {
  eventId: string;
  currentAttendance: number;
  updatedAt: string | null;
  totalCheckedIn: number;
}
