export const errors: Record<string, string> = {
  INSUFFICIENT_TICKETS: "Số vé còn lại không đủ. Bạn hãy chọn số lượng khác.",
  ORDER_EXPIRED: "Đã hết 3 phút giữ vé. Vui lòng chọn lại vé.",
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
