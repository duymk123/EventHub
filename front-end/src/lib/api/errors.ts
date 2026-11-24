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
  EMAIL_NOT_VERIFIED: "Tài khoản chưa hoàn tất xác minh email. Vui lòng kiểm tra hộp thư.",
  TOO_MANY_REQUESTS: "Bạn đã thao tác quá nhiều lần. Vui lòng thử lại sau ít phút.",
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

  // Kết nối, Gateway
  NETWORK_ERROR: "Không kết nối được máy chủ. Hãy kiểm tra mạng và thử lại.",
  SERVICE_UNAVAILABLE: "Hệ thống đang bận hoặc tạm dừng. Vui lòng thử lại sau ít phút.",
  GATEWAY_TIMEOUT: "Máy chủ phản hồi quá lâu. Hãy kiểm tra lại trạng thái trước khi thử lại.",
  NOT_FOUND: "Không tìm thấy nội dung này.",
  FORBIDDEN_NOT_OWNER: "Mục này thuộc về người khác nên bạn không thao tác được.",
  CONCURRENT_UPDATE: "Dữ liệu vừa được thay đổi. Vui lòng tải lại rồi thử lại.",

  // Event Service
  EVENT_NOT_FOUND: "Không tìm thấy sự kiện.",
  INVALID_EVENT_STATE: "Sự kiện đang ở trạng thái không cho phép thao tác này.",
  INVALID_DATE_RANGE: "Khoảng ngày chưa hợp lệ.",
  TICKET_CONFIGURATION_NOT_READY: "Cần ít nhất một hạng vé đang bán và có ghế trước khi công bố sự kiện.",
  CATEGORY_NOT_FOUND: "Không tìm thấy danh mục.",
  CATEGORY_NAME_EXISTS: "Tên danh mục đã tồn tại.",
  CATEGORY_IN_USE: "Danh mục đang được sự kiện sử dụng nên không xóa được.",
  EVENT_SERVICE_UNAVAILABLE: "Dịch vụ sự kiện đang bận. Vui lòng thử lại sau.",

  // Ticket Service
  TICKET_TYPE_NOT_FOUND: "Không tìm thấy hạng vé.",
  TICKET_TYPES_ALREADY_EXIST: "Sự kiện đã có hạng vé này.",
  SEAT_LAYOUT_LOCKED: "Không đổi được sơ đồ vì đã có ghế từng được giữ hoặc bán.",
  TICKET_NOT_FOUND: "Không tìm thấy vé.",
  INVALID_QR: "Mã QR không hợp lệ.",
  TICKET_WRONG_EVENT: "Vé này thuộc sự kiện khác.",
  INVALID_TICKET_STATE: "Vé đã được quét hoặc không còn hiệu lực.",
  TICKET_ALREADY_CHECKED_IN: "Đơn có vé đã được sử dụng nên không hoàn tiền được.",
  SCAN_OUTSIDE_EVENT_TIME: "Chỉ quét được từ 60 phút trước giờ bắt đầu đến khi sự kiện kết thúc.",
  TICKET_SERVICE_UNAVAILABLE: "Dịch vụ vé đang bận. Vui lòng thử lại sau.",

  // Order Service
  ORDER_NOT_FOUND: "Không tìm thấy đơn hàng.",
  ORDER_NOT_PAYABLE: "Đơn này không còn ở trạng thái chờ thanh toán.",
  IDEMPOTENCY_KEY_REUSED: "Yêu cầu bị trùng. Vui lòng tải lại trang rồi thử lại.",
  PAYMENT_PROVIDER_UNAVAILABLE: "Cổng thanh toán VNPay đang bận. Vui lòng thử lại sau ít phút.",
  ORDER_NOT_REFUNDABLE: "Chỉ hoàn được đơn đã thanh toán và đã phát vé.",
  REFUND_ALREADY_EXISTS: "Đơn này đã có yêu cầu hoàn tiền.",
  REFUND_WINDOW_EXPIRED: "Chỉ hoàn tiền khi còn ít nhất 48 giờ trước giờ bắt đầu sự kiện.",
  REFUND_NOT_FOUND: "Không tìm thấy yêu cầu hoàn tiền.",
  INVALID_REFUND_STATE: "Yêu cầu hoàn tiền đã được xử lý hoặc đang xử lý.",
  PAYMENT_NOT_FOUND: "Không tìm thấy giao dịch thanh toán.",
  PAYMENT_NOT_RECONCILABLE: "Giao dịch này không nằm trong danh sách cần đối soát.",

  // Notification Service
  NOTIFICATION_NOT_FOUND: "Không tìm thấy thông báo.",
  TEMPLATE_NOT_FOUND: "Không tìm thấy mẫu thông báo.",

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

/** Lỗi nghiệp vụ mang `code` theo hợp đồng ErrorResponse; message đã là câu tiếng Việt cho người dùng. */
export class ApiError extends Error {
  readonly code: string;
  /** Chỉ có khi code = SEAT_TAKEN: các ghế đã bị người khác giữ hoặc mua. */
  readonly takenSeatIds: string[];
  /** HTTP status; 0 khi không kết nối được. */
  readonly status: number;
  constructor(code: string, takenSeatIds: string[] = [], status = 0) {
    super(errorMessage(code));
    this.name = "ApiError";
    this.code = code;
    this.takenSeatIds = takenSeatIds;
    this.status = status;
  }
}
