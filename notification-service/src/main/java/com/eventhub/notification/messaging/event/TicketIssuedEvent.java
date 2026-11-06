package com.eventhub.notification.messaging.event;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Payload topic ticket.issued chứa snapshot sự kiện và danh sách vé/QR. Handler gom các vé vào một email gửi
 * customerEmail và tạo IN_APP cho ownerUserId.
 *
 * @param messageId UUID định danh message nguồn, giữ nguyên qua retry/replay để chống xử lý trùng.
 * @param eventType Tên loại sự kiện, cần trùng topic Kafka nhận được.
 * @param occurredAt Thời điểm nghiệp vụ gốc theo UTC.
 * @param eventId UUID sự kiện bán vé mà dữ liệu thuộc về.
 * @param orderId UUID đơn hàng, là Kafka key cho các sự kiện liên quan đơn và phát vé.
 * @param ownerUserId UUID owner của vé trong payload, dùng xác định người nhận IN_APP.
 * @param eventTitle Tên sự kiện snapshot để render email vé.
 * @param eventStartTime Thời điểm bắt đầu sự kiện trong snapshot email.
 * @param venue Địa điểm snapshot, handler hiện render chuỗi rỗng khi payload thiếu giá trị.
 * @param customerEmail Snapshot email người mua do producer cung cấp để gửi email.
 * @param tickets Danh sách vé và dữ liệu QR được gom trong email của người mua.
 */
public record TicketIssuedEvent(
        @NotNull UUID messageId, @NotBlank String eventType, @NotNull Instant occurredAt, @NotNull UUID eventId,
        @NotNull UUID orderId, @NotNull UUID ownerUserId,
        @NotBlank @Size(max = 255) String eventTitle, @NotNull Instant eventStartTime,
        @Size(max = 500) String venue,
        @NotBlank @Email @Size(max = 255) String customerEmail,
        @NotEmpty List<@NotNull @Valid Ticket> tickets) implements BusinessEvent {
    /**
     * Trả orderId để kiểm tra Kafka key của lô vé đã phát hành.
     */
    @Override
    public UUID aggregateId() {
        return orderId;
    }

    /**
     * Snapshot một vé cùng token QR và thông tin hiển thị.
     *
     * @param ticketId UUID vé, dùng làm Kafka key của sự kiện quét.
     * @param ticketTypeId UUID hạng vé, là khóa nhóm số liệu theo từng hạng.
     * @param ticketTypeName Tên hạng vé được snapshot, nhận VIP, PREMIUM hoặc STANDARD.
     * @param holderName Tên người sử dụng vé để hiển thị trong email.
     * @param holderEmail Email người sử dụng vé trong payload, luồng hiện tại gửi email về người mua.
     * @param qrCodeValue Token QR opaque đã được Ticket cấp, dùng mã hóa PNG gửi email.
     */
    public record Ticket(
            @NotNull UUID ticketId, @NotNull UUID ticketTypeId,
            @NotNull @Pattern(regexp = "VIP|PREMIUM|STANDARD") String ticketTypeName,
            @NotBlank @Size(max = 255) String holderName,
            @Email @Size(max = 255) String holderEmail,
            @NotBlank @Size(max = 2048) String qrCodeValue) {
    }
}
