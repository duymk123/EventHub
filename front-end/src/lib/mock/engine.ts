"use client";
import type {
  DemoState,
  Event,
  EventStatus,
  Holder,
  Order,
  Role,
  Ticket,
  TicketType,
} from "@/types";
import { demoStore } from "./store";
import { wait } from "@/lib/utils";
import { errorMessage } from "@/lib/api/errors";
const id = (prefix: string) =>
  `${prefix}-${crypto.randomUUID().slice(0, 8).toUpperCase()}`;
const fail = (code: string): never => {
  throw new Error(errorMessage(code));
};
const actor = () => {
  const s = demoStore.get();
  const u = s.users.find((u) => u.id === s.userId);
  if (!u || u.status === "LOCKED") return fail("FORBIDDEN");
  return u;
};
function role(required: Role) {
  const u = actor();
  if (u.role !== required) fail("FORBIDDEN");
  return u;
}
const notice = (
  s: DemoState,
  userId: string,
  title: string,
  content: string,
  href: string,
): DemoState => ({
  ...s,
  notices: [
    {
      id: id("N"),
      userId,
      title,
      content,
      href,
      read: false,
      createdAt: new Date().toISOString(),
    },
    ...s.notices,
  ],
});
function restoreStock(s: DemoState, order: Order): Event[] {
  return s.events.map((e) =>
    e.id !== order.eventId
      ? e
      : {
          ...e,
          ticketTypes: e.ticketTypes.map((t) => ({
            ...t,
            available:
              t.available +
              (order.lines.find((l) => l.tierId === t.id)?.quantity ?? 0),
          })),
        },
  );
}
export function expireOrders() {
  const expired = demoStore
    .get()
    .orders.filter(
      (o) =>
        o.status === "PENDING_PAYMENT" && Date.parse(o.expiresAt) <= Date.now(),
    );
  if (!expired.length) return;
  demoStore.set((s) =>
    expired.reduce(
      (next, o) => ({
        ...next,
        events: restoreStock(next, o),
        orders: next.orders.map((x) =>
          x.id === o.id ? { ...x, status: "EXPIRED" } : x,
        ),
      }),
      s,
    ),
  );
}
export const mock = {
  async reserve(
    eventId: string,
    quantities: Record<string, number>,
    holders: Holder[],
  ) {
    const u = role("CUSTOMER");
    await wait(650);
    expireOrders();
    const s = demoStore.get();
    const e = s.events.find((e) => e.id === eventId);
    if (!e || e.status !== "PUBLISHED" || Date.parse(e.startTime) <= Date.now())
      return fail("INVALID_STATE");
    const lines = e.ticketTypes
      .filter((t) => (quantities[t.id] ?? 0) > 0)
      .map((t) => ({
        tierId: t.id,
        tier: t.name,
        quantity: quantities[t.id],
        unitPrice: t.price,
      }));
    if (
      !lines.length ||
      lines.some(
        (l) =>
          l.quantity > 6 || l.quantity < 1 || !Number.isInteger(l.quantity),
      )
    )
      return fail("VALIDATION_ERROR");
    if (
      lines.some((l) => {
        const t = e.ticketTypes.find((t) => t.id === l.tierId)!;
        return !t.active || t.available < l.quantity;
      })
    )
      return fail("INSUFFICIENT_TICKETS");
    if (
      holders.length !== lines.reduce((n, l) => n + l.quantity, 0) ||
      holders.some((h) => !h.name.trim()) ||
      lines.some(
        (l) =>
          holders.filter((h) => h.tierId === l.tierId).length !== l.quantity,
      )
    )
      return fail("VALIDATION_ERROR");
    const reservedAt=Date.now();
    const order: Order = {
      id: id("EH"),
      eventId,
      customerId: u.id,
      customerEmail: u.email,
      lines,
      holders: holders.map((h) => ({ ...h, email: h.email.trim() || u.email })),
      total: lines.reduce((sum, l) => sum + l.quantity * l.unitPrice, 0),
      status: "PENDING_PAYMENT",
      createdAt: new Date(reservedAt).toISOString(),
      expiresAt: new Date(reservedAt + 180000).toISOString(),
    };
    demoStore.set((next) => ({
      ...next,
      events: next.events.map((x) =>
        x.id !== eventId
          ? x
          : {
              ...x,
              ticketTypes: x.ticketTypes.map((t) => ({
                ...t,
                available: t.available - (quantities[t.id] ?? 0),
              })),
            },
      ),
      orders: [order, ...next.orders],
    }));
    return order.id;
  },
  async pay(orderId: string, success = true) {
    const u = role("CUSTOMER");
    await wait(1400);
    expireOrders();
    const o = demoStore
      .get()
      .orders.find((o) => o.id === orderId && o.customerId === u.id);
    if (!o) return fail("FORBIDDEN");
    if (o.status === "PAID") return;
    if (o.status !== "PENDING_PAYMENT") return fail("ORDER_EXPIRED");
    if (!success) return fail("PAYMENT_FAILED");
    const tickets: Ticket[] = o.holders.map((h) => ({
      id: id("EH-T"),
      orderId: o.id,
      eventId: o.eventId,
      customerId: u.id,
      holderName: h.name,
      holderEmail: h.email,
      tier: o.lines.find((l) => l.tierId === h.tierId)!.tier,
      status: "ISSUED",
    }));
    demoStore.set((s) =>
      notice(
        {
          ...s,
          orders: s.orders.map((x) =>
            x.id === o.id
              ? { ...x, status: "PAID", paidAt: new Date().toISOString() }
              : x,
          ),
          tickets: [...tickets, ...s.tickets],
        },
        u.id,
        "Vé của bạn đã sẵn sàng!",
        `${tickets.length} vé đã được phát hành. Xem chi tiết trong Ví vé của tôi.`,
        "/tickets",
      ),
    );
  },
  async cancelOrder(orderId: string) {
    const u = role("CUSTOMER");
    await wait();
    const o = demoStore
      .get()
      .orders.find((o) => o.id === orderId && o.customerId === u.id);
    if (!o || o.status !== "PENDING_PAYMENT") return fail("INVALID_STATE");
    demoStore.set((s) => ({
      ...s,
      events: restoreStock(s, o),
      orders: s.orders.map((x) =>
        x.id === o.id ? { ...x, status: "FAILED" } : x,
      ),
    }));
  },
  async requestRefund(orderId: string, reason: string) {
    const u = role("CUSTOMER");
    await wait();
    const s = demoStore.get();
    const o = s.orders.find((o) => o.id === orderId && o.customerId === u.id);
    if (!o || !refundEligible(s, o) || !reason.trim())
      return fail("REFUND_NOT_ELIGIBLE");
    if (
      s.refunds.some((r) => r.orderId === orderId && r.status === "REQUESTED")
    )
      return fail("INVALID_STATE");
    demoStore.set((s) => ({
      ...s,
      refunds: [
        {
          id: id("RF"),
          orderId,
          reason,
          amount: Math.round(o.total * 0.7),
          status: "REQUESTED",
          createdAt: new Date().toISOString(),
        },
        ...s.refunds,
      ],
    }));
  },
  async reviewRefund(refundId: string, approve: boolean, reason: string) {
    role("ADMIN");
    await wait(800);
    const s = demoStore.get();
    const r = s.refunds.find((r) => r.id === refundId);
    const o = s.orders.find((o) => o.id === r?.orderId);
    if (!r || !o || r.status !== "REQUESTED") return fail("INVALID_STATE");
    if (approve && !refundEligible(s, o)) return fail("REFUND_NOT_ELIGIBLE");
    if (!approve && !reason.trim()) return fail("VALIDATION_ERROR");
    demoStore.set((s) =>
      notice(
        {
          ...s,
          refunds: s.refunds.map((x) =>
            x.id === refundId
              ? {
                  ...x,
                  status: approve ? "REFUNDED" : "REJECTED",
                  reviewReason: reason,
                  reviewedAt: new Date().toISOString(),
                }
              : x,
          ),
          orders: s.orders.map((x) =>
            x.id === o.id && approve ? { ...x, status: "REFUNDED" } : x,
          ),
          tickets: s.tickets.map((t) =>
            t.orderId === o.id && approve ? { ...t, status: "CANCELLED" } : t,
          ),
          events: approve ? restoreStock(s, o) : s.events,
        },
        o.customerId,
        approve
          ? "Hoàn tiền thành công"
          : "Yêu cầu hoàn tiền chưa được chấp thuận",
        approve
          ? "70% giá trị đơn đã được hoàn trong bản trải nghiệm. Các vé của đơn đã được hủy."
          : reason,
        "/orders",
      ),
    );
  },
  async transition(eventId: string, next: EventStatus, reason = "") {
    const u = actor();
    await wait();
    const e = demoStore.get().events.find((e) => e.id === eventId);
    if (!e) return fail("INVALID_STATE");
    const review =
      u.role === "ADMIN" &&
      e.status === "SUBMITTED" &&
      ["APPROVED", "REJECTED"].includes(next);
    const own =
      u.role === "ORGANIZER" &&
      e.organizerId === u.id &&
      ((e.status === "DRAFT" && next === "SUBMITTED") ||
        (e.status === "APPROVED" && next === "PUBLISHED") ||
        (e.status === "PUBLISHED" && next === "CLOSED"));
    if (!review && !own) return fail("FORBIDDEN");
    if (next === "REJECTED" && !reason.trim()) return fail("VALIDATION_ERROR");
    if (
      next === "PUBLISHED" &&
      (!e.ticketTypes.some((t) => t.active && t.quota > 0) ||
        Date.parse(e.startTime) <= Date.now())
    )
      throw new Error(
        "Cần ít nhất một hạng vé đang bán có số lượng lớn hơn 0 và thời gian sự kiện hợp lệ.",
      );
    demoStore.set((s) =>
      notice(
        {
          ...s,
          events: s.events.map((x) =>
            x.id === eventId ? { ...x, status: next, reviewReason: reason } : x,
          ),
        },
        e.organizerId,
        "Sự kiện đã được cập nhật",
        `${e.title}: ${next === "REJECTED" ? reason : next === "APPROVED" ? "đã được duyệt, sẵn sàng mở bán" : "đã chuyển trạng thái"}.`,
        "/organizer/events",
      ),
    );
  },
  async saveEvent(
    input: Pick<
      Event,
      | "title"
      | "subtitle"
      | "description"
      | "category"
      | "startTime"
      | "endTime"
    >,
    types: TicketType[],
    eventId?: string,
  ) {
    const u = role("ORGANIZER");
    await wait();
    const existing = demoStore.get().events.find((e) => e.id === eventId);
    if (
      eventId &&
      (!existing ||
        existing.organizerId !== u.id ||
        !["DRAFT", "REJECTED"].includes(existing.status))
    )
      return fail("FORBIDDEN");
    if (
      !input.title.trim() ||
      !Number.isFinite(Date.parse(input.startTime)) ||
      !Number.isFinite(Date.parse(input.endTime)) ||
      Date.parse(input.startTime) >= Date.parse(input.endTime) ||
      Date.parse(input.startTime) <= Date.now()
    )
      return fail("VALIDATION_ERROR");
    if (
      types.some(
        (t) =>
          t.quota < 0 ||
          t.price < 0 ||
          !Number.isSafeInteger(t.quota) ||
          !Number.isSafeInteger(t.price),
      )
    )
      return fail("VALIDATION_ERROR");
    const savedId = eventId ?? id("event");
    const event: Event = {
      ...input,
      id: savedId,
      organizerId: u.id,
      organizerName: "EventHub Live",
      image: existing?.image ?? "/images/concert.jpg",
      status: "DRAFT",
      ticketTypes: types.map((t) => ({
        ...t,
        id: existing ? t.id : `${savedId}-${t.name.toLowerCase()}`,
        available: t.quota,
      })),
    };
    demoStore.set((s) => ({
      ...s,
      events: existing
        ? s.events.map((e) => (e.id === event.id ? event : e))
        : [event, ...s.events],
    }));
    return event.id;
  },
  async scan(ticketId: string, action: "in" | "out") {
    const u = role("ORGANIZER");
    await wait(450);
    const s = demoStore.get();
    const t = s.tickets.find((t) => t.id === ticketId.trim());
    if (!t) throw new Error("Không tìm thấy vé. Kiểm tra lại mã vé.");
    if (s.events.find((e) => e.id === t.eventId)?.organizerId !== u.id)
      return fail("FORBIDDEN");
    if (
      s.refunds.some((r) => r.orderId === t.orderId && r.status === "REQUESTED")
    )
      throw new Error(
        "Đơn đang chờ xét hoàn tiền. Vé tạm ngừng quét trong bản trải nghiệm.",
      );
    if (
      (action === "in" && t.status !== "ISSUED") ||
      (action === "out" && t.status !== "CHECKED_IN")
    )
      throw new Error(
        "Vé không hợp lệ cho thao tác này. Vé đã ra cổng không được vào lại.",
      );
    demoStore.set((s) => ({
      ...s,
      tickets: s.tickets.map((x) =>
        x.id === t.id
          ? {
              ...x,
              status: action === "in" ? "CHECKED_IN" : "CHECKED_OUT",
              scannedAt: new Date().toISOString(),
            }
          : x,
      ),
    }));
    return t;
  },
  async login(email: string) {
    await wait();
    const u = demoStore
      .get()
      .users.find((u) => u.email.toLowerCase() === email.toLowerCase());
    if (!u) return fail("INVALID_CREDENTIALS");
    if (u.status === "LOCKED") return fail("ACCOUNT_LOCKED");
    demoStore.set((s) => ({ ...s, userId: u.id }));
    return u;
  },
  async register(name: string, email: string) {
    await wait();
    if (
      demoStore
        .get()
        .users.some((u) => u.email.toLowerCase() === email.toLowerCase())
    )
      return fail("EMAIL_ALREADY_EXISTS");
    const u = {
      id: id("user"),
      name,
      email,
      role: "CUSTOMER" as const,
      status: "ACTIVE" as const,
    };
    demoStore.set((s) => ({ ...s, userId: u.id, users: [...s.users, u] }));
  },
  switchRole(role: Role) {
    const users = demoStore
      .get()
      .users.filter((u) => u.role === role && u.status === "ACTIVE");
    const u = users.find((u) => u.id === role.toLowerCase()) ?? users[0];
    if (u) demoStore.set((s) => ({ ...s, userId: u.id }));
    return u;
  },
  async userRole(userId: string, nextRole: Role) {
    const admin = role("ADMIN");
    await wait();
    if (admin.id === userId) return fail("FORBIDDEN");
    demoStore.set((s) => ({
      ...s,
      users: s.users.map((u) =>
        u.id === userId ? { ...u, role: nextRole } : u,
      ),
    }));
  },
  async userStatus(userId: string, locked: boolean) {
    const admin = role("ADMIN");
    await wait();
    if (admin.id === userId) return fail("FORBIDDEN");
    demoStore.set((s) => ({
      ...s,
      users: s.users.map((u) =>
        u.id === userId ? { ...u, status: locked ? "LOCKED" : "ACTIVE" } : u,
      ),
    }));
  },
  async saveCategory(name: string, previous?: string) {
    role("ADMIN");
    await wait();
    const value = name.trim();
    if (value.length < 2 || value.length > 50) return fail("VALIDATION_ERROR");
    if (
      demoStore
        .get()
        .categories.some(
          (c) =>
            c !== previous &&
            c.toLocaleLowerCase("vi") === value.toLocaleLowerCase("vi"),
        )
    )
      throw new Error("Tên danh mục này đã tồn tại.");
    demoStore.set((s) => ({
      ...s,
      categories: previous
        ? s.categories.map((c) => (c === previous ? value : c))
        : [...s.categories, value],
      events: s.events.map((e) =>
        e.category === previous ? { ...e, category: value } : e,
      ),
    }));
  },
  async saveTemplate(templateId: string, subject: string, body: string) {
    role("ADMIN");
    await wait();
    if (!subject.trim() || !body.trim()) return fail("VALIDATION_ERROR");
    demoStore.set((s) => ({
      ...s,
      templates: s.templates.map((t) =>
        t.id === templateId
          ? { ...t, subject: subject.trim(), body: body.trim() }
          : t,
      ),
    }));
  },
};
export function refundEligible(s: DemoState, o: Order) {
  const e = s.events.find((e) => e.id === o.eventId);
  const tickets = s.tickets.filter((t) => t.orderId === o.id);
  return (
    o.status === "PAID" &&
    Boolean(e) &&
    Date.parse(e!.startTime) - Date.now() >= 48 * 3600000 &&
    tickets.length > 0 &&
    tickets.every((t) => t.status === "ISSUED")
  );
}
