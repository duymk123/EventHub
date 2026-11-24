import type { Role } from "@/types";

/** C03: một địa điểm chung cho mọi sự kiện (cùng text với email vé của Notification). */
export const VENUE = "141 Đường Chiến Thắng, Hà Nội";

export const roleNames: Record<Role, string> = {
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
  PROCESSING: "Đang hoàn tiền",
  ISSUED: "Chưa sử dụng",
  CHECKED_IN: "Đã vào cổng",
  CHECKED_OUT: "Đã ra cổng",
  CANCELLED: "Đã hủy",
  ACTIVE: "Hoạt động",
  INACTIVE: "Tạm ngừng bán",
  LOCKED: "Đã khóa",
};
