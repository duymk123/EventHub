package com.eventhub.analytics.messaging.event;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Payload topic order.paid để cộng doanh số sau khi Order đã thanh toán và confirm vé thành công. Từng item
 * mang giá và tổng dòng đã snapshot.
 *
 * @param messageId UUID định danh message nguồn, giữ nguyên qua retry/replay để chống xử lý trùng.
 * @param eventType Tên loại sự kiện, cần trùng topic Kafka nhận được.
 * @param occurredAt Thời điểm nghiệp vụ gốc theo UTC.
 * @param eventId UUID sự kiện bán vé mà dữ liệu thuộc về.
 * @param orderId UUID đơn hàng, là Kafka key cho các sự kiện liên quan đơn và phát vé.
 * @param customerId UUID người mua trong snapshot đơn hàng, metadata đi cùng event.
 * @param currency Mã tiền tệ của dữ liệu nguồn, hiện nhận VND.
 * @param totalAmount Tổng tiền đơn từ snapshot nguồn, được đối chiếu với tổng các dòng khi xử lý sales.
 * @param paidAt Thời điểm thanh toán gốc, dùng quy đổi sang ngày báo cáo Việt Nam.
 * @param items Các dòng hạng vé của đơn hoặc khoản hoàn, được validate cả dữ liệu lồng bên trong.
 */
public record OrderPaidEvent(
        @NotNull UUID messageId, @NotBlank String eventType, @NotNull Instant occurredAt, @NotNull UUID eventId,
        @NotNull UUID orderId, @NotNull UUID customerId,
        @Pattern(regexp = "VND") @NotNull String currency,
        @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal totalAmount,
        @NotNull Instant paidAt, @NotEmpty List<@NotNull @Valid Item> items) implements BusinessEvent {
    /**
     * Trả orderId để đối chiếu Kafka key của sự kiện thanh toán.
     */
    @Override public UUID aggregateId() { return orderId; }

    /**
     * Một dòng dữ liệu hạng vé trong event.
     *
     * @param ticketTypeId UUID hạng vé, là khóa nhóm số liệu theo từng hạng.
     * @param ticketTypeName Tên hạng vé được snapshot, nhận VIP, PREMIUM hoặc STANDARD.
     * @param quantity Số lượng vé của dòng, cần là số nguyên dương.
     * @param unitPrice Giá mua một vé trong snapshot nguồn.
     * @param lineTotal Thành tiền dòng, Analytics kiểm tra bằng unitPrice nhân quantity.
     */
    public record Item(
            @NotNull UUID ticketTypeId, @NotNull @Pattern(regexp = "VIP|PREMIUM|STANDARD") String ticketTypeName,
            @Positive int quantity,
            @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal unitPrice,
            @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal lineTotal) {}
}
