export const errors: Record<string, string> = {
  SEAT_TAKEN:
    "Có ghế vừa được người khác giữ. Các ghế đó đã được bỏ chọn, bạn hãy chọn ghế khác.",
  SEAT_NOT_FOUND: "Ghế đã chọn không còn trong sơ đồ. Bạn hãy chọn lại.",
  TOO_MANY_SEATS: "Mỗi đơn được chọn tối đa 10 ghế.",
  ORDER_EXPIRED: "Đã hết thời gian giữ ghế. Vui lòng chọn lại ghế.",
  PENDING_ORDER_EXISTS:
    "Bạn đang có một đơn chưa thanh toán cho sự kiện này. Hãy thanh toán hoặc hủy đơn đó trước.",
  ORDER_NOT_CANCELLABLE: "Đơn này không còn hủy được.",
  TICKET_TYPE_NOT_ON_SALE: "Sự kiện hoặc hạng vé này đã ngừng bán.",
  INVALID_CREDENTIALS: "Email hoặc mật khẩu chưa đúng.",
  EMAIL_ALREADY_EXISTS: "Email này đã được đăng ký.",
  ACCOUNT_LOCKED: "Tài khoản đang bị khóa. Vui lòng liên hệ hỗ trợ.",
  INVALID_REFRESH_TOKEN:
    "Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại.",
  REFRESH_TOKEN_EXPIRED: "Phiên đăng nhập đã hết hạn.",
  REFRESH_TOKEN_REUSED: "Vui lòng đăng nhập lại để tiếp tục.",
  PAYMENT_FAILED:
    "Thanh toán chưa thành công. Vé vẫn được giữ trong thời gian còn lại.",
  FORBIDDEN: "Bạn không có quyền thực hiện thao tác này.",
  INVALID_STATE: "Trạng thái đã thay đổi. Vui lòng kiểm tra lại.",
  REFUND_NOT_ELIGIBLE:
    "Chỉ hoàn đơn còn ít nhất 48 giờ trước sự kiện và tất cả vé chưa sử dụng.",
  VALIDATION_ERROR: "Vui lòng kiểm tra lại thông tin đã nhập.",
};
export const errorMessage = (code: string) =>
  errors[code] ?? "Chưa thể thực hiện thao tác. Vui lòng thử lại.";

/** Lỗi nghiệp vụ mang `code` theo hợp đồng ErrorResponse; message đã là câu tiếng Việt cho người dùng. */
export class ApiError extends Error {
  readonly code: string;
  /** Chỉ có khi code = SEAT_TAKEN: các ghế đã bị người khác giữ hoặc mua. */
  readonly takenSeatIds: string[];
  constructor(code: string, takenSeatIds: string[] = []) {
    super(errorMessage(code));
    this.name = "ApiError";
    this.code = code;
    this.takenSeatIds = takenSeatIds;
  }
}
