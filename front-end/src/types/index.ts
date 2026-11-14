export type Role = "CUSTOMER" | "ORGANIZER" | "ADMIN";
export type EventStatus =
  "DRAFT" | "SUBMITTED" | "APPROVED" | "REJECTED" | "PUBLISHED" | "CLOSED";
export type TierName = "VIP" | "PREMIUM" | "STANDARD";
export type TicketStatus =
  "ISSUED" | "CHECKED_IN" | "CHECKED_OUT" | "CANCELLED";
export type OrderStatus =
  "PENDING_PAYMENT" | "PAID" | "FAILED" | "EXPIRED" | "REFUNDED";
export interface User {
  id: string;
  name: string;
  email: string;
  role: Role;
  status: "ACTIVE" | "LOCKED";
}
export interface TicketType {
  id: string;
  name: TierName;
  price: number;
  quota: number;
  available: number;
  active: boolean;
}
export interface Event {
  id: string;
  title: string;
  subtitle: string;
  category: string;
  startTime: string;
  endTime: string;
  image: string;
  organizerId: string;
  organizerName: string;
  status: EventStatus;
  description: string;
  ticketTypes: TicketType[];
  reviewReason?: string;
}
export interface Holder {
  name: string;
  email: string;
  tierId: string;
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
  customerEmail: string;
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
  customerId: string;
  holderName: string;
  holderEmail: string;
  tier: TierName;
  status: TicketStatus;
  scannedAt?: string;
}
export interface Refund {
  id: string;
  orderId: string;
  reason: string;
  amount: number;
  status: "REQUESTED" | "REFUNDED" | "REJECTED";
  createdAt: string;
  reviewedAt?: string;
  reviewReason?: string;
}
export interface Notice {
  id: string;
  userId: string;
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
export interface DemoState {
  version: 1;
  userId: string | null;
  users: User[];
  events: Event[];
  orders: Order[];
  tickets: Ticket[];
  refunds: Refund[];
  notices: Notice[];
  favorites: string[];
  categories: string[];
  templates: Template[];
}
export type DataMode = "ready" | "loading" | "empty" | "error";
