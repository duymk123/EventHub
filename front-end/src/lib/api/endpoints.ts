// Hàm gọi API theo từng service, đường dẫn khớp docs/api/*-openapi.yaml (không có /internal/**).
import { api } from "./client";
import type {
  AnalyticsOverviewDto, AttendanceDto, AuthPage, CategoryDto, CreateOrderDto, EventAnalyticsRowDto, EventDto,
  EventRequestDto, EventStatusDto, EventSummaryDto, GateScanDto, NotificationDto, OrderDto, PaymentDto, ProfileDto,
  QrDto, ReconciliationDto, RefundDto, RefundStatusDto, SalesReportDto, SeatMapDto, SpringPage, TemplateDto,
  TemporaryPasswordDto, TicketDto, TicketTypeDto, UserDetailDto, UserDto,
} from "@/types/api";
import type { EventStatus, Role, TierName } from "@/types";

// ---------------------------------------------------------------- Auth
export const authApi = {
  register: (body: { email: string; password: string; fullName: string; phoneNumber?: string }) =>
    api<UserDto>("/auth/register", { method: "POST", body, auth: false }),
  me: () => api<UserDto>("/auth/me"),
  changePassword: (body: { currentPassword: string; newPassword: string }) =>
    api<void>("/auth/change-password", { method: "POST", body }),
  forgotPassword: (email: string) => api<void>("/auth/forgot-password", { method: "POST", body: { email }, auth: false }),
  resendVerification: () => api<void>("/auth/verify-email/resend", { method: "POST" }),
  profile: () => api<ProfileDto>("/me/profile"),
  updateProfile: (body: { fullName?: string; phoneNumber?: string }) =>
    api<ProfileDto>("/me/profile", { method: "PATCH", body }),
  /** Tài khoản không đi qua /auth/register (nạp sẵn từ realm) chưa có hồ sơ: khởi tạo bằng PUT. */
  initProfile: (body: { fullName: string; phoneNumber?: string }) =>
    api<ProfileDto>("/me/profile", { method: "PUT", body }),
};

export const adminUserApi = {
  list: (query: { page: number; size: number; search?: string; role?: Role; status?: "ACTIVE" | "LOCKED" }) =>
    api<AuthPage<UserDetailDto>>("/admin/users", { query }),
  lock: (id: string) => api<void>(`/admin/users/${id}/lock`, { method: "PATCH" }),
  unlock: (id: string) => api<void>(`/admin/users/${id}/unlock`, { method: "PATCH" }),
  assignRole: (id: string, role: Role) => api<void>(`/admin/users/${id}/role`, { method: "PATCH", body: { role } }),
  resetPassword: (id: string, action: "SEND_EMAIL" | "SET_TEMPORARY_PASSWORD") =>
    api<TemporaryPasswordDto>(`/admin/users/${id}/reset-password`, { method: "POST", body: { action } }),
};

// ---------------------------------------------------------------- Event
export const eventApi = {
  search: (query: { keyword?: string; category?: string; startDate?: string; page?: number; size?: number; sort?: string }) =>
    api<SpringPage<EventSummaryDto>>("/events", { query, auth: false }),
  get: (id: string) => api<EventDto>(`/events/${id}`, { auth: false }),
  categories: () => api<CategoryDto[]>("/categories", { auth: false }),
  mine: (query: { status?: EventStatus; page?: number; size?: number }) =>
    api<SpringPage<EventDto>>("/organizer/events", { query }),
  adminList: (query: { status?: EventStatus; page?: number; size?: number }) =>
    api<SpringPage<EventDto>>("/admin/events", { query }),
  create: (body: EventRequestDto) => api<EventDto>("/events", { method: "POST", body }),
  update: (id: string, body: EventRequestDto) => api<EventDto>(`/events/${id}`, { method: "PUT", body }),
  submit: (id: string) => api<EventStatusDto>(`/events/${id}/submit`, { method: "POST" }),
  publish: (id: string) => api<EventStatusDto>(`/events/${id}/publish`, { method: "POST" }),
  close: (id: string) => api<EventStatusDto>(`/events/${id}/close`, { method: "POST" }),
  review: (id: string, decision: "APPROVE" | "REJECT", reason?: string) =>
    api<EventStatusDto>(`/admin/events/${id}/review`, { method: "PATCH", body: { decision, reason } }),
  createCategory: (name: string, description?: string) =>
    api<CategoryDto>("/categories", { method: "POST", body: { name, description } }),
  updateCategory: (id: string, name: string, description?: string) =>
    api<CategoryDto>(`/categories/${id}`, { method: "PUT", body: { name, description } }),
  deleteCategory: (id: string) => api<void>(`/categories/${id}`, { method: "DELETE" }),
};

// ---------------------------------------------------------------- Ticket
export const ticketApi = {
  // Gửi kèm token nếu đã đăng nhập: chủ sự kiện/ADMIN đọc được hạng vé của sự kiện chưa công bố.
  typesOf: (eventId: string) => api<TicketTypeDto[]>(`/events/${eventId}/ticket-types`),
  seatMap: (typeId: string) => api<SeatMapDto>(`/ticket-types/${typeId}/seats`, { auth: false }),
  createTypes: (eventId: string, ticketTypes: { name: TierName; price: number; rowCount: number; seatsPerRow: number }[]) =>
    api<TicketTypeDto[]>("/ticket-types", { method: "POST", body: { eventId, ticketTypes } }),
  updateType: (id: string, body: { price?: number; rowCount?: number; seatsPerRow?: number; status?: "ACTIVE" | "INACTIVE" }) =>
    api<TicketTypeDto>(`/ticket-types/${id}`, { method: "PUT", body }),
  mine: () => api<TicketDto[]>("/tickets/me"),
  qr: (id: string) => api<QrDto>(`/tickets/${id}/qr`),
  scan: (kind: "check-in" | "check-out", body: { qrCodeValue: string; eventId: string; gateId: string }) =>
    api<GateScanDto>(`/gate/${kind}`, { method: "POST", body }),
};

// ---------------------------------------------------------------- Order
export const orderApi = {
  create: (body: CreateOrderDto, idempotencyKey: string) =>
    api<OrderDto>("/orders", { method: "POST", body, headers: { "Idempotency-Key": idempotencyKey } }),
  mine: (query: { page?: number; size?: number }) => api<SpringPage<OrderDto>>("/orders/me", { query }),
  get: (id: string) => api<OrderDto>(`/orders/${id}`),
  cancel: (id: string) => api<OrderDto>(`/orders/${id}/cancel`, { method: "POST" }),
  pay: (id: string, idempotencyKey: string) =>
    api<PaymentDto>(`/orders/${id}/payment`, { method: "POST", headers: { "Idempotency-Key": idempotencyKey } }),
  /** returnParams: các tham số vnp_* trên returnUrl, chuyển nguyên cho server tự kiểm chữ ký. */
  verify: (id: string, returnParams: Record<string, string> = {}) =>
    api<OrderDto>(`/orders/${id}/payment/verify`, { method: "POST", body: returnParams }),
  requestRefund: (id: string, reason: string) =>
    api<RefundDto>(`/orders/${id}/refunds`, { method: "POST", body: { reason } }),
  refundsOf: (id: string) => api<RefundDto[]>(`/orders/${id}/refunds`),
  adminRefunds: (status?: RefundStatusDto) => api<RefundDto[]>("/admin/refunds", { query: { status } }),
  approveRefund: (id: string) => api<RefundDto>(`/admin/refunds/${id}/approve`, { method: "POST" }),
  rejectRefund: (id: string, reason: string) =>
    api<RefundDto>(`/admin/refunds/${id}/reject`, { method: "POST", body: { reason } }),
  retryRefund: (id: string) => api<RefundDto>(`/admin/refunds/${id}/retry`, { method: "POST" }),
  reconciliation: () => api<ReconciliationDto[]>("/admin/payments/reconciliation"),
  resolvePayment: (id: string, reason: string) =>
    api<ReconciliationDto>(`/admin/payments/${id}/resolve`, { method: "POST", body: { reason } }),
};

// ---------------------------------------------------------------- Notification
export const notificationApi = {
  mine: (query: { page?: number; size?: number; unreadOnly?: boolean }) =>
    api<SpringPage<NotificationDto>>("/notifications/me", { query }),
  unreadCount: () => api<{ count: number }>("/notifications/me/unread-count"),
  markRead: (id: string) => api<void>(`/notifications/${id}/read`, { method: "PUT" }),
  templates: () => api<TemplateDto[]>("/admin/notification-templates"),
  saveTemplate: (code: string, body: { channel: "IN_APP" | "EMAIL"; subject: string; bodyTemplate: string }) =>
    api<TemplateDto>(`/admin/notification-templates/${code}`, { method: "PUT", body }),
};

// ---------------------------------------------------------------- Analytics
export const analyticsApi = {
  overview: (query: { from?: string; to?: string; granularity?: "DAY" | "MONTH"; eventId?: string }) =>
    api<AnalyticsOverviewDto>("/analytics/overview", { query }),
  events: (query: { from?: string; to?: string; page?: number; size?: number; sort?: string }) =>
    api<SpringPage<EventAnalyticsRowDto>>("/analytics/events", { query }),
  sales: (eventId: string, query: { from?: string; to?: string }) =>
    api<SalesReportDto>(`/analytics/events/${eventId}/sales`, { query }),
  attendance: (eventId: string) => api<AttendanceDto>(`/analytics/events/${eventId}/attendance`),
};
