// Chuyển DTO backend sang kiểu dữ liệu giao diện (src/types). Giữ mọi suy diễn ở một chỗ để component chỉ hiển thị.
import type { Event, Notice, Order, Refund, Template, Ticket, TicketType, User } from "@/types";
import type {
  EventDto, EventSummaryDto, NotificationDto, OrderDto, RefundDto, SeatMapDto, TemplateDto, TicketDto,
  TicketTypeDto, UserDto,
} from "@/types/api";
import { seatId } from "@/lib/seats";

/** Backend chưa lưu ảnh sự kiện: chọn ảnh có sẵn theo danh mục (quyết định 2026-11-24). */
const IMAGES = ["/images/concert.jpg", "/images/festival.jpg", "/images/indie.jpg", "/images/art.jpg",
  "/images/acoustic.jpg", "/images/workshop.jpg"];
const KEYWORD_IMAGES: [RegExp, string][] = [
  [/nhạc|concert|music|live/i, "/images/concert.jpg"],
  [/lễ hội|festival/i, "/images/festival.jpg"],
  [/nghệ thuật|art|triển lãm/i, "/images/art.jpg"],
  [/workshop|hội thảo|talk|học/i, "/images/workshop.jpg"],
  [/acoustic|indie/i, "/images/acoustic.jpg"],
];

export function eventImage(category: string, eventId: string) {
  const match = KEYWORD_IMAGES.find(([re]) => re.test(category));
  if (match) return match[1];
  let hash = 0;
  for (const ch of eventId) hash = (hash * 31 + ch.charCodeAt(0)) >>> 0;
  return IMAGES[hash % IMAGES.length];
}

function subtitleOf(description: string | null | undefined) {
  if (!description) return "";
  const first = description.split(/(?<=[.!?])\s+/)[0] ?? "";
  return first.length > 140 ? `${first.slice(0, 137)}…` : first;
}

export function toUser(dto: UserDto): User {
  return {
    id: dto.id,
    name: dto.fullName?.trim() || dto.email,
    email: dto.email,
    role: dto.role,
    status: dto.status,
    emailVerified: dto.emailVerified,
    createdAt: dto.createdAt ?? undefined,
  };
}

/** Sự kiện chưa kèm hạng vé (ticketTypes rỗng); hạng vé ghép bằng withTicketTypes. */
export function toEvent(dto: EventDto | EventSummaryDto): Event {
  const full = "status" in dto ? dto : null;
  const category = dto.category?.name ?? "Sự kiện";
  return {
    id: dto.id,
    title: dto.title,
    subtitle: subtitleOf(full?.description),
    category,
    categoryId: dto.category?.id ?? "",
    startTime: dto.startTime,
    endTime: dto.endTime,
    image: eventImage(category, dto.id),
    organizerId: full?.organizerId ?? "",
    organizerName: "",
    status: full?.status ?? "PUBLISHED",
    description: full?.description ?? "",
    ticketTypes: [],
    reviewReason: full?.rejectReason ?? undefined,
    createdAt: full?.createdAt,
  };
}

/** Hạng vé + (nếu có) sơ đồ ghế: `taken` theo nhãn ghế, `seatIds` nhãn → UUID để gửi khi đặt vé. */
export function toTicketType(dto: TicketTypeDto, seatMap?: SeatMapDto): TicketType {
  const taken: TicketType["taken"] = {};
  const seatIds: Record<string, string> = {};
  seatMap?.rows.forEach((row) =>
    row.seats.forEach((seat) => {
      seatIds[seat.label] = seat.id;
      if (seat.status !== "AVAILABLE") taken[seat.label] = seat.status;
    }),
  );
  return {
    id: dto.id,
    name: dto.name,
    price: Number(dto.price),
    rowCount: dto.rowCount,
    seatsPerRow: dto.seatsPerRow,
    quota: dto.totalQuota,
    available: seatMap ? dto.totalQuota - Object.keys(taken).length : dto.availableSeats,
    taken,
    active: dto.status === "ACTIVE",
    seatIds: seatMap ? seatIds : undefined,
    held: dto.heldSeats,
    sold: dto.soldSeats,
  };
}

const TIER_ORDER = { VIP: 0, PREMIUM: 1, STANDARD: 2 } as const;

export function withTicketTypes(event: Event, types: TicketType[]): Event {
  return { ...event, ticketTypes: [...types].sort((a, b) => TIER_ORDER[a.name] - TIER_ORDER[b.name]) };
}

export function toOrder(dto: OrderDto): Order {
  return {
    id: dto.id,
    eventId: dto.eventId,
    customerId: dto.customerId,
    lines: dto.items.map((item) => ({
      tierId: item.ticketTypeId,
      tier: item.ticketTypeName,
      quantity: item.quantity,
      unitPrice: Number(item.unitPrice),
    })),
    holders: dto.items.flatMap((item) =>
      item.holders.map((h) => ({
        name: h.holderName,
        email: h.holderEmail ?? "",
        tierId: item.ticketTypeId,
        seatId: seatId(item.ticketTypeId, h.seatLabel),
        seatLabel: h.seatLabel,
      })),
    ),
    total: Number(dto.totalAmount),
    status: dto.status,
    createdAt: dto.createdAt,
    expiresAt: dto.expiresAt,
    paidAt: dto.paidAt ?? undefined,
  };
}

export function toTicket(dto: TicketDto): Ticket {
  return {
    id: dto.id,
    orderId: dto.orderId,
    eventId: dto.eventId,
    holderName: dto.holderName ?? "",
    tier: dto.ticketTypeName,
    seatLabel: dto.seatLabel,
    status: dto.status,
    issuedAt: dto.issuedAt,
  };
}

export function toRefund(dto: RefundDto): Refund {
  return {
    id: dto.id,
    orderId: dto.orderId,
    reason: dto.reason,
    amount: Number(dto.amount),
    status: dto.status,
    createdAt: dto.requestedAt,
    reviewedAt: dto.reviewedAt ?? undefined,
    reviewReason: dto.reviewReason ?? undefined,
    refundedAt: dto.refundedAt ?? undefined,
    failureReason: dto.failureReason ?? undefined,
  };
}

const NOTICE_LINKS: Record<string, string> = {
  TICKET_ISSUED: "/tickets",
  ORDER_FAILED: "/orders",
  ORDER_REFUNDED: "/orders",
};

export function toNotice(dto: NotificationDto): Notice {
  return {
    id: dto.id,
    title: dto.title,
    content: dto.content,
    read: Boolean(dto.readAt),
    href: NOTICE_LINKS[dto.templateCode] ?? "/notifications",
    createdAt: dto.createdAt,
  };
}

export function toTemplate(dto: TemplateDto): Template {
  return { id: dto.id, code: dto.code, channel: dto.channel, subject: dto.subject, body: dto.bodyTemplate };
}
