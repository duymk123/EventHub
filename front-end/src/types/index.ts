export type Role = "CUSTOMER" | "ORGANIZER" | "ADMIN";
export type EventStatus =
  "DRAFT" | "SUBMITTED" | "APPROVED" | "REJECTED" | "PUBLISHED" | "CLOSED";
export type TierName = "VIP" | "PREMIUM" | "STANDARD";
export type TicketStatus =
  "ISSUED" | "CHECKED_IN" | "CHECKED_OUT" | "CANCELLED";
export type OrderStatus =
  "PENDING_PAYMENT" | "PAID" | "FAILED" | "EXPIRED" | "CANCELLED" | "REFUNDED";
export interface User {
  id: string;
  name: string;
  email: string;
  role: Role;
  status: "ACTIVE" | "LOCKED";
  emailVerified: boolean;
  createdAt?: string;
}
/** Trạng thái một ghế đã có người giữ hoặc mua; ghế không có trong map là còn trống. */
export type TakenSeatState = "HELD" | "SOLD";
/** Một hạng vé đồng thời là một zone ngồi (C12). */
export interface TicketType {
  id: string;
  name: TierName;
  price: number;
  /** Số hàng ghế organizer nhập cho zone của sự kiện này. */
  rowCount: number;
  /** Số ghế mỗi hàng; quota = rowCount × seatsPerRow. */
  seatsPerRow: number;
  quota: number;
  /** Số ghế còn trống, luôn bằng quota trừ số ghế trong `taken`. */
  available: number;
  /** Ghế đã giữ/đã bán theo mã ghế, ví dụ "B-07". */
  taken: Record<string, TakenSeatState>;
  active: boolean;
  /** Nhãn ghế (B-07) → UUID ghế ở Ticket Service; chỉ có khi đã tải sơ đồ ghế. */
  seatIds?: Record<string, string>;
  /** Số ghế đang được giữ và đã bán (từ Ticket Service). */
  held?: number;
  sold?: number;
}
export interface Event {
  id: string;
  title: string;
  subtitle: string;
  category: string;
  categoryId: string;
  startTime: string;
  endTime: string;
  image: string;
  organizerId: string;
  organizerName: string;
  status: EventStatus;
  description: string;
  ticketTypes: TicketType[];
  reviewReason?: string;
  createdAt?: string;
}
export interface Holder {
  name: string;
  email: string;
  tierId: string;
  /** Mã ghế dạng "<tierId>:<B-07>". */
  seatId: string;
  /** Nhãn ghế hiển thị, ví dụ "B-07". */
  seatLabel: string;
}
export interface OrderLine {
  tierId: string;
  tier: TierName;
  quantity: number;
  unitPrice: number;
}
export interface Order {
  id: string;
  eventId: string;
  customerId: string;
  lines: OrderLine[];
  holders: Holder[];
  total: number;
  status: OrderStatus;
  createdAt: string;
  expiresAt: string;
  paidAt?: string;
}
export interface Ticket {
  id: string;
  orderId: string;
  eventId: string;
  holderName: string;
  tier: TierName;
  seatLabel?: string;
  status: TicketStatus;
  issuedAt: string;
}
export interface Refund {
  id: string;
  orderId: string;
  reason: string;
  amount: number;
  status: "REQUESTED" | "APPROVED" | "PROCESSING" | "REFUNDED" | "FAILED" | "REJECTED";
  createdAt: string;
  reviewedAt?: string;
  reviewReason?: string;
  refundedAt?: string;
  failureReason?: string;
}
export interface Notice {
  id: string;
  title: string;
  content: string;
  read: boolean;
  href: string;
  createdAt: string;
}
export interface Template {
  id: string;
  code: string;
  channel: "EMAIL" | "IN_APP";
  subject: string;
  body: string;
}
