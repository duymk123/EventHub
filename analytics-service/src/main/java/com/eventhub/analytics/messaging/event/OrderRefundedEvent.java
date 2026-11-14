package com.eventhub.analytics.messaging.event;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Payload topic order.refunded phục vụ thống kê. Mỗi item cần refundedAmount và tổng phân bổ phải khớp amount
 * của message.
 *
 * @param messageId UUID định danh message nguồn, giữ nguyên qua retry/replay để chống xử lý trùng.
 * @param eventType Tên loại sự kiện, cần trùng topic Kafka nhận được.
 * @param occurredAt Thời điểm nghiệp vụ gốc theo UTC.
 * @param eventId UUID sự kiện bán vé mà dữ liệu thuộc về.
 * @param orderId UUID đơn hàng, là Kafka key cho các sự kiện liên quan đơn và phát vé.
 * @param refundId UUID yêu cầu hoàn tiền, phục vụ đối soát kết quả refund.
 * @param customerId UUID người mua trong snapshot đơn hàng, metadata đi cùng event.
 * @param currency Mã tiền tệ của dữ liệu nguồn, hiện nhận VND.
 * @param amount Tổng tiền đã hoàn, Analytics đối chiếu với tổng refundedAmount từng hạng.
 * @param refundedAt Thời điểm hoàn tiền, dùng xác định ngày ghi nhận khoản hoàn.
 * @param items Các dòng hạng vé của đơn hoặc khoản hoàn, được validate cả dữ liệu lồng bên trong.
 */
public record OrderRefundedEvent(
        @NotNull UUID messageId, @NotBlank String eventType, @NotNull Instant occurredAt, @NotNull UUID eventId,
        @NotNull UUID orderId, @NotNull UUID refundId, @NotNull UUID customerId,
        @NotNull @Pattern(regexp = "VND") String currency,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 2) BigDecimal amount,
        @NotNull Instant refundedAt, @NotEmpty List<@NotNull @Valid Item> items) implements BusinessEvent {
    /**
     * Trả orderId để đối chiếu Kafka key của sự kiện hoàn tiền.
     */
    @Override public UUID aggregateId() { return orderId; }

    /**
     * Một dòng dữ liệu hạng vé trong event.
     *
     * @param ticketTypeId UUID hạng vé, là khóa nhóm số liệu theo từng hạng.
     * @param ticketTypeName Tên hạng vé được snapshot, nhận VIP, PREMIUM hoặc STANDARD.
     * @param quantity Số lượng vé của dòng, cần là số nguyên dương.
     * @param refundedAmount Tiền hoàn được phân bổ cho hạng vé hoặc tổng tiền hoàn của dòng báo cáo.
     */
    public record Item(
            @NotNull UUID ticketTypeId, @NotNull @Pattern(regexp = "VIP|PREMIUM|STANDARD") String ticketTypeName,
            @Positive int quantity,
            @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal refundedAmount) {}
}
