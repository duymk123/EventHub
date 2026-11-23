package com.eventhub.order.common;

/**
 * Mã lỗi Order Service (docs/api/order-service-openapi.yaml, plan Order). Lỗi nghiệp vụ của Ticket khi reserve
 * được giữ nguyên code để client hiển thị đúng (SEAT_TAKEN, SEAT_NOT_FOUND, TICKET_TYPE_NOT_ON_SALE…).
 */
public enum ErrorCode {
    VALIDATION_ERROR(400),
    /** Tài khoản chưa có email đã xác minh nên không snapshot được email người mua (C02). */
    EMAIL_NOT_VERIFIED(403),
    ORDER_NOT_FOUND(404),
    REFUND_NOT_FOUND(404),
    PAYMENT_NOT_FOUND(404),
    /** Mã của Ticket khi hủy vé theo đơn (nội bộ, thành failureReason của refund). */
    TICKET_NOT_FOUND(404),
    EVENT_NOT_FOUND(404),
    TICKET_TYPE_NOT_FOUND(404),
    /** C12: ghế không tồn tại hoặc không thuộc hạng/sự kiện của dòng đặt. */
    SEAT_NOT_FOUND(404),
    /** Mã của Ticket khi confirm (nội bộ, không trả cho client): reservation không còn để phát vé. */
    RESERVATION_NOT_FOUND(404),
    RESERVATION_RELEASED_OR_EXPIRED(409),
    /** C12: ghế đã bị đơn khác giữ hoặc mua; ErrorResponse kèm takenSeatIds. */
    SEAT_TAKEN(409),
    TICKET_TYPE_NOT_ON_SALE(409),
    /** C14: cùng Idempotency-Key nhưng nội dung request khác lần trước. */
    IDEMPOTENCY_KEY_REUSED(409),
    /** C06: đơn không còn ở PENDING_PAYMENT nên không khởi tạo thanh toán được. */
    ORDER_NOT_PAYABLE(409),
    ORDER_EXPIRED(409),
    /** C20: khách đã có một đơn PENDING_PAYMENT cho sự kiện này; thanh toán hoặc hủy đơn đó trước. */
    PENDING_ORDER_EXISTS(409),
    /** C19: chỉ hủy được đơn PENDING_PAYMENT. */
    ORDER_NOT_CANCELLABLE(409),
    CONCURRENT_UPDATE(409),
    /** C04: chỉ hoàn đơn PAID đã phát vé. */
    ORDER_NOT_REFUNDABLE(409),
    /** C04: đơn đã có yêu cầu hoàn (trừ REJECTED). */
    REFUND_ALREADY_EXISTS(409),
    INVALID_REFUND_STATE(409),
    /** Mã của Ticket: có vé của đơn đã được quét nên không hủy cả đơn. */
    TICKET_ALREADY_CHECKED_IN(409),
    /** C21: payment không nằm trong danh sách đối soát. */
    PAYMENT_NOT_RECONCILABLE(409),
    /** C04: còn dưới 48 giờ trước giờ bắt đầu sự kiện. */
    REFUND_WINDOW_EXPIRED(422),
    INTERNAL_ERROR(500),
    TICKET_SERVICE_UNAVAILABLE(503),
    EVENT_SERVICE_UNAVAILABLE(503),
    /** C06: chưa cấu hình VNPay hoặc VNPay lỗi/timeout. */
    PAYMENT_PROVIDER_UNAVAILABLE(503);

    private final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
