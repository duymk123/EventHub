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

  // Auth Service — tài khoản & phiên
  CURRENT_PASSWORD_INCORRECT: "Mật khẩu hiện tại chưa đúng.",
  UNAUTHORIZED: "Bạn cần đăng nhập để tiếp tục.",
  USER_NOT_FOUND: "Không tìm thấy tài khoản này.",
  ACCOUNT_ALREADY_LOCKED: "Tài khoản này đã bị khóa từ trước.",
  ACCOUNT_ALREADY_ACTIVE: "Tài khoản này đang hoạt động bình thường.",
  CANNOT_MODIFY_OWN_ACCOUNT: "Bạn không thể thao tác trên chính tài khoản của mình.",
  ROLE_MAPPING_CONFLICT: "Tài khoản đang có nhiều quyền xung đột. Vui lòng liên hệ hỗ trợ.",
  EMAIL_ALREADY_VERIFIED: "Email này đã được xác thực rồi.",

  // Auth Service — hồ sơ
  PROFILE_NOT_FOUND: "Chưa có hồ sơ cho tài khoản này.",
  PROFILE_ALREADY_EXISTS: "Hồ sơ đã tồn tại, hãy dùng chức năng cập nhật.",
  PHONE_ALREADY_EXISTS: "Số điện thoại này đã được dùng cho tài khoản khác.",

  // Auth Service — hệ thống
  AUTH_PROVIDER_UNAVAILABLE: "Hệ thống đăng nhập đang bận. Vui lòng thử lại sau ít phút.",
  INTERNAL_ERROR: "Hệ thống gặp sự cố. Vui lòng thử lại sau.",
};
export const errorMessage = (code: string) =>
  errors[code] ?? "Chưa thể thực hiện thao tác. Vui lòng thử lại.";
