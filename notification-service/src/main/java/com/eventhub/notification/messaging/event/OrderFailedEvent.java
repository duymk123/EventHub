package com.eventhub.notification.messaging.event;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Payload topic order.failed do Order phát khi đơn thanh toán thất bại hoặc hết hạn. Notification dùng
 * customerId/customerEmail, reason và totalAmount để tạo thông báo.
 *
 * @param messageId UUID định danh message nguồn, giữ nguyên qua retry/replay để chống xử lý trùng.
 * @param eventType Tên loại sự kiện, cần trùng topic Kafka nhận được.
 * @param occurredAt Thời điểm nghiệp vụ gốc theo UTC.
 * @param eventId UUID sự kiện bán vé mà dữ liệu thuộc về.
 * @param orderId UUID đơn hàng, là Kafka key cho các sự kiện liên quan đơn và phát vé.
 * @param customerId UUID người mua, dùng xác định owner IN_APP của đơn.
 * @param customerEmail Snapshot email người mua do producer cung cấp để gửi email.
 * @param reason Lý do đơn thất bại PAYMENT_FAILED hoặc EXPIRED.
 * @param totalAmount Tổng tiền đơn từ snapshot nguồn, được đối chiếu với tổng các dòng khi xử lý sales.
 */
public record OrderFailedEvent(
        @NotNull UUID messageId, @NotBlank String eventType, @NotNull Instant occurredAt, @NotNull UUID eventId,
        @NotNull UUID orderId, @NotNull UUID customerId,
        @NotBlank @Email @Size(max = 255) String customerEmail,
        @NotNull @Pattern(regexp = "PAYMENT_FAILED|EXPIRED") String reason,
        @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal totalAmount) implements BusinessEvent {
    /**
     * Trả orderId để EventDecoder kiểm tra key của record order.failed.
     */
    @Override public UUID aggregateId() { return orderId; }
}
