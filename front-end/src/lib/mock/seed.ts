import type { DemoState, Event, TicketType } from "@/types";
export const VENUE = "Không gian sự kiện EventHub, TP. Hồ Chí Minh";
export const roleNames = {
  CUSTOMER: "Khách hàng",
  ORGANIZER: "Ban tổ chức",
  ADMIN: "Quản trị viên",
};
export const statusLabels: Record<string, string> = {
  DRAFT: "Bản nháp",
  SUBMITTED: "Chờ duyệt",
  APPROVED: "Đã duyệt",
  REJECTED: "Từ chối",
  PUBLISHED: "Đang mở bán",
  CLOSED: "Đã đóng bán",
  PENDING_PAYMENT: "Chờ thanh toán",
  PAID: "Đã thanh toán",
  FAILED: "Thất bại",
  EXPIRED: "Hết hạn",
  REFUNDED: "Đã hoàn tiền",
  REQUESTED: "Chờ duyệt hoàn",
  ISSUED: "Chưa sử dụng",
  CHECKED_IN: "Đã vào cổng",
  CHECKED_OUT: "Đã ra cổng",
  CANCELLED: "Đã hủy",
  ACTIVE: "Hoạt động",
  LOCKED: "Đã khóa",
};
const tiers = (id: string, multiplier = 1): TicketType[] => [
  {
    id: `${id}-standard`,
    name: "STANDARD",
    price: 450000 * multiplier,
    quota: 186,
    available: 186,
    active: true,
  },
  {
    id: `${id}-premium`,
    name: "PREMIUM",
    price: 850000 * multiplier,
    quota: id === "indie" ? 66 : 64,
    available: 64,
    active: true,
  },
  {
    id: `${id}-vip`,
    name: "VIP",
    price: 1500000 * multiplier,
    quota: 12,
    available: 12,
    active: true,
  },
];
const event = (
  id: string,
  title: string,
  subtitle: string,
  category: string,
  day: number,
  image: string,
  status: Event["status"] = "PUBLISHED",
  multiplier = 1,
): Event => ({
  id,
  title,
  subtitle,
  category,
  startTime: `2026-11-${day}T12:00:00.000Z`,
  endTime: `2026-11-${day}T16:00:00.000Z`,
  image,
  organizerId: "organizer",
  organizerName: "EventHub Live",
  status,
  ticketTypes: tiers(id, multiplier),
  description:
    "Một đêm để tạm gác những bộn bề và sống trọn trong từng khoảnh khắc. Cùng những người bạn yêu quý, hòa mình vào không gian âm nhạc, ánh sáng và những kết nối thật gần. Chương trình được thiết kế để bạn có một trải nghiệm trọn vẹn từ lúc bước vào đến giai điệu cuối cùng.",
});
export function seedState(): DemoState {
  const events = [
    event(
      "echoes",
      "ECHOES OF THE NIGHT",
      "Khi âm nhạc chạm đến cảm xúc.",
      "Âm nhạc",
      21,
      "/images/concert.jpg",
    ),
    event(
      "indie",
      "Indie trong thành phố",
      "Những thanh âm rất riêng.",
      "Âm nhạc",
      22,
      "/images/indie.jpg",
      "PUBLISHED",
      0.6,
    ),
    event(
      "spectrum",
      "SPECTRUM: Art & Light",
      "Bước vào miền sáng tạo.",
      "Nghệ thuật",
      28,
      "/images/art.jpg",
      "PUBLISHED",
      0.4,
    ),
    event(
      "afterglow",
      "AFTERGLOW Festival",
      "Những giai điệu dưới bầu trời đêm.",
      "Lễ hội",
      29,
      "/images/festival.jpg",
      "PUBLISHED",
      1.2,
    ),
    event(
      "creative",
      "Creative Tomorrow",
      "Gặp gỡ những ý tưởng mới.",
      "Hội thảo",
      25,
      "/images/workshop.jpg",
      "PUBLISHED",
      0,
    ),
    event(
      "acoustic",
      "Một tối acoustic",
      "Chậm lại cùng những bản tình ca.",
      "Âm nhạc",
      27,
      "/images/acoustic.jpg",
      "SUBMITTED",
      0.5,
    ),
    event(
      "winter",
      "Winter Sessions",
      "Một mùa âm nhạc đang đến.",
      "Âm nhạc",
      30,
      "/images/concert.jpg",
      "DRAFT",
    ),
    event(
      "neon",
      "NEON: The Experience",
      "Thành phố lên đèn.",
      "Lễ hội",
      26,
      "/images/festival.jpg",
      "APPROVED",
    ),
  ];
  return {
    version: 1,
    userId: "customer",
    users: [
      {
        id: "customer",
        name: "Nguyễn Minh Anh",
        email: "minhanh@example.com",
        role: "CUSTOMER",
        status: "ACTIVE",
      },
      {
        id: "organizer",
        name: "Trần Hoàng Nam",
        email: "nam@example.com",
        role: "ORGANIZER",
        status: "ACTIVE",
      },
      {
        id: "admin",
        name: "Lê Khánh Linh",
        email: "linh@example.com",
        role: "ADMIN",
        status: "ACTIVE",
      },
      {
        id: "customer-2",
        name: "Phạm Hải Đăng",
        email: "haidang@example.com",
        role: "CUSTOMER",
        status: "ACTIVE",
      },
      {
        id: "customer-3",
        name: "Vũ Thanh Hà",
        email: "thanhha@example.com",
        role: "CUSTOMER",
        status: "LOCKED",
      },
    ],
    events,
    orders: [
      {
        id: "EH-260901",
        eventId: "indie",
        customerId: "customer",
        customerEmail: "minhanh@example.com",
        lines: [
          {
            tierId: "indie-premium",
            tier: "PREMIUM",
            quantity: 2,
            unitPrice: 510000,
          },
        ],
        holders: [
          {
            name: "Nguyễn Minh Anh",
            email: "minhanh@example.com",
            tierId: "indie-premium",
          },
          {
            name: "Trần Hoài An",
            email: "hoaian@example.com",
            tierId: "indie-premium",
          },
        ],
        total: 1020000,
        status: "PAID",
        createdAt: "2026-11-07T09:30:00Z",
        paidAt: "2026-11-07T09:31:00Z",
        expiresAt: "2026-11-07T09:33:00Z",
      },
    ],
    tickets: [
      {
        id: "EH-T001",
        orderId: "EH-260901",
        eventId: "indie",
        customerId: "customer",
        holderName: "Nguyễn Minh Anh",
        holderEmail: "minhanh@example.com",
        tier: "PREMIUM",
        status: "ISSUED",
      },
      {
        id: "EH-T002",
        orderId: "EH-260901",
        eventId: "indie",
        customerId: "customer",
        holderName: "Trần Hoài An",
        holderEmail: "hoaian@example.com",
        tier: "PREMIUM",
        status: "ISSUED",
      },
    ],
    refunds: [],
    favorites: ["indie"],
    notices: [
      {
        id: "notice-1",
        userId: "customer",
        title: "Vé của bạn đã sẵn sàng!",
        content:
          "2 vé Indie trong thành phố đã được thêm vào ví vé. Hẹn gặp bạn ở hàng ghế khán giả.",
        read: false,
        href: "/tickets",
        createdAt: "2026-11-07T09:31:00Z",
      },
      {
        id: "notice-2",
        userId: "customer",
        title: "Chào mừng đến với EventHub",
        content:
          "Khám phá sự kiện, tìm trải nghiệm của riêng bạn và lưu giữ những khoảnh khắc đáng nhớ.",
        read: true,
        href: "/explore",
        createdAt: "2026-11-06T08:00:00Z",
      },
    ],
    categories: ["Âm nhạc", "Nghệ thuật", "Lễ hội", "Hội thảo"],
    templates: ["TICKET_ISSUED", "ORDER_FAILED", "ORDER_REFUNDED"].flatMap(
      (code) =>
        (["EMAIL", "IN_APP"] as const).map((channel) => ({
          id: `${code}-${channel}`,
          code,
          channel,
          subject:
            code === "TICKET_ISSUED"
              ? "Vé của bạn đã sẵn sàng!"
              : code === "ORDER_FAILED"
                ? "Đơn hàng chưa hoàn tất"
                : "Yêu cầu hoàn tiền đã được xử lý",
          body: "Xin chào {{customerName}}, thông tin đơn {{orderId}} của bạn đã được cập nhật. Cảm ơn bạn đã đồng hành cùng EventHub.",
        })),
    ),
  };
}
